-- Script di migrazione dati da SQLite a PostgreSQL
-- Questo script deve essere eseguito dopo aver configurato PostgreSQL
-- e aver eseguito le migrazioni Flyway

-- Note per la migrazione:
-- 1. Eseguire prima il backup del database SQLite esistente
-- 2. Configurare PostgreSQL e creare il database 'komga'
-- 3. Eseguire le migrazioni Flyway per creare la struttura delle tabelle
-- 4. Utilizzare uno strumento come pgloader o script personalizzato per migrare i dati

-- Esempio di comando pgloader per migrare da SQLite a PostgreSQL:
-- pgloader sqlite:///path/to/komga.db postgresql://user:password@localhost/komga

-- Script alternativo usando COPY (da eseguire dopo aver esportato i dati da SQLite):

-- Disabilita i vincoli di chiave esterna temporaneamente
SET session_replication_role = replica;

-- Esempio di inserimento dati (da adattare ai dati reali esportati da SQLite)
-- COPY book (id, name, summary, number, created_date, last_modified_date, file_last_modified, file_size, media_type, status, library_id, series_id, url) 
-- FROM '/path/to/exported/book_data.csv' 
-- WITH (FORMAT csv, HEADER true);

-- COPY series (id, name, summary, status, created_date, last_modified_date, file_last_modified, library_id) 
-- FROM '/path/to/exported/series_data.csv' 
-- WITH (FORMAT csv, HEADER true);

-- COPY library (id, name, root, scan_force_modified_time, scan_deep, repair_extensions, convert_tiff_to_cbz, empty_trash_after_scan, series_cover, hash_files, hash_pages, analyze_dimensions, created_date, last_modified_date) 
-- FROM '/path/to/exported/library_data.csv' 
-- WITH (FORMAT csv, HEADER true);

-- Riabilita i vincoli di chiave esterna
SET session_replication_role = DEFAULT;

-- Aggiorna le sequenze PostgreSQL dopo l'inserimento dei dati
-- SELECT setval('book_id_seq', (SELECT MAX(id) FROM book));
-- SELECT setval('series_id_seq', (SELECT MAX(id) FROM series));
-- SELECT setval('library_id_seq', (SELECT MAX(id) FROM library));

-- Verifica l'integrità dei dati migrati
-- SELECT COUNT(*) FROM book;
-- SELECT COUNT(*) FROM series;
-- SELECT COUNT(*) FROM library;