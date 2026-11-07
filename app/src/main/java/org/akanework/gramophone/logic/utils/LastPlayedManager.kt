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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.db.GramophoneDatabase
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import java.util.PriorityQueue
import kotlin.time.Duration.Companion.milliseconds

class LastPlayedManager(
    context: Context,
    private val controller: EndedWorkaroundPlayer,
    val database: GramophoneDatabase,
) {

    companion object {
        private const val TAG = "LastPlayedManager"
    }

    val coroutineScope = CoroutineScope(Dispatchers.IO)
    var allowSavingState = Mutex()
    private val prefs by lazy { context.getSharedPreferences("LastPlayedManager", 0) }

    val queueJobs = PriorityQueue<PriorityJob>()
    val mqJobs = PriorityQueue<PriorityJob>()

    class PriorityJob(val priority: Int, val id: Long?, val job: Job) : Comparable<PriorityJob> {
        override fun compareTo(other: PriorityJob): Int = this.priority - other.priority
    }

    // TODO(mq): explain how the queues to save queues work, the looper, etc
    enum class SaveMode {
        ALL, QUEUE, // queueJobs
        ALL_QUEUE_METADATA, QUEUE_METADATA, // mqJobs
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
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "dumping playlist & submitting mode=$mode...")
        }

        val playbackParameters = controller.playbackParameters
        val activeQueue = controller.getActiveQueue().copy()
        val inactiveQueues = controller.queueBoard.getInactiveQueues().toImmutableList()

        val q = if (id == null) {
            activeQueue
        } else {
            inactiveQueues.find { it.id == id }
        }

        fun onSave() {
            if (BuildConfig.DEBUG) {
                val mqs = database.readQueues()
                Log.d(
                    TAG,
                    "Saved queues. Queues before ${1 + inactiveQueues.size}, queues after ${mqs.second.size + if (mqs.first == null) 0 else 1}"
                )
                /*
                (inactiveQueues + activeQueue).forEach {
                    Log.i(TAG, "old: " + it)
                }
                (mqs.second + mqs.first).mapNotNull {
                    Log.i(TAG, "new: " + it)
                }
                 */
            }

            prefs.edit {
                putFloat("speed", playbackParameters.speed)
                putFloat("pitch", playbackParameters.pitch)
                apply()
            }
            Log.d(TAG, "Save complete")

        }

        when (mode) {
            SaveMode.QUEUE -> {
                if (q == null) {
                    throw IllegalArgumentException("trying to save non-existent queue: $id")
                }
                queueJobs.add(
                    PriorityJob(1, q.id, coroutineScope.launch(start = CoroutineStart.LAZY) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "mode=$mode: saving playlist $q")
                        }
                        database.saveQueue(q, id == null)
                        onSave()
                        mqJobs.removeIf { q.id == it.id }
                    })
                )
            }

            SaveMode.QUEUE_METADATA -> {
                if (q == null) {
                    throw IllegalArgumentException("trying to save non-existent queue: $id")
                }
                mqJobs.add(
                    PriorityJob(1, q.id, coroutineScope.launch(start = CoroutineStart.LAZY) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "mode=$mode: saving playlist $q")
                        }
                        database.updateMq(q, id == null)
                        onSave()
                    })
                )
            }

            SaveMode.ALL_QUEUE_METADATA -> {
                mqJobs.add(
                    PriorityJob(0, null, coroutineScope.launch(start = CoroutineStart.LAZY) {
                        val queues = inactiveQueues
                        if (BuildConfig.DEBUG) {
                            queues.forEach { Log.d(TAG, "mode=$mode: saving playlist $it") }
                        }
                        database.updateMqs(queues, activeQueue.copy())
                        onSave()
                        mqJobs.clear()
                    })
                )
            }

            SaveMode.ALL -> {
                // this the only case a hard reset is intended
                queueJobs.add(
                    PriorityJob(0, null, coroutineScope.launch(start = CoroutineStart.LAZY) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "Nuking and saving all queues")
                        }
                        val queues = inactiveQueues + activeQueue
                        database.deleteAllQueues()
                        if (BuildConfig.DEBUG) {
                            queues.forEach { Log.d(TAG, "mode=$mode: saving playlist $it") }
                        }
                        queues.forEach {
                            database.saveQueue(it, it == activeQueue)
                        }
                        onSave()
                        queueJobs.clear()
                    })
                )
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (allowSavingState.isLocked) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Database save task is already active, aborting")
                }
                return@launch
            }

            Log.d(TAG, "Starting database save looper...")

            // run until there are no more tasks left
            allowSavingState.withLock {
                while (!queueJobs.isEmpty() || !mqJobs.isEmpty()) {
                    delay(1000L.milliseconds)

                    Log.d(TAG, "Running database save task")

                    // saving songs nukes the queue entity in the process, about it shouldn't matter since are same queue object
                    val jobs: PriorityQueue<PriorityJob> = if (!queueJobs.isEmpty()) {
                        queueJobs
                    } else if (!mqJobs.isEmpty()) {
                        mqJobs
                    } else {
                        PriorityQueue()
                    }

                    if (!jobs.isEmpty()) {
                        var items = jobs.toList().dropLast(1)
                        val job = jobs.last()
                        job.job.start()
                        job.job.join()
                        jobs.clear()
                        items = items.filterNot { job.id == it.id && it.id != null }
                        jobs.addAll(items)
                    }
                }
            }

            Log.d(TAG, "Exiting database save looper")
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
