CREATE INDEX IF NOT EXISTS ILL_SRC_IDX ON ILL(SOURCE_LANG_ID, SOURCE_ID);
CREATE INDEX IF NOT EXISTS ILL_DEST_IDX ON ILL(DEST_LANG_ID, DEST_ID)
