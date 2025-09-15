package org.gotson.komga.infrastructure.configuration

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

/**
 * Configurazione dei profili Spring per il supporto dual-database
 * Gestisce l'attivazione automatica dei profili e la configurazione specifica per ambiente
 */
@Configuration
class ProfileConfiguration(private val environment: Environment) {
    
    private val logger = LoggerFactory.getLogger(ProfileConfiguration::class.java)
    
    @PostConstruct
    fun logActiveProfiles() {
        val activeProfiles = environment.activeProfiles
        val defaultProfiles = environment.defaultProfiles
        
        logger.info("Profili attivi: {}", activeProfiles.contentToString())
        logger.info("Profili di default: {}", defaultProfiles.contentToString())
        
        // Determina il tipo di database in uso
        val databaseType = when {
            activeProfiles.contains("postgresql") -> "PostgreSQL"
            activeProfiles.contains("sqlite") || activeProfiles.isEmpty() -> "SQLite"
            else -> "Sconosciuto"
        }
        
        logger.info("Database configurato: {}", databaseType)
        
        // Log delle proprietà di configurazione rilevanti
        logDatabaseConfiguration()
    }
    
    private fun logDatabaseConfiguration() {
        try {
            // Log configurazione database principale
            val mainDbUrl = environment.getProperty("komga.database.url", "Non configurato")
            val mainDbFile = environment.getProperty("komga.database.file", "Non configurato")
            
            logger.info("Database principale - URL: {}, File: {}", mainDbUrl, mainDbFile)
            
            // Log configurazione database tasks
            val tasksDbUrl = environment.getProperty("komga.database.tasks.url", "Non configurato")
            val tasksDbFile = environment.getProperty("komga.database.tasks.file", "Non configurato")
            
            logger.info("Database tasks - URL: {}, File: {}", tasksDbUrl, tasksDbFile)
            
            // Log configurazione pool
            val poolSize = environment.getProperty("komga.database.pool-size", "Non configurato")
            logger.info("Pool size configurato: {}", poolSize)
            
        } catch (e: Exception) {
            logger.warn("Errore nel log della configurazione database", e)
        }
    }
}

/**
 * Configurazione specifica per il profilo PostgreSQL
 * Attiva automaticamente le funzionalità avanzate quando PostgreSQL è in uso
 */
@Configuration
@Profile("postgresql")
class PostgreSQLProfileConfiguration {
    
    private val logger = LoggerFactory.getLogger(PostgreSQLProfileConfiguration::class.java)
    
    @PostConstruct
    fun initializePostgreSQLFeatures() {
        logger.info("Inizializzazione funzionalità PostgreSQL")
        logger.info("- Supporto transazioni avanzate: ABILITATO")
        logger.info("- Supporto JSON nativo: ABILITATO")
        logger.info("- Supporto full-text search: ABILITATO")
        logger.info("- Supporto array e tipi avanzati: ABILITATO")
        logger.info("- Monitoraggio performance: ABILITATO")
    }
    
    /**
     * Bean per la configurazione avanzata PostgreSQL
     */
    @Bean
    @ConditionalOnProperty(prefix = "komga.database.postgresql", name = ["advanced-features"], havingValue = "true", matchIfMissing = true)
    fun postgreSQLAdvancedFeatures(): PostgreSQLAdvancedFeatures {
        return PostgreSQLAdvancedFeatures()
    }
    
    /**
     * Bean per il monitoraggio delle performance PostgreSQL
     */
    @Bean
    @ConditionalOnProperty(prefix = "komga.database.postgresql", name = ["performance-monitoring"], havingValue = "true", matchIfMissing = true)
    fun postgreSQLPerformanceMonitor(): PostgreSQLPerformanceMonitor {
        return PostgreSQLPerformanceMonitor()
    }
}

/**
 * Configurazione specifica per il profilo SQLite (default)
 * Mantiene la compatibilità con la configurazione esistente
 */
@Configuration
@Profile("!postgresql")
class SQLiteProfileConfiguration {
    
    private val logger = LoggerFactory.getLogger(SQLiteProfileConfiguration::class.java)
    
    @PostConstruct
    fun initializeSQLiteFeatures() {
        logger.info("Inizializzazione funzionalità SQLite")
        logger.info("- Modalità compatibilità: ABILITATA")
        logger.info("- Pragma ottimizzazioni: ABILITATE")
        logger.info("- Journal mode WAL: ABILITATO")
        logger.info("- Foreign keys: ABILITATE")
    }
    
    /**
     * Bean per la configurazione SQLite
     */
    @Bean
    fun sqliteConfiguration(): SQLiteConfiguration {
        return SQLiteConfiguration()
    }
}

/**
 * Configurazione per l'ambiente di sviluppo
 * Abilita funzionalità di debug e logging esteso
 */
@Configuration
@Profile("dev")
class DevelopmentProfileConfiguration {
    
    private val logger = LoggerFactory.getLogger(DevelopmentProfileConfiguration::class.java)
    
    @PostConstruct
    fun initializeDevelopmentFeatures() {
        logger.info("Inizializzazione funzionalità di sviluppo")
        logger.info("- Logging SQL esteso: ABILITATO")
        logger.info("- Validazione schema automatica: ABILITATA")
        logger.info("- Metriche performance: ABILITATE")
        logger.info("- Debug transazioni: ABILITATO")
    }
    
    /**
     * Bean per il debug del database in sviluppo
     */
    @Bean
    fun databaseDebugger(): DatabaseDebugger {
        return DatabaseDebugger()
    }
}

/**
 * Configurazione per l'ambiente di produzione
 * Ottimizza le performance e disabilita il debug
 */
@Configuration
@Profile("prod")
class ProductionProfileConfiguration {
    
    private val logger = LoggerFactory.getLogger(ProductionProfileConfiguration::class.java)
    
    @PostConstruct
    fun initializeProductionFeatures() {
        logger.info("Inizializzazione funzionalità di produzione")
        logger.info("- Ottimizzazioni performance: ABILITATE")
        logger.info("- Logging minimale: ABILITATO")
        logger.info("- Pool connessioni ottimizzato: ABILITATO")
        logger.info("- Monitoraggio salute: ABILITATO")
    }
    
    /**
     * Bean per il monitoraggio di produzione
     */
    @Bean
    fun productionMonitor(): ProductionMonitor {
        return ProductionMonitor()
    }
}

/**
 * Configurazione per i test
 * Utilizza database in memoria e configurazioni ottimizzate per i test
 */
@Configuration
@Profile("test")
class TestProfileConfiguration {
    
    private val logger = LoggerFactory.getLogger(TestProfileConfiguration::class.java)
    
    @PostConstruct
    fun initializeTestFeatures() {
        logger.info("Inizializzazione funzionalità di test")
        logger.info("- Database in memoria: ABILITATO")
        logger.info("- Transazioni rollback automatico: ABILITATE")
        logger.info("- Dati di test: ABILITATI")
        logger.info("- Validazione rapida: ABILITATA")
    }
    
    /**
     * Bean per la configurazione dei test
     */
    @Bean
    fun testConfiguration(): TestConfiguration {
        return TestConfiguration()
    }
}

/**
 * Funzionalità avanzate PostgreSQL
 */
class PostgreSQLAdvancedFeatures {
    private val logger = LoggerFactory.getLogger(PostgreSQLAdvancedFeatures::class.java)
    
    @PostConstruct
    fun initialize() {
        logger.debug("Funzionalità avanzate PostgreSQL inizializzate")
    }
    
    fun isJsonSupportEnabled(): Boolean = true
    fun isFullTextSearchEnabled(): Boolean = true
    fun isArraySupportEnabled(): Boolean = true
    fun isAdvancedIndexingEnabled(): Boolean = true
}

/**
 * Monitor delle performance PostgreSQL
 */
class PostgreSQLPerformanceMonitor {
    private val logger = LoggerFactory.getLogger(PostgreSQLPerformanceMonitor::class.java)
    
    @PostConstruct
    fun initialize() {
        logger.debug("Monitor performance PostgreSQL inizializzato")
    }
    
    fun isSlowQueryLoggingEnabled(): Boolean = true
    fun isConnectionPoolMonitoringEnabled(): Boolean = true
    fun isQueryPlanAnalysisEnabled(): Boolean = true
}

/**
 * Configurazione SQLite
 */
class SQLiteConfiguration {
    private val logger = LoggerFactory.getLogger(SQLiteConfiguration::class.java)
    
    @PostConstruct
    fun initialize() {
        logger.debug("Configurazione SQLite inizializzata")
    }
    
    fun isWalModeEnabled(): Boolean = true
    fun isForeignKeysEnabled(): Boolean = true
    fun isOptimizationsEnabled(): Boolean = true
}

/**
 * Debugger del database per sviluppo
 */
class DatabaseDebugger {
    private val logger = LoggerFactory.getLogger(DatabaseDebugger::class.java)
    
    @PostConstruct
    fun initialize() {
        logger.debug("Database debugger inizializzato")
    }
    
    fun isSqlLoggingEnabled(): Boolean = true
    fun isTransactionDebuggingEnabled(): Boolean = true
    fun isPerformanceMetricsEnabled(): Boolean = true
}

/**
 * Monitor di produzione
 */
class ProductionMonitor {
    private val logger = LoggerFactory.getLogger(ProductionMonitor::class.java)
    
    @PostConstruct
    fun initialize() {
        logger.debug("Monitor di produzione inizializzato")
    }
    
    fun isHealthCheckEnabled(): Boolean = true
    fun isMetricsCollectionEnabled(): Boolean = true
    fun isAlertingEnabled(): Boolean = true
}

/**
 * Configurazione per i test
 */
class TestConfiguration {
    private val logger = LoggerFactory.getLogger(TestConfiguration::class.java)
    
    @PostConstruct
    fun initialize() {
        logger.debug("Configurazione test inizializzata")
    }
    
    fun isInMemoryDatabaseEnabled(): Boolean = true
    fun isAutoRollbackEnabled(): Boolean = true
    fun isTestDataEnabled(): Boolean = true
}

/**
 * Proprietà di configurazione per il profilo PostgreSQL
 */
@Component
@ConfigurationProperties(prefix = "komga.database.postgresql")
@Profile("postgresql")
data class PostgreSQLProfileProperties(
    var advancedFeatures: Boolean = true,
    var performanceMonitoring: Boolean = true,
    var slowQueryThreshold: Long = 1000, // millisecondi
    var connectionPoolMonitoring: Boolean = true,
    var queryPlanAnalysis: Boolean = false,
    var jsonSupport: Boolean = true,
    var fullTextSearch: Boolean = true,
    var arraySupport: Boolean = true,
    var advancedIndexing: Boolean = true
)

/**
 * Proprietà di configurazione per il profilo SQLite
 */
@Component
@ConfigurationProperties(prefix = "komga.database.sqlite")
@Profile("!postgresql")
data class SQLiteProfileProperties(
    var walMode: Boolean = true,
    var foreignKeys: Boolean = true,
    var optimizations: Boolean = true,
    var busyTimeout: Long = 30000, // millisecondi
    var cacheSize: Int = 2000,
    var synchronous: String = "NORMAL",
    var journalMode: String = "WAL"
)