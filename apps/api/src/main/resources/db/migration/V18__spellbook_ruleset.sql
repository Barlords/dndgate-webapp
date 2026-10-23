-- Existing spell books were written for the 2014 rules; the default fills them, then new rows must name their ruleset.
ALTER TABLE spellbook ADD COLUMN ruleset varchar(50) NOT NULL DEFAULT 'dnd5e-2014';
ALTER TABLE spellbook ALTER COLUMN ruleset DROP DEFAULT;
