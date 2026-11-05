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
import androidx.core.content.edit
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Log
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.db.GramophoneDatabase
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer

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

    enum class SaveMode {
        ALL, QUEUE, QUEUE_METADATA, ALL_QUEUE_METADATA
    }

    /**
     * Save all queue data. Same as legacy behaviour
     */
    @Deprecated("This function nukes the database and rewrites it. It is highly recommended not to use this.")
    fun saveAll() = save(SaveMode.ALL)

    /**
     * Save all queues (excl. media items)
     */
    fun saveAllMqMetadata() = save(SaveMode.ALL_QUEUE_METADATA)

    /**
     * Save current queue (incl. media items)
     */
    fun saveQueue(id: Long? = null) = save(SaveMode.QUEUE, id)

    /**
     * Save current queue (excl. media items)
     */
    fun saveMqMetadata(id: Long? = null) = save(SaveMode.QUEUE_METADATA, id)

    private fun save(mode: SaveMode, id: Long? = null) {
        if (!allowSavingState) {
            Log.i(TAG, "skipped save")
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "dumping playlist... Mode=$mode")
        }

        val playbackParameters = controller.playbackParameters
        val activeQueue = controller.getActiveQueue().copy()
        if (mode == SaveMode.ALL) {
            job?.cancel()
        }
        job = CoroutineScope(Dispatchers.Default).launch {
            if (mode != SaveMode.ALL) {
                job?.join()
            }

            val q = if (id == null) {
                activeQueue
            } else {
                controller.queueBoard.getInactiveQueues().find { it.id == id }
            }

            when(mode) {
                SaveMode.QUEUE -> {
                    if (q == null) {
                        throw IllegalArgumentException("trying to save non-existent queue: $id")
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "saving playlist $q")
                    }
                    database.saveQueue(q, id == null)
                }
                SaveMode.QUEUE_METADATA -> {
                    if (q == null) {
                        throw IllegalArgumentException("trying to save non-existent queue: $id")
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "saving playlist $q")
                    }
                    database.updateMq(q, id == null)
                }
                SaveMode.ALL_QUEUE_METADATA -> {
                    val queues = controller.queueBoard.getInactiveQueues()
                    if (BuildConfig.DEBUG) {
                        queues.forEach { Log.d(TAG, "saving playlist $it") }
                    }
                    database.updateMqs(queues, activeQueue.copy())
                }
                SaveMode.ALL -> {
                    // this the only case a hard reset is intended
                    val queues = controller.queueBoard.getInactiveQueues() + activeQueue
                    database.deleteAllQueues()
                    if (BuildConfig.DEBUG) {
                        queues.forEach { Log.d(TAG, "saving playlist $it") }
                    }
                    queues.forEach {
                        database.saveQueue(it, it == activeQueue)
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                val mqs = database.readQueues()
                Log.d(TAG, "Saved queues. Queues before ${1 + controller.queueBoard.getInactiveQueues().size}, queues after ${mqs.second.size + if (mqs.first == null) 0 else 1}")
                /*
                (controller.queueBoard.getInactiveQueues() + activeQueue).forEach {
                    Log.i(TAG, "old: " + it)
                }
                (mqs.second +mqs.first).mapNotNull {
                    Log.i(TAG, "new: " + it)
                }
                 */
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
        val isOriginal: Boolean, val repeatMode: Int, val shuffle: Boolean,
        val playbackParameters: PlaybackParameters
    )

    suspend fun restore(callback: suspend (RestoredPlaylist?) -> Unit) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "decoding playlist...")
        }
        withContext(Dispatchers.Default) {
            try {
                val queues = database.readQueues()
                val inactiveQueues = queues.second
                val activeQueue = if (queues.first != null) {
                    queues.first
                } else if (queues.second.isEmpty()) {
                    Log.d(TAG, "No queues to load")
                    null
                } else {
                    Log.w(TAG, "Active queue is not found, loading an inactive queue as active")
                    inactiveQueues.removeAt(0)
                }

                inactiveQueues.forEach {
                    controller.queueBoard.masterQueues.add(it)
                }
                if (activeQueue == null) {
                    callback(null)
                    return@withContext
                }

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Active queue: $activeQueue")
                    Log.d(TAG, "Inactive queues: ${inactiveQueues.size}")
                }

                val repeatMode = activeQueue.repeatMode
                var shuffleModeEnabled = activeQueue.shuffleModeEnabled
                var seed = activeQueue.shuffleOrder
                val pinned = activeQueue.expiry == null
                val ended = activeQueue.ended
                val isOriginal = activeQueue.isOriginal
                val playbackParameters = PlaybackParameters(
                    prefs.getFloat("speed", 1f),
                    prefs.getFloat("pitch", 1f)
                )

                val data = MediaItemsWithStartPosition(
                    activeQueue.queue,
                    activeQueue.startIndex,
                    activeQueue.startPositionMs
                )
                if (shuffleModeEnabled && seed!!.data != null && seed.data.size != data.mediaItems.size) {
                    Log.w(
                        TAG, "Bad shuffle order size ${seed.data.size} for" +
                                " ${data.mediaItems.size} items. Disabling shuffle for this queue.")
                    shuffleModeEnabled = false
                    seed = null
                }
                callback(RestoredPlaylist(data, activeQueue.title, seed, pinned, ended,
                    isOriginal, repeatMode, shuffleModeEnabled, playbackParameters))
                return@withContext
            } catch (e: Exception) {
                Log.e(TAG, Log.getThrowableString(e)!!)
                callback(null)
                return@withContext
            }
        }
    }
}
