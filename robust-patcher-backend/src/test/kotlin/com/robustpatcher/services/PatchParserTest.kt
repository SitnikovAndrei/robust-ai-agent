package com.robustpatcher.services

import com.robustpatcher.models.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PatchParserTest {

    @Test
    fun `parses basic patch`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test Patch
            DESCRIPTION: Test description
            AUTHOR: Test Author
            VERSION: 1.0.0
            ---
            
            --- FILE: Test.kt ---
            ACTION: replace
            DESCRIPTION: Replace function
            <<< FIND
            val x = 10
            FIND >>>
            <<< REPLACE
            val x = 20
            REPLACE >>>
            
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (metadata, patches) = parser.parse(patchContent)

        assertEquals("Test Patch", metadata.name)
        assertEquals("Test description", metadata.description)
        assertEquals("Test Author", metadata.author)
        assertEquals("1.0.0", metadata.version)

        assertEquals(1, patches.size)
        val patch = patches[0]
        assertEquals("Test.kt", patch.file)
        assertEquals("Replace function", patch.description)
        assertTrue(patch.action is PatchAction.Replace)

        val action = patch.action as PatchAction.Replace
        assertEquals("val x = 10", action.find)
        assertEquals("val x = 20", action.replace)
    }

    @Test
    fun `parses STRICT mode by default`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace
            <<< FIND
            test
            FIND >>>
            <<< REPLACE
            replaced
            REPLACE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Replace
        assertEquals(MatchMode.STRICT, action.matchOptions.mode)
    }

    @Test
    fun `parses FUZZY mode with parameters`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace
            MATCH_MODE: fuzzy
            FUZZY_THRESHOLD: 0.90
            IGNORE_COMMENTS: true
            IGNORE_EMPTY_LINES: false
            CASE_SENSITIVE: false
            <<< FIND
            test
            FIND >>>
            <<< REPLACE
            replaced
            REPLACE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Replace
        assertEquals(MatchMode.FUZZY, action.matchOptions.mode)
        assertEquals(0.90, action.matchOptions.fuzzyThreshold)
        assertEquals(true, action.matchOptions.ignoreComments)
        assertEquals(false, action.matchOptions.ignoreEmptyLines)
        assertEquals(false, action.matchOptions.caseSensitive)
    }

    @Test
    fun `parses SEMANTIC mode with parameters`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace
            MATCH_MODE: semantic
            MATCH_FUNCTION_NAME: true
            MATCH_PARAMETER_TYPES: true
            MATCH_PARAMETER_NAMES: true
            MATCH_RETURN_TYPE: true
            MATCH_MODIFIERS: true
            <<< FIND
            fun test(): Unit
            FIND >>>
            <<< REPLACE
            fun test(): Unit { }
            REPLACE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Replace
        assertEquals(MatchMode.SEMANTIC, action.matchOptions.mode)
        assertEquals(true, action.matchOptions.matchFunctionName)
        assertEquals(true, action.matchOptions.matchParameterTypes)
        assertEquals(true, action.matchOptions.matchParameterNames)
        assertEquals(true, action.matchOptions.matchReturnType)
        assertEquals(true, action.matchOptions.matchModifiers)
    }

    @Test
    fun `parses ANCHOR with parameters`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace
            MATCH_MODE: fuzzy
            ANCHOR_MATCH_MODE: semantic
            ANCHOR_SCOPE: function
            ANCHOR_SEARCH_DEPTH: 2
            <<< ANCHOR
            fun myFunction()
            ANCHOR >>>
            <<< FIND
            val x = 10
            FIND >>>
            <<< REPLACE
            val x = 20
            REPLACE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Replace
        assertNotNull(action.matchOptions.anchor)
        assertEquals("fun myFunction()", action.matchOptions.anchor!!.anchorText)
        assertEquals(MatchMode.SEMANTIC, action.matchOptions.anchor!!.matchMode)
        assertEquals(AnchorScope.FUNCTION, action.matchOptions.anchor!!.scope)
        assertEquals(2, action.matchOptions.anchor!!.searchDepth)
    }

    @Test
    fun `parses INSERT_BEFORE action`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: insert_before
            <<< MARKER
            return x
            MARKER >>>
            <<< CONTENT
            validate()
            CONTENT >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        assertTrue(patches[0].action is PatchAction.InsertBefore)
        val action = patches[0].action as PatchAction.InsertBefore
        assertEquals("return x", action.marker)
        assertEquals("validate()", action.content)
    }

    @Test
    fun `parses INSERT_AFTER action`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: insert_after
            <<< MARKER
            fun test()
            MARKER >>>
            <<< CONTENT
            logger.info("test")
            CONTENT >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        assertTrue(patches[0].action is PatchAction.InsertAfter)
        val action = patches[0].action as PatchAction.InsertAfter
        assertEquals("fun test()", action.marker)
        assertEquals("logger.info(\"test\")", action.content)
    }

    @Test
    fun `parses DELETE action`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: delete
            <<< FIND
            deprecated code
            FIND >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        assertTrue(patches[0].action is PatchAction.Delete)
        val action = patches[0].action as PatchAction.Delete
        assertEquals("deprecated code", action.find)
    }

    @Test
    fun `parses CREATE_FILE action`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: NewFile.kt ---
            ACTION: create_file
            <<< CONTENT
            package com.test
            
            class NewClass
            CONTENT >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        assertTrue(patches[0].action is PatchAction.CreateFile)
        val action = patches[0].action as PatchAction.CreateFile
        assertTrue(action.content.contains("class NewClass"))
    }

    @Test
    fun `parses REPLACE_FILE action`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace_file
            <<< CONTENT
            new content
            CONTENT >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        assertTrue(patches[0].action is PatchAction.ReplaceFile)
        val action = patches[0].action as PatchAction.ReplaceFile
        assertEquals("new content", action.content)
    }

    @Test
    fun `parses DELETE_FILE action`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: delete_file
            <<< CONFIRM
            true
            CONFIRM >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        assertTrue(patches[0].action is PatchAction.DeleteFile)
        val action = patches[0].action as PatchAction.DeleteFile
        assertEquals(true, action.confirm)
    }

    @Test
    fun `parses MOVE_FILE action`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Old.kt ---
            ACTION: move_file
            <<< TO
            New.kt
            TO >>>
            <<< OVERWRITE
            true
            OVERWRITE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        assertTrue(patches[0].action is PatchAction.MoveFile)
        val action = patches[0].action as PatchAction.MoveFile
        assertEquals("New.kt", action.destination)
        assertEquals(true, action.overwrite)
    }

    @Test
    fun `parses multiple patches in one file`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: File1.kt ---
            ACTION: replace
            <<< FIND
            test1
            FIND >>>
            <<< REPLACE
            replaced1
            REPLACE >>>
            
            --- FILE: File2.kt ---
            ACTION: replace
            <<< FIND
            test2
            FIND >>>
            <<< REPLACE
            replaced2
            REPLACE >>>
            
            --- FILE: File3.kt ---
            ACTION: delete
            <<< FIND
            test3
            FIND >>>
            
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        assertEquals(3, patches.size)
        assertEquals("File1.kt", patches[0].file)
        assertEquals("File2.kt", patches[1].file)
        assertEquals("File3.kt", patches[2].file)
    }

    @Test
    fun `parses multiline content`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace
            <<< FIND
            fun calculate() {
                val x = 10
                val y = 20
                return x + y
            }
            FIND >>>
            <<< REPLACE
            fun calculate() {
                val result = 10 + 20
                return result
            }
            REPLACE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Replace
        assertTrue(action.find.contains("val x = 10"))
        assertTrue(action.find.contains("val y = 20"))
        assertTrue(action.replace.contains("val result = 10 + 20"))
    }

    @Test
    fun `parses REGEX mode`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace
            MATCH_MODE: regex
            <<< FIND
            const val VERSION = "\d+\.\d+\.\d+"
            FIND >>>
            <<< REPLACE
            const val VERSION = "2.0.0"
            REPLACE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Replace
        assertEquals(MatchMode.REGEX, action.matchOptions.mode)
    }

    @Test
    fun `parses CONTAINS mode`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace
            MATCH_MODE: contains
            <<< FIND
            fun calculate
            FIND >>>
            <<< REPLACE
            fun calculate() { }
            REPLACE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Replace
        assertEquals(MatchMode.CONTAINS, action.matchOptions.mode)
    }

    @Test
    fun `parses LINE_RANGE mode`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: delete
            MATCH_MODE: line_range
            <<< FIND
            10-15
            FIND >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Delete
        assertEquals(MatchMode.LINE_RANGE, action.matchOptions.mode)
    }

    @Test
    fun `default values when parameters not specified`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace
            MATCH_MODE: fuzzy
            <<< FIND
            test
            FIND >>>
            <<< REPLACE
            replaced
            REPLACE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Replace
        // Проверяем значения по умолчанию
        assertEquals(1.0, action.matchOptions.fuzzyThreshold)
        assertEquals(false, action.matchOptions.ignoreComments)
        assertEquals(true, action.matchOptions.ignoreEmptyLines)
        assertEquals(true, action.matchOptions.caseSensitive)
    }

    @Test
    fun `semantic defaults when parameters not specified`() {
        val patchContent = """
            === PATCH START ===
            NAME: Test
            ---
            --- FILE: Test.kt ---
            ACTION: replace
            MATCH_MODE: semantic
            <<< FIND
            fun test()
            FIND >>>
            <<< REPLACE
            fun test() { }
            REPLACE >>>
            === PATCH END ===
        """.trimIndent()

        val parser = PatchParser()
        val (_, patches) = parser.parse(patchContent)

        val action = patches[0].action as PatchAction.Replace
        // Проверяем значения по умолчанию для SEMANTIC
        assertEquals(true, action.matchOptions.matchFunctionName)
        assertEquals(true, action.matchOptions.matchClassName)
        assertEquals(true, action.matchOptions.matchParameterTypes)
        assertEquals(false, action.matchOptions.matchParameterNames)
        assertEquals(true, action.matchOptions.matchReturnType)
        assertEquals(false, action.matchOptions.matchModifiers)
    }
}