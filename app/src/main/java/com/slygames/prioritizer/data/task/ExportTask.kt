package com.slygames.prioritizer.data.task

import android.annotation.SuppressLint
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ExportTask(
    val description: String,
    val priority: Int,
    val done: Boolean
)

fun createTaskExport(tasks: List<TaskEntity>): String {
    val exportData = tasks.map {
        ExportTask(it.description, it.priority, it.done)
    }
    return Json.encodeToString(exportData)
}
