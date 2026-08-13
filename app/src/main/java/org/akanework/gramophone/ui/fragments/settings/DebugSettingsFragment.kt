package org.akanework.gramophone.ui.fragments.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.akanework.gramophone.db.PlayEventWithSong
import org.akanework.gramophone.db.SongWithPlaycount
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.ui.BaseComposeActivity
import org.akanework.gramophone.ui.GramophoneTheme
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class DebugSettingsActivity : BaseComposeActivity() {
    val history: SnapshotStateList<PlayEventWithSong> = mutableStateListOf()
    val playCounts: SnapshotStateList<SongWithPlaycount> = mutableStateListOf()
    val db = GramophonePlaybackService.instanceForWidgetAndLyricsOnly!!.database


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            history.addAll(db.history())
            playCounts.addAll(db.dumpSongsWithPlayCount())
        }
        setContent {
            GramophoneTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 64.dp)

                ) {
                    Text("History")
                    val historyListState = rememberLazyListState()
                    LazyColumn(
                        state = historyListState,
                        modifier = Modifier
                            .heightIn(max = 500.dp)
                    ) {
                        itemsIndexed(
                            items = history,
//                            key = { _, item -> item. }
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
                                            text = "(${event.song.id}) " + (event.song.title
                                                ?: "Unknown title")
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
                                    list.sortBy {
                                        it.playcount
                                    }
                                }

                                2 -> {
                                    list.sortBy {
                                        it.playcount
                                    }
                                }

                                else -> {
                                    list.sortBy {
                                        it.song.title
                                    }
                                }
                            }
                        } else {
                            when (sort) {
                                1 -> {
                                    list.sortByDescending {
                                        it.playcount
                                    }
                                }

                                2 -> {
                                    list.sortByDescending {
                                        it.playcount
                                    }
                                }

                                else -> {
                                    list.sortByDescending {
                                        it.song.title
                                    }
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
                                            text = "(${song.song.id}) " + (song.song.title
                                                ?: "Unknown title")
                                        )
                                        Text(
                                            text = song.song.artist ?: "Unknown artist"
                                        )
                                    }
                                    Text(
                                        text = "Total: ${song.playcount} (Event: ${song.playEvents.size}, Legacy: ${song.playEventsLegacy.size})"
                                    )
                                }
                            }
                        }
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
