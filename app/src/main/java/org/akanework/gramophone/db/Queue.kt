package org.akanework.gramophone.db

import androidx.room3.Embedded
import androidx.room3.Relation
import org.akanework.gramophone.db.entities.QueueEntity
import org.akanework.gramophone.db.entities.QueueSongMap

data class Queue(
    @Embedded val queue: QueueEntity,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["queueId"]
    )
    val songs: List<QueueSongMap>
)
