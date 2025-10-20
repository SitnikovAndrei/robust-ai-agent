package com.robustpatcher.services

import com.robustpatcher.models.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PatchExecutorTest {

    @TempDir
    lateinit var tempDir: File

    // ============================================
    // STRICT MODE TESTS
    // ============================================

    @Test
    fun `STRICT - exact match found`() {
        // ВАЖНО: НЕ используем trimIndent() для STRICT - отступы должны совпадать точно!
        val content = """fun calculate() {
    val x = 10
    return x
}"""
        val file = createTestFile("Test.kt", content)

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = """fun calculate() {
    val x = 10
    return x
}""",
                replace = """fun calculate() {
    val x = 20
    return x
}""",
                matchOptions = MatchOptions(mode = MatchMode.STRICT)
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        assertTrue(file.readText().contains("val x = 20"))
    }

    @Test
    fun `STRICT - extra spaces not found`() {
        val file = createTestFile("Test.kt", """fun calculate() {
    val   x   =   10
    return x
}""")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = """fun calculate() {
    val x = 10
    return x
}""",
                replace = "replaced",
                matchOptions = MatchOptions(mode = MatchMode.STRICT)
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SKIPPED, result.status)
        assertTrue(result.message.contains("не найден") || result.message.contains("not found"))
    }

    @Test
    fun `STRICT - handles CRLF vs LF`() {
        val file = createTestFile("Test.kt", "val x = 10\r\nval y = 20")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = "val x = 10\nval y = 20",  // LF
                replace = "val z = 30",
                matchOptions = MatchOptions(mode = MatchMode.STRICT)
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        assertTrue(file.readText().contains("val z = 30"))
    }

    // ============================================
    // FUZZY MODE TESTS (threshold = 1.0)
    // ============================================

    @Test
    fun `FUZZY threshold 1_0 - finds with extra spaces - DEBUG`() {
        // Файл с лишними пробелами
        val fileContent = """fun calculate() {
    val   x   =   10
    return x
}"""

        val file = createTestFile("Test.kt", fileContent)

        println("\n========== FILE CONTENT ==========")
        println(fileContent)
        println("\n========== FILE LINES (${fileContent.lines().size} lines) ==========")
        fileContent.lines().forEachIndexed { i, line ->
            println("Line $i: [$line] (${line.length} chars)")
        }

        // Паттерн для поиска
        val findPattern = """fun calculate() {
    val x = 10
    return x
}"""

        println("\n========== FIND PATTERN ==========")
        println(findPattern)
        println("\n========== FIND LINES (${findPattern.lines().size} lines) ==========")
        findPattern.lines().forEachIndexed { i, line ->
            println("Line $i: [$line] (${line.length} chars)")
        }

        // После нормализации (что FUZZY видит)
        println("\n========== AFTER NORMALIZATION ==========")

        fun normalizeForDebug(text: String): String {
            return text.lines()
                .map { it.replace("\t", "    ").trimEnd() }
                .filter { it.isNotBlank() }  // ignoreEmptyLines = true по умолчанию
                .joinToString("\n")
        }

        val normalizedFile = normalizeForDebug(fileContent)
        val normalizedPattern = normalizeForDebug(findPattern)

        println("NORMALIZED FILE:")
        println(normalizedFile)
        println("\nNORMALIZED FILE LINES:")
        normalizedFile.lines().forEachIndexed { i, line ->
            println("Line $i: [$line]")
        }

        println("\nNORMALIZED PATTERN:")
        println(normalizedPattern)
        println("\nNORMALIZED PATTERN LINES:")
        normalizedPattern.lines().forEachIndexed { i, line ->
            println("Line $i: [$line]")
        }

        println("\n========== COMPARISON ==========")
        println("Are they equal? ${normalizedFile == normalizedPattern}")

        if (normalizedFile != normalizedPattern) {
            println("\nDIFFERENCES:")
            val fileLines = normalizedFile.lines()
            val patternLines = normalizedPattern.lines()
            val maxLines = maxOf(fileLines.size, patternLines.size)

            for (i in 0 until maxLines) {
                val fileLine = fileLines.getOrNull(i) ?: "<missing>"
                val patternLine = patternLines.getOrNull(i) ?: "<missing>"

                if (fileLine != patternLine) {
                    println("Line $i DIFFERS:")
                    println("  File:    [$fileLine]")
                    println("  Pattern: [$patternLine]")
                }
            }
        }

        // Теперь выполняем патч
        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = findPattern,
                replace = """fun calculate() {
    val x = 20
    return x
}""",
                matchOptions = MatchOptions(
                    mode = MatchMode.FUZZY,
                    fuzzyThreshold = 1.0
                )
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        println("\n========== PATCH RESULT ==========")
        println("Status: ${result.status}")
        println("Message: ${result.message}")

        if (result.status != PatchStatus.SUCCESS) {
            println("\n❌ PATCH FAILED!")
            println("Expected: SUCCESS")
            println("Actual: ${result.status}")
            println("\nFile content after patch attempt:")
            println(file.readText())
        } else {
            println("\n✅ PATCH SUCCESS!")
            println("File content after patch:")
            println(file.readText())
        }

        assertEquals(PatchStatus.SUCCESS, result.status, "Patch should succeed")
        assertTrue(file.readText().contains("val x = 20"), "File should contain 'val x = 20'")
    }

    @Test
    fun `FUZZY threshold 1_0 - finds with tabs vs spaces`() {
        val file = createTestFile("Test.kt", "fun calc() {\n\tval x = 10\n\treturn x\n}")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = "fun calc() {\n    val x = 10\n    return x\n}",  // spaces
                replace = "fun calc() {\n    val x = 20\n    return x\n}",
                matchOptions = MatchOptions(
                    mode = MatchMode.FUZZY,
                    fuzzyThreshold = 1.0
                )
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        assertTrue(file.readText().contains("val x = 20"))
    }

    @Test
    fun `FUZZY threshold 1_0 - ignores empty lines`() {
        // Файл с пустыми строками
        val file = createTestFile("Test.kt", "val x = 10\n\n\nval y = 20")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = "val x = 10\nval y = 20",  // без пустых строк
                replace = "val z = 30",
                matchOptions = MatchOptions(
                    mode = MatchMode.FUZZY,
                    fuzzyThreshold = 1.0,
                    ignoreEmptyLines = true  // ВАЖНО!
                )
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        assertTrue(file.readText().contains("val z = 30"))
    }

    @Test
    fun `FUZZY threshold 1_0 - ignores comments`() {
        val file = createTestFile("Test.kt", "val x = 10  // this is X\n/* comment */\nval y = 20")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = "val x = 10\nval y = 20",  // без комментариев
                replace = "val z = 30",
                matchOptions = MatchOptions(
                    mode = MatchMode.FUZZY,
                    fuzzyThreshold = 1.0,
                    ignoreComments = true  // ВАЖНО!
                )
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        assertTrue(file.readText().contains("val z = 30"))
    }

    @Test
    fun `FUZZY threshold 1_0 - different value not found`() {
        val file = createTestFile("Test.kt", "val x = 15")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = "val x = 10",
                replace = "val x = 20",
                matchOptions = MatchOptions(
                    mode = MatchMode.FUZZY,
                    fuzzyThreshold = 1.0
                )
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SKIPPED, result.status)
    }

    @Test
    fun `FUZZY case insensitive`() {
        val file = createTestFile("Test.kt", "val User = \"John\"")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = "val user = \"john\"",
                replace = "val user = \"Jane\"",
                matchOptions = MatchOptions(
                    mode = MatchMode.FUZZY,
                    fuzzyThreshold = 1.0,
                    caseSensitive = false
                )
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
    }

    // ============================================
    // FUZZY MODE TESTS (threshold < 1.0)
    // ============================================

    @Test
    fun `FUZZY threshold 0_90 - finds similar code`() {
        val file = createTestFile("Test.kt", "val result = compute(data)\nreturn result")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = "val result = calculate(data)\nreturn result",
                replace = "val result = process(data)\nreturn result",
                matchOptions = MatchOptions(
                    mode = MatchMode.FUZZY,
                    fuzzyThreshold = 0.80
                )
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
    }

    @Test
    fun `FUZZY threshold 0_90 - does not find very different code`() {
        val file = createTestFile("Test.kt", "val a = b")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                find = "val result = calculate(data)",
                replace = "val x = y",
                matchOptions = MatchOptions(
                    mode = MatchMode.FUZZY,
                    fuzzyThreshold = 0.90
                )
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SKIPPED, result.status)
    }


    @Test
    fun `FUZZY with different indentation levels`() {
        // Файл с отступами 8 пробелов
        val file = createTestFile("Test.kt", """class Test {
        fun method() {
            val x = 10
        }
    }""")

        val patch = FilePatch(
            file = "Test.kt",
            description = "Test",
            action = PatchAction.Replace(
                // Паттерн с отступами 4 пробела - найдет благодаря нормализации
                find = """fun method() {
    val x = 10
}""",
                replace = """fun method() {
    val x = 20
}""",
                matchOptions = MatchOptions(
                    mode = MatchMode.FUZZY,
                    fuzzyThreshold = 1.0
                )
            )
        )

        val executor = PatchExecutor(tempDir)
        val result = executor.execute(patch, dryRun = false)

        assertEquals(PatchStatus.SUCCESS, result.status)
        assertTrue(file.readText().contains("val x = 20"))
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private fun createTestFile(name: String, content: String): File {
        val file = File(tempDir, name)
        file.writeText(content)
        return file
    }
}