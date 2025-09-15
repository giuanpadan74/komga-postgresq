# Analisi Migrazione Database Komga: SQLite → PostgreSQL

## Panoramica
Komga attualmente utilizza SQLite come database principale, che presenta limitazioni per gestire grandi quantità di dati. Questa analisi documenta la struttura completa del database per pianificare la migrazione a PostgreSQL.

## Database Attuali
Komga utilizza **due database SQLite separati**:
1. **Database principale** (`database.sqlite`) - dati dell'applicazione
2. **Database tasks** (`tasks.sqlite`) - gestione task asincroni

## Struttura Database Principale

### Tabelle Core

#### LIBRARY
- **Scopo**: Gestione delle librerie di contenuti
- **Campi chiave**: ID (varchar PK), NAME, ROOT (path), import flags
- **Note**: Contiene configurazioni per import automatico metadati

#### USER
- **Scopo**: Gestione utenti e autenticazione
- **Campi chiave**: ID (varchar PK), EMAIL (unique), PASSWORD
- **Evoluzione**: Migrato da ruoli booleani a sistema USER_ROLE (v2025)

#### USER_ROLE
- **Scopo**: Sistema ruoli flessibile (sostituisce colonne booleane)
- **Ruoli**: ADMIN, FILE_DOWNLOAD, PAGE_STREAMING, KOBO_SYNC, KOREADER_SYNC

#### SERIES
- **Scopo**: Gestione serie di fumetti/manga
- **Campi chiave**: ID (varchar PK), NAME, URL, LIBRARY_ID (FK)
- **Relazioni**: Appartiene a LIBRARY

#### BOOK
- **Scopo**: Gestione singoli volumi/libri
- **Campi chiave**: ID (varchar PK), NAME, URL, SERIES_ID (FK), LIBRARY_ID (FK)
- **Note**: Doppia relazione con SERIES e LIBRARY

### Tabelle Metadati

#### SERIES_METADATA
- **Scopo**: Metadati delle serie
- **Caratteristiche**: Campi con lock per prevenire sovrascritture
- **Campi**: STATUS, TITLE, TITLE_SORT + relativi _LOCK

#### BOOK_METADATA
- **Scopo**: Metadati dei libri
- **Campi complessi**: AGE_RATING, NUMBER, PUBLISHER, READING_DIRECTION
- **Sistema lock**: Ogni campo ha corrispondente _LOCK boolean

#### BOOK_METADATA_AUTHOR
- **Scopo**: Autori dei libri (relazione many-to-many)
- **Campi**: NAME, ROLE, BOOK_ID (FK)

### Tabelle Media

#### MEDIA
- **Scopo**: Informazioni sui file media
- **Campi critici**: MEDIA_TYPE, STATUS, THUMBNAIL (blob), PAGE_COUNT
- **Relazione**: One-to-one con BOOK

#### MEDIA_PAGE
- **Scopo**: Pagine individuali dei media
- **PK composita**: (BOOK_ID, NUMBER)
- **Campi**: FILE_NAME, MEDIA_TYPE, NUMBER

#### MEDIA_FILE
- **Scopo**: File associati ai media
- **Relazione**: Many-to-one con BOOK

### Tabelle Funzionalità

#### READ_PROGRESS
- **Scopo**: Progresso di lettura per utente
- **PK composita**: (BOOK_ID, USER_ID)
- **Campi**: PAGE, COMPLETED, READ_DATE

#### COLLECTION
- **Scopo**: Collezioni di serie
- **Caratteristiche**: ORDERED (boolean), SERIES_COUNT

#### COLLECTION_SERIES
- **Scopo**: Relazione many-to-many Collection-Series
- **PK composita**: (COLLECTION_ID, SERIES_ID)
- **Ordinamento**: Campo NUMBER

### Tabelle Avanzate

#### USER_API_KEY
- **Scopo**: Gestione API keys per utenti
- **Campi**: API_KEY (unique), COMMENT, USER_ID (FK)

#### AUTHENTICATION_ACTIVITY
- **Scopo**: Log attività autenticazione
- **Campi**: SOURCE, API_KEY_ID, API_KEY_COMMENT

## Caratteristiche SQLite da Migrare

### Tipi di Dati Specifici
- `varchar` → `VARCHAR` o `TEXT`
- `datetime` → `TIMESTAMP`
- `boolean` → `BOOLEAN`
- `blob` → `BYTEA`
- `int8` → `BIGINT`
- `real` → `REAL` o `NUMERIC`

### Funzionalità SQLite
- `CURRENT_TIMESTAMP` → `CURRENT_TIMESTAMP`
- `PRAGMA foreign_keys` → Gestione automatica FK in PostgreSQL
- Indici specifici da ricreare

### Indici Identificati
- `idx__user_api_key__user_id`
- Indici su campi di ricerca frequente
- Indici per performance su tabelle grandi

## Sfide della Migrazione

### 1. Gestione BLOB
- Thumbnails in MEDIA.THUMBNAIL
- Migrazione a BYTEA PostgreSQL

### 2. Chiavi Primarie VARCHAR
- Tutte le PK sono VARCHAR (probabilmente UUID)
- Mantenere compatibilità con codice esistente

### 3. Sistema Lock Metadati
- Pattern _LOCK per ogni campo metadati
- Preservare logica applicativa

### 4. Relazioni Complesse
- BOOK ha FK sia verso SERIES che LIBRARY
- Molte relazioni many-to-many

### 5. Database Separati
- Migrare sia database principale che tasks
- Mantenere separazione logica

## Vantaggi Attesi con PostgreSQL

### Performance
- Migliori performance su dataset grandi
- Indici più efficienti
- Query parallele

### Scalabilità
- Supporto per connessioni concorrenti
- Gestione memoria migliorata
- Vacuum automatico

### Funzionalità
- Full-text search nativo
- JSON support per metadati complessi
- Stored procedures per logica complessa

## Prossimi Passi
1. Creare migrazioni Flyway PostgreSQL
2. Aggiornare configurazione Gradle/Spring
3. Modificare generazione JOOQ
4. Implementare script migrazione dati
5. Testing completo

---
*Documento generato per la migrazione Komga SQLite → PostgreSQL*