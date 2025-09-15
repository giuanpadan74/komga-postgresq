# Istruzioni per la Migrazione da SQLite a PostgreSQL

## Panoramica

Queste istruzioni guidano attraverso il processo di migrazione del database Komga da SQLite a PostgreSQL, mantenendo tutti i dati esistenti e garantendo la continuità del servizio.

## Prerequisiti

### Software Richiesto
- PostgreSQL 12+ installato e configurato
- pgloader (per migrazione automatica)
- Backup del database SQLite esistente
- Accesso amministrativo al server PostgreSQL

### Verifica Prerequisiti
```bash
# Verifica PostgreSQL
psql --version

# Verifica pgloader
pgloader --version

# Verifica connessione PostgreSQL
psql -h localhost -U postgres -c "SELECT version();"
```

## Fase 1: Preparazione e Backup

### 1.1 Backup del Database SQLite
```bash
# Ferma Komga
sudo systemctl stop komga

# Crea backup del database
cp ~/.komga/database.sqlite ~/.komga/database.sqlite.backup.$(date +%Y%m%d_%H%M%S)

# Verifica integrità backup
sqlite3 ~/.komga/database.sqlite.backup.* "PRAGMA integrity_check;"
```

### 1.2 Preparazione Database PostgreSQL
```sql
-- Connetti a PostgreSQL come superuser
psql -h localhost -U postgres

-- Crea database e utente
CREATE DATABASE komga;
CREATE USER komga_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE komga TO komga_user;

-- Connetti al database komga
\c komga

-- Crea estensioni necessarie
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
```

## Fase 2: Configurazione Komga

### 2.1 Variabili d'Ambiente
Crea o modifica il file di configurazione:

```bash
# File: /etc/systemd/system/komga.service.d/override.conf
[Service]
Environment="SPRING_PROFILES_ACTIVE=postgresql"
Environment="SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/komga"
Environment="SPRING_DATASOURCE_USERNAME=komga_user"
Environment="SPRING_DATASOURCE_PASSWORD=your_secure_password"
Environment="KOMGA_DATABASE_BACKUP_PATH=/path/to/backup"
```

### 2.2 Configurazione Docker (Alternativa)
```yaml
# docker-compose.yml
version: '3.8'
services:
  komga:
    image: gotson/komga:latest
    environment:
      - SPRING_PROFILES_ACTIVE=postgresql
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/komga
      - SPRING_DATASOURCE_USERNAME=komga_user
      - SPRING_DATASOURCE_PASSWORD=your_secure_password
    depends_on:
      - postgres
  
  postgres:
    image: postgres:15
    environment:
      - POSTGRES_DB=komga
      - POSTGRES_USER=komga_user
      - POSTGRES_PASSWORD=your_secure_password
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

## Fase 3: Migrazione Dati

### Metodo 1: Migrazione Automatica con pgloader (Raccomandato)

#### 3.1 Configurazione pgloader
Crea il file `komga-migration.load`:

```
LOAD DATABASE
  FROM sqlite:///home/user/.komga/database.sqlite
  INTO postgresql://komga_user:your_secure_password@localhost/komga

WITH include drop, create tables, create indexes, reset sequences

SET work_mem to '256MB',
    maintenance_work_mem to '512MB',
    search_path to 'public'

CAST type datetime to timestamptz drop default drop not null using zero-dates-to-null,
     type date drop not null drop default using zero-dates-to-null

ALTER SCHEMA 'main' RENAME TO 'public'

BEFORE LOAD DO
  $$ DROP SCHEMA IF EXISTS public CASCADE; $$,
  $$ CREATE SCHEMA public; $$;
```

#### 3.2 Esecuzione Migrazione
```bash
# Esegui migrazione
pgloader komga-migration.load

# Verifica risultati
psql -h localhost -U komga_user -d komga -c "\dt"
```

### Metodo 2: Migrazione Manuale

#### 3.1 Esportazione da SQLite
```bash
# Esporta schema
sqlite3 ~/.komga/database.sqlite ".schema" > komga_schema.sql

# Esporta dati
sqlite3 ~/.komga/database.sqlite ".dump" > komga_data.sql
```

#### 3.2 Conversione e Importazione
```bash
# Converti tipi di dati SQLite -> PostgreSQL
sed -i 's/INTEGER PRIMARY KEY AUTOINCREMENT/SERIAL PRIMARY KEY/g' komga_schema.sql
sed -i 's/TEXT/VARCHAR/g' komga_schema.sql
sed -i 's/REAL/DECIMAL/g' komga_schema.sql

# Importa in PostgreSQL
psql -h localhost -U komga_user -d komga -f komga_schema.sql
psql -h localhost -U komga_user -d komga -f komga_data.sql
```

## Fase 4: Avvio e Verifica

### 4.1 Primo Avvio
```bash
# Avvia Komga con profilo PostgreSQL
sudo systemctl start komga

# Verifica logs
sudo journalctl -u komga -f
```

### 4.2 Verifica Migrazione
```sql
-- Connetti al database
psql -h localhost -U komga_user -d komga

-- Verifica tabelle
\dt

-- Conta record principali
SELECT 'libraries' as table_name, COUNT(*) as count FROM library
UNION ALL
SELECT 'series', COUNT(*) FROM series
UNION ALL
SELECT 'books', COUNT(*) FROM book
UNION ALL
SELECT 'users', COUNT(*) FROM "user";

-- Verifica integrità referenziale
SELECT conname, conrelid::regclass, confrelid::regclass
FROM pg_constraint
WHERE contype = 'f';
```

### 4.3 Test Funzionalità
1. Accedi all'interfaccia web
2. Verifica visualizzazione librerie
3. Testa ricerca e filtri
4. Controlla lettura e progressi
5. Verifica funzioni amministrative

## Fase 5: Ottimizzazione Post-Migrazione

### 5.1 Ottimizzazione Database
```sql
-- Aggiorna statistiche
ANALYZE;

-- Ricostruisci indici
REINDEX DATABASE komga;

-- Configura autovacuum
ALTER TABLE series SET (autovacuum_vacuum_scale_factor = 0.1);
ALTER TABLE book SET (autovacuum_vacuum_scale_factor = 0.1);
```

### 5.2 Configurazione Performance
```sql
-- postgresql.conf ottimizzazioni
shared_buffers = 256MB
effective_cache_size = 1GB
work_mem = 4MB
maintenance_work_mem = 64MB
wal_buffers = 16MB
checkpoint_completion_target = 0.9
```

## Risoluzione Problemi

### Errori Comuni

#### Errore di Connessione
```bash
# Verifica servizio PostgreSQL
sudo systemctl status postgresql

# Verifica configurazione pg_hba.conf
sudo nano /etc/postgresql/15/main/pg_hba.conf
# Aggiungi: host komga komga_user 127.0.0.1/32 md5

# Riavvia PostgreSQL
sudo systemctl restart postgresql
```

#### Errori di Migrazione
```sql
-- Verifica encoding
SHOW server_encoding;
SHOW client_encoding;

-- Risolvi problemi charset
SET client_encoding = 'UTF8';
```

#### Performance Lente
```sql
-- Identifica query lente
SELECT query, mean_time, calls
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;

-- Crea indici mancanti
CREATE INDEX CONCURRENTLY idx_series_library_id ON series(library_id);
CREATE INDEX CONCURRENTLY idx_book_series_id ON book(series_id);
```

## Rollback (Se Necessario)

### Procedura di Rollback
```bash
# Ferma Komga
sudo systemctl stop komga

# Ripristina configurazione SQLite
sudo nano /etc/systemd/system/komga.service.d/override.conf
# Rimuovi variabili PostgreSQL

# Ripristina backup SQLite (se necessario)
cp ~/.komga/database.sqlite.backup.* ~/.komga/database.sqlite

# Riavvia con SQLite
sudo systemctl daemon-reload
sudo systemctl start komga
```

## Monitoraggio Post-Migrazione

### Script di Monitoraggio
```bash
#!/bin/bash
# monitor-komga.sh

echo "=== Komga PostgreSQL Status ==="
echo "Service Status:"
sudo systemctl is-active komga

echo "\nDatabase Connections:"
psql -h localhost -U komga_user -d komga -c "SELECT count(*) as active_connections FROM pg_stat_activity WHERE datname='komga';"

echo "\nDatabase Size:"
psql -h localhost -U komga_user -d komga -c "SELECT pg_size_pretty(pg_database_size('komga')) as database_size;"

echo "\nTop Tables by Size:"
psql -h localhost -U komga_user -d komga -c "SELECT schemaname,tablename,pg_size_pretty(size) as size FROM (SELECT schemaname,tablename,pg_total_relation_size(schemaname||'.'||tablename) AS size FROM pg_tables WHERE schemaname='public') AS table_sizes ORDER BY size DESC LIMIT 5;"
```

## Backup Automatico PostgreSQL

### Script di Backup
```bash
#!/bin/bash
# backup-komga-postgresql.sh

BACKUP_DIR="/backup/komga"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="komga_backup_$DATE.sql"

mkdir -p $BACKUP_DIR

# Backup completo
pg_dump -h localhost -U komga_user -d komga > "$BACKUP_DIR/$BACKUP_FILE"

# Comprimi backup
gzip "$BACKUP_DIR/$BACKUP_FILE"

# Rimuovi backup vecchi (mantieni ultimi 7 giorni)
find $BACKUP_DIR -name "komga_backup_*.sql.gz" -mtime +7 -delete

echo "Backup completato: $BACKUP_DIR/$BACKUP_FILE.gz"
```

### Crontab per Backup Automatico
```bash
# Aggiungi a crontab
crontab -e

# Backup giornaliero alle 2:00
0 2 * * * /path/to/backup-komga-postgresql.sh
```

## Note Finali

- **Tempo di Migrazione**: Dipende dalla dimensione del database (tipicamente 10-30 minuti per database medi)
- **Downtime**: Pianifica 1-2 ore di downtime per la migrazione completa
- **Test**: Testa sempre su ambiente di sviluppo prima della produzione
- **Backup**: Mantieni sempre backup recenti prima di procedere
- **Monitoraggio**: Monitora performance per le prime settimane post-migrazione

## Supporto

Per problemi specifici:
1. Controlla i log di Komga: `sudo journalctl -u komga -f`
2. Verifica log PostgreSQL: `sudo tail -f /var/log/postgresql/postgresql-15-main.log`
3. Consulta la documentazione ufficiale Komga
4. Contatta il supporto della community

---

**Importante**: Questa migrazione è irreversibile senza backup. Assicurati di avere backup completi prima di procedere.