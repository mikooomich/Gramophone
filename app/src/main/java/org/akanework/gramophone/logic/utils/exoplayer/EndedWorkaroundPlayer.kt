/*
 *     Copyright (C) 2024 nift4
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

package org.akanework.gramophone.logic.utils.exoplayer

import android.os.Bundle
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.MultiQueueObject
import org.akanework.gramophone.logic.QueueBoard
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.json.JSONObject
import uk.akane.libphonograph.items.EXTRA_HD_ARTWORK_URI
import uk.akane.libphonograph.items.hdArtworkUri
import java.util.Objects


/**
 * If player in STATE_ENDED is resumed, state will be STATE_READY, on play button press it will
 * update to STATE_ENDED and only then media3 will wrap around playlist for us. This is a workaround
 * to restore STATE_ENDED as well and fake it for media3 until it indeed wraps around playlist.
 */
class EndedWorkaroundPlayer(
    exoPlayer: ExoPlayer,
    private val getLyric: () -> SemanticLyrics?,
    val queueBoard: QueueBoard
) : ForwardingSimpleBasePlayer(exoPlayer),
    Player.Listener {

    companion object {
        private const val TAG = "EndedWorkaroundPlayer"

    }

    private val remoteDeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()

    init {
        player.addListener(this)
    }

    val exoPlayer
        get() = player as ExoPlayer

    var nextShuffleOrder:
            ((firstIndex: Int, mediaItemCount: Int, EndedWorkaroundPlayer) -> CircularShuffleOrder)? =
        null
    var currentTitle: String? = null
    var currentIsPinned = false
    var currentIsOriginal = false
    private var isEnded = false
        set(value) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "isEnded set to $value (was $field)")
            }
            field = value
        }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == DISCONTINUITY_REASON_SEEK) {
            isEnded = false
        }
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    fun updateLyricNow() {
        if (BuildConfig.APPLICATION_ID == "com.tencent.qqmusic") {
            invalidateState()
        }
    }

    override fun getState(): State {
        var superState = super.state
        if (superState.currentMetadata.artworkUri != null &&
            superState.currentMetadata.hdArtworkUri != null) {
            superState = superState.buildUpon()
                .setPlaylist(superState.timeline, superState.currentTracks,
                    superState.currentMetadata.buildUpon()
                        .setArtworkUri(superState.currentMetadata.hdArtworkUri)
                        .setExtras(Bundle(superState.currentMetadata.extras!!).apply {
                            remove(EXTRA_HD_ARTWORK_URI)
                        })
                        .build())
                .build()
        }
        if (BuildConfig.APPLICATION_ID == "com.tencent.qqmusic") {
            // Oplus uses package name whitelist for their lockscreen lyric feature
            val lyric = getLyric()
            if (lyric != null && lyric is SemanticLyrics.SyncedLyrics) {
                superState = superState.buildUpon()
                    .setPlaylist(superState.timeline, superState.currentTracks,
                        superState.currentMetadata.buildUpon()
                            .setExtras((superState.currentMetadata.extras?.let { Bundle(it) }
                                ?: Bundle()).apply {
                                putString("lyricInfo", JSONObject().apply {
                                    put("songName", superState.currentMetadata.title)
                                    put("artist", superState.currentMetadata.artist)
                                    // Put lyric hash code into songId as well to be able to reset
                                    // lyrics if they load late or get changed.
                                    put("songId", superState.playlist.getOrNull(
                                        superState.currentMediaItemIndex)?.mediaItem?.mediaId
                                        .toString() + Objects.toIdentityString(lyric))
                                    // This can parse some odd Netease-specific JSON list or normal
                                    // LRC without bells and whistles (fwiw, the Netease format is
                                    // not even better than plain LRC), no word sync as of right now
                                    put("lyric", lyric.text.joinToString(
                                        "\n") {
                                        val s = it.start.toLong() / 1000
                                        "[%02d:%02d.%02d]".format(s / 60, s % 60,
                                            (it.start.toLong() % 1000) / 10) + it.text
                                    })
                                }.toString())
                            }).build()).build()
            }
        }
        if (isEnded) {
            if (superState.playerError != null) {
                isEnded = false
                return superState
            }
            return superState.buildUpon().setPlaybackState(STATE_ENDED).setIsLoading(false).build()
        }
        if (player.currentTimeline.isEmpty) {
            return superState.buildUpon().setDeviceInfo(remoteDeviceInfo).build()
        }
        return superState
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        currentIsOriginal = false
        return super.handleAddMediaItems(index, mediaItems)
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int
    ): ListenableFuture<*> {
        currentIsOriginal = false
        return super.handleMoveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> {
        currentIsOriginal = false
        return super.handleReplaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        currentIsOriginal = false
        if (fromIndex == 0 && toIndex == Int.MAX_VALUE) { // clearMediaItems() -> delete queue
            currentTitle = null
        }
        return super.handleRemoveMediaItems(fromIndex, toIndex)
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        if (mediaItems.isEmpty()) return Futures.immediateVoidFuture()
        val nextTitle = mediaItems.firstOrNull()?.mediaMetadata?.extras?.getString("mq_title")
        val mediaItems = mediaItems.toMutableList().apply {
            this[0] = this[0].buildUpon().setMediaMetadata(
                this[0].mediaMetadata.buildUpon()
                    .setExtras(Bundle(this[0].mediaMetadata.extras!!).apply {
                        // Remove mq_title extra as this is purely for transport to here
                        if (nextTitle != null) {
                            remove("mq_title")
                        }
                    }).build()
            ).build()
        }

        return if (nextTitle != null) {
            Log.d(TAG, "handleSetMediaItems has been hijacked")
            setCurrQueueGen2(
                mediaItems = mediaItems,
                startIndex = startIndex,
                startPositionMs = startPositionMs,
                nextTitle = nextTitle,
                nextIsPinned = null,
                nextIsOriginal = true,
                nextRepeatMode = null,
                nextShuffleOrder = null
            )
            Futures.immediateVoidFuture()
        } else {
            super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
        }
    }


    /**
     * Push this queue to the player, and save the player queue back to QueueBoard.
     *
     * @param index
     */
    fun commitQueue(
        index: Int,
        startIndex: Int? = null,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "commitQueue() called")
        }
        if (index < 0 || index >= queueBoard.masterQueues.size) {
            Log.w(
                TAG,
                "commitQueue() index $index out of bounds (size = ${queueBoard.masterQueues.size}). Aborting"
            )
            return
        }

        val mq = queueBoard.masterQueues[index]
        setCurrQueueGen2(mq, startIndex)
    }

    fun setCurrQueueGen2(
        mq: MultiQueueObject,
        startIndex: Int? = null,
    ) = setCurrQueueGen2(
        mq.queue,
        startIndex ?: mq.startIndex,
        mq.startPositionMs,
        mq.title,
        mq.expiry.value == null,
        mq.isOriginal,
        mq.repeatMode,
        mq.shuffleOrder,
    )

    /**
     * Load a new queue into the player. Calling this function will automatically handle saving the
     * existing active queue from the player, and subsequently loading the new inactive queue, and
     * updating the active queue metadata in EWP. When nextTitle is same as the currentTitle, this
     * function will not interact with QueueBoard. Furthermore, in special cases (where startIndex
     * is the same song as the currently playing song) a seamless transition without interrupting
     * playback is possible. 
     *
     * @param mediaItems
     * @param startIndex
     * @param startPositionMs
     * @param nextTitle
     * @param nextIsPinned True or False are acceptable values. null for a no-op.
     * @param nextIsOriginal True or False are acceptable values. null for a no-op.
     * @param nextShuffleOrder Specify a shuffle order. null for a no-op.
     */
    fun setCurrQueueGen2(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
        nextTitle: String,
        nextIsPinned: Boolean?,
        nextIsOriginal: Boolean?,
        nextRepeatMode: (@Player.RepeatMode Int)?,
        nextShuffleOrder: CircularShuffleOrder.Persistent?,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "setCurrQueueGen2() called")
        }

        // different queue, do all this bs
        if (currentTitle != nextTitle) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "setCurrQueueGen2: Different queue detected")
            }
            // nuke old inactive queue with same name, save current player queue to qb
            queueBoard.deleteQueue(nextTitle)
            if (currentTitle != null && exoPlayer.mediaItemCount > 0) {
                queueBoard.addQueue(
                    currentTitle!!,
                    ArrayList<MediaItem>(exoPlayer.mediaItemCount).apply {
                        for (i in 0..<exoPlayer.mediaItemCount) {
                            add(exoPlayer.getMediaItemAt(i))
                        }
                    },
                    exoPlayer.currentMediaItemIndex,
                    exoPlayer.currentPosition,
                    currentIsPinned,
                    currentIsOriginal,
                    repeatMode,
                    if (shuffleModeEnabled) {
                        CircularShuffleOrder.Persistent(
                            exoPlayer.shuffleOrder as
                                    CircularShuffleOrder
                        )
                    } else {
                        null
                    },
                    exoPlayer.playbackState == STATE_ENDED,
                )
            }

            // load current queue into player
            currentTitle = nextTitle
            nextIsPinned?.let {
                currentIsPinned = it
            }
            nextIsOriginal?.let {
                currentIsOriginal = it
            }
            nextRepeatMode?.let {
                repeatMode = it
            }
            this@EndedWorkaroundPlayer.nextShuffleOrder = nextShuffleOrder?.toFactory()
            shuffleModeEnabled = nextShuffleOrder != null
            setMediaItems(mediaItems, startIndex, startPositionMs)
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "setCurrQueueGen2: Same queue detected")
            }
            // same queue, jump to position, or seamless edit queue
            val seamlessSupported = (startIndex < mediaItems.size)
                    && currentMediaItem?.mediaId == mediaItems[startIndex].mediaId

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "setCurrQueueGen2: Setting media items. seamless=$seamlessSupported")
            }
            if (seamlessSupported) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "setCurrQueueGen2: Trying seamless queue switch. Is first song?: ${startIndex == 0}")
                }
                val playerIndex = currentMediaItemIndex

                handleReplaceMediaItems(
                    playerIndex,
                    playerIndex + 1,
                    listOf(mediaItems[playerIndex])
                ) // update current's metadata
                if (startIndex == 0) {
                    // remove all songs before the currently playing one and then replace all the items after
                    if (playerIndex > 0) {
                        removeMediaItems(0, playerIndex)
                    }
                    handleReplaceMediaItems(1, Int.MAX_VALUE, mediaItems.drop(1))
                } else {
                    // replace items up to current playing, then replace items after current
                    handleReplaceMediaItems(
                        0, playerIndex,
                        mediaItems.subList(0, startIndex)
                    )
                    handleReplaceMediaItems(
                        startIndex + 1, Int.MAX_VALUE,
                        mediaItems.subList(startIndex + 1, mediaItems.size)
                    )
                }

                nextIsPinned?.let {
                    currentIsPinned = it
                }
                nextIsOriginal?.let {
                    currentIsOriginal = it
                }
                nextRepeatMode?.let {
                    repeatMode = it
                }
                nextShuffleOrder?.let {
                    exoPlayer.setShuffleOrder(
                        it.toFactory()(
                            startIndex,
                            mediaItems.size,
                            this@EndedWorkaroundPlayer
                        )
                    )
                }
                shuffleModeEnabled = nextShuffleOrder != null
            } else {
                nextIsPinned?.let {
                    currentIsPinned = it
                }
                nextIsOriginal?.let {
                    currentIsOriginal = it
                }
                nextRepeatMode?.let {
                    repeatMode = it
                }
                this@EndedWorkaroundPlayer.nextShuffleOrder = nextShuffleOrder?.toFactory()
                shuffleModeEnabled = nextShuffleOrder != null
                setMediaItems(mediaItems, startIndex, startPositionMs)
            }
        }

    }
}