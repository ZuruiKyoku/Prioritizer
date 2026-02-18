package com.slygames.prioritizer.data.task

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.slygames.prioritizer.data.list.TaskListEntity
import kotlinx.serialization.Serializable

@Serializable
@Entity(
        tableName = "tasks",
        foreignKeys =
                [
                        ForeignKey(
                                entity = TaskListEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["listId"],
                                onDelete = ForeignKey.CASCADE
                        )],
        indices =
                [Index("listId"), Index(value = ["listId", "done"], name = "index_task_list_done")]
)
data class TaskEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val description: String,
        val priority: Int,
        val done: Boolean = false,
        val listId: Long
)
