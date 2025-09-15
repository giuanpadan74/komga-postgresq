package org.gotson.komga.infrastructure.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import javax.sql.DataSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * Test per verificare la configurazione dual-database con profili SQLite e PostgreSQL
 */
class DatabaseConfigurationTest {

    @SpringBootTest
    @ActiveProfiles("sqlite", "test")
    @TestPropertySource(properties = [
        "komga.database.file=test.db",
        "komga.database.journal-mode=WAL",
        "komga.database.busy-timeout=30000",
        "komga.database.pool-size=1"
    ])
    class SQLiteConfigurationTest {

        @Autowired
        private lateinit var applicationContext: ApplicationContext

        @Test
        fun `should configure SQLite datasources correctly`() {
            // Verifica che i bean SQLite siano presenti
            assertThat(applicationContext.containsBean("dataSource")).isTrue()
            assertThat(applicationContext.containsBean("tasksDataSource")).isTrue()
            assertThat(applicationContext.containsBean("jdbcTemplate")).isTrue()
            assertThat(applicationContext.containsBean("tasksJdbcTemplate")).isTrue()
            assertThat(applicationContext.containsBean("transactionManager")).isTrue()
            assertThat(applicationContext.containsBean("tasksTransactionManager")).isTrue()

            // Verifica che i datasource siano configurati correttamente
            val dataSource = applicationContext.getBean("dataSource", DataSource::class.java)
            val tasksDataSource = applicationContext.getBean("tasksDataSource", DataSource::class.java)
            
            assertThat(dataSource).isNotNull()
            assertThat(tasksDataSource).isNotNull()
            assertThat(dataSource).isNotSameAs(tasksDataSource)
        }

        @Test
        fun `should configure SQLite JDBC templates correctly`() {
            val jdbcTemplate = applicationContext.getBean("jdbcTemplate", JdbcTemplate::class.java)
            val tasksJdbcTemplate = applicationContext.getBean("tasksJdbcTemplate", JdbcTemplate::class.java)
            
            assertThat(jdbcTemplate).isNotNull()
            assertThat(tasksJdbcTemplate).isNotNull()
            assertThat(jdbcTemplate.dataSource).isNotSameAs(tasksJdbcTemplate.dataSource)
        }

        @Test
        fun `should configure SQLite transaction managers correctly`() {
            val transactionManager = applicationContext.getBean("transactionManager", PlatformTransactionManager::class.java)
            val tasksTransactionManager = applicationContext.getBean("tasksTransactionManager", PlatformTransactionManager::class.java)
            
            assertThat(transactionManager).isNotNull()
            assertThat(tasksTransactionManager).isNotNull()
        }
    }

    @SpringBootTest
    @ActiveProfiles("postgresql", "test")
    @TestPropertySource(properties = [
        "POSTGRES_DB=komga_test",
        "POSTGRES_USER=test_user",
        "POSTGRES_PASSWORD=test_password",
        "POSTGRES_HOST=localhost",
        "POSTGRES_PORT=5432",
        "komga.database.postgresql.pool-size=5",
        "komga.database.postgresql.connection-timeout=30000",
        "komga.database.postgresql.idle-timeout=600000",
        "komga.database.postgresql.max-lifetime=1800000"
    ])
    class PostgreSQLConfigurationTest {

        @Autowired
        private lateinit var applicationContext: ApplicationContext

        @Test
        fun `should configure PostgreSQL datasources correctly`() {
            // Verifica che i bean PostgreSQL siano presenti
            assertThat(applicationContext.containsBean("dataSource")).isTrue()
            assertThat(applicationContext.containsBean("tasksDataSource")).isTrue()
            assertThat(applicationContext.containsBean("readOnlyDataSource")).isTrue()
            assertThat(applicationContext.containsBean("jdbcTemplate")).isTrue()
            assertThat(applicationContext.containsBean("tasksJdbcTemplate")).isTrue()
            assertThat(applicationContext.containsBean("readOnlyJdbcTemplate")).isTrue()
            assertThat(applicationContext.containsBean("transactionManager")).isTrue()
            assertThat(applicationContext.containsBean("tasksTransactionManager")).isTrue()

            // Verifica che i datasource siano configurati correttamente
            val dataSource = applicationContext.getBean("dataSource", DataSource::class.java)
            val tasksDataSource = applicationContext.getBean("tasksDataSource", DataSource::class.java)
            val readOnlyDataSource = applicationContext.getBean("readOnlyDataSource", DataSource::class.java)
            
            assertThat(dataSource).isNotNull()
            assertThat(tasksDataSource).isNotNull()
            assertThat(readOnlyDataSource).isNotNull()
            assertThat(dataSource).isNotSameAs(tasksDataSource)
            assertThat(dataSource).isNotSameAs(readOnlyDataSource)
        }

        @Test
        fun `should configure PostgreSQL JDBC templates correctly`() {
            val jdbcTemplate = applicationContext.getBean("jdbcTemplate", JdbcTemplate::class.java)
            val tasksJdbcTemplate = applicationContext.getBean("tasksJdbcTemplate", JdbcTemplate::class.java)
            val readOnlyJdbcTemplate = applicationContext.getBean("readOnlyJdbcTemplate", JdbcTemplate::class.java)
            
            assertThat(jdbcTemplate).isNotNull()
            assertThat(tasksJdbcTemplate).isNotNull()
            assertThat(readOnlyJdbcTemplate).isNotNull()
            assertThat(jdbcTemplate.dataSource).isNotSameAs(tasksJdbcTemplate.dataSource)
            assertThat(jdbcTemplate.dataSource).isNotSameAs(readOnlyJdbcTemplate.dataSource)
        }

        @Test
        fun `should configure PostgreSQL transaction managers correctly`() {
            val transactionManager = applicationContext.getBean("transactionManager", PlatformTransactionManager::class.java)
            val tasksTransactionManager = applicationContext.getBean("tasksTransactionManager", PlatformTransactionManager::class.java)
            
            assertThat(transactionManager).isNotNull()
            assertThat(tasksTransactionManager).isNotNull()
        }
    }

    @SpringBootTest
    @ActiveProfiles("test")
    class ProfileConfigurationTest {

        @Autowired
        private lateinit var applicationContext: ApplicationContext

        @Test
        fun `should have profile-specific configurations`() {
            // Verifica che le configurazioni dei profili siano presenti
            assertThat(applicationContext.containsBean("postgresqlConfiguration")).isFalse() // Non attivo senza profilo postgresql
            assertThat(applicationContext.containsBean("sqliteConfiguration")).isTrue() // Attivo per default
        }
    }
}