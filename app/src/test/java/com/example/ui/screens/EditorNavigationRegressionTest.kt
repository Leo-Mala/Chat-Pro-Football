package com.example.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorNavigationRegressionTest {

    @Test
    fun `editor route wins before transient pre-career save can select team screen`() {
        val source = readProjectSource("src/main/java/com/example/ui/screens/GameScreens.kt")

        val editorRouteIndex = source.indexOf("menuScreenState == \"EDITOR\" -> \"EDITOR\"")
        val teamSelectionIndex = source.indexOf("gameSave == null -> \"TEAM_SELECTION\"")
        assertTrue("Editor route must be evaluated before TEAM_SELECTION", editorRouteIndex >= 0)
        assertTrue("TEAM_SELECTION route must still exist", teamSelectionIndex >= 0)
        assertTrue("Editor route must have priority", editorRouteIndex < teamSelectionIndex)

        val openEditorStart = source.indexOf("onOpenEditor = {")
        val openEditorEnd = source.indexOf("}\n                            )", startIndex = openEditorStart)
        assertTrue("Main menu must expose onOpenEditor", openEditorStart >= 0)
        assertTrue("Could not isolate onOpenEditor block", openEditorEnd > openEditorStart)
        val openEditorBlock = source.substring(openEditorStart, openEditorEnd)

        assertTrue(openEditorBlock.contains("menuScreenState = \"EDITOR\""))
        assertFalse(
            "Navigation must not wait for editor database bootstrap",
            openEditorBlock.contains("ensureSaveActiveForEditor")
        )
    }

    @Test
    fun `editor bootstrap belongs to composable lifecycle instead of detached viewmodel job`() {
        val editorScreen = readProjectSource("src/main/java/com/example/ui/screens/EditorScreen.kt")
        assertTrue(
            "Editor must launch preparation from its LaunchedEffect",
            editorScreen.contains("LaunchedEffect(Unit) {\n        viewModel.ensureSaveActiveForEditor()\n    }")
        )

        val viewModelSource = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelEditor.kt")
        val preparationStart = viewModelSource.indexOf("suspend fun GameViewModel.ensureSaveActiveForEditor(")
        val nextFunction = viewModelSource.indexOf("fun GameViewModel.ensureRosterForTeam", preparationStart)
        assertTrue("Editor preparation must be suspend", preparationStart >= 0)
        assertTrue("Could not isolate editor preparation", nextFunction > preparationStart)

        val preparationBlock = viewModelSource.substring(preparationStart, nextFunction)
        assertTrue(
            "Preparation should perform its IO in the caller-owned coroutine",
            preparationBlock.contains("withContext(Dispatchers.IO)")
        )
        assertFalse(
            "Preparation must not detach from LaunchedEffect through viewModelScope.launch",
            preparationBlock.contains("viewModelScope.launch")
        )
        assertTrue(
            "Cancellation must remain transparent so leaving the screen stops preparation",
            preparationBlock.contains("catch (e: CancellationException)") && preparationBlock.contains("throw e")
        )
    }

    private fun readProjectSource(relativeToApp: String): String {
        val candidates = listOf(
            File(relativeToApp),
            File("app/$relativeToApp"),
            File("../app/$relativeToApp")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Source file not found: $relativeToApp; cwd=${File(".").absolutePath}")
        return file.readText()
    }
}
