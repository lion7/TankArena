package com.tankarena.ui.compose.menu

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordStoreTest {

    @Test
    fun `merging appends new codes once and ignores blanks`() {
        assertEquals(listOf("BEGIN1"), emptyList<String>().withPassword("BEGIN1"))
        assertEquals(listOf("BEGIN1"), listOf("BEGIN1").withPassword("BEGIN1"))
        assertEquals(listOf("BEGIN1"), listOf("BEGIN1").withPassword("  "))
        assertEquals(listOf("BEGIN1", "BEGIN2"), listOf("BEGIN1").withPassword(" BEGIN2 "))
    }

    @Test
    fun `codes survive a new store on the same file`() {
        val file = Files.createTempDirectory("tankarena-passwd").resolve("nested/passwd.json")
        PasswordStore(file).add("BEGIN1")
        PasswordStore(file).add("BEGIN2")
        PasswordStore(file).add("BEGIN1")
        assertEquals(listOf("BEGIN1", "BEGIN2"), PasswordStore(file).load())
    }

    @Test
    fun `a missing or corrupt file means no known codes`() {
        val dir = Files.createTempDirectory("tankarena-passwd")
        assertEquals(emptyList(), PasswordStore(dir.resolve("missing.json")).load())
        val corrupt = dir.resolve("corrupt.json")
        Files.writeString(corrupt, "not json")
        assertEquals(emptyList(), PasswordStore(corrupt).load())
        assertEquals(listOf("BEGIN1"), PasswordStore(corrupt).add("BEGIN1"))
    }

    @Test
    fun `an unwritable location does not throw`() {
        val dir = Files.createTempDirectory("tankarena-passwd")
        val blocker = dir.resolve("file")
        Files.writeString(blocker, "")
        // The parent "directory" is a regular file, so saving fails.
        assertEquals(listOf("BEGIN1"), PasswordStore(blocker.resolve("passwd.json")).add("BEGIN1"))
    }
}
