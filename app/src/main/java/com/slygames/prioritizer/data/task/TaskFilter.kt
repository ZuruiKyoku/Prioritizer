package com.slygames.prioritizer.data.task

/** Task visibility filter. Lives at the repository/ViewModel layer so it can be reused by any
 * screen without being tangled into UI code (spec: filtering must not be tightly coupled to UI). */
enum class TaskFilter {
    ALL,
    ACTIVE,
    COMPLETED
}
