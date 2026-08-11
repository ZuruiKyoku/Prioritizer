package com.slygames.prioritizer

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flow from the spec's UI-testing example: launch -> continue as guest -> add tasks at
 * different priorities -> verify the list auto-sorts by priority descending, then complete one and
 * verify it moves to the Completed tab.
 *
 * Runs against the real Firebase SDK (via the checked-in `google-services.json`) but never
 * actually signs in -- guest mode only sets a local DataStore flag, so this never touches the
 * network and is safe to run without a configured Firebase project of your own.
 *
 * NOTE: written and compiled against `compileDebugAndroidTestKotlin`, but not executed -- this
 * sandbox has no emulator/device attached. Run with `./gradlew connectedAndroidTest` (needs a
 * running emulator or connected device) to get an actual pass/fail signal.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TaskFlowTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun guestLogin_addTasks_sortsByPriorityDescending_thenCompletes() {
        // Splash holds for a minimum display time, then resolves to Login for a fresh guest.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Continue as Guest").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Continue as Guest").performClick()

        // TaskViewModel seeds a default "My Tasks" list for a brand-new guest; wait for the Add
        // Task FAB to confirm we've landed on the task screen.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("Add Task").fetchSemanticsNodes().isNotEmpty()
        }

        addTask("Low priority errand", priority = 2f)
        addTask("Critical production fix", priority = 10f)
        addTask("Normal follow-up", priority = 5f)

        // Priority descending: critical, normal, low.
        composeRule.onAllNodesWithText("(10) Critical production fix").assertCountEquals(1)
        composeRule.onNodeWithText("(10) Critical production fix").assertIsDisplayed()
        composeRule.onNodeWithText("(5) Normal follow-up").assertIsDisplayed()
        composeRule.onNodeWithText("(2) Low priority errand").assertIsDisplayed()

        // Complete the low-priority task by tapping its row's checkmark (the "Mark as Done" icon
        // button is a sibling of the row's title text, and every active row shares that same
        // content description, so scope the match to the one sitting next to this task's text).
        composeRule.onNode(
                hasContentDescription("Mark as Done") and hasAnySibling(hasText("(2) Low priority errand"))
        ).performClick()

        composeRule.onNodeWithText("Completed (1)").performClick()
        composeRule.onNodeWithText("(2) Low priority errand").assertIsDisplayed()
    }

    private fun addTask(description: String, priority: Float) {
        composeRule.onNodeWithContentDescription("Add Task").performClick()
        composeRule.onNodeWithText("Task Description").performTextInput(description)

        // Compose's Slider exposes a SetProgress semantics action -- drive it directly rather
        // than simulating a drag gesture, which is far less reliable in a scripted test.
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
                .performSemanticsAction(SemanticsActions.SetProgress) { it(priority) }

        composeRule.onNodeWithText("Add").performClick()
    }
}
