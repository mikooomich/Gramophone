package org.akanework.gramophone

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.time.LocalDateTime
import java.time.ZoneOffset

object DataBaseTestData {
    fun genMediaItem(

        chromaprint: String?,
        title: String?,
        artist: String?,
        album: String?,

        uri: Uri,
        id: String? = null,
    ): MediaItem {
        if (chromaprint == null && title == null) throw IllegalArgumentException("chromaprint or title must be defined")
        val metadata = MediaMetadata.Builder()
            .setTitle(title.takeIf { !it.isNullOrBlank() })
            .setArtist(artist.takeIf { !it.isNullOrBlank() })
            .setAlbumTitle(album.takeIf { !it.isNullOrBlank() })

        if (chromaprint != null) {
            metadata.setExtras(Bundle().apply { putString("chromaprint", chromaprint) })
        }

        val mediaItem =  MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata.build())

            id?.let {
                mediaItem.setMediaId(it)
            }

            return mediaItem.build()
    }

    val m1 = genMediaItem(
        chromaprint = null,
        title = "one",
        artist = "Mikooo",
        album = "UwU",
        uri = "/sdcard/music".toUri()
    )
    val m2 = genMediaItem(
        chromaprint = "uwu_rawr",
        title = "two",
        artist = "Mikooo",
        album = "OwO",
        uri = "/sdcard/music".toUri()
    )
    val m3 = genMediaItem(
        chromaprint = "OH WHAT HAVE I DOOOOOOONE",
        title = "three",
        artist = "Evil Mikooo",
        album = "Yes",
        uri = "/sdcard/music".toUri()
    )
}
