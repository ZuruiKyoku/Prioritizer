package com.slygames.prioritizer.data.list

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
        tableName = "task_lists",
        indices =
                [
                        Index(value = ["uuid"], name = "index_task_list_uuid"),
                        Index(
                                value = ["importedFromUid", "importedListId"],
                                name = "index_task_list_import_source"
                        )]
)
data class TaskListEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0L,
        val uuid: String = UUID.randomUUID().toString(),
        val name: String,
        val todoCount: Int,
        val completedCount: Int,
        val totalCount: Int,
        val isShared: Boolean = false,
        val importedFromUid: String? = null,
        val importedListId: Long? = null,
        val ownerId: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
) {
    fun isOwner(currentUid: String?) = ownerId != null && ownerId == currentUid
}
