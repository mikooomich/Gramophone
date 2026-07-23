/*
 *     Copyright (C) 2026 The Gramophone contributors
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
package org.akanework.gramophone.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.akanework.gramophone.db.MusicDatabase.Companion.MUSIC_DATABASE_VERSION
import org.akanework.gramophone.db.entities.ChromaprintEntity
import org.akanework.gramophone.db.entities.PlayEventEntity
import org.akanework.gramophone.db.entities.PlayEventLegacyEntity
import org.akanework.gramophone.db.entities.SongEntity
import org.akanework.gramophone.db.entities.SongTagEntity


class MusicDatabase(
    private val delegate: InternalDatabase,
) : DatabaseDao by delegate.dao {
    val openHelper: SupportSQLiteOpenHelper
        get() = delegate.openHelper

    fun query(block: MusicDatabase.() -> Unit) = with(delegate) {
        queryExecutor.execute {
            block(this@MusicDatabase)
        }
    }

    fun transaction(block: MusicDatabase.() -> Unit) = with(delegate) {
        transactionExecutor.execute {
            runInTransaction {
                block(this@MusicDatabase)
            }
        }
    }

    fun close() = delegate.close()

    companion object {
        const val MUSIC_DATABASE_VERSION = 1
    }
}

@Database(
    entities = [
        SongEntity::class,
        SongTagEntity::class,
        PlayEventEntity::class,
        PlayEventLegacyEntity::class,
        ChromaprintEntity::class,
    ],
    version = MUSIC_DATABASE_VERSION,
    exportSchema = true,
    autoMigrations = [
    ]
)

abstract class InternalDatabase : RoomDatabase() {
    abstract val dao: DatabaseDao

    companion object {
        const val DB_NAME = "song.db"
        const val TEST_DB_NAME = "probe_song.db"

        fun newInstance(context: Context): MusicDatabase =
            MusicDatabase(
                delegate = Room.databaseBuilder(context, InternalDatabase::class.java, DB_NAME)
                    .build()
            )

        // keep this separate in the rare case we come across concepts of a plan to support migrations from other forks
        fun newTestInstance(context: Context, dbName: String): MusicDatabase =
            MusicDatabase(
                delegate = Room.databaseBuilder(context, InternalDatabase::class.java, dbName)
                    .build()
            )

        fun newUnitTestInstance(context: Context): MusicDatabase =
            MusicDatabase(
                delegate = Room.databaseBuilder(context, InternalDatabase::class.java, "unit_test")
                    .allowMainThreadQueries()
                    .build()
            )
    }
}
