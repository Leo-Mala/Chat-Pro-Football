package com.example.ui.viewmodel

import com.example.data.model.SaveSlotMetadata
import com.example.data.model.SaveSlotsPublicationClock
import com.example.data.model.SaveSlotsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase106SaveSlotsPublicationGateTest {

    @Test
    fun olderReconciliationCannotOverwriteNewerPublishedSnapshot() {
        val state = MutableStateFlow<List<SaveSlotMetadata>>(emptyList())

        val oldGeneration = SaveSlotsPublicationClock.reserve()
        val oldSnapshot = SaveSlotsSnapshot(
            publicationGeneration = oldGeneration,
            slots = listOf(SaveSlotMetadata(id = "1", exists = true, coachName = "Antigo"))
        )

        val newGeneration = SaveSlotsPublicationClock.reserve()
        val newSnapshot = SaveSlotsSnapshot(
            publicationGeneration = newGeneration,
            slots = listOf(SaveSlotMetadata(id = "1", exists = false))
        )

        state.value = newSnapshot
        assertFalse(state.value.single().exists)

        // Simula exatamente a coroutine antiga retomando depois que o snapshot novo já publicou.
        state.value = oldSnapshot

        assertEquals(
            "Snapshot antigo não pode sobrescrever a exclusão/reconciliação mais nova",
            newGeneration,
            (state.value as SaveSlotsSnapshot).publicationGeneration
        )
        assertFalse(state.value.single().exists)
    }

    @Test
    fun mutationInvalidatesSnapshotThatReturnedButHasNotPublishedYet() {
        val state = MutableStateFlow<List<SaveSlotMetadata>>(
            listOf(SaveSlotMetadata(id = "1", exists = false))
        )

        val inFlightGeneration = SaveSlotsPublicationClock.reserve()
        val returnedButNotPublished = SaveSlotsSnapshot(
            publicationGeneration = inFlightGeneration,
            slots = listOf(SaveSlotMetadata(id = "1", exists = true, coachName = "Obsoleto"))
        )

        SaveSlotsPublicationClock.invalidate()
        state.value = returnedButNotPublished

        assertTrue(
            "Mutação iniciada depois do retorno precisa tornar o snapshot in-flight inelegível",
            state.value !is SaveSlotsSnapshot
        )
        assertFalse(state.value.single().exists)
    }
}
