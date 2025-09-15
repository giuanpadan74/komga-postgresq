package org.gotson.komga.infrastructure.configuration

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.Environment
import javax.sql.DataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * Verifica manuale della configurazione dual-database
 * Questo script può essere eseguito per testare la configurazione senza dipendere da Gradle
 */
@SpringBootApplication
class ManualConfigurationVerification

fun main(args: Array<String>) {
    println("=== Verifica Configurazione Dual-Database Komga ===")
    
    // Test configurazione SQLite
    println("\n1. Testing configurazione SQLite...")
    testSQLiteConfiguration()
    
    // Test configurazione PostgreSQL
    println("\n2. Testing configurazione PostgreSQL...")
    testPostgreSQLConfiguration()
    
    println("\n=== Verifica completata ===")
}

fun testSQLiteConfiguration() {
    try {
        System.setProperty("spring.profiles.active", "sqlite,test")
        System.setProperty("komga.database.file", "test.db")
        System.setProperty("komga.database.journal-mode", "WAL")
        System.setProperty("komga.database.busy-timeout", "30000")
        System.setProperty("komga.database.pool-size", "1")
        
        val context = SpringApplication.run(ManualConfigurationVerification::class.java)
        
        verifyBeans(context, "SQLite")
        
        context.close()
        println("✓ Configurazione SQLite verificata con successo")
        
    } catch (e: Exception) {
        println("✗ Errore nella configurazione SQLite: ${e.message}")
        e.printStackTrace()
    }
}

fun testPostgreSQLConfiguration() {
    try {
        System.setProperty("spring.profiles.active", "postgresql,test")
        System.setProperty("POSTGRES_DB", "komga_test")
        System.setProperty("POSTGRES_USER", "test_user")
        System.setProperty("POSTGRES_PASSWORD", "test_password")
        System.setProperty("POSTGRES_HOST", "localhost")
        System.setProperty("POSTGRES_PORT", "5432")
        System.setProperty("komga.database.postgresql.pool-size", "5")
        System.setProperty("komga.database.postgresql.connection-timeout", "30000")
        System.setProperty("komga.database.postgresql.idle-timeout", "600000")
        System.setProperty("komga.database.postgresql.max-lifetime", "1800000")
        
        val context = SpringApplication.run(ManualConfigurationVerification::class.java)
        
        verifyBeans(context, "PostgreSQL")
        verifyPostgreSQLSpecificBeans(context)
        
        context.close()
        println("✓ Configurazione PostgreSQL verificata con successo")
        
    } catch (e: Exception) {
        println("✗ Errore nella configurazione PostgreSQL: ${e.message}")
        e.printStackTrace()
    }
}

fun verifyBeans(context: ConfigurableApplicationContext, dbType: String) {
    val environment = context.getBean(Environment::class.java)
    println("  Profili attivi: ${environment.activeProfiles.joinToString(", ")}")
    
    // Verifica bean comuni
    val commonBeans = listOf(
        "dataSource",
        "tasksDataSource",
        "jdbcTemplate",
        "tasksJdbcTemplate",
        "transactionManager",
        "tasksTransactionManager"
    )
    
    commonBeans.forEach { beanName ->
        if (context.containsBean(beanName)) {
            println("  ✓ Bean '$beanName' presente")
            
            // Verifica tipo del bean
            when (beanName) {
                "dataSource", "tasksDataSource" -> {
                    val bean = context.getBean(beanName, DataSource::class.java)
                    println("    - Tipo: ${bean::class.simpleName}")
                }
                "jdbcTemplate", "tasksJdbcTemplate" -> {
                    val bean = context.getBean(beanName, JdbcTemplate::class.java)
                    println("    - DataSource: ${bean.dataSource?.let { it::class.simpleName }}")
                }
                "transactionManager", "tasksTransactionManager" -> {
                    val bean = context.getBean(beanName, PlatformTransactionManager::class.java)
                    println("    - Tipo: ${bean::class.simpleName}")
                }
            }
        } else {
            println("  ✗ Bean '$beanName' mancante")
        }
    }
}

fun verifyPostgreSQLSpecificBeans(context: ConfigurableApplicationContext) {
    // Verifica bean specifici per PostgreSQL
    val postgresqlBeans = listOf(
        "readOnlyDataSource",
        "readOnlyJdbcTemplate"
    )
    
    postgresqlBeans.forEach { beanName ->
        if (context.containsBean(beanName)) {
            println("  ✓ Bean PostgreSQL '$beanName' presente")
            
            when (beanName) {
                "readOnlyDataSource" -> {
                    val bean = context.getBean(beanName, DataSource::class.java)
                    println("    - Tipo: ${bean::class.simpleName}")
                }
                "readOnlyJdbcTemplate" -> {
                    val bean = context.getBean(beanName, JdbcTemplate::class.java)
                    println("    - DataSource: ${bean.dataSource?.let { it::class.simpleName }}")
                }
            }
        } else {
            println("  ✗ Bean PostgreSQL '$beanName' mancante")
        }
    }
    
    // Verifica configurazioni specifiche
    if (context.containsBean("postgresqlConfiguration")) {
        println("  ✓ PostgreSQLConfiguration presente")
    } else {
        println("  ✗ PostgreSQLConfiguration mancante")
    }
    
    if (context.containsBean("migrationConfiguration")) {
        println("  ✓ MigrationConfiguration presente")
    } else {
        println("  ✗ MigrationConfiguration mancante")
    }
}