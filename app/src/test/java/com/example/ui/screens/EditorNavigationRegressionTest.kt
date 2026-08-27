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
            "Navigation callback itself must not launch detached bootstrap work",
            openEditorBlock.contains("ensureSaveActiveForEditor")
        )
        assertTrue(
            "Pre-career route must render through readiness guard",
            source.contains("PreparedPreCareerEditorScreen(")
        )
    }

    @Test
    fun `pre-career editor does not render persisted values until Room preparation succeeds`() {
        val source = readProjectSource("src/main/java/com/example/ui/screens/PreparedPreCareerEditorScreen.kt")

        val readyBranch = source.indexOf("PreCareerEditorPreparationState.READY ->")
        val editorRender = source.indexOf("TeamAndPlayerEditorScreen(", startIndex = readyBranch)
        assertTrue("READY branch must exist", readyBranch >= 0)
        assertTrue("Editor must render only after READY", editorRender > readyBranch)
        assertTrue(source.contains("precareer_editor_loading_guard"))
        assertTrue(source.contains("viewModel.ensureSaveActiveForEditor"))
    }

    @Test
    fun `pre-career menu has club-player editor but no coach editor while career keeps coach editor`() {
        val mainMenu = readProjectSource("src/main/java/com/example/ui/screens/MainMenuScreen.kt")
        val coach = readProjectSource("src/main/java/com/example/ui/screens/CoachScreen.kt")

        assertFalse("Coach editor entry must not exist before career", mainMenu.contains("EDITOR TÉCNICO"))
        assertTrue(mainMenu.contains("EDITOR DE CLUBES E JOGADORES"))
        assertTrue(mainMenu.contains("open_club_player_editor_button"))

        assertTrue("Career coach editor must remain available", coach.contains("Text(\"Editor Técnico\""))
        assertTrue(coach.contains("Text(\"ABRIR EDITOR TÉCNICO\""))
        assertTrue(coach.contains("TeamAndPlayerEditorScreen("))
    }

    @Test
    fun `manual monthly evolution debug trigger is absent while automatic four-week evolution remains`() {
        val training = readProjectSource("src/main/java/com/example/ui/screens/TrainingScreen.kt")
        val dashboard = readProjectSource("src/main/java/com/example/ui/screens/DashboardScreen.kt")
        val matchRuntime = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt")

        assertFalse(training.contains("advance_month_button"))
        assertFalse(training.contains("Avançar Mês (Processar Evolução de Atributos)"))
        assertFalse(dashboard.contains("advanceMonthAndRunEvolution"))
        assertTrue(matchRuntime.contains("requestedSave.currentWeek % 4 == 0"))
        assertTrue(matchRuntime.contains("commitMonthlyEvolution"))
    }

    @Test
    fun `editor bootstrap belongs to screen lifecycle and cannot detach into viewmodel scope`() {
        val source = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelEditor.kt")

        val functionStart = source.indexOf("suspend fun GameViewModel.ensureSaveActiveForEditor(")
        val nextFunction = source.indexOf("fun GameViewModel.ensureRosterForTeam", startIndex = functionStart)
        assertTrue("Editor bootstrap must be suspend", functionStart >= 0)
        assertTrue("Could not isolate editor bootstrap", nextFunction > functionStart)

        val bootstrapBody = source.substring(functionStart, nextFunction)
        assertFalse(
            "Editor bootstrap must stay owned by the caller lifecycle",
            bootstrapBody.contains("viewModelScope.launch")
        )
        assertTrue(
            "Editor bootstrap must remain cancellable while doing IO",
            bootstrapBody.contains("withContext(Dispatchers.IO)")
        )
    }

    @Test
    fun `editor mutations require the currently prepared session repository`() {
        val source = readProjectSource("src/main/java/com/example/ui/viewmodel/GameViewModelEditor.kt")

        assertTrue(
            "Prepared-session repository gate must exist",
            source.contains("private fun GameViewModel.preparedEditorRepositoryOrNull()")
        )

        val guardedMutationEntrypoints = listOf(
            "ensureRosterForTeam",
            "saveTeamFromEditor",
            "saveTeamStrength",
            "deleteTeamFromEditor",
            "savePlayerFromEditor",
            "deletePlayerFromEditor",
            "transferPlayerFromEditor"
        )
        guardedMutationEntrypoints.forEach { functionName ->
            val start = source.indexOf("fun GameViewModel.$functionName")
            assertTrue("Missing editor mutation entrypoint: $functionName", start >= 0)
            val next = source.indexOf("\nfun GameViewModel.", startIndex = start + 1)
                .let { if (it >= 0) it else source.length }
            val body = source.substring(start, next)
            assertTrue(
                "$functionName must reject writes outside the prepared editor session",
                body.contains("preparedEditorRepositoryOrNull()")
            )
        }
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
