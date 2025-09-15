# Guida alla Migrazione da SQLite a PostgreSQL per Komga

## Prerequisiti

1. **PostgreSQL installato e configurato**
   - PostgreSQL 12 o superiore
   - Database `komga` creato
   - Utente `komga` con privilegi sul database

2. **Backup del database SQLite esistente**
   ```bash
   cp /path/to/komga.db /path/to/komga.db.backup
   ```

## Passaggi per la Migrazione

### 1. Configurazione delle Variabili d'Ambiente

Creare un file `.env` nella root del progetto:

```bash
# Database PostgreSQL
DATABASE_URL=jdbc:postgresql://localhost:5432/komga
DATABASE_USER=komga
DATABASE_PASSWORD=your_password_here

# Profilo Spring attivo
SPRING_PROFILES_ACTIVE=postgresql
```

### 2. Avvio con PostgreSQL

```bash
# Impostare il profilo PostgreSQL
export SPRING_PROFILES_ACTIVE=postgresql

# Avviare l'applicazione
java -jar komga.jar
```

### 3. Migrazione dei Dati (Opzioni)

#### Opzione A: Utilizzando pgloader (Raccomandato)

1. Installare pgloader:
   ```bash
   # Ubuntu/Debian
   sudo apt-get install pgloader
   
   # macOS
   brew install pgloader
   
   # Windows (usando WSL o Docker)
   docker run --rm -it dimitri/pgloader:latest
   ```

2. Creare file di configurazione pgloader (`migration.load`):
   ```
   LOAD DATABASE
        FROM sqlite:///path/to/komga.db
        INTO postgresql://komga:password@localhost/komga
   
   WITH include drop, create tables, create indexes, reset sequences
   
   SET work_mem to '16MB', maintenance_work_mem to '512 MB';
   ```

3. Eseguire la migrazione:
   ```bash
   pgloader migration.load
   ```

#### Opzione B: Esportazione/Importazione Manuale

1. Esportare dati da SQLite:
   ```bash
   sqlite3 komga.db <<EOF
   .headers on
   .mode csv
   .output library.csv
   SELECT * FROM library;
   .output series.csv
   SELECT * FROM series;
   .output book.csv
   SELECT * FROM book;
   .quit
   EOF
   ```

2. Importare in PostgreSQL:
   ```sql
   \copy library FROM 'library.csv' WITH (FORMAT csv, HEADER true);
   \copy series FROM 'series.csv' WITH (FORMAT csv, HEADER true);
   \copy book FROM 'book.csv' WITH (FORMAT csv, HEADER true);
   ```

### 4. Verifica della Migrazione

1. Controllare che l'applicazione si avvii correttamente
2. Verificare che i dati siano presenti:
   ```sql
   SELECT COUNT(*) FROM library;
   SELECT COUNT(*) FROM series;
   SELECT COUNT(*) FROM book;
   ```
3. Testare le funzionalità principali dell'applicazione

### 5. Configurazione di Produzione

#### Docker Compose con PostgreSQL

```yaml
version: '3.8'
services:
  komga:
    image: gotson/komga:latest
    environment:
      - SPRING_PROFILES_ACTIVE=postgresql
      - DATABASE_URL=jdbc:postgresql://postgres:5432/komga
      - DATABASE_USER=komga
      - DATABASE_PASSWORD=komga_password
    depends_on:
      - postgres
    ports:
      - "25600:25600"
    volumes:
      - komga_data:/config
      - /path/to/books:/books

  postgres:
    image: postgres:15
    environment:
      - POSTGRES_DB=komga
      - POSTGRES_USER=komga
      - POSTGRES_PASSWORD=komga_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

volumes:
  komga_data:
  postgres_data:
```

## Risoluzione Problemi

### Errori Comuni

1. **Errore di connessione PostgreSQL**
   - Verificare che PostgreSQL sia in esecuzione
   - Controllare le credenziali nel file di configurazione
   - Verificare che il database `komga` esista

2. **Errori di migrazione dati**
   - Verificare che le tabelle siano state create correttamente
   - Controllare i vincoli di chiave esterna
   - Verificare i tipi di dati compatibili

3. **Problemi di performance**
   - Aumentare la dimensione del pool di connessioni
   - Ottimizzare gli indici PostgreSQL
   - Configurare parametri PostgreSQL per il carico di lavoro

### Rollback a SQLite

Se necessario tornare a SQLite:

1. Cambiare il profilo Spring:
   ```bash
   export SPRING_PROFILES_ACTIVE=default
   ```

2. Ripristinare il file di configurazione originale

3. Riavviare l'applicazione

## Note Aggiuntive

- La migrazione può richiedere tempo a seconda della dimensione del database
- È consigliabile eseguire la migrazione in un ambiente di test prima della produzione
- Mantenere sempre un backup del database SQLite originale
- Monitorare le performance dopo la migrazione e ottimizzare se necessario