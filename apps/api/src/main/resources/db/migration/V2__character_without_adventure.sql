-- A character can be kept outside any adventure of the application (one played at a real table, for instance).
ALTER TABLE "character" ALTER COLUMN adventure_id DROP NOT NULL;

-- The characters of an account kept outside any adventure are counted for its quantity limit.
CREATE INDEX idx_character_account_without_adventure ON "character" (account_id) WHERE adventure_id IS NULL;
