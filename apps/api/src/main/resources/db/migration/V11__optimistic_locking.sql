-- Every row that can be edited carries a version, checked and increased by each update: a write made from a stale
-- read is refused instead of erasing the change made meanwhile (the adventure has had one since V1).
-- It also tells a new row from an existing one, so that a new row is inserted without being looked for first.
ALTER TABLE accounts ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE spellbook ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE "character" ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE adventure_note ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE historical_note ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE planning ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE availability ADD COLUMN version bigint NOT NULL DEFAULT 0;
