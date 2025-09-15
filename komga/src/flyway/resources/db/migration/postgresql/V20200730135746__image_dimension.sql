-- Add image dimension columns to media_page table for PostgreSQL
-- Converted from SQLite image dimension migration

ALTER TABLE MEDIA_PAGE ADD COLUMN WIDTH INTEGER;
ALTER TABLE MEDIA_PAGE ADD COLUMN HEIGHT INTEGER;