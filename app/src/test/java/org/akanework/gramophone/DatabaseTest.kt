package org.akanework.gramophone

import android.os.Bundle
import androidx.core.net.toUri
import junit.framework.TestCase.assertEquals
import org.akanework.gramophone.db.AppDatabase
import org.akanework.gramophone.db.GramophoneDatabase
import org.akanework.gramophone.ui.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.java

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {

    private lateinit var db: GramophoneDatabase
    private lateinit var dao: GramophoneDatabase

    fun genDb1() {
        db.recordEvent(
            mediaItem = DataBaseTestData.m1,
            timestamp = 1563327152L,
            duration = 20 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = 1563327152L + 123L,
            duration = 21 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m3,
            timestamp = 1563327152L + 1234L,
            duration = 22 * 1000,
        )
    }

    fun genDb2() {
        db.recordEvent(
            mediaItem = DataBaseTestData.m1,
            timestamp = 1563327152L,
            duration = 20 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = 1563327152L + 1234L,
            duration = 21 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = 1563327152L + 12345L,
            duration = 21 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = 1563327152L + 123456L,
            duration = 67 * 1000,
        )
    }

    fun genDb3() {
        db.recordEvent(
            mediaItem = DataBaseTestData.m1,
            timestamp = 1563327152L,
            duration = 20 * 1000,
        )

        db.recordEvent(
            mediaItem = DataBaseTestData.m3,
            timestamp = 1563327152L + 1234L,
            duration = 22 * 1000,
        )
    }

    @Before
    fun setup() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start()
        val activity = controller.get()
        db = AppDatabase.newUnitTestInstance(activity)
        dao = db
    }

    @After
    fun teardown() {
        db.close()
    }


    /**
     * Test adding play counts to the database for new and existing songs
     */
    @Test
    fun testBasicInserts() {
        var result1 = db.dumpSongsWithPlayCount()
        assertEquals(0, result1.size)

        genDb1()
        genDb2()
        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(
            2,
            result1.find { it.song.title == "one" && it.chromaprints.isEmpty() }!!.playCount
        )
        assertEquals(
            4,
            result1.find { it.song.title == "two" && it.chromaprints.any { it.chromaprint == "uwu_rawr" } }!!.playCount
        )
        assertEquals(
            1,
            result1.find { it.song.title == "three" && it.chromaprints.any { it.chromaprint == "OH WHAT HAVE I DOOOOOOONE" } }!!.playCount
        )

        // existing song should only increment
        db.recordEvent(
            mediaItem = DataBaseTestData.m1,
            timestamp = 1784251952L,
            duration = 20 * 1000,
        )
        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(3, result1.find { it.song.title == "one" }!!.playCount)
        assertEquals(4, result1.find { it.song.title == "two" }!!.playCount)
        assertEquals(1, result1.find { it.song.title == "three" }!!.playCount)

        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = 1784251952L + 123L,
            duration = 21 * 1000,
        )
        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(3, result1.find { it.song.title == "one" }!!.playCount)
        assertEquals(5, result1.find { it.song.title == "two" }!!.playCount)
        assertEquals(1, result1.find { it.song.title == "three" }!!.playCount)

        // adding a new media item should be a new entry
        val m4 = DataBaseTestData.genMediaItem(
            chromaprint = "yes",
            title = "four",
            artist = "Evil Mikooo",
            album = "UwU",
            uri = "/sdcard/music".toUri()
        )
        db.recordEvent(
            mediaItem = m4,
            timestamp = 1784251952L + 123L,
            duration = 21 * 1000,
        )
        result1 = db.dumpSongsWithPlayCount()
        assertEquals(4, result1.size)
        assertEquals(3, result1.find { it.song.title == "one" }!!.playCount)
        assertEquals(5, result1.find { it.song.title == "two" }!!.playCount)
        assertEquals(1, result1.find { it.song.title == "three" }!!.playCount)
        assertEquals(1, result1.find { it.song.title == "four" }!!.playCount)
    }

    @Test
    fun testLegacyPlayCounts() {
        genDb1()

        var result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)

        // The assumption is the song already exists in the database
        val s1 = db.getSongById(result1.first { it.song.title == DataBaseTestData.m1.mediaMetadata.title }.song.id)!!

        db.recordEventLegacy(
            mediaItem = DataBaseTestData.m1,
            month = 4,
            year = 2024,
            count = 56
        )

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(1, result1.find { it.song.id == s1.id }!!.playCount)
        assertEquals(56, result1.find { it.song.id == s1.id }!!.playCountLegacy)

        db.recordEventLegacy(
            mediaItem = DataBaseTestData.m1,
            month = 5,
            year = 2024,
            count = 12
        )

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(1, result1.find { it.song.id == s1.id }!!.playCount)
        assertEquals(68, result1.find { it.song.id == s1.id }!!.playCountLegacy)

        try {
            // failure due to bad month
            db.recordEventLegacy(
                mediaItem = DataBaseTestData.m1,
                month = 13,
                year = 2024,
                count = 10000
            )
            assertEquals(1, 0)
        } catch (e: IllegalArgumentException) {
        }

        try {
            db.recordEventLegacy(
                mediaItem = DataBaseTestData.m1,
                month = -1,
                year = 2024,
                count = 40000
            )
            assertEquals(1, 0)
        } catch (e: IllegalArgumentException) {
        }

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(1, result1.find { it.song.id == s1.id }!!.playCount)
        assertEquals(68, result1.find { it.song.id == s1.id }!!.playCountLegacy)


        // duplicate entries will replace existing values
        db.recordEventLegacy(
            mediaItem = DataBaseTestData.m1,
            month = 5,
            year = 2024,
            count = 14
        )

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(1, result1.find { it.song.id == s1.id }!!.playCount)
        assertEquals(70, result1.find { it.song.id == s1.id }!!.playCountLegacy)
    }

    /**
     * Merge songs with same chromaprint
     */
    @Test
    fun testMergeChromaprint1() {
        genDb1()
        genDb2()

        var result1 = db.dumpSongsWithPlayCount()
        db.recordEvent(
            mediaItem = DataBaseTestData.m1.buildUpon().setMediaMetadata(
                DataBaseTestData.m1.mediaMetadata.buildUpon()
                    .setExtras(Bundle().apply { putString("chromaprint", "uwu_rawr") }).build()
            ).build(),
            timestamp = 1563327152L + 1L,
            duration = 25 * 1000,
        )

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(2, result1.size)
        assertEquals(
            7,
            result1.find { it.chromaprints.any { it.chromaprint == "uwu_rawr" } }!!.playCount
        )
        assertEquals(1, result1.find { it.song.title == "three" }!!.playCount)
    }

    /**
     * Merging with a db without songs that share the same chromaprint values should not do anything
     */
    @Test
    fun testMergeChromaprint2() {
        genDb1()
        genDb3()
        var result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(2, result1.find { it.song.title == "one" }!!.playCount)
        assertEquals(2, result1.find { it.song.title == "three" }!!.playCount)
        db.mergeSongsByChromaprint()

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(result1.size, 3)
        assertEquals(2, result1.find { it.song.title == "one" }!!.playCount)
        assertEquals(2, result1.find { it.song.title == "three" }!!.playCount)
    }

}
