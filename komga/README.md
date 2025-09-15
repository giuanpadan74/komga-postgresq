# Komga - Server per Fumetti e Manga

Komga è un server multimediale gratuito e open source per i tuoi fumetti, manga e BD.

## Caratteristiche

- Interfaccia web responsive
- Supporto per formati CBZ, CBR, PDF, EPUB
- Gestione di librerie multiple
- Metadati automatici e manuali
- Utenti e controllo accessi
- API REST completa
- Supporto per lettori OPDS

## Supporto Database

Komga supporta due tipi di database:

### SQLite (Default)
- Database embedded, nessuna configurazione aggiuntiva richiesta
- Ideale per installazioni domestiche e piccole librerie
- Configurazione automatica

### PostgreSQL (Nuovo)
- Database enterprise per grandi volumi di dati
- Migliori performance con librerie estese
- Supporto per deployment scalabili
- Richiede configurazione manuale

## Configurazione PostgreSQL

### Prerequisiti
1. PostgreSQL 12 o superiore installato
2. Database `komga` creato
3. Utente con privilegi appropriati

### Configurazione

1. **Variabili d'ambiente:**
   ```bash
   export SPRING_PROFILES_ACTIVE=postgresql
   export DATABASE_URL=jdbc:postgresql://localhost:5432/komga
   export DATABASE_USER=komga
   export DATABASE_PASSWORD=your_password
   ```

2. **File di configurazione (.env):**
   ```
   SPRING_PROFILES_ACTIVE=postgresql
   DATABASE_URL=jdbc:postgresql://localhost:5432/komga
   DATABASE_USER=komga
   DATABASE_PASSWORD=your_password
   ```

### Docker Compose con PostgreSQL

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

volumes:
  komga_data:
  postgres_data:
```

## Migrazione da SQLite a PostgreSQL

Per migrare un'installazione esistente da SQLite a PostgreSQL:

1. **Backup del database esistente:**
   ```bash
   cp /config/database.db /config/database.db.backup
   ```

2. **Seguire la guida completa:**
   Consultare `scripts/migration-guide.md` per istruzioni dettagliate

3. **Strumenti consigliati:**
   - pgloader per migrazione automatica
   - Export/Import manuale per controllo granulare

## Installazione

### Docker (Raccomandato)

```bash
# SQLite (default)
docker run -d \
  --name komga \
  -p 25600:25600 \
  -v /path/to/config:/config \
  -v /path/to/books:/books \
  gotson/komga:latest

# PostgreSQL
docker run -d \
  --name komga \
  -p 25600:25600 \
  -e SPRING_PROFILES_ACTIVE=postgresql \
  -e DATABASE_URL=jdbc:postgresql://host:5432/komga \
  -e DATABASE_USER=komga \
  -e DATABASE_PASSWORD=password \
  -v /path/to/config:/config \
  -v /path/to/books:/books \
  gotson/komga:latest
```

### JAR

```bash
# SQLite (default)
java -jar komga.jar

# PostgreSQL
export SPRING_PROFILES_ACTIVE=postgresql
export DATABASE_URL=jdbc:postgresql://localhost:5432/komga
export DATABASE_USER=komga
export DATABASE_PASSWORD=password
java -jar komga.jar
```

## Sviluppo

### Requisiti
- JDK 17 o superiore
- Node.js 18 o superiore
- PostgreSQL (per sviluppo con PostgreSQL)

### Build

```bash
# Build completo
./gradlew build

# Build senza test
./gradlew build -x test

# Esecuzione con profilo PostgreSQL
export SPRING_PROFILES_ACTIVE=postgresql
./gradlew bootRun
```

### Test

```bash
# Test con SQLite
./gradlew test

# Test con PostgreSQL
export SPRING_PROFILES_ACTIVE=postgresql
./gradlew test
```

## Performance

### SQLite vs PostgreSQL

| Caratteristica | SQLite | PostgreSQL |
|----------------|--------|------------|
| Setup | Automatico | Manuale |
| Librerie piccole (<10k libri) | Ottimo | Buono |
| Librerie grandi (>50k libri) | Limitato | Eccellente |
| Concurrent users | Limitato | Eccellente |
| Backup | File singolo | Dump SQL |
| Scalabilità | Verticale | Orizzontale |

### Raccomandazioni

- **SQLite**: Installazioni domestiche, librerie fino a 10-20k libri
- **PostgreSQL**: Installazioni enterprise, librerie estese, utenti multipli

## Contribuire

1. Fork del repository
2. Creare branch per feature (`git checkout -b feature/amazing-feature`)
3. Commit delle modifiche (`git commit -m 'Add amazing feature'`)
4. Push del branch (`git push origin feature/amazing-feature`)
5. Aprire Pull Request

## Licenza

Questo progetto è rilasciato sotto licenza MIT. Vedere `LICENSE` per dettagli.

## Supporto

- [Documentazione](https://komga.org)
- [Discord](https://discord.gg/TdRpkDu)
- [GitHub Issues](https://github.com/gotson/komga/issues)