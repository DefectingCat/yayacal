ALTER TABLE media
    ADD COLUMN thumbnail_bytes bigint CHECK (thumbnail_bytes > 0),
    ADD COLUMN preview_bytes bigint CHECK (preview_bytes > 0);
