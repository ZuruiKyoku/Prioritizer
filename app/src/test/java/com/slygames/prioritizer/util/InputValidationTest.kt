package com.slygames.prioritizer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidationTest {

    @Test
    fun `task title rejects blank and whitespace-only input`() {
        assertTrue(InputValidation.validateTaskTitle("") is InputValidation.Result.Invalid)
        assertTrue(InputValidation.validateTaskTitle("   ") is InputValidation.Result.Invalid)
    }

    @Test
    fun `task title trims surrounding whitespace`() {
        val result = InputValidation.validateTaskTitle("  Buy milk  ")
        assertTrue(result is InputValidation.Result.Valid)
        assertEquals("Buy milk", (result as InputValidation.Result.Valid).value)
    }

    @Test
    fun `task title rejects text over the max length`() {
        val tooLong = "a".repeat(InputValidation.MAX_TASK_TITLE_LENGTH + 1)
        assertTrue(InputValidation.validateTaskTitle(tooLong) is InputValidation.Result.Invalid)
    }

    @Test
    fun `task title accepts text at exactly the max length`() {
        val exact = "a".repeat(InputValidation.MAX_TASK_TITLE_LENGTH)
        assertTrue(InputValidation.validateTaskTitle(exact) is InputValidation.Result.Valid)
    }

    @Test
    fun `list name rejects blank input`() {
        assertTrue(InputValidation.validateListName("") is InputValidation.Result.Invalid)
    }

    @Test
    fun `list name trims and accepts valid input`() {
        val result = InputValidation.validateListName("  Groceries  ")
        assertEquals("Groceries", (result as InputValidation.Result.Valid).value)
    }

    @Test
    fun `friend code normalization is case-insensitive but preserves dashes`() {
        assertEquals("AB-7K-9Q", InputValidation.normalizeFriendCode("ab-7k-9q"))
        assertEquals("AB-7K-9Q", InputValidation.normalizeFriendCode("  AB-7K-9Q  "))
    }

    @Test
    fun `email validation accepts well-formed addresses and rejects malformed ones`() {
        assertTrue(InputValidation.isValidEmail("user@example.com"))
        assertFalse(InputValidation.isValidEmail("not-an-email"))
        assertFalse(InputValidation.isValidEmail(""))
    }

    @Test
    fun `password validation enforces minimum length`() {
        assertTrue(InputValidation.validatePassword("123456") is InputValidation.Result.Valid)
        assertTrue(InputValidation.validatePassword("12345") is InputValidation.Result.Invalid)
    }
}
