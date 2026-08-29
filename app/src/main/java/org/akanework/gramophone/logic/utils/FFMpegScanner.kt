package org.akanework.gramophone.logic.utils

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.net.toUri
import org.akanework.gramophone.db.entities.SongEntity
import wah.mikooomich.ffMetadataEx.AudioMetadata
import wah.mikooomich.ffMetadataEx.FFMetadataEx
import wah.mikooomich.ffMetadataEx.FFmpegWrapper
import java.io.File
import java.lang.Integer.parseInt
import java.time.LocalDate
import java.time.LocalDateTime

const val toSeconds = 1000 * 60 * 16.7 // convert FFmpeg duration to seconds
const val EXTRACTOR_TAG = "FFmpegScanner"
const val EXTRACTOR_DEBUG = false
const val DEBUG_SAVE_OUTPUT = false
val ARTIST_SEPARATORS =
    Regex("\\s*;\\s*|\\s*ft\\.\\s*|\\s*feat\\.\\s*|\\s*&\\s*|\\s*,\\s*", RegexOption.IGNORE_CASE)

class FFmpegScanner(val context: Context) {
    // load advanced scanner libs
    init {
//        System.loadLibrary("avcodec")
//        System.loadLibrary("avdevice")
//        System.loadLibrary("avfilter")
//        System.loadLibrary("avformat")
//        System.loadLibrary("avutil")
//        System.loadLibrary("swresample")
//        System.loadLibrary("swscale")
        System.loadLibrary("ffMetadataEx")
    }

    /**
     * Given a path to a file, extract necessary metadata.
     *
     * @param file Full file path
     */
    fun getAllMetadataFromFile(file: File): Pair<SongEntity, String?> {
        if (EXTRACTOR_DEBUG)
            Log.v(EXTRACTOR_TAG, "Starting Full Extractor session on: ${file.absolutePath}")

        val ffmpeg = FFmpegWrapper()

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val data: AudioMetadata? = ffmpeg.getFullAudioMetadata(fd.dup().detachFd())

            if (data == null) {
                Log.e(EXTRACTOR_TAG, "Fatal extraction error")
                throw RuntimeException("Fatal FFmpeg scanner extraction error")
            }
            if (data.status != 0) {
                throw RuntimeException("Fatal FFmpeg scanner extraction error. Status: ${data.status}")
            }
            if (EXTRACTOR_DEBUG && DEBUG_SAVE_OUTPUT) {
                Log.v(EXTRACTOR_TAG, "Full output for: ${file.absolutePath} \n ${data.extrasRaw.joinToString { "$it\n" }}")
            }

            val songId = -1L
            var acoustid: String? = null
            var rawTitle: String? = data.title
            val rawArtists: String? = data.artist
            var albumName: String? = data.album
            var rawDate: String? = null

            var artistList: MutableList<String> = ArrayList()
            var genresList: MutableList<String> = ArrayList()

            var extraData: String = "" // extra data field

            // read extra data from FFmpeg
            // album, artist, genre, title all have their own fields, but it is not detected for all songs. We use the
            // extra values to supplement those.
            data.extrasRaw.forEach {
                val tag = it.substringBefore(':').trim()
                when (tag) {
                    // why the fsck does an error here get swallowed silently????
                    "ALBUM", "album" -> {
                        if (albumName == null) {
                            albumName = it.substringAfter(':').trim()
                        }
                    }

                    "ARTISTS", "ARTIST", "artist" -> {
                        val splitArtists = it.split(ARTIST_SEPARATORS)
                        splitArtists.forEach { artistVal ->
                            artistList.add(
                                artistVal.substringAfter(':').trim(),
                            )
                        }
                    }

                    "DATE", "date" -> rawDate = it.substringAfter(':').trim()

                    "GENRE", "genre" -> {
                        val splitGenres = it.split(ARTIST_SEPARATORS)
                        splitGenres.forEach { genreVal ->
                            genresList.add(
                                genreVal.substringAfter(':').trim(),
                            )
                        }
                    }

                    "TITLE", "title" -> {
                        if (rawTitle == null) {
                            rawTitle = it.substringAfter(':').trim()
                        }
                    }

                    "ACOUSTID_FINGERPRINT", "Acoustid Fingerprint" -> {
                        acoustid = it.substringAfter(':').trim()
                    }

                    else -> {
                        extraData += "$it\n"
                    }
                }
            }


            /**
             * These vars need a bit more parsing
             */

            val title: String =
                if (rawTitle != null && !rawTitle.isBlank()) { // songs with no title tag
                    rawTitle.trim()
                } else {
                    file.absolutePath
                }

            // should never be invalid if scanner even gets here fine...

            /**
             * Parse the more complicated structures
             */
            var year: Int? = null
            var date: LocalDateTime? = null

            // parse date and year
            try {
                if (rawDate != null) {
                    try {
                        date = LocalDate.parse(rawDate.substringAfter(';').trim()).atStartOfDay()
                    } catch (e: Exception) {
                    }

                    year = date?.year ?: parseInt(rawDate.trim())
                }
            } catch (e: Exception) {
                // user error at this point. I am not parsing all the weird ways the string can come in
            }

            // parse artist
            rawArtists?.split(ARTIST_SEPARATORS)?.forEach { element ->
                val artistVal = element.trim()
                artistList.add(artistVal)
            }

            artistList =
                artistList.filterNot { it.isBlank() }.distinctBy { it.lowercase() }.toMutableList()


            return Pair(SongEntity(
                id = songId,
                uri = file.absolutePath,
                title = title,
                artist = artistList.joinToString("; "),
                album = albumName,
                year = year
            ), acoustid)
        }
    }

    companion object {
        const val VERSION_STRING = "${FFMetadataEx.VERSION_NAME} (${FFMetadataEx.VERSION_CODE})"
    }
}