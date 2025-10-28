package com.robustpatcher

import com.robustpatcher.models.*
import com.robustpatcher.services.PatchExecutor
import java.io.File
import kotlin.test.*

class PatcherTests {

    private val testBaseDir = File("test_temp")

    @BeforeTest
    fun setup() {
        testBaseDir.mkdirs()
    }

    @AfterTest
    fun cleanup() {
        testBaseDir.deleteRecursively()
    }

    // ==================== NORMALIZED MODE TESTS ====================

    @Test
    fun `test normalized match - exact same formatting`() {
        val content = """
            val x = 1
            val y = 2
        """.trimIndent()

        val pattern = """
            val x = 1
            val y = 2
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(mode = MatchMode.NORMALIZED)
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find exact match")
        assertEquals(2, result.matchedText.lines().size, "Should match exactly 2 lines")
        assertTrue(result.matchedText.contains("val x = 1"))
        assertTrue(result.matchedText.contains("val y = 2"))
    }

    @Test
    fun `test normalized match - different whitespace`() {
        val content = """
            val   x   =   1
            	val y=2
        """.trimIndent()

        val pattern = """
            val x = 1
            val y = 2
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(mode = MatchMode.NORMALIZED)
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find match despite whitespace differences")
        assertEquals(2, result.matchedText.lines().size)
    }

    @Test
    fun `test normalized match - with comments ignored`() {
        val content = """
            val x = 1 // comment here
            val y = 2 /* block comment */
        """.trimIndent()

        val pattern = """
            val x = 1
            val y = 2
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(
            mode = MatchMode.NORMALIZED,
            ignoreComments = true
        )
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find match ignoring comments")
    }

    @Test
    fun `test normalized match - case insensitive`() {
        val content = """
            VAL X = 1
            VAL Y = 2
        """.trimIndent()

        val pattern = """
            val x = 1
            val y = 2
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(
            mode = MatchMode.NORMALIZED,
            caseSensitive = false
        )
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find match ignoring case")
    }

    @Test
    fun `test normalized match - should not find partial match`() {
        val content = """
            val x = 1
            val y = 2
            val z = 3
        """.trimIndent()

        val pattern = """
            val x = 1
            val y = 99
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(mode = MatchMode.NORMALIZED)
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNull(result, "Should not find match when content differs")
    }

    @Test
    fun `test normalized match - exact line count`() {
        val content = """
            val x = 1
            val y = 2
            val z = 3
            val w = 4
        """.trimIndent()

        val pattern = """
            val y = 2
            val z = 3
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(mode = MatchMode.NORMALIZED)
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find 2-line match in middle")
        assertEquals(2, result.matchedText.lines().size, "Should return exactly 2 lines")
        assertFalse(result.matchedText.contains("val x = 1"), "Should not include line before")
        assertFalse(result.matchedText.contains("val w = 4"), "Should not include line after")
    }

    // ==================== FUZZY MODE TESTS ====================

    @Test
    fun `test fuzzy match - high similarity`() {
        val content = """
            val x = 1
            val y = 2
        """.trimIndent()

        val pattern = """
            val x = 1
            val z = 2
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(
            mode = MatchMode.FUZZY,
            fuzzyThreshold = 0.7
        )
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find fuzzy match with threshold 0.7")
        assertEquals(2, result.matchedText.lines().size, "Should match exactly 2 lines")
    }

    @Test
    fun `test fuzzy match - low threshold finds more`() {
        val content = """
            val x = 1
            val y = 2
        """.trimIndent()

        val pattern = """
            val a = 1
            val b = 2
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(
            mode = MatchMode.FUZZY,
            fuzzyThreshold = 0.5
        )
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find match with low threshold")
    }

    @Test
    fun `test fuzzy match - threshold too high`() {
        val content = """
            val x = 1
            val y = 2
        """.trimIndent()

        val pattern = """
            val a = 1
            val b = 2
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(
            mode = MatchMode.FUZZY,
            fuzzyThreshold = 0.95
        )
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNull(result, "Should not find match with high threshold")
    }

    @Test
    fun `test fuzzy match - exact line count returned`() {
        val content = """
            val x = 1
            val y = 2
            val z = 3
        """.trimIndent()

        val pattern = """
            val x = 1
            val w = 2
        """.trimIndent()

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(
            mode = MatchMode.FUZZY,
            fuzzyThreshold = 0.7
        )
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result)
        assertEquals(2, result.matchedText.lines().size, "Fuzzy should return exactly same line count as pattern")
        assertFalse(result.matchedText.contains("val z = 3"), "Should not include extra lines")
    }

    // ==================== SEMANTIC MODE TESTS ====================

    @Test
    fun `test semantic match - function by name`() {
        val content = """
            fun process(input: String, count: Int): Boolean {
                return true
            }
        """.trimIndent()

        val pattern = "fun process(input: String, count: Int): Boolean"

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(
            mode = MatchMode.SEMANTIC,
            matchFunctionName = true,
            matchParameterTypes = true
        )
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find function by signature")
        assertTrue(result.matchedText.contains("fun process"))
        assertTrue(result.matchedText.contains("return true"))
    }

    @Test
    fun `test semantic match - distinguish between similar functions`() {
        val content = """
            fun process(input: String, count: Int): Boolean {
                println("First")
                return true
            }
            
            fun process(data: String, num: Int): Boolean {
                println("Second")
                return false
            }
        """.trimIndent()

        val pattern = "fun process(data: String, num: Int): Boolean"

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(
            mode = MatchMode.SEMANTIC,
            matchFunctionName = true,
            matchParameterTypes = true,
            matchParameterNames = true
        )
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find second function by parameter names")
        assertTrue(result.matchedText.contains("Second"), "Should match second function")
        assertFalse(result.matchedText.contains("First"), "Should not match first function")
    }

    @Test
    fun `test semantic match - ignore parameter names when disabled`() {
        val content = """
            fun process(input: String, count: Int): Boolean {
                return true
            }
        """.trimIndent()

        val pattern = "fun process(data: String, num: Int): Boolean"

        val executor = PatchExecutor(testBaseDir)
        val options = MatchOptions(
            mode = MatchMode.SEMANTIC,
            matchFunctionName = true,
            matchParameterTypes = true,
            matchParameterNames = false
        )
        val result = executor.findMatch(content, pattern, options, "\n")

        assertNotNull(result, "Should find function ignoring parameter names")
    }

    // ==================== REPLACE OPERATION TESTS ====================

    @Test
    fun `test replace operation - simple replacement`() {
        val testFile = File(testBaseDir, "test.kt")
        testFile.writeText("""
            fun example() {
                val x = 1
                val y = 2
                println(x + y)
            }
        """.trimIndent())

        val patch = FilePatch(
            file = "test.kt",
            description = "Update variables",
            action = PatchAction.Replace(
                find = "val x = 1\nval y = 2",
                replace = "val x = 10\nval y = 20\nval z = 30",
                matchOptions = MatchOptions(mode = MatchMode.NORMALIZED)
            )
        )

        val executor = PatchExecutor(testBaseDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        val newContent = testFile.readText()
        assertTrue(newContent.contains("val x = 10"))
        assertTrue(newContent.contains("val y = 20"))
        assertTrue(newContent.contains("val z = 30"))
        assertTrue(newContent.contains("println(x + y)"), "Should preserve other code")
    }

    @Test
    fun `test replace operation - does not affect surrounding code`() {
        val testFile = File(testBaseDir, "test.kt")
        testFile.writeText("""
            val a = 0
            val x = 1
            val y = 2
            val b = 3
        """.trimIndent())

        val patch = FilePatch(
            file = "test.kt",
            description = "Replace middle lines",
            action = PatchAction.Replace(
                find = "val x = 1\nval y = 2",
                replace = "val x = 99\nval y = 99",
                matchOptions = MatchOptions(mode = MatchMode.NORMALIZED)
            )
        )

        val executor = PatchExecutor(testBaseDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        val newContent = testFile.readText()
        assertTrue(newContent.contains("val a = 0"), "Should preserve line before")
        assertTrue(newContent.contains("val b = 3"), "Should preserve line after")
        assertTrue(newContent.contains("val x = 99"))
        assertFalse(newContent.contains("val x = 1"))
    }

    // ==================== INSERT TESTS ====================

    @Test
    fun `test insert_after operation`() {
        val testFile = File(testBaseDir, "test.kt")
        testFile.writeText("""
            fun example() {
                val x = 1
            }
        """.trimIndent())

        val patch = FilePatch(
            file = "test.kt",
            description = "Add logging",
            action = PatchAction.InsertAfter(
                marker = "val x = 1",
                content = "    println(\"x = \$x\")",
                matchOptions = MatchOptions(mode = MatchMode.NORMALIZED)
            )
        )

        val executor = PatchExecutor(testBaseDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        val lines = testFile.readText().lines()
        val xIndex = lines.indexOfFirst { it.contains("val x = 1") }
        assertTrue(xIndex >= 0)
        assertTrue(lines[xIndex + 1].contains("println"))
    }

    @Test
    fun `test insert_before operation`() {
        val testFile = File(testBaseDir, "test.kt")
        testFile.writeText("""
            fun example() {
                return result
            }
        """.trimIndent())

        val patch = FilePatch(
            file = "test.kt",
            description = "Add validation",
            action = PatchAction.InsertBefore(
                marker = "return result",
                content = "    check(result != null)",
                matchOptions = MatchOptions(mode = MatchMode.NORMALIZED)
            )
        )

        val executor = PatchExecutor(testBaseDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        val lines = testFile.readText().lines()
        val returnIndex = lines.indexOfFirst { it.contains("return result") }
        assertTrue(returnIndex > 0)
        assertTrue(lines[returnIndex - 1].contains("check"))
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `test delete operation`() {
        val testFile = File(testBaseDir, "test.kt")
        testFile.writeText("""
            val x = 1
            val y = 2
            val z = 3
        """.trimIndent())

        val patch = FilePatch(
            file = "test.kt",
            description = "Remove middle line",
            action = PatchAction.Delete(
                find = "val y = 2",
                matchOptions = MatchOptions(mode = MatchMode.NORMALIZED)
            )
        )

        val executor = PatchExecutor(testBaseDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        val newContent = testFile.readText()
        assertTrue(newContent.contains("val x = 1"))
        assertFalse(newContent.contains("val y = 2"))
        assertTrue(newContent.contains("val z = 3"))
    }

    // ==================== FILE OPERATIONS TESTS ====================

    @Test
    fun `test create_file operation`() {
        val patch = FilePatch(
            file = "newfile.kt",
            description = "Create new file",
            action = PatchAction.CreateFile(
                content = "package com.example\n\nclass NewClass"
            )
        )

        val executor = PatchExecutor(testBaseDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        val newFile = File(testBaseDir, "newfile.kt")
        assertTrue(newFile.exists())
        assertTrue(newFile.readText().contains("class NewClass"))
    }

    @Test
    fun `test delete_file operation`() {
        val testFile = File(testBaseDir, "todelete.kt")
        testFile.writeText("content")

        val patch = FilePatch(
            file = "todelete.kt",
            description = "Delete file",
            action = PatchAction.DeleteFile()
        )

        val executor = PatchExecutor(testBaseDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        assertFalse(testFile.exists())
    }

    @Test
    fun `test move_file operation`() {
        val testFile = File(testBaseDir, "old.kt")
        testFile.writeText("content")

        val patch = FilePatch(
            file = "old.kt",
            description = "Move file",
            action = PatchAction.MoveFile(
                destination = "new.kt",
                overwrite = false
            )
        )

        val executor = PatchExecutor(testBaseDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        assertFalse(testFile.exists())
        assertTrue(File(testBaseDir, "new.kt").exists())
    }

    // ==================== DRY RUN TESTS ====================

    @Test
    fun `test dry_run does not modify file`() {
        val testFile = File(testBaseDir, "test.kt")
        val originalContent = "val x = 1"
        testFile.writeText(originalContent)

        val patch = FilePatch(
            file = "test.kt",
            description = "Test dry run",
            action = PatchAction.Replace(
                find = "val x = 1",
                replace = "val x = 999",
                matchOptions = MatchOptions(mode = MatchMode.NORMALIZED)
            )
        )

        val executor = PatchExecutor(testBaseDir)
        val result = executor.execute(patch, dryRun = true)

        assertEquals(PatchStatus.SUCCESS, result.status)
        assertEquals(originalContent, testFile.readText(), "File should not be modified in dry run")
    }
}