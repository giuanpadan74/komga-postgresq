package org.gotson.komga.infrastructure.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

/**
 * Configurazione specifica per PostgreSQL che estende le proprietà base del database
 * Supporta la migrazione dual-database SQLite -> PostgreSQL
 */
@Configuration
@ConfigurationProperties(prefix = "komga.postgresql")
@Validated
@Profile("postgresql")
class PostgreSQLConfiguration {
    
    /**
     * Configurazione del database principale PostgreSQL
     */
    var database = PostgreSQLDatabase()
    
    /**
     * Configurazione del database tasks PostgreSQL
     */
    var tasksDb = PostgreSQLDatabase()
    
    /**
     * Configurazione specifica per PostgreSQL
     */
    class PostgreSQLDatabase {
        
        /**
         * URL di connessione PostgreSQL
         * Formato: jdbc:postgresql://host:port/database
         */
        @get:NotBlank
        var url: String = "jdbc:postgresql://localhost:5432/komga"
        
        /**
         * Username per la connessione PostgreSQL
         */
        @get:NotBlank
        var username: String = "komga"
        
        /**
         * Password per la connessione PostgreSQL
         */
        @get:NotBlank
        var password: String = "komga"
        
        /**
         * Dimensione del pool di connessioni
         */
        @get:Positive
        var poolSize: Int = 10
        
        /**
         * Dimensione massima del pool di connessioni
         */
        @get:Positive
        var maxPoolSize: Int = 20
        
        /**
         * Timeout di connessione in millisecondi
         */
        @get:Positive
        var connectionTimeout: Long = 30000
        
        /**
         * Timeout di idle in millisecondi
         */
        @get:Positive
        var idleTimeout: Long = 600000
        
        /**
         * Tempo di vita massimo della connessione in millisecondi
         */
        @get:Positive
        var maxLifetime: Long = 1800000
        
        /**
         * Abilita la separazione read/write per performance
         */
        var separateReadWrite: Boolean = false
        
        /**
         * URL del database di sola lettura (se separateReadWrite = true)
         */
        var readOnlyUrl: String? = null
        
        /**
         * Username per il database di sola lettura
         */
        var readOnlyUsername: String? = null
        
        /**
         * Password per il database di sola lettura
         */
        var readOnlyPassword: String? = null
    }
}

/**
 * Configurazione per la migrazione dei dati da SQLite a PostgreSQL
 */
@Configuration
@ConfigurationProperties(prefix = "komga.migration")
@Validated
class MigrationConfiguration {
    
    /**
     * Abilita la modalità di migrazione
     */
    var enabled: Boolean = false
    
    /**
     * Percorso del database SQLite sorgente per la migrazione
     */
    var sourceSqliteDb: String = ""
    
    /**
     * Percorso del database SQLite tasks sorgente per la migrazione
     */
    var sourceSqliteTasksDb: String = ""
    
    /**
     * Dimensione del batch per la migrazione dei dati
     */
    @get:Positive
    var batchSize: Int = 1000
    
    /**
     * Abilita la verifica dell'integrità dei dati dopo la migrazione
     */
    var verifyIntegrity: Boolean = true
    
    /**
     * Abilita il backup automatico prima della migrazione
     */
    var autoBackup: Boolean = true
    
    /**
     * Directory per i backup automatici
     */
    var backupDirectory: String = "./backups"
}

/**
 * Configurazione per il monitoraggio delle performance del database
 */
@Configuration
@ConfigurationProperties(prefix = "komga.database.monitoring")
@Validated
class DatabaseMonitoringConfiguration {
    
    /**
     * Abilita il monitoraggio delle performance
     */
    var enabled: Boolean = false
    
    /**
     * Soglia di tempo per le query lente (in millisecondi)
     */
    @get:Positive
    var slowQueryThreshold: Long = 1000
    
    /**
     * Abilita il logging delle query SQL
     */
    var logQueries: Boolean = false
    
    /**
     * Abilita le metriche del pool di connessioni
     */
    var poolMetrics: Boolean = true
    
    /**
     * Intervallo di raccolta metriche (in secondi)
     */
    @get:Positive
    var metricsInterval: Int = 60
}