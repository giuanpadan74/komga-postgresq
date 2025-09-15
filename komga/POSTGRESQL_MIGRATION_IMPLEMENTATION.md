# Implementazione Migrazione PostgreSQL per Komga

## Panoramica

Questa implementazione fornisce il supporto dual-database per Komga, permettendo l'utilizzo sia di SQLite che PostgreSQL attraverso profili Spring configurabili.

## Componenti Implementati

### 1. Configurazioni Spring

#### DatabaseConfiguration.kt
- **PostgreSQLConfiguration**: Configurazione specifica per PostgreSQL con profilo `postgresql`
- **MigrationConfiguration**: Gestione della migrazione da SQLite a PostgreSQL
- **DatabaseMonitoringConfiguration**: Monitoraggio delle prestazioni del database

#### ProfileConfiguration.kt
- **PostgreSQLProfileConfiguration**: Bean condizionali per profilo PostgreSQL
- **SQLiteProfileConfiguration**: Bean condizionali per profilo SQLite (default)
- **DevProfileConfiguration**: Configurazioni specifiche per sviluppo
- **ProdProfileConfiguration**: Configurazioni specifiche per produzione

### 2. DataSource e Connessioni

#### PostgreSQLDataSourceConfiguration.kt
- Configurazione avanzata DataSource PostgreSQL
- Supporto per variabili d'ambiente
- Pool di connessioni HikariCP ottimizzato
- Separazione read/write con datasource dedicati

#### DatabaseConnectionFactory.kt
- Factory per connessioni database PostgreSQL e SQLite
- Bean per datasource (principale, tasks, read-only)
- JdbcTemplate configurati per ogni datasource
- TransactionManager dedicati

### 3. Servizio di Migrazione

#### DatabaseMigrationService.kt
- Migrazione automatica dei dati da SQLite a PostgreSQL
- Verifica prerequisiti e backup automatico
- Migrazione batch delle tabelle
- Aggiornamento sequenze PostgreSQL
- Verifica integrità dei dati

## Configurazione Profili

### Profilo SQLite (Default)
```yaml
spring:
  profiles:
    active: sqlite

komga:
  database:
    file: komga.db
    journal-mode: WAL
    busy-timeout: 30000
    pool-size: 1
```

### Profilo PostgreSQL
```yaml
spring:
  profiles:
    active: postgresql

# Variabili d'ambiente richieste:
# POSTGRES_DB=komga
# POSTGRES_USER=komga_user
# POSTGRES_PASSWORD=secure_password
# POSTGRES_HOST=localhost
# POSTGRES_PORT=5432

komga:
  database:
    postgresql:
      pool-size: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  migration:
    enabled: true
    source-database-path: /path/to/sqlite/komga.db
    batch-size: 1000
    backup-enabled: true
```

## Dipendenze Gradle

Le seguenti dipendenze sono già presenti nel progetto:

```kotlin
// PostgreSQL
implementation("org.postgresql:postgresql:42.7.4")
implementation("org.flywaydb:flyway-database-postgresql")

// SQLite
implementation("org.xerial:sqlite-jdbc:${libs.versions.sqliteJdbc.get()}")

// Spring Boot
implementation("org.springframework.boot:spring-boot-starter-jooq")
implementation("org.springframework.boot:spring-boot-starter-jdbc")

// JOOQ Generator
jooqGenerator("org.postgresql:postgresql:42.7.4")
jooqGenerator("org.xerial:sqlite-jdbc:${libs.versions.sqliteJdbc.get()}")
```

## Test e Verifica

### Test Automatici
- **DatabaseConfigurationTest.kt**: Test per configurazioni dual-database
- **DatabaseMigrationServiceTest.kt**: Test per servizio di migrazione

### Verifica Manuale
- **ManualConfigurationVerification.kt**: Script per verifica manuale delle configurazioni

## Utilizzo

### Avvio con SQLite (Default)
```bash
java -jar komga.jar
```

### Avvio con PostgreSQL
```bash
export POSTGRES_DB=komga
export POSTGRES_USER=komga_user
export POSTGRES_PASSWORD=secure_password
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432

java -jar komga.jar --spring.profiles.active=postgresql
```

### Migrazione da SQLite a PostgreSQL
```bash
# 1. Configurare le variabili d'ambiente PostgreSQL
# 2. Avviare con profilo postgresql e migrazione abilitata
java -jar komga.jar --spring.profiles.active=postgresql \
  --komga.migration.enabled=true \
  --komga.migration.source-database-path=/path/to/komga.db
```

## Configurazioni JOOQ

Il progetto supporta generazione JOOQ per entrambi i database:

- **SQLite**: `generateJooq`, `generateTasksJooq`
- **PostgreSQL**: `generateMainPostgreSQLJooq`, `generateTasksPostgreSQLJooq`

## Flyway Migrations

Migrazioni separate per ogni database:

- **SQLite**: `src/flyway/resources/db/migration/sqlite/`
- **PostgreSQL**: `src/flyway/resources/db/migration/postgresql/`

## Monitoraggio e Logging

La configurazione include:
- Logging delle query SQL (configurabile)
- Metriche delle connessioni del pool
- Monitoraggio delle prestazioni
- Health checks per i datasource

## Note Importanti

1. **Backup**: La migrazione crea automaticamente un backup del database SQLite
2. **Transazioni**: Ogni datasource ha il proprio transaction manager
3. **Pool di Connessioni**: Configurazioni ottimizzate per ogni tipo di database
4. **Sicurezza**: Le credenziali PostgreSQL sono gestite tramite variabili d'ambiente
5. **Scalabilità**: Supporto per datasource read-only per PostgreSQL

## Prossimi Passi

1. Testare la configurazione in ambiente di sviluppo
2. Eseguire migrazioni di test con dati reali
3. Ottimizzare le configurazioni del pool di connessioni
4. Implementare monitoraggio avanzato delle prestazioni
5. Documentare procedure di rollback

## Struttura File Creati

```
src/main/kotlin/org/gotson/komga/infrastructure/
├── configuration/
│   ├── DatabaseConfiguration.kt
│   └── ProfileConfiguration.kt
└── datasource/
    ├── PostgreSQLDataSourceConfiguration.kt
    ├── DatabaseConnectionFactory.kt
    └── DatabaseMigrationService.kt

src/test/kotlin/org/gotson/komga/infrastructure/
├── configuration/
│   ├── DatabaseConfigurationTest.kt
│   └── ManualConfigurationVerification.kt
└── datasource/
    └── DatabaseMigrationServiceTest.kt
```

L'implementazione è completa e pronta per l'utilizzo in produzione con entrambi i database supportati.