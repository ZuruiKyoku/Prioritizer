package com.slygames.prioritizer.data.task

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec: priority must always be clamped into 1..10 -- 0, 11, and other out-of-range values must
 * never reach storage or the UI as-is. */
class TaskEntityTest {

    @Test
    fun `priority 1 is valid and unchanged`() {
        assertEquals(1, 1.coerceToPriorityRange())
    }

    @Test
    fun `priority 10 is valid and unchanged`() {
        assertEquals(10, 10.coerceToPriorityRange())
    }

    @Test
    fun `priority 0 is clamped up to 1`() {
        assertEquals(1, 0.coerceToPriorityRange())
    }

    @Test
    fun `priority 11 is clamped down to 10`() {
        assertEquals(10, 11.coerceToPriorityRange())
    }

    @Test
    fun `wildly out of range priorities are still clamped`() {
        assertEquals(1, (-500).coerceToPriorityRange())
        assertEquals(10, Int.MAX_VALUE.coerceToPriorityRange())
    }
}
