package org.gotson.komga.infrastructure.jooq

import org.jooq.DSLContext
import org.jooq.ExecuteListenerProvider
import org.jooq.SQLDialect
import org.jooq.TransactionProvider
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultDSLContext
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import javax.sql.DataSource

// taken from https://github.com/spring-projects/spring-boot/blob/v3.1.4/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jooq/JooqAutoConfiguration.java
// as advised in https://docs.spring.io/spring-boot/docs/3.1.4/reference/htmlsingle/#howto.data-access.configure-jooq-with-multiple-datasources
@Configuration
class KomgaJooqConfiguration(
  private val environment: Environment,
) {
  // PostgreSQL DSL Context beans
  @Bean("dslContextRW")
  @Profile("postgresql")
  @Primary
  fun mainDslContextRWPostgreSQL(
    @Qualifier("postgresqlDataSourceRW") dataSource: DataSource,
    transactionProvider: ObjectProvider<TransactionProvider?>,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider?>,
  ): DSLContext = createDslContext(dataSource, transactionProvider, executeListenerProviders, SQLDialect.POSTGRES)

  @Bean("dslContextRO")
  @Profile("postgresql")
  fun mainDslContextROPostgreSQL(
    @Qualifier("postgresqlDataSourceRO") dataSource: DataSource,
    transactionProvider: ObjectProvider<TransactionProvider?>,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider?>,
  ): DSLContext = createDslContext(dataSource, transactionProvider, executeListenerProviders, SQLDialect.POSTGRES)

  @Bean("tasksDslContextRW")
  @Profile("postgresql")
  fun tasksDslContextRWPostgreSQL(
    @Qualifier("postgresqlTasksDataSourceRW") dataSource: DataSource,
    transactionProvider: ObjectProvider<TransactionProvider?>,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider?>,
  ): DSLContext = createDslContext(dataSource, transactionProvider, executeListenerProviders, SQLDialect.POSTGRES)

  @Bean("tasksDslContextRO")
  @Profile("postgresql")
  fun tasksDslContextROPostgreSQL(
    @Qualifier("postgresqlTasksDataSourceRO") dataSource: DataSource,
    transactionProvider: ObjectProvider<TransactionProvider?>,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider?>,
  ): DSLContext = createDslContext(dataSource, transactionProvider, executeListenerProviders, SQLDialect.POSTGRES)

  // SQLite DSL Context beans (default)
  @Bean("dslContextRW")
  @Profile("!postgresql")
  @Primary
  fun mainDslContextRW(
    dataSource: DataSource,
    transactionProvider: ObjectProvider<TransactionProvider?>,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider?>,
  ): DSLContext = createDslContext(dataSource, transactionProvider, executeListenerProviders, SQLDialect.SQLITE)

  @Bean("dslContextRO")
  @Profile("!postgresql")
  fun mainDslContextRO(
    @Qualifier("sqliteDataSourceRO") dataSource: DataSource,
    transactionProvider: ObjectProvider<TransactionProvider?>,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider?>,
  ): DSLContext = createDslContext(dataSource, transactionProvider, executeListenerProviders, SQLDialect.SQLITE)

  @Bean("tasksDslContextRW")
  @Profile("!postgresql")
  fun tasksDslContextRW(
    @Qualifier("tasksDataSourceRW") dataSource: DataSource,
    transactionProvider: ObjectProvider<TransactionProvider?>,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider?>,
  ): DSLContext = createDslContext(dataSource, transactionProvider, executeListenerProviders, SQLDialect.SQLITE)

  @Bean("tasksDslContextRO")
  @Profile("!postgresql")
  fun tasksDslContextRO(
    @Qualifier("tasksDataSourceRO") dataSource: DataSource,
    transactionProvider: ObjectProvider<TransactionProvider?>,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider?>,
  ): DSLContext = createDslContext(dataSource, transactionProvider, executeListenerProviders, SQLDialect.SQLITE)

  private fun createDslContext(
    dataSource: DataSource,
    transactionProvider: ObjectProvider<TransactionProvider?>,
    executeListenerProviders: ObjectProvider<ExecuteListenerProvider?>,
    dialect: SQLDialect,
  ) = DefaultDSLContext(
    DefaultConfiguration().also { configuration ->
      configuration.set(dialect)
      configuration.set(DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource)))
      transactionProvider.ifAvailable { newTransactionProvider: TransactionProvider? -> configuration.set(newTransactionProvider) }
      configuration.set(*executeListenerProviders.orderedStream().toList().toTypedArray())
    },
  )
}
