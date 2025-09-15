-- Full-text search implementation for PostgreSQL
-- Converted from SQLite FTS5 to PostgreSQL GIN indices

-- Add tsvector columns for full-text search
ALTER TABLE BOOK_METADATA ADD COLUMN search_vector tsvector;
ALTER TABLE SERIES_METADATA ADD COLUMN search_vector tsvector;
ALTER TABLE COLLECTION ADD COLUMN search_vector tsvector;
ALTER TABLE READLIST ADD COLUMN search_vector tsvector;
ALTER TABLE BOOK_METADATA_AGGREGATION_AUTHOR ADD COLUMN search_vector tsvector;

-- Create GIN indices for full-text search
CREATE INDEX idx_book_metadata_search ON BOOK_METADATA USING GIN(search_vector);
CREATE INDEX idx_series_metadata_search ON SERIES_METADATA USING GIN(search_vector);
CREATE INDEX idx_collection_search ON COLLECTION USING GIN(search_vector);
CREATE INDEX idx_readlist_search ON READLIST USING GIN(search_vector);
CREATE INDEX idx_book_metadata_aggregation_author_search ON BOOK_METADATA_AGGREGATION_AUTHOR USING GIN(search_vector);

-- Function to update search vectors for BOOK_METADATA
CREATE OR REPLACE FUNCTION update_book_metadata_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := 
        setweight(to_tsvector('english', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.isbn, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to update search vectors for SERIES_METADATA
CREATE OR REPLACE FUNCTION update_series_metadata_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := 
        setweight(to_tsvector('english', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.publisher, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to update search vectors for COLLECTION
CREATE OR REPLACE FUNCTION update_collection_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', COALESCE(NEW.name, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to update search vectors for READLIST
CREATE OR REPLACE FUNCTION update_readlist_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', COALESCE(NEW.name, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to update search vectors for BOOK_METADATA_AGGREGATION_AUTHOR
CREATE OR REPLACE FUNCTION update_book_metadata_aggregation_author_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', COALESCE(NEW.name, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers to keep the search vectors up to date
CREATE TRIGGER book_metadata_search_vector_update
    BEFORE INSERT OR UPDATE ON BOOK_METADATA
    FOR EACH ROW EXECUTE FUNCTION update_book_metadata_search_vector();

CREATE TRIGGER series_metadata_search_vector_update
    BEFORE INSERT OR UPDATE ON SERIES_METADATA
    FOR EACH ROW EXECUTE FUNCTION update_series_metadata_search_vector();

CREATE TRIGGER collection_search_vector_update
    BEFORE INSERT OR UPDATE ON COLLECTION
    FOR EACH ROW EXECUTE FUNCTION update_collection_search_vector();

CREATE TRIGGER readlist_search_vector_update
    BEFORE INSERT OR UPDATE ON READLIST
    FOR EACH ROW EXECUTE FUNCTION update_readlist_search_vector();

CREATE TRIGGER book_metadata_aggregation_author_search_vector_update
    BEFORE INSERT OR UPDATE ON BOOK_METADATA_AGGREGATION_AUTHOR
    FOR EACH ROW EXECUTE FUNCTION update_book_metadata_aggregation_author_search_vector();

-- Update existing records with search vectors
UPDATE BOOK_METADATA SET search_vector = 
    setweight(to_tsvector('english', COALESCE(title, '')), 'A') ||
    setweight(to_tsvector('english', COALESCE(isbn, '')), 'B');

UPDATE SERIES_METADATA SET search_vector = 
    setweight(to_tsvector('english', COALESCE(title, '')), 'A') ||
    setweight(to_tsvector('english', COALESCE(publisher, '')), 'B');

UPDATE COLLECTION SET search_vector = to_tsvector('english', COALESCE(name, ''));

UPDATE READLIST SET search_vector = to_tsvector('english', COALESCE(name, ''));

UPDATE BOOK_METADATA_AGGREGATION_AUTHOR SET search_vector = to_tsvector('english', COALESCE(name, ''));