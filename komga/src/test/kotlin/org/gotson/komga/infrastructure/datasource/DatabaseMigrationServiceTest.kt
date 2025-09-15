package org.gotson.komga.infrastructure.datasource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.nio.file.Files
import javax.sql.DataSource
import org.springframework.boot.test.mock.mockito.MockBean
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.any

/**
 * Test per il servizio di migrazione del database da SQLite a PostgreSQL
 */
@SpringBootTest
@ActiveProfiles("postgresql", "test")
@TestPropertySource(properties = [
    "POSTGRES_DB=komga_migration_test",
    "POSTGRES_USER=test_user",
    "POSTGRES_PASSWORD=test_password",
    "POSTGRES_HOST=localhost",
    "POSTGRES_PORT=5432",
    "komga.migration.enabled=true",
    "komga.migration.source-database-path=/tmp/test.db",
    "komga.migration.batch-size=1000",
    "komga.migration.backup-enabled=true"
])
class DatabaseMigrationServiceTest {

    @Autowired
    private lateinit var migrationService: DatabaseMigrationService

    @MockBean
    private lateinit var sqliteDataSource: DataSource

    @MockBean
    private lateinit var postgresqlDataSource: DataSource

    @MockBean
    private lateinit var sqliteJdbcTemplate: JdbcTemplate

    @MockBean
    private lateinit var postgresqlJdbcTemplate: JdbcTemplate

    @TempDir
    private lateinit var tempDir: Path

    private lateinit var testDatabasePath: Path

    @BeforeEach
    fun setup() {
        testDatabasePath = tempDir.resolve("test.db")
        Files.createFile(testDatabasePath)
    }

    @Test
    fun `should check migration prerequisites correctly`() {
        // Mock delle verifiche prerequisiti
        whenever(sqliteJdbcTemplate.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table'", Int::class.java))
            .thenReturn(10)
        whenever(postgresqlJdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'", Int::class.java))
            .thenReturn(0)

        // Esegui la verifica dei prerequisiti
        val result = migrationService.checkPrerequisites()

        assertThat(result).isTrue()
        verify(sqliteJdbcTemplate).queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table'", Int::class.java)
        verify(postgresqlJdbcTemplate).queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'", Int::class.java)
    }

    @Test
    fun `should fail prerequisites when SQLite database is empty`() {
        // Mock di un database SQLite vuoto
        whenever(sqliteJdbcTemplate.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table'", Int::class.java))
            .thenReturn(0)

        // Esegui la verifica dei prerequisiti
        val result = migrationService.checkPrerequisites()

        assertThat(result).isFalse()
    }

    @Test
    fun `should fail prerequisites when PostgreSQL database is not empty`() {
        // Mock delle verifiche prerequisiti
        whenever(sqliteJdbcTemplate.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table'", Int::class.java))
            .thenReturn(10)
        whenever(postgresqlJdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'", Int::class.java))
            .thenReturn(5)

        // Esegui la verifica dei prerequisiti
        val result = migrationService.checkPrerequisites()

        assertThat(result).isFalse()
    }

    @Test
    fun `should create backup correctly`() {
        val backupPath = migrationService.createBackup(testDatabasePath.toString())

        assertThat(backupPath).isNotNull()
        assertThat(Files.exists(Path.of(backupPath))).isTrue()
        assertThat(backupPath).contains("backup")
        assertThat(backupPath).endsWith(".db")
    }

    @Test
    fun `should get table list from SQLite`() {
        // Mock della lista delle tabelle
        val expectedTables = listOf("users", "books", "series", "libraries")
        whenever(sqliteJdbcTemplate.queryForList(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            String::class.java
        )).thenReturn(expectedTables)

        val tables = migrationService.getTableList()

        assertThat(tables).isEqualTo(expectedTables)
        verify(sqliteJdbcTemplate).queryForList(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            String::class.java
        )
    }

    @Test
    fun `should get table row count correctly`() {
        val tableName = "books"
        val expectedCount = 1500L
        
        whenever(sqliteJdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Long::class.java))
            .thenReturn(expectedCount)

        val count = migrationService.getTableRowCount(tableName)

        assertThat(count).isEqualTo(expectedCount)
        verify(sqliteJdbcTemplate).queryForObject("SELECT COUNT(*) FROM $tableName", Long::class.java)
    }

    @Test
    fun `should verify data integrity correctly`() {
        val tableName = "books"
        val expectedCount = 1000L
        
        // Mock dei conteggi per entrambi i database
        whenever(sqliteJdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Long::class.java))
            .thenReturn(expectedCount)
        whenever(postgresqlJdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Long::class.java))
            .thenReturn(expectedCount)

        val result = migrationService.verifyDataIntegrity(tableName)

        assertThat(result).isTrue()
        verify(sqliteJdbcTemplate).queryForObject("SELECT COUNT(*) FROM $tableName", Long::class.java)
        verify(postgresqlJdbcTemplate).queryForObject("SELECT COUNT(*) FROM $tableName", Long::class.java)
    }

    @Test
    fun `should fail data integrity when counts don't match`() {
        val tableName = "books"
        
        // Mock di conteggi diversi
        whenever(sqliteJdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Long::class.java))
            .thenReturn(1000L)
        whenever(postgresqlJdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Long::class.java))
            .thenReturn(999L)

        val result = migrationService.verifyDataIntegrity(tableName)

        assertThat(result).isFalse()
    }

    @Test
    fun `should update PostgreSQL sequences correctly`() {
        val tableName = "books"
        val maxId = 1500L
        
        // Mock del valore massimo dell'ID
        whenever(postgresqlJdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM $tableName", Long::class.java))
            .thenReturn(maxId)

        migrationService.updatePostgreSQLSequences(tableName)

        // Verifica che la sequenza sia stata aggiornata
        verify(postgresqlJdbcTemplate).queryForObject("SELECT COALESCE(MAX(id), 0) FROM $tableName", Long::class.java)
        verify(postgresqlJdbcTemplate).execute("SELECT setval('${tableName}_id_seq', ${maxId + 1})")
    }

    @Test
    fun `should handle migration status correctly`() {
        // Test dello stato della migrazione
        val status = migrationService.getMigrationStatus()
        
        assertThat(status).containsKeys("enabled", "completed", "lastRun")
        assertThat(status["enabled"]).isEqualTo(true)
    }

    @Test
    fun `should validate migration configuration`() {
        // Test della validazione della configurazione
        val isValid = migrationService.validateConfiguration()
        
        assertThat(isValid).isTrue()
    }
}