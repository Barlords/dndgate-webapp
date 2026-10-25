-- Non-player characters of the game master of an adventure: private (only the master reads them) or public.
ALTER TABLE "character" ADD COLUMN npc boolean NOT NULL DEFAULT false;
ALTER TABLE "character" ADD COLUMN visibility varchar(20) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE "character" ADD CONSTRAINT ck_character_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC'));
