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
import java.time.LocalDateTime
import kotlin.jvm.java

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {

    private lateinit var db: GramophoneDatabase
    private lateinit var dao: GramophoneDatabase

    val t1 = LocalDateTime.of(2019, 4, 21, 6, 23,0)
    val t2 = LocalDateTime.of(2019, 4, 21, 6, 23,5)
    val t3 = LocalDateTime.of(2019, 5, 1, 5, 23,6)

    val t4 = LocalDateTime.of(2020, 3, 15, 13, 23,0)
    val t5 = LocalDateTime.of(2020, 3, 15, 13, 41,3)

    
    fun genDb1() {
        db.recordEvent(
            mediaItem = DataBaseTestData.m1,
            timestamp = t1,
            duration = 20 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = t2,
            duration = 21 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m3,
            timestamp = t3,
            duration = 22 * 1000,
        )
    }

    fun genDb2() {
        db.recordEvent(
            mediaItem = DataBaseTestData.m1,
            timestamp = t1,
            duration = 20 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = t1,
            duration = 21 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = t3,
            duration = 21 * 1000,
        )
        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = t3,
            duration = 67 * 1000,
        )
    }

    fun genDb3() {
        db.recordEvent(
            mediaItem = DataBaseTestData.m1,
            timestamp = t1,
            duration = 20 * 1000,
        )

        db.recordEvent(
            mediaItem = DataBaseTestData.m3,
            timestamp = t2,
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
            result1.find { it.song.title == "one" && it.chromaprints.isEmpty() }!!.playcount
        )
        assertEquals(
            4,
            result1.find { it.song.title == "two" && it.chromaprints.any { it.chromaprint == "uwu_rawr" } }!!.playcount
        )
        assertEquals(
            1,
            result1.find { it.song.title == "three" && it.chromaprints.any { it.chromaprint == "OH WHAT HAVE I DOOOOOOONE" } }!!.playcount
        )

        // existing song should only increment
        db.recordEvent(
            mediaItem = DataBaseTestData.m1,
            timestamp = t4,
            duration = 20 * 1000,
        )
        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(3, result1.find { it.song.title == "one" }!!.playcount)
        assertEquals(4, result1.find { it.song.title == "two" }!!.playcount)
        assertEquals(1, result1.find { it.song.title == "three" }!!.playcount)

        db.recordEvent(
            mediaItem = DataBaseTestData.m2,
            timestamp = t5,
            duration = 21 * 1000,
        )
        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(3, result1.find { it.song.title == "one" }!!.playcount)
        assertEquals(5, result1.find { it.song.title == "two" }!!.playcount)
        assertEquals(1, result1.find { it.song.title == "three" }!!.playcount)

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
            timestamp = t5,
            duration = 21 * 1000,
        )
        result1 = db.dumpSongsWithPlayCount()
        assertEquals(4, result1.size)
        assertEquals(3, result1.find { it.song.title == "one" }!!.playcount)
        assertEquals(5, result1.find { it.song.title == "two" }!!.playcount)
        assertEquals(1, result1.find { it.song.title == "three" }!!.playcount)
        assertEquals(1, result1.find { it.song.title == "four" }!!.playcount)
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
        assertEquals(1, result1.find { it.song.id == s1.id }!!.playcount)
        assertEquals(56, result1.find { it.song.id == s1.id }!!.playcountLegacy)
        assertEquals(57, result1.find { it.song.id == s1.id }!!.totalPlaycount)

        db.recordEventLegacy(
            mediaItem = DataBaseTestData.m1,
            month = 5,
            year = 2024,
            count = 12
        )

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(1, result1.find { it.song.id == s1.id }!!.playcount)
        assertEquals(68, result1.find { it.song.id == s1.id }!!.playcountLegacy)
        assertEquals(69, result1.find { it.song.id == s1.id }!!.totalPlaycount)

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
        assertEquals(1, result1.find { it.song.id == s1.id }!!.playcount)
        assertEquals(68, result1.find { it.song.id == s1.id }!!.playcountLegacy)
        assertEquals(69, result1.find { it.song.id == s1.id }!!.totalPlaycount)


        // duplicate entries will replace existing values
        db.recordEventLegacy(
            mediaItem = DataBaseTestData.m1,
            month = 5,
            year = 2024,
            count = 14
        )

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(3, result1.size)
        assertEquals(1, result1.find { it.song.id == s1.id }!!.playcount)
        assertEquals(70, result1.find { it.song.id == s1.id }!!.playcountLegacy)
        assertEquals(71, result1.find { it.song.id == s1.id }!!.totalPlaycount)
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
            timestamp = t1,
            duration = 25 * 1000,
        )

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(2, result1.size)
        assertEquals(
            7,
            result1.find { it.chromaprints.any { it.chromaprint == "uwu_rawr" } }!!.playcount
        )
        assertEquals(1, result1.find { it.song.title == "three" }!!.playcount)
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
        assertEquals(2, result1.find { it.song.title == "one" }!!.playcount)
        assertEquals(2, result1.find { it.song.title == "three" }!!.playcount)
        db.mergeSongsByChromaprint()

        result1 = db.dumpSongsWithPlayCount()
        assertEquals(result1.size, 3)
        assertEquals(2, result1.find { it.song.title == "one" }!!.playcount)
        assertEquals(2, result1.find { it.song.title == "three" }!!.playcount)
    }

}
