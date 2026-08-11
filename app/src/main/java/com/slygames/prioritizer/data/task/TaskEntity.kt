package com.slygames.prioritizer.data.task

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.slygames.prioritizer.data.list.TaskListEntity
import java.util.UUID
import kotlinx.serialization.Serializable

/** Valid range for task priority. 10 = most urgent, 1 = least urgent. See [PRIORITY_RANGE]. */
val PRIORITY_RANGE = 1..10
const val DEFAULT_PRIORITY = 5

/** Clamp any incoming priority value into the supported 1..10 range. */
fun Int.coerceToPriorityRange(): Int = this.coerceIn(PRIORITY_RANGE.first, PRIORITY_RANGE.last)

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
                [
                        Index("listId"),
                        Index(value = ["listId", "done"], name = "index_task_list_done"),
                        Index(value = ["uuid"], name = "index_task_uuid", unique = true)]
)
data class TaskEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        /**
         * Stable, client-generated identity for this task. This is what gets used as the
         * Firestore document key for synced tasks -- never the Room [id], which is only a
         * per-device autoincrement counter and is therefore unsafe to use as a shared identity
         * (two devices can independently mint the same next [id] for two different tasks in a
         * shared list, silently clobbering each other's data).
         */
        val uuid: String = UUID.randomUUID().toString(),
        val description: String,
        val priority: Int,
        val done: Boolean = false,
        val listId: Long,
        /** Epoch millis (UTC). Used as the secondary sort key after priority. */
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val createdBy: String? = null,
        val updatedBy: String? = null
)
