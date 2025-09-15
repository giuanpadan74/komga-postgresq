package org.gotson.komga.infrastructure.datasource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.gotson.komga.infrastructure.configuration.PostgreSQLConfiguration
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

/**
 * Configurazione avanzata dei DataSource PostgreSQL per Komga
 * Supporta configurazioni separate per database principale e tasks
 * con gestione automatica delle variabili d'ambiente
 */
@Configuration
@Profile("postgresql")
class PostgreSQLDataSourceConfiguration(
    private val postgresqlConfig: PostgreSQLConfiguration
) {
    
    private val logger = LoggerFactory.getLogger(PostgreSQLDataSourceConfiguration::class.java)
    
    companion object {
        // Variabili d'ambiente per il database principale
        const val MAIN_DB_URL = "KOMGA_DATABASE_URL"
        const val MAIN_DB_USER = "KOMGA_DATABASE_USER"
        const val MAIN_DB_PASSWORD = "KOMGA_DATABASE_PASSWORD"
        
        // Variabili d'ambiente per il database tasks
        const val TASKS_DB_URL = "KOMGA_TASKS_DATABASE_URL"
        const val TASKS_DB_USER = "KOMGA_TASKS_DATABASE_USER"
        const val TASKS_DB_PASSWORD = "KOMGA_TASKS_DATABASE_PASSWORD"
        
        // Fallback per compatibilità
        const val LEGACY_DB_URL = "DATABASE_URL"
        const val LEGACY_DB_USER = "DATABASE_USER"
        const val LEGACY_DB_PASSWORD = "DATABASE_PASSWORD"
    }
    
    /**
     * DataSource principale PostgreSQL per operazioni di scrittura
     */
    @Bean("postgresqlDataSourceRW")
    @Primary
    fun postgresqlMainDataSourceRW(): DataSource {
        logger.info("Configurazione PostgreSQL DataSource principale (Read/Write)")
        
        val config = resolveMainDatabaseConfig()
        return createHikariDataSource(
            poolName = "PostgreSQL-Main-RW",
            config = config,
            maxPoolSize = if (postgresqlConfig.database.separateReadWrite) 1 else postgresqlConfig.database.maxPoolSize
        )
    }
    
    /**
     * DataSource principale PostgreSQL per operazioni di lettura
     */
    @Bean("postgresqlDataSourceRO")
    fun postgresqlMainDataSourceRO(): DataSource {
        return if (postgresqlConfig.database.separateReadWrite && 
                   !postgresqlConfig.database.readOnlyUrl.isNullOrBlank()) {
            logger.info("Configurazione PostgreSQL DataSource principale (Read-Only)")
            
            val config = DatabaseConfig(
                url = postgresqlConfig.database.readOnlyUrl!!
                    .takeIf { it.isNotBlank() }
                    ?: getEnvironmentVariable(MAIN_DB_URL, LEGACY_DB_URL)
                    ?: postgresqlConfig.database.url,
                username = postgresqlConfig.database.readOnlyUsername
                    ?.takeIf { it.isNotBlank() }
                    ?: getEnvironmentVariable(MAIN_DB_USER, LEGACY_DB_USER)
                    ?: postgresqlConfig.database.username,
                password = postgresqlConfig.database.readOnlyPassword
                    ?.takeIf { it.isNotBlank() }
                    ?: getEnvironmentVariable(MAIN_DB_PASSWORD, LEGACY_DB_PASSWORD)
                    ?: postgresqlConfig.database.password
            )
            
            createHikariDataSource(
                poolName = "PostgreSQL-Main-RO",
                config = config,
                maxPoolSize = postgresqlConfig.database.maxPoolSize
            )
        } else {
            logger.info("Utilizzo del DataSource principale per operazioni di lettura")
            postgresqlMainDataSourceRW()
        }
    }
    
    /**
     * DataSource tasks PostgreSQL per operazioni di scrittura
     */
    @Bean("postgresqlTasksDataSourceRW")
    fun postgresqlTasksDataSourceRW(): DataSource {
        logger.info("Configurazione PostgreSQL DataSource tasks (Read/Write)")
        
        val config = resolveTasksDatabaseConfig()
        return createHikariDataSource(
            poolName = "PostgreSQL-Tasks-RW",
            config = config,
            maxPoolSize = 1 // Tasks sempre con pool size 1 per scrittura
        )
    }
    
    /**
     * DataSource tasks PostgreSQL per operazioni di lettura
     */
    @Bean("postgresqlTasksDataSourceRO")
    fun postgresqlTasksDataSourceRO(): DataSource {
        return if (postgresqlConfig.tasksDb.separateReadWrite &&
                   !postgresqlConfig.tasksDb.readOnlyUrl.isNullOrBlank()) {
            logger.info("Configurazione PostgreSQL DataSource tasks (Read-Only)")
            
            val config = DatabaseConfig(
                url = postgresqlConfig.tasksDb.readOnlyUrl!!
                    .takeIf { it.isNotBlank() }
                    ?: getEnvironmentVariable(TASKS_DB_URL)
                    ?: postgresqlConfig.tasksDb.url,
                username = postgresqlConfig.tasksDb.readOnlyUsername
                    ?.takeIf { it.isNotBlank() }
                    ?: getEnvironmentVariable(TASKS_DB_USER)
                    ?: postgresqlConfig.tasksDb.username,
                password = postgresqlConfig.tasksDb.readOnlyPassword
                    ?.takeIf { it.isNotBlank() }
                    ?: getEnvironmentVariable(TASKS_DB_PASSWORD)
                    ?: postgresqlConfig.tasksDb.password
            )
            
            createHikariDataSource(
                poolName = "PostgreSQL-Tasks-RO",
                config = config,
                maxPoolSize = postgresqlConfig.tasksDb.maxPoolSize
            )
        } else {
            logger.info("Utilizzo del DataSource tasks per operazioni di lettura")
            postgresqlTasksDataSourceRW()
        }
    }
    
    /**
     * Risolve la configurazione del database principale dalle variabili d'ambiente o configurazione
     */
    private fun resolveMainDatabaseConfig(): DatabaseConfig {
        val url = getEnvironmentVariable(MAIN_DB_URL, LEGACY_DB_URL) 
            ?: postgresqlConfig.database.url
        val username = getEnvironmentVariable(MAIN_DB_USER, LEGACY_DB_USER) 
            ?: postgresqlConfig.database.username
        val password = getEnvironmentVariable(MAIN_DB_PASSWORD, LEGACY_DB_PASSWORD) 
            ?: postgresqlConfig.database.password
            
        logger.debug("Database principale - URL: {}, User: {}", url, username)
        return DatabaseConfig(url, username, password)
    }
    
    /**
     * Risolve la configurazione del database tasks dalle variabili d'ambiente o configurazione
     */
    private fun resolveTasksDatabaseConfig(): DatabaseConfig {
        val url = getEnvironmentVariable(TASKS_DB_URL) 
            ?: postgresqlConfig.tasksDb.url
        val username = getEnvironmentVariable(TASKS_DB_USER) 
            ?: postgresqlConfig.tasksDb.username
        val password = getEnvironmentVariable(TASKS_DB_PASSWORD) 
            ?: postgresqlConfig.tasksDb.password
            
        logger.debug("Database tasks - URL: {}, User: {}", url, username)
        return DatabaseConfig(url, username, password)
    }
    
    /**
     * Crea un HikariDataSource configurato per PostgreSQL
     */
    private fun createHikariDataSource(
        poolName: String,
        config: DatabaseConfig,
        maxPoolSize: Int
    ): HikariDataSource {
        val pgDataSource = createPGDataSource(config)
        
        val hikariConfig = HikariConfig().apply {
            dataSource = pgDataSource
            this.poolName = poolName
            this.maximumPoolSize = maxPoolSize
            connectionTimeout = postgresqlConfig.database.connectionTimeout
            idleTimeout = postgresqlConfig.database.idleTimeout
            maxLifetime = postgresqlConfig.database.maxLifetime
            
            // Configurazioni specifiche per PostgreSQL
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("useLocalSessionState", "true")
            addDataSourceProperty("rewriteBatchedStatements", "true")
            addDataSourceProperty("cacheResultSetMetadata", "true")
            addDataSourceProperty("cacheServerConfiguration", "true")
            addDataSourceProperty("elideSetAutoCommits", "true")
            addDataSourceProperty("maintainTimeStats", "false")
        }
        
        return HikariDataSource(hikariConfig)
    }
    
    /**
     * Crea un PGSimpleDataSource dalla configurazione
     */
    private fun createPGDataSource(config: DatabaseConfig): PGSimpleDataSource {
        val dataSource = PGSimpleDataSource()
        
        // Parse dell'URL PostgreSQL
        val urlPattern = "jdbc:postgresql://([^:/]+)(?::(\\d+))?/(.+)".toRegex()
        val matchResult = urlPattern.find(config.url)
        
        if (matchResult != null) {
            val (host, port, database) = matchResult.destructured
            dataSource.serverNames = arrayOf(host)
            if (port.isNotEmpty()) {
                dataSource.portNumbers = intArrayOf(port.toInt())
            }
            dataSource.databaseName = database
        } else {
            logger.warn("Formato URL PostgreSQL non valido: {}. Utilizzo valori di default.", config.url)
            dataSource.serverNames = arrayOf("localhost")
            dataSource.portNumbers = intArrayOf(5432)
            dataSource.databaseName = "komga"
        }
        
        dataSource.user = config.username
        dataSource.password = config.password
        
        return dataSource
    }
    
    /**
     * Ottiene una variabile d'ambiente con fallback
     */
    private fun getEnvironmentVariable(vararg keys: String): String? {
        for (key in keys) {
            val value = System.getenv(key)
            if (!value.isNullOrBlank()) {
                return value
            }
        }
        return null
    }
    
    /**
     * Classe di configurazione del database
     */
    private data class DatabaseConfig(
        val url: String,
        val username: String,
        val password: String
    )
}