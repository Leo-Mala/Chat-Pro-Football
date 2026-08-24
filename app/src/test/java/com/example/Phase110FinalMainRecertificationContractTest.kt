package com.example

import com.example.data.APP_DATABASE_SCHEMA_VERSION
import com.example.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 10.10 — contrato mínimo de recertificação da main.
 *
 * Este teste não altera gameplay nem dados factuais. Ele mantém a cadeia Room fail-closed e,
 * por viver apenas no source set de testes, força a matriz permanente de certificação a executar
 * todos os escopos no HEAD candidato sem modificar o APK/AAB de produção.
 */
class Phase110FinalMainRecertificationContractTest {

    @Test
    fun `automatic Room migration chain is consecutive from supported floor to current schema`() {
        val floor = AppDatabase.MINIMUM_AUTOMATICALLY_MIGRATABLE_VERSION
        val current = APP_DATABASE_SCHEMA_VERSION
        val migrations = AppDatabase.ALL_MIGRATIONS

        assertTrue("Room migration floor must not exceed the current schema", floor <= current)
        assertEquals(
            "ALL_MIGRATIONS must contain exactly one edge for every supported schema step",
            current - floor,
            migrations.size
        )

        migrations.forEachIndexed { index, migration ->
            val expectedStart = floor + index
            assertEquals("Unexpected migration start at index $index", expectedStart, migration.startVersion)
            assertEquals("Migration chain must advance exactly one schema version", expectedStart + 1, migration.endVersion)
        }

        if (migrations.isNotEmpty()) {
            assertEquals("Migration chain must start at the supported floor", floor, migrations.first().startVersion)
            assertEquals("Migration chain must terminate at the current schema", current, migrations.last().endVersion)
        } else {
            assertEquals("An empty migration chain is valid only when floor equals current", floor, current)
        }
    }
}
