package com.slygames.prioritizer.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.slygames.prioritizer.data.task.TaskEntity
import com.slygames.prioritizer.data.list.TaskListEntity

object FirestoreService {
    private val db = FirebaseFirestore.getInstance()

    fun createList(userId: String, list: TaskListEntity) {
        val doc = db.collection("users").document(userId)
            .collection("lists").document(list.id.toString())
        doc.set(list)
    }

    fun createOrUpdateTask(userId: String, listId: Long, task: TaskEntity) {
        val doc = db.collection("users").document(userId)
            .collection("lists").document(listId.toString())
            .collection("tasks").document(task.id.toString())
        doc.set(task)
    }

    fun updateTaskDoneState(userId: String, listId: Long, taskId: Long, done: Boolean) {
        val doc = db.collection("users").document(userId)
            .collection("lists").document(listId.toString())
            .collection("tasks").document(taskId.toString())
        doc.update("done", done)
    }

    fun shareListWith(userId: String, listId: Long, otherUserId: String) {
        val doc = db.collection("users").document(userId)
            .collection("lists").document(listId.toString())
        doc.update("sharedWith", FieldValue.arrayUnion(otherUserId))
    }
}