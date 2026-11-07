package org.akanework.gramophone.ui.fragments.settings

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.R
import org.akanework.gramophone.db.AppDatabase
import org.akanework.gramophone.db.GramophoneDatabase
import org.akanework.gramophone.db.PlayEventWithSong
import org.akanework.gramophone.db.SongWithPlaycount
import org.akanework.gramophone.ui.LocalDatabase
import org.akanework.gramophone.ui.components.settings.NavigationPreferenceRow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

@Composable
fun DbDebugScreen(shape: Shape) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current

    val history: SnapshotStateList<PlayEventWithSong> = remember { mutableStateListOf() }
    val playCounts: SnapshotStateList<SongWithPlaycount> = remember { mutableStateListOf() }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            history.addAll(database.history())
            playCounts.addAll(database.dumpSongsWithPlayCount())
        }
    }


    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.applicationContext.contentResolver.openOutputStream(uri)?.use {
                    ZipOutputStream(it.buffered()).use { outputStream ->
                        outputStream.setLevel(Deflater.BEST_COMPRESSION)
                        runBlocking(Dispatchers.IO) {
                            database.checkpoint()
                        }
                        FileInputStream(context.getDatabasePath(AppDatabase.DB_NAME)).use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(AppDatabase.DB_NAME))
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }.onSuccess {
                Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                it.printStackTrace()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val TAG = "restoreLauncher"
                context.applicationContext.contentResolver.openInputStream(uri)?.use {
                    ZipInputStream(it).use { inputStream ->
                        var entry = inputStream.nextEntry
                        while (entry != null) {
                            when (entry.name) {

                                AppDatabase.DB_NAME -> {
                                    Log.i(TAG, "Starting database restore")
                                    runBlocking(Dispatchers.IO) {
                                        database.checkpoint()
                                    }
                                    database.close()

                                    Log.i(TAG, "Testing new database for compatibility...")
                                    val destFile = context.getDatabasePath(AppDatabase.TEST_DB_NAME)
                                    destFile.parentFile?.apply {
                                        if (!exists()) mkdirs()
                                    }
                                    FileOutputStream(destFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }

                                    val status = try {
                                        val t = AppDatabase.newTestInstance(
                                            context,
                                            AppDatabase.TEST_DB_NAME
                                        )
                                        runBlocking(Dispatchers.IO) {
                                            t.isDatabaseIntegrityOk()
                                        }
                                        t.close()
                                        true
                                    } catch (e: Exception) {
                                        Log.e(TAG, "DB validation failed", e)
                                        e.printStackTrace()
                                        false
                                    }

                                    if (status) {
                                        Log.i(TAG, "Found valid database, proceeding with restore")
                                        destFile.inputStream().use { inputStream ->
                                            FileOutputStream(context.getDatabasePath(AppDatabase.DB_NAME)).use { outputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        }
                                    } else {
                                        Log.e(TAG, "Incompatible database, aborting restore")
                                        Toast.makeText(
                                            context,
                                            "R.string.err_restore_incompatible_database",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            entry = inputStream.nextEntry
                        }
                    }
                }

//                val stopIntent = Intent(context, GramophonePlaybackService::class.java)
//                context.stopService(stopIntent)
//                val startIntent = Intent(context, MainActivity::class.java)
//                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                context.startActivity(startIntent)
                exitProcess(0)
            }.onFailure {
                it.printStackTrace()
                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 64.dp)

    ) {
        NavigationPreferenceRow(
            shape,
            title = "Export",
            onClick = {
                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                val fileName = "${context.getString(R.string.app_name)}_${GramophoneDatabase.MUSIC_DATABASE_VERSION}_" +
                        "${LocalDateTime.now().format(formatter)}.backup"
                backupLauncher.launch(fileName)
            },
        )
        NavigationPreferenceRow(
            shape,
            title = "Import",
            onClick = {
                restoreLauncher.launch(arrayOf("application/octet-stream"))
            },
        )
        Text("History")
        val historyListState = rememberLazyListState()
        LazyColumn(
            state = historyListState,
            modifier = Modifier
                .heightIn(max = 500.dp)
        ) {
            itemsIndexed(
                items = history,
//                key = { _, item -> item. }
            ) { index, event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()

                        ) {
                            Text(
                                text = "(${event.song.id}) " + (event.song.title ?: "Unknown title")
                            )
                            Text(
                                text = event.song.artist ?: "Unknown artist"
                            )
                        }
                        Text(
                            text = event.event.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        )
                        Text(
                            text = event.song.uri.toString()
                        )
                    }
                }
            }
        }




        Text("Play Counts")
        var sort by remember { mutableIntStateOf(2) }
        var asc by remember { mutableStateOf(false) }

        Row() {
            Button(
                onClick = {
                    sort = 1
                }
            ) {
                Text("Play count")
            }
            Button(
                onClick = {
                    sort = 2
                }
            ) {
                Text("Date Modified")
            }
            Button(
                onClick = {
                    sort = 3
                }
            ) {
                Text("Title")
            }
        }

        Button(
            onClick = {
                asc = !asc
            }
        ) {
            Text(if (asc) "Ascending" else "Descending")
        }

        LaunchedEffect(asc, sort) {
            val list = ArrayList<SongWithPlaycount>()
            list.addAll(playCounts)
            if (asc) {
                when (sort) {
                    1 -> {
                        list.sortBy { it.totalPlaycount }
                    }

                    2 -> {
                        list.sortBy { it.totalPlaycount }
                    }

                    else -> {
                        list.sortBy { it.song.title }
                    }
                }
            } else {
                when (sort) {
                    1 -> {
                        list.sortByDescending { it.totalPlaycount }
                    }

                    2 -> {
                        list.sortByDescending { it.totalPlaycount }
                    }

                    else -> {
                        list.sortByDescending { it.song.title }
                    }
                }
            }
            playCounts.apply {
                clear()
                addAll(list)
            }
        }


        val playCountListState = rememberLazyListState()

        LazyColumn(
            state = playCountListState,
            modifier = Modifier
                .heightIn(max = 500.dp)
        ) {

            itemsIndexed(
                items = playCounts
//                            key = { _, item -> item. }
            ) { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()

                        ) {
                            Text(
                                text = "(${song.song.id}) " + (song.song.title ?: "Unknown title")
                            )
                            Text(
                                text = song.song.artist ?: "Unknown artist"
                            )
                        }
                        Text(
                            text = "Total: ${song.totalPlaycount} (Event: ${song.playcount}, Legacy: ${song.playcountLegacy})"
                        )
                    }
                }
            }
        }
    }
}


enum class PlayCountSort {
    PLAY_COUNT,
    TITLE,
    RECENT
}
