-- Performance indices for PostgreSQL
-- Converted from SQLite performance indices

-- Missing foreign key indices
CREATE INDEX IF NOT EXISTS idx__book_metadata_link__book_id
    ON BOOK_METADATA_LINK (BOOK_ID);
CREATE INDEX IF NOT EXISTS idx__series_metadata_sharing__series_id
    ON SERIES_METADATA_SHARING (SERIES_ID);
CREATE INDEX IF NOT EXISTS idx__book_metadata_aggregation_tag__series_id
    ON BOOK_METADATA_AGGREGATION_TAG (SERIES_ID);
CREATE INDEX IF NOT EXISTS idx__thumbnail_collection__collection_id
    ON THUMBNAIL_COLLECTION (COLLECTION_ID);
CREATE INDEX IF NOT EXISTS idx__thumbnail_readlist__readlist_id
    ON THUMBNAIL_READLIST (READLIST_ID);
CREATE INDEX IF NOT EXISTS idx__thumbnail_series__series_id
    ON THUMBNAIL_SERIES (SERIES_ID);
CREATE INDEX IF NOT EXISTS idx__authentication_activity__user_id
    ON AUTHENTICATION_ACTIVITY (USER_ID);

-- Sorting indices - if you sort by it, index it
CREATE INDEX IF NOT EXISTS idx__book_metadata__number_sort
    ON BOOK_METADATA (NUMBER_SORT);
CREATE INDEX IF NOT EXISTS idx__series__last_modified_date
    ON SERIES (LAST_MODIFIED_DATE);
CREATE INDEX IF NOT EXISTS idx__series__created_date
    ON SERIES (CREATED_DATE);
CREATE INDEX IF NOT EXISTS idx__book_metadata__release_date
    ON BOOK_METADATA (RELEASE_DATE);
CREATE INDEX IF NOT EXISTS idx__book__created_date
    ON BOOK (CREATED_DATE);
CREATE INDEX IF NOT EXISTS idx__read_progress__last_modified_date
    ON READ_PROGRESS (LAST_MODIFIED_DATE);
CREATE INDEX IF NOT EXISTS idx__media__status
    ON MEDIA (STATUS);