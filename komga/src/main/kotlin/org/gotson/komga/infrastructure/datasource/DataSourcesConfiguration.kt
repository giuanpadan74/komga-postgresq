package org.gotson.komga.infrastructure.datasource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.gotson.komga.infrastructure.configuration.KomgaProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.postgresql.ds.PGSimpleDataSource
import javax.sql.DataSource

@Configuration
class DataSourcesConfiguration(
  private val komgaProperties: KomgaProperties,
) {
  
  // PostgreSQL DataSource beans
  @Bean("postgresqlDataSourceRW")
  @Profile("postgresql")
  @Primary
  fun postgresqlDataSourceRW(): DataSource =
    buildPostgreSQLDataSource("PostgreSQLMainPoolRW", komgaProperties.database)
      .apply {
        // force pool size to 1 if the pool is only used for writes
        if (komgaProperties.database.shouldSeparateReadFromWrites()) this.maximumPoolSize = 1
      }

  @Bean("postgresqlDataSourceRO")
  @Profile("postgresql")
  fun postgresqlDataSourceRO(): DataSource =
    if (komgaProperties.database.shouldSeparateReadFromWrites())
      buildPostgreSQLDataSource("PostgreSQLMainPoolRO", komgaProperties.database)
    else
      postgresqlDataSourceRW()

  @Bean("postgresqlTasksDataSourceRW")
  @Profile("postgresql")
  fun postgresqlTasksDataSourceRW(): DataSource =
    buildPostgreSQLDataSource("PostgreSQLTasksPoolRW", komgaProperties.tasksDb)
      .apply {
        this.maximumPoolSize = 1
      }

  @Bean("postgresqlTasksDataSourceRO")
  @Profile("postgresql")
  fun postgresqlTasksDataSourceRO(): DataSource =
    if (komgaProperties.tasksDb.shouldSeparateReadFromWrites())
      buildPostgreSQLDataSource("PostgreSQLTasksPoolRO", komgaProperties.tasksDb)
    else
      postgresqlTasksDataSourceRW()

  // SQLite DataSource beans (default)
  @Bean("sqliteDataSourceRW")
  @Profile("!postgresql")
  @Primary
  fun sqliteDataSourceRW(): DataSource =
    buildDataSource("SqliteMainPoolRW", SqliteUdfDataSource::class.java, komgaProperties.database)
      .apply {
        // force pool size to 1 if the pool is only used for writes
        if (komgaProperties.database.shouldSeparateReadFromWrites()) this.maximumPoolSize = 1
      }

  @Bean("sqliteDataSourceRO")
  @Profile("!postgresql")
  fun sqliteDataSourceRO(): DataSource =
    if (komgaProperties.database.shouldSeparateReadFromWrites())
      buildDataSource("SqliteMainPoolRO", SqliteUdfDataSource::class.java, komgaProperties.database)
    else
      sqliteDataSourceRW()

  @Bean("tasksDataSourceRW")
  @Profile("!postgresql")
  fun tasksDataSourceRW(): DataSource =
    buildDataSource("SqliteTasksPoolRW", SQLiteDataSource::class.java, komgaProperties.tasksDb)
      .apply {
        // pool size is always 1:
        // - if there's only 1 pool for read and writes, size should be 1
        // - if there's a separate read pool, the write pool size should be 1
        this.maximumPoolSize = 1
      }

  @Bean("tasksDataSourceRO")
  @Profile("!postgresql")
  fun tasksDataSourceRO(): DataSource =
    if (komgaProperties.tasksDb.shouldSeparateReadFromWrites())
      buildDataSource("SqliteTasksPoolRO", SQLiteDataSource::class.java, komgaProperties.tasksDb)
    else
      tasksDataSourceRW()

  private fun buildDataSource(
    poolName: String,
    dataSourceClass: Class<out SQLiteDataSource>,
    databaseProps: KomgaProperties.Database,
  ): HikariDataSource {
    val extraPragmas =
      databaseProps.pragmas.let {
        if (it.isEmpty())
          ""
        else
          "?" + it.map { (key, value) -> "$key=$value" }.joinToString(separator = "&")
      }

    val dataSource =
      DataSourceBuilder
        .create()
        .driverClassName("org.sqlite.JDBC")
        .url("jdbc:sqlite:${databaseProps.file}$extraPragmas")
        .type(dataSourceClass)
        .build()

    with(dataSource) {
      setEnforceForeignKeys(true)
      setGetGeneratedKeys(false)
    }
    with(databaseProps) {
      journalMode?.let { dataSource.setJournalMode(it.name) }
      busyTimeout?.let { dataSource.config.busyTimeout = it.toMillis().toInt() }
    }

    val poolSize =
      if (databaseProps.isMemory())
        1
      else if (databaseProps.poolSize != null)
        databaseProps.poolSize!!
      else
        Runtime.getRuntime().availableProcessors().coerceAtMost(databaseProps.maxPoolSize)

    return HikariDataSource(
      HikariConfig().apply {
        this.dataSource = dataSource
        this.poolName = poolName
        this.maximumPoolSize = poolSize
      },
    )
  }

  private fun buildPostgreSQLDataSource(
    poolName: String,
    databaseProps: KomgaProperties.Database,
  ): HikariDataSource {
    val dataSource = PGSimpleDataSource().apply {
      // Parse PostgreSQL URL from database file property or use environment variables
      val dbUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/komga"
      val dbUser = System.getenv("DATABASE_USER") ?: "komga"
      val dbPassword = System.getenv("DATABASE_PASSWORD") ?: "komga"
      
      // Parse URL components
      val urlPattern = "jdbc:postgresql://([^:/]+)(?::(\\d+))?/(.+)".toRegex()
      val matchResult = urlPattern.find(dbUrl)
      
      if (matchResult != null) {
        val (host, port, database) = matchResult.destructured
        serverNames = arrayOf(host)
        if (port.isNotEmpty()) portNumbers = intArrayOf(port.toInt())
        databaseName = database
      } else {
        // Fallback to default values
        serverNames = arrayOf("localhost")
        portNumbers = intArrayOf(5432)
        databaseName = "komga"
      }
      
      user = dbUser
      password = dbPassword
    }

    val poolSize = Runtime.getRuntime().availableProcessors().coerceAtMost(10)

    return HikariDataSource(
      HikariConfig().apply {
        this.dataSource = dataSource
        this.poolName = poolName
        this.maximumPoolSize = poolSize
        this.connectionTimeout = 30000
        this.idleTimeout = 600000
        this.maxLifetime = 1800000
      },
    )
  }

  fun KomgaProperties.Database.isMemory() = file.contains(":memory:") || file.contains("mode=memory")

  fun KomgaProperties.Database.shouldSeparateReadFromWrites(): Boolean = !isMemory() && journalMode == SQLiteConfig.JournalMode.WAL
}
