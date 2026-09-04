/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Log
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.db.GramophoneDatabase
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import java.nio.charset.StandardCharsets

class LastPlayedManager(
    context: Context,
    private val controller: EndedWorkaroundPlayer,
    val database: GramophoneDatabase,
) {

    companion object {
        private const val TAG = "LastPlayedManager"
    }

    var allowSavingState = true
    private var job: Job? = null
    private val prefs by lazy { context.getSharedPreferences("LastPlayedManager", 0) }

    fun eraseShuffleOrder() {
        prefs.edit(commit = true) {
            putString("shuffle_persist", null)
        }
    }

    enum class SaveMode {
        ALL, CURRENT_QUEUE, CURRENT_QUEUE_METADATA, ALL_QUEUE_METADATA
    }

    fun saveAll() = save(SaveMode.ALL)
    fun saveAllQueueMetadata() = save(SaveMode.ALL_QUEUE_METADATA)
    fun saveCurrentQueue() = save(SaveMode.CURRENT_QUEUE)
    fun saveCurrentQueueMetadataOnly() = save(SaveMode.CURRENT_QUEUE_METADATA)

    private fun save(mode: SaveMode) {
        if (!allowSavingState) {
            Log.i(TAG, "skipped save")
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "dumping playlist... Mode=$mode")
        }

        val playbackParameters = controller.playbackParameters
        val activeQueue = controller.getActiveQueue().copy()
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "saving playlist (${activeQueue.queue.size} items, repeat ${activeQueue.repeatMode}, " +
                            "shuffle ${activeQueue.shuffleModeEnabled}, ended ${activeQueue.ended})..."
                )
            }

            when(mode) {
                SaveMode.CURRENT_QUEUE -> {
                    database.saveQueue(activeQueue, true)
                }
                /*
                SaveMode.CURRENT_QUEUE_METADATA -> {
                    database.updateQueue(activeQueue, true)
                }
                 */
                SaveMode.CURRENT_QUEUE_METADATA, SaveMode.ALL_QUEUE_METADATA -> {
                    val queues = controller.queueBoard.getInactiveQueues() + activeQueue
                    database.updateAllQueues(
                        queues,
                        activeQueue.index
                    )
                }
                else -> {
                    val queues = controller.queueBoard.getInactiveQueues() + activeQueue
                    queues.forEach {
                        database.saveQueue(it, it == activeQueue)
                    }
                }
            }
            prefs.edit {
                putFloat("speed", playbackParameters.speed)
                putFloat("pitch", playbackParameters.pitch)
                apply()
            }
        }
    }

    class RestoredPlaylist(
        val items: MediaItemsWithStartPosition, val title: String,
        val seed: CircularShuffleOrder.Persistent?, val isPinned: Boolean, val isEnded: Boolean,
        val repeatMode: Int, val shuffle: Boolean, val playbackParameters: PlaybackParameters
    )

    suspend fun restore(callback: suspend (RestoredPlaylist?) -> Unit) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "decoding playlist...")
        }
        withContext(Dispatchers.Default) {
            val seed = try {
                CircularShuffleOrder.Persistent.deserialize(
                    prefs.getString(
                        "shuffle_persist",
                        null
                    )
                )
            } catch (e: Exception) {
                eraseShuffleOrder()
                throw e
            }
            try {
                val queues = database.readQueues()
                val activeQueue = queues.first
                queues.second.forEach {
                    controller.queueBoard.masterQueues.add(it)
                }
                if (activeQueue == null) {
                    callback(null)
                    return@withContext
                }

                val repeatMode = activeQueue.repeatMode
                val shuffleModeEnabled = activeQueue.shuffleModeEnabled
                val pinned = activeQueue.expiry == null
                val ended = activeQueue.ended
                val playbackParameters = PlaybackParameters(
                    prefs.getFloat("speed", 1f),
                    prefs.getFloat("pitch", 1f)
                )

                val data = MediaItemsWithStartPosition(
                    activeQueue.queue,
                    activeQueue.startIndex,
                    activeQueue.startPositionMs
                )
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "restoring playlist (${data.mediaItems.size} items, repeat $repeatMode, " +
                                "shuffle $shuffleModeEnabled, ended $ended)..."
                    )
                }
                if (seed.data != null && seed.data.size != data.mediaItems.size)
                    throw IllegalStateException("Bad shuffle order size ${seed.data.size} for" +
                            " ${data.mediaItems.size} items")
                callback(RestoredPlaylist(data, activeQueue.title,
                    seed, pinned, ended, repeatMode, shuffleModeEnabled, playbackParameters))
                return@withContext
            } catch (e: Exception) {
                try {
                    this@LastPlayedManager.eraseShuffleOrder()
                } catch (_: Exception) {
                }
                Log.e(TAG, Log.getThrowableString(e)!!)
                callback(null)
                return@withContext
            }
        }
    }
}

private class SafeDelimitedStringConcat(private val delimiter: String) {
    private val b = StringBuilder()
    private var hadFirst = false

    private fun append(s: String?) {
        if (s?.contains(delimiter, false) == true) {
            throw IllegalArgumentException("argument must not contain delimiter")
        }
        if (hadFirst) {
            b.append(delimiter)
        } else {
            hadFirst = true
        }
        s?.let { b.append(it) }
    }

    override fun toString(): String {
        return b.toString()
    }

    fun writeStringUnsafe(s: CharSequence?) = append(s?.toString())
    fun writeBase64(b: ByteArray?) = append(b?.let { Base64.encodeToString(it, Base64.DEFAULT) })
    fun writeStringSafe(s: CharSequence?) =
        writeBase64(s?.toString()?.toByteArray(StandardCharsets.UTF_8))

    fun writeInt(i: Int?) = append(i?.toString())
    fun writeLong(i: Long?) = append(i?.toString())
    fun writeBool(b: Boolean?) = append(b?.toString())
    fun writeUri(u: Uri?) = writeStringSafe(u?.toString())
    fun skip() = append(null)
}

private class SafeDelimitedStringDecat(delimiter: String, str: String) {
    private val items = str.split(delimiter)
    private var pos = 0

    private fun read(): String? {
        if (pos == items.size) return null
        return items[pos++].ifEmpty { null }
    }

    fun readStringUnsafe(): String? = read()
    fun readBase64(): ByteArray? = read()?.let { Base64.decode(it, Base64.DEFAULT) }
    fun readStringSafe(): String? = readBase64()?.toString(StandardCharsets.UTF_8)
    fun readInt(): Int? = read()?.toInt()
    fun readLong(): Long? = read()?.toLong()
    fun readBool(): Boolean? = read()?.toBooleanStrict()
    fun readUri(): Uri? = readStringSafe()?.toUri()
    fun skip() {
        read()
    }
}

private object PrefsListUtils {
    fun parse(stringSet: Set<String>, groupStr: String): List<String> {
        if (groupStr.isEmpty()) return emptyList()
        val groups = groupStr.split(",")
        return groups.map { hc ->
            stringSet.firstOrNull { it.hashCode().toString() == hc }
                ?: throw NoSuchElementException(
                    "tried to find \"$hc\" (from \"$groupStr\") in: " +
                            stringSet.joinToString { it.hashCode().toString() })
        }
    }

    fun dump(inList: List<String>): Pair<Set<String>, String> {
        val list = inList.map { it.trim() }
        return Pair(list.toSet(), list.joinToString(",") { it.hashCode().toString() })
    }
}