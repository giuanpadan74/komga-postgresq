package org.gotson.komga.infrastructure.datasource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.gotson.komga.infrastructure.configuration.KomgaProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import java.io.File
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Factory per la creazione e configurazione delle connessioni al database
 * Supporta sia SQLite che PostgreSQL con configurazioni ottimizzate per ciascun tipo
 */
@Configuration
class DatabaseConnectionFactory(
    private val komgaProperties: KomgaProperties,
    private val environment: Environment
) {
    
    private val logger = LoggerFactory.getLogger(DatabaseConnectionFactory::class.java)
    
    /**
     * DataSource principale per PostgreSQL (Read/Write)
     */
    @Bean(name = ["postgresqlDataSourceRW"])
    @Primary
    @Profile("postgresql")
    fun postgresqlDataSourceRW(): DataSource {
        logger.info("Configurazione DataSource PostgreSQL principale (Read/Write)")
        
        val config = createPostgreSQLHikariConfig(
            databaseUrl = komgaProperties.database.url,
            poolName = "PostgreSQL-Main-RW",
            isReadOnly = false
        )
        
        // Configurazioni specifiche per il database principale
        config.maximumPoolSize = komgaProperties.database.poolSize
        config.minimumIdle = (komgaProperties.database.poolSize * 0.2).toInt().coerceAtLeast(2)
        config.connectionTimeout = TimeUnit.SECONDS.toMillis(30)
        config.idleTimeout = TimeUnit.MINUTES.toMillis(10)
        config.maxLifetime = TimeUnit.MINUTES.toMillis(30)
        config.leakDetectionThreshold = TimeUnit.SECONDS.toMillis(60)
        
        // Ottimizzazioni per operazioni di lettura/scrittura
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        config.addDataSourceProperty("useServerPrepStmts", "true")
        config.addDataSourceProperty("useLocalSessionState", "true")
        config.addDataSourceProperty("rewriteBatchedStatements", "true")
        config.addDataSourceProperty("cacheResultSetMetadata", "true")
        config.addDataSourceProperty("cacheServerConfiguration", "true")
        config.addDataSourceProperty("elideSetAutoCommits", "true")
        config.addDataSourceProperty("maintainTimeStats", "false")
        
        return HikariDataSource(config)
    }
    
    /**
     * DataSource per PostgreSQL Tasks (Read/Write)
     */
    @Bean(name = ["postgresqlTasksDataSourceRW"])
    @Profile("postgresql")
    fun postgresqlTasksDataSourceRW(): DataSource {
        logger.info("Configurazione DataSource PostgreSQL Tasks (Read/Write)")
        
        val config = createPostgreSQLHikariConfig(
            databaseUrl = komgaProperties.database.tasks.url,
            poolName = "PostgreSQL-Tasks-RW",
            isReadOnly = false
        )
        
        // Pool più piccolo per il database tasks
        val tasksPoolSize = (komgaProperties.database.poolSize * 0.3).toInt().coerceAtLeast(2)
        config.maximumPoolSize = tasksPoolSize
        config.minimumIdle = (tasksPoolSize * 0.5).toInt().coerceAtLeast(1)
        config.connectionTimeout = TimeUnit.SECONDS.toMillis(20)
        config.idleTimeout = TimeUnit.MINUTES.toMillis(5)
        config.maxLifetime = TimeUnit.MINUTES.toMillis(15)
        
        // Ottimizzazioni per operazioni batch (tasks)
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "100")
        config.addDataSourceProperty("rewriteBatchedStatements", "true")
        config.addDataSourceProperty("defaultRowFetchSize", "1000")
        
        return HikariDataSource(config)
    }
    
    /**
     * DataSource di sola lettura per PostgreSQL (opzionale per ottimizzazioni)
     */
    @Bean(name = ["postgresqlDataSourceRO"])
    @Profile("postgresql")
    @ConditionalOnProperty(prefix = "komga.database", name = ["separate-read-write"], havingValue = "true")
    fun postgresqlDataSourceRO(): DataSource {
        logger.info("Configurazione DataSource PostgreSQL Read-Only")
        
        val readOnlyUrl = environment.getProperty("komga.database.read-url") ?: komgaProperties.database.url
        
        val config = createPostgreSQLHikariConfig(
            databaseUrl = readOnlyUrl,
            poolName = "PostgreSQL-Main-RO",
            isReadOnly = true
        )
        
        // Pool ottimizzato per letture
        val readPoolSize = (komgaProperties.database.poolSize * 0.6).toInt().coerceAtLeast(3)
        config.maximumPoolSize = readPoolSize
        config.minimumIdle = (readPoolSize * 0.3).toInt().coerceAtLeast(2)
        config.connectionTimeout = TimeUnit.SECONDS.toMillis(15)
        config.idleTimeout = TimeUnit.MINUTES.toMillis(15)
        config.maxLifetime = TimeUnit.MINUTES.toMillis(45)
        
        // Ottimizzazioni specifiche per lettura
        config.addDataSourceProperty("readOnly", "true")
        config.addDataSourceProperty("defaultTransactionIsolation", "TRANSACTION_READ_COMMITTED")
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "500")
        config.addDataSourceProperty("cacheResultSetMetadata", "true")
        config.addDataSourceProperty("cacheServerConfiguration", "true")
        
        return HikariDataSource(config)
    }
    
    /**
     * DataSource principale per SQLite (compatibilità)
     */
    @Bean(name = ["sqliteDataSource"])
    @Primary
    @Profile("!postgresql")
    fun sqliteDataSource(): DataSource {
        logger.info("Configurazione DataSource SQLite principale")
        
        val config = createSQLiteHikariConfig(
            databaseFile = komgaProperties.database.file,
            poolName = "SQLite-Main"
        )
        
        return HikariDataSource(config)
    }
    
    /**
     * DataSource per SQLite Tasks (compatibilità)
     */
    @Bean(name = ["sqliteTasksDataSource"])
    @Profile("!postgresql")
    fun sqliteTasksDataSource(): DataSource {
        logger.info("Configurazione DataSource SQLite Tasks")
        
        val config = createSQLiteHikariConfig(
            databaseFile = komgaProperties.database.tasks.file,
            poolName = "SQLite-Tasks"
        )
        
        return HikariDataSource(config)
    }
    
    /**
     * JdbcTemplate per PostgreSQL principale
     */
    @Bean(name = ["postgresqlJdbcTemplate"])
    @Primary
    @Profile("postgresql")
    fun postgresqlJdbcTemplate(postgresqlDataSourceRW: DataSource): JdbcTemplate {
        logger.debug("Configurazione JdbcTemplate PostgreSQL principale")
        
        val template = JdbcTemplate(postgresqlDataSourceRW)
        template.queryTimeout = 30 // secondi
        template.fetchSize = 1000
        
        return template
    }
    
    /**
     * JdbcTemplate per PostgreSQL Tasks
     */
    @Bean(name = ["postgresqlTasksJdbcTemplate"])
    @Profile("postgresql")
    fun postgresqlTasksJdbcTemplate(postgresqlTasksDataSourceRW: DataSource): JdbcTemplate {
        logger.debug("Configurazione JdbcTemplate PostgreSQL Tasks")
        
        val template = JdbcTemplate(postgresqlTasksDataSourceRW)
        template.queryTimeout = 60 // secondi (tasks possono essere più lunghe)
        template.fetchSize = 500
        
        return template
    }
    
    /**
     * JdbcTemplate per SQLite principale (compatibilità)
     */
    @Bean(name = ["sqliteJdbcTemplate"])
    @Primary
    @Profile("!postgresql")
    fun sqliteJdbcTemplate(sqliteDataSource: DataSource): JdbcTemplate {
        logger.debug("Configurazione JdbcTemplate SQLite principale")
        
        val template = JdbcTemplate(sqliteDataSource)
        template.queryTimeout = 30
        
        return template
    }
    
    /**
     * Transaction Manager per PostgreSQL
     */
    @Bean(name = ["postgresqlTransactionManager"])
    @Primary
    @Profile("postgresql")
    fun postgresqlTransactionManager(postgresqlDataSourceRW: DataSource): PlatformTransactionManager {
        logger.debug("Configurazione Transaction Manager PostgreSQL")
        
        val transactionManager = DataSourceTransactionManager(postgresqlDataSourceRW)
        transactionManager.setDefaultTimeout(300) // 5 minuti
        transactionManager.setRollbackOnCommitFailure(true)
        
        return transactionManager
    }
    
    /**
     * Transaction Manager per SQLite (compatibilità)
     */
    @Bean(name = ["sqliteTransactionManager"])
    @Primary
    @Profile("!postgresql")
    fun sqliteTransactionManager(sqliteDataSource: DataSource): PlatformTransactionManager {
        logger.debug("Configurazione Transaction Manager SQLite")
        
        val transactionManager = DataSourceTransactionManager(sqliteDataSource)
        transactionManager.setDefaultTimeout(60) // 1 minuto
        
        return transactionManager
    }
    
    /**
     * Crea configurazione HikariCP per PostgreSQL
     */
    private fun createPostgreSQLHikariConfig(
        databaseUrl: String,
        poolName: String,
        isReadOnly: Boolean = false
    ): HikariConfig {
        val config = HikariConfig()
        
        // Configurazione base
        config.driverClassName = "org.postgresql.Driver"
        config.jdbcUrl = databaseUrl
        config.poolName = poolName
        config.isReadOnly = isReadOnly
        
        // Credenziali da environment variables
        config.username = environment.getProperty("KOMGA_DB_USER", "komga")
        config.password = environment.getProperty("KOMGA_DB_PASSWORD", "komga")
        
        // Configurazioni di connessione
        config.connectionInitSql = "SELECT 1"
        config.validationTimeout = TimeUnit.SECONDS.toMillis(5)
        config.initializationFailTimeout = TimeUnit.SECONDS.toMillis(30)
        
        // Health check
        config.connectionTestQuery = "SELECT 1"
        
        // Configurazioni PostgreSQL specifiche
        config.addDataSourceProperty("applicationName", "Komga-${poolName}")
        config.addDataSourceProperty("stringtype", "unspecified")
        config.addDataSourceProperty("tcpKeepAlive", "true")
        config.addDataSourceProperty("socketTimeout", "30")
        config.addDataSourceProperty("loginTimeout", "10")
        
        if (isReadOnly) {
            config.addDataSourceProperty("readOnly", "true")
            config.addDataSourceProperty("readOnlyMode", "always")
        }
        
        logger.debug("Configurazione HikariCP PostgreSQL creata per pool: {}", poolName)
        
        return config
    }
    
    /**
     * Crea configurazione HikariCP per SQLite
     */
    private fun createSQLiteHikariConfig(
        databaseFile: String,
        poolName: String
    ): HikariConfig {
        val config = HikariConfig()
        
        // Assicura che la directory esista
        val dbFile = File(databaseFile)
        dbFile.parentFile?.mkdirs()
        
        // Configurazione base SQLite
        config.driverClassName = "org.sqlite.JDBC"
        config.jdbcUrl = "jdbc:sqlite:$databaseFile"
        config.poolName = poolName
        
        // Pool limitato per SQLite (non supporta connessioni multiple efficienti)
        config.maximumPoolSize = 1
        config.minimumIdle = 1
        config.connectionTimeout = TimeUnit.SECONDS.toMillis(30)
        config.idleTimeout = TimeUnit.MINUTES.toMillis(5)
        config.maxLifetime = TimeUnit.MINUTES.toMillis(30)
        
        // Configurazioni SQLite specifiche
        config.addDataSourceProperty("foreign_keys", "true")
        config.addDataSourceProperty("journal_mode", komgaProperties.database.journalMode)
        config.addDataSourceProperty("synchronous", "NORMAL")
        config.addDataSourceProperty("cache_size", "2000")
        config.addDataSourceProperty("temp_store", "MEMORY")
        config.addDataSourceProperty("mmap_size", "268435456") // 256MB
        config.addDataSourceProperty("busy_timeout", komgaProperties.database.busyTimeout.toString())
        
        // Ottimizzazioni SQLite
        config.addDataSourceProperty("wal_autocheckpoint", "1000")
        config.addDataSourceProperty("optimize", "true")
        
        logger.debug("Configurazione HikariCP SQLite creata per pool: {}", poolName)
        
        return config
    }
    
    /**
     * Verifica se il database è configurato per separare letture e scritture
     */
    fun isSeparateReadWriteEnabled(): Boolean {
        return environment.getProperty("komga.database.separate-read-write", Boolean::class.java, false)
    }
    
    /**
     * Verifica se il database è in memoria (per test)
     */
    fun isInMemoryDatabase(): Boolean {
        return when {
            environment.acceptsProfiles("postgresql") -> {
                komgaProperties.database.url.contains(":memory:") || 
                komgaProperties.database.url.contains("h2:mem")
            }
            else -> {
                komgaProperties.database.file == ":memory:" || 
                komgaProperties.database.file.contains(":memory:")
            }
        }
    }
    
    /**
     * Ottiene informazioni sullo stato delle connessioni
     */
    fun getConnectionPoolInfo(): Map<String, Any> {
        val info = mutableMapOf<String, Any>()
        
        try {
            info["database_type"] = if (environment.acceptsProfiles("postgresql")) "PostgreSQL" else "SQLite"
            info["separate_read_write"] = isSeparateReadWriteEnabled()
            info["in_memory"] = isInMemoryDatabase()
            info["pool_size"] = komgaProperties.database.poolSize
            
            // Informazioni specifiche del profilo attivo
            if (environment.acceptsProfiles("postgresql")) {
                info["main_db_url"] = komgaProperties.database.url
                info["tasks_db_url"] = komgaProperties.database.tasks.url
            } else {
                info["main_db_file"] = komgaProperties.database.file
                info["tasks_db_file"] = komgaProperties.database.tasks.file
                info["journal_mode"] = komgaProperties.database.journalMode
                info["busy_timeout"] = komgaProperties.database.busyTimeout
            }
            
        } catch (e: Exception) {
            logger.warn("Errore nel recupero informazioni pool connessioni", e)
            info["error"] = e.message ?: "Errore sconosciuto"
        }
        
        return info
    }
}