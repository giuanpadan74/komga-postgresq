package org.gotson.komga.infrastructure.datasource

import org.gotson.komga.infrastructure.configuration.MigrationConfiguration
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

/**
 * Servizio per la migrazione dei dati da SQLite a PostgreSQL
 * Gestisce il processo di migrazione completo con backup e verifica dell'integrità
 */
@Service
@Profile("postgresql")
@ConditionalOnProperty(prefix = "komga.migration", name = ["enabled"], havingValue = "true")
class DatabaseMigrationService(
    private val migrationConfig: MigrationConfiguration,
    private val postgresqlDataSourceRW: DataSource,
    private val postgresqlTasksDataSourceRW: DataSource
) {
    
    private val logger = LoggerFactory.getLogger(DatabaseMigrationService::class.java)
    private val postgresqlJdbcTemplate = JdbcTemplate(postgresqlDataSourceRW)
    private val postgresqlTasksJdbcTemplate = JdbcTemplate(postgresqlTasksDataSourceRW)
    
    /**
     * Esegue la migrazione completa da SQLite a PostgreSQL
     */
    fun executeMigration(): MigrationResult {
        logger.info("Avvio migrazione da SQLite a PostgreSQL")
        
        try {
            // 1. Verifica prerequisiti
            validatePrerequisites()
            
            // 2. Backup automatico se abilitato
            if (migrationConfig.autoBackup) {
                createBackup()
            }
            
            // 3. Migrazione database principale
            val mainDbResult = migrateMainDatabase()
            
            // 4. Migrazione database tasks
            val tasksDbResult = migrateTasksDatabase()
            
            // 5. Verifica integrità se abilitata
            val integrityResult = if (migrationConfig.verifyIntegrity) {
                verifyDataIntegrity()
            } else {
                IntegrityCheckResult(true, emptyList())
            }
            
            val result = MigrationResult(
                success = mainDbResult.success && tasksDbResult.success && integrityResult.success,
                mainDatabase = mainDbResult,
                tasksDatabase = tasksDbResult,
                integrityCheck = integrityResult,
                timestamp = LocalDateTime.now()
            )
            
            if (result.success) {
                logger.info("Migrazione completata con successo")
            } else {
                logger.error("Migrazione fallita: {}", result.getErrorSummary())
            }
            
            return result
            
        } catch (e: Exception) {
            logger.error("Errore durante la migrazione", e)
            return MigrationResult(
                success = false,
                mainDatabase = DatabaseMigrationResult(false, 0, listOf("Errore generale: ${e.message}")),
                tasksDatabase = DatabaseMigrationResult(false, 0, listOf("Migrazione non eseguita")),
                integrityCheck = IntegrityCheckResult(false, listOf("Verifica non eseguita")),
                timestamp = LocalDateTime.now()
            )
        }
    }
    
    /**
     * Verifica i prerequisiti per la migrazione
     */
    private fun validatePrerequisites() {
        logger.info("Verifica prerequisiti migrazione")
        
        // Verifica esistenza file SQLite sorgente
        if (!File(migrationConfig.sourceSqliteDb).exists()) {
            throw IllegalStateException("Database SQLite principale non trovato: ${migrationConfig.sourceSqliteDb}")
        }
        
        if (!File(migrationConfig.sourceSqliteTasksDb).exists()) {
            throw IllegalStateException("Database SQLite tasks non trovato: ${migrationConfig.sourceSqliteTasksDb}")
        }
        
        // Verifica connessione PostgreSQL
        try {
            postgresqlJdbcTemplate.queryForObject("SELECT 1", Int::class.java)
            postgresqlTasksJdbcTemplate.queryForObject("SELECT 1", Int::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Impossibile connettersi a PostgreSQL", e)
        }
        
        logger.info("Prerequisiti verificati con successo")
    }
    
    /**
     * Crea backup dei database esistenti
     */
    private fun createBackup() {
        logger.info("Creazione backup automatico")
        
        val backupDir = Path.of(migrationConfig.backupDirectory)
        Files.createDirectories(backupDir)
        
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        
        // Backup database principale
        val mainDbBackup = backupDir.resolve("komga_main_${timestamp}.db")
        Files.copy(Path.of(migrationConfig.sourceSqliteDb), mainDbBackup, StandardCopyOption.REPLACE_EXISTING)
        
        // Backup database tasks
        val tasksDbBackup = backupDir.resolve("komga_tasks_${timestamp}.db")
        Files.copy(Path.of(migrationConfig.sourceSqliteTasksDb), tasksDbBackup, StandardCopyOption.REPLACE_EXISTING)
        
        logger.info("Backup creato in: {}", backupDir.toAbsolutePath())
    }
    
    /**
     * Migra il database principale
     */
    @Transactional
    private fun migrateMainDatabase(): DatabaseMigrationResult {
        logger.info("Avvio migrazione database principale")
        
        val errors = mutableListOf<String>()
        var totalRecords = 0
        
        try {
            // Connessione al database SQLite sorgente
            val sqliteUrl = "jdbc:sqlite:${migrationConfig.sourceSqliteDb}"
            val sqliteTemplate = JdbcTemplate().apply {
                dataSource = org.springframework.jdbc.datasource.DriverManagerDataSource().apply {
                    setDriverClassName("org.sqlite.JDBC")
                    url = sqliteUrl
                }
            }
            
            // Lista delle tabelle da migrare (ordine importante per le FK)
            val tables = listOf(
                "LIBRARY", "USER", "SERIES", "SERIES_METADATA", "BOOK", "BOOK_METADATA",
                "BOOK_METADATA_AUTHOR", "MEDIA", "MEDIA_PAGE", "MEDIA_FILE", "READ_PROGRESS",
                "COLLECTION", "COLLECTION_SERIES", "USER_LIBRARY_SHARING", "USER_API_KEY",
                "AUTHENTICATION_ACTIVITY"
            )
            
            for (table in tables) {
                try {
                    val count = migrateTable(sqliteTemplate, postgresqlJdbcTemplate, table)
                    totalRecords += count
                    logger.info("Migrati {} record dalla tabella {}", count, table)
                } catch (e: Exception) {
                    val error = "Errore migrazione tabella $table: ${e.message}"
                    errors.add(error)
                    logger.error(error, e)
                }
            }
            
            // Aggiorna le sequenze PostgreSQL
            updatePostgreSQLSequences(postgresqlJdbcTemplate)
            
        } catch (e: Exception) {
            errors.add("Errore generale migrazione database principale: ${e.message}")
            logger.error("Errore migrazione database principale", e)
        }
        
        return DatabaseMigrationResult(
            success = errors.isEmpty(),
            recordsMigrated = totalRecords,
            errors = errors
        )
    }
    
    /**
     * Migra il database tasks
     */
    @Transactional
    private fun migrateTasksDatabase(): DatabaseMigrationResult {
        logger.info("Avvio migrazione database tasks")
        
        val errors = mutableListOf<String>()
        var totalRecords = 0
        
        try {
            // Connessione al database SQLite tasks sorgente
            val sqliteUrl = "jdbc:sqlite:${migrationConfig.sourceSqliteTasksDb}"
            val sqliteTemplate = JdbcTemplate().apply {
                dataSource = org.springframework.jdbc.datasource.DriverManagerDataSource().apply {
                    setDriverClassName("org.sqlite.JDBC")
                    url = sqliteUrl
                }
            }
            
            // Ottieni lista tabelle dal database tasks
            val tables = sqliteTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                String::class.java
            )
            
            for (table in tables) {
                try {
                    val count = migrateTable(sqliteTemplate, postgresqlTasksJdbcTemplate, table)
                    totalRecords += count
                    logger.info("Migrati {} record dalla tabella tasks {}", count, table)
                } catch (e: Exception) {
                    val error = "Errore migrazione tabella tasks $table: ${e.message}"
                    errors.add(error)
                    logger.error(error, e)
                }
            }
            
            // Aggiorna le sequenze PostgreSQL
            updatePostgreSQLSequences(postgresqlTasksJdbcTemplate)
            
        } catch (e: Exception) {
            errors.add("Errore generale migrazione database tasks: ${e.message}")
            logger.error("Errore migrazione database tasks", e)
        }
        
        return DatabaseMigrationResult(
            success = errors.isEmpty(),
            recordsMigrated = totalRecords,
            errors = errors
        )
    }
    
    /**
     * Migra una singola tabella
     */
    private fun migrateTable(sourceTemplate: JdbcTemplate, targetTemplate: JdbcTemplate, tableName: String): Int {
        // Ottieni struttura della tabella
        val columns = sourceTemplate.queryForList(
            "PRAGMA table_info($tableName)"
        ).map { it["name"] as String }
        
        if (columns.isEmpty()) {
            logger.warn("Tabella {} non trovata o vuota", tableName)
            return 0
        }
        
        // Conta i record totali
        val totalCount = sourceTemplate.queryForObject(
            "SELECT COUNT(*) FROM $tableName",
            Int::class.java
        ) ?: 0
        
        if (totalCount == 0) {
            logger.info("Tabella {} è vuota, skip migrazione", tableName)
            return 0
        }
        
        // Migrazione a batch
        var offset = 0
        var totalMigrated = 0
        
        while (offset < totalCount) {
            val batch = sourceTemplate.queryForList(
                "SELECT * FROM $tableName LIMIT ${migrationConfig.batchSize} OFFSET $offset"
            )
            
            if (batch.isNotEmpty()) {
                insertBatch(targetTemplate, tableName, columns, batch)
                totalMigrated += batch.size
            }
            
            offset += migrationConfig.batchSize
            
            if (offset % (migrationConfig.batchSize * 10) == 0) {
                logger.info("Migrazione tabella {}: {}/{} record", tableName, offset, totalCount)
            }
        }
        
        return totalMigrated
    }
    
    /**
     * Inserisce un batch di record nella tabella target
     */
    private fun insertBatch(
        targetTemplate: JdbcTemplate,
        tableName: String,
        columns: List<String>,
        batch: List<Map<String, Any?>>
    ) {
        val columnsList = columns.joinToString(", ")
        val placeholders = columns.joinToString(", ") { "?" }
        val sql = "INSERT INTO $tableName ($columnsList) VALUES ($placeholders)"
        
        val batchArgs = batch.map { row ->
            columns.map { column -> row[column] }.toTypedArray()
        }
        
        targetTemplate.batchUpdate(sql, batchArgs)
    }
    
    /**
     * Aggiorna le sequenze PostgreSQL dopo l'inserimento dei dati
     */
    private fun updatePostgreSQLSequences(jdbcTemplate: JdbcTemplate) {
        logger.info("Aggiornamento sequenze PostgreSQL")
        
        try {
            // Ottieni tutte le sequenze
            val sequences = jdbcTemplate.queryForList(
                "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public'",
                String::class.java
            )
            
            for (sequence in sequences) {
                try {
                    jdbcTemplate.execute(
                        "SELECT setval('$sequence', COALESCE((SELECT MAX(id) FROM ${sequence.replace("_seq", "")}), 1))"
                    )
                } catch (e: Exception) {
                    logger.warn("Impossibile aggiornare sequenza {}: {}", sequence, e.message)
                }
            }
        } catch (e: Exception) {
            logger.warn("Errore aggiornamento sequenze", e)
        }
    }
    
    /**
     * Verifica l'integrità dei dati migrati
     */
    private fun verifyDataIntegrity(): IntegrityCheckResult {
        logger.info("Verifica integrità dati migrati")
        
        val errors = mutableListOf<String>()
        
        try {
            // Verifica conteggi tabelle principali
            val mainTables = listOf("LIBRARY", "USER", "SERIES", "BOOK")
            
            for (table in mainTables) {
                try {
                    val pgCount = postgresqlJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM $table",
                        Int::class.java
                    ) ?: 0
                    
                    logger.info("Tabella {}: {} record in PostgreSQL", table, pgCount)
                    
                } catch (e: Exception) {
                    errors.add("Errore verifica tabella $table: ${e.message}")
                }
            }
            
            // Verifica vincoli di integrità referenziale
            verifyForeignKeyConstraints(errors)
            
        } catch (e: Exception) {
            errors.add("Errore generale verifica integrità: ${e.message}")
        }
        
        return IntegrityCheckResult(
            success = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Verifica i vincoli di chiave esterna
     */
    private fun verifyForeignKeyConstraints(errors: MutableList<String>) {
        val constraints = listOf(
            "SELECT COUNT(*) FROM BOOK b LEFT JOIN SERIES s ON b.SERIES_ID = s.ID WHERE s.ID IS NULL" to "BOOK -> SERIES",
            "SELECT COUNT(*) FROM SERIES s LEFT JOIN LIBRARY l ON s.LIBRARY_ID = l.ID WHERE l.ID IS NULL" to "SERIES -> LIBRARY",
            "SELECT COUNT(*) FROM MEDIA m LEFT JOIN BOOK b ON m.BOOK_ID = b.ID WHERE b.ID IS NULL" to "MEDIA -> BOOK"
        )
        
        for ((sql, description) in constraints) {
            try {
                val orphanCount = postgresqlJdbcTemplate.queryForObject(sql, Int::class.java) ?: 0
                if (orphanCount > 0) {
                    errors.add("Trovati $orphanCount record orfani per vincolo $description")
                }
            } catch (e: Exception) {
                errors.add("Errore verifica vincolo $description: ${e.message}")
            }
        }
    }
    
    /**
     * Risultato della migrazione completa
     */
    data class MigrationResult(
        val success: Boolean,
        val mainDatabase: DatabaseMigrationResult,
        val tasksDatabase: DatabaseMigrationResult,
        val integrityCheck: IntegrityCheckResult,
        val timestamp: LocalDateTime
    ) {
        fun getErrorSummary(): String {
            val allErrors = mutableListOf<String>()
            allErrors.addAll(mainDatabase.errors)
            allErrors.addAll(tasksDatabase.errors)
            allErrors.addAll(integrityCheck.errors)
            return allErrors.joinToString("; ")
        }
    }
    
    /**
     * Risultato della migrazione di un singolo database
     */
    data class DatabaseMigrationResult(
        val success: Boolean,
        val recordsMigrated: Int,
        val errors: List<String>
    )
    
    /**
     * Risultato della verifica di integrità
     */
    data class IntegrityCheckResult(
        val success: Boolean,
        val errors: List<String>
    )
}