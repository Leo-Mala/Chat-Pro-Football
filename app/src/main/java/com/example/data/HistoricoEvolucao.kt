package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "historico_evolucao")
data class HistoricoEvolucao(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jogadorId: Long,
    val data: String, // ex: "2026-01", "2026-02"
    val atributo: String,
    val valorAntigo: Int,
    val valorNovo: Int
)

@Dao
interface HistoricoEvolucaoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(historico: HistoricoEvolucao)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(historicoList: List<HistoricoEvolucao>)

    @Query("SELECT * FROM historico_evolucao WHERE jogadorId = :jogadorId ORDER BY id DESC")
    suspend fun getHistoricoPorJogador(jogadorId: Long): List<HistoricoEvolucao>

    @Query("SELECT * FROM historico_evolucao WHERE data = :dataPeriod ORDER BY id DESC")
    suspend fun getHistoricoPorData(dataPeriod: String): List<HistoricoEvolucao>

    @Query("SELECT * FROM historico_evolucao")
    suspend fun getAll(): List<HistoricoEvolucao>

    @Query("DELETE FROM historico_evolucao")
    suspend fun deleteAll()
}
