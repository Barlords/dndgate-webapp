-- The character a player currently plays in an adventure. At most one per player and adventure: the index backs the service rule.
ALTER TABLE "character" ADD COLUMN active boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX uq_character_active ON "character" (adventure_id, account_id) WHERE active;
