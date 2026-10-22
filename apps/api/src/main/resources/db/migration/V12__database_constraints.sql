-- Constraints that only the code used to enforce (review v2, point 44): foreign keys, unicity, NOT NULL, and the
-- legacy name of the spell list table. Data that cannot take them stops the migration with a clear message
-- (like V2 for the pseudos), instead of leaving a half-constrained schema: fix the rows named, then start again.
DO $$
DECLARE
    problem text;
BEGIN
    SELECT string_agg(found.description, '; ') INTO problem FROM (
        SELECT 'adventure.creator_id ' || t.creator_id AS description FROM adventure t
            WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.creator_id)
        UNION ALL SELECT 'adventure_players.player_id ' || t.player_id FROM adventure_players t
            WHERE t.player_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.player_id)
        UNION ALL SELECT 'message.sender_id ' || t.sender_id FROM message t
            WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.sender_id)
        UNION ALL SELECT 'rating.emitter_id ' || t.emitter_id FROM rating t
            WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.emitter_id)
        UNION ALL SELECT 'rating.target_id ' || t.target_id FROM rating t
            WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.target_id)
        UNION ALL SELECT 'availability.account_id ' || t.account_id FROM availability t
            WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id)
        UNION ALL SELECT 'master_claims.candidate_id ' || t.candidate_id FROM master_claims t
            WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.candidate_id)
        UNION ALL SELECT 'master_claim_votes.voter_id ' || t.voter_id FROM master_claim_votes t
            WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.voter_id)
        UNION ALL SELECT 'message.adventure_id ' || t.adventure_id FROM message t
            WHERE NOT EXISTS (SELECT 1 FROM adventure a WHERE a.id = t.adventure_id)
        UNION ALL SELECT 'planning.adventure_id ' || t.adventure_id FROM planning t
            WHERE NOT EXISTS (SELECT 1 FROM adventure a WHERE a.id = t.adventure_id)
        UNION ALL SELECT 'availability.planning_id ' || t.planning_id FROM availability t
            WHERE NOT EXISTS (SELECT 1 FROM planning p WHERE p.id = t.planning_id)
    ) found;
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'V12 stopped: references to rows that do not exist (%)', problem;
    END IF;

    SELECT string_agg(found.description, '; ') INTO problem FROM (
        SELECT 'adventure ' || adventure_id || ' lists player ' || player_id || ' twice' AS description
            FROM adventure_players WHERE player_id IS NOT NULL
            GROUP BY adventure_id, player_id HAVING count(*) > 1
        UNION ALL SELECT 'account ' || emitter_id || ' rated account ' || target_id || ' more than once'
            FROM rating GROUP BY emitter_id, target_id HAVING count(*) > 1
        UNION ALL SELECT 'invitation code ' || invitation_code || ' is used by several adventures'
            FROM adventure WHERE invitation_code IS NOT NULL
            GROUP BY invitation_code HAVING count(*) > 1
    ) found;
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'V12 stopped: duplicates (%)', problem;
    END IF;
END $$;

-- A missing player is a row that points to nobody: it has no meaning, and the adventure does not read it as a player.
DELETE FROM adventure_players WHERE player_id IS NULL;
-- Unknown visibility becomes the safe one: a private adventure is not listed, nobody sees more than before.
UPDATE adventure SET visibility = 'PRIVATE' WHERE visibility IS NULL;

ALTER TABLE adventure_players ALTER COLUMN player_id SET NOT NULL;
ALTER TABLE adventure ALTER COLUMN visibility SET NOT NULL;

-- No ON DELETE: accounts are never deleted (they are anonymized), and the code deletes the children of an
-- adventure explicitly, so that a forgotten child fails loudly instead of disappearing silently.
ALTER TABLE adventure ADD CONSTRAINT fk_adventure_creator FOREIGN KEY (creator_id) REFERENCES accounts (id);
ALTER TABLE adventure_players ADD CONSTRAINT fk_adventure_players_player FOREIGN KEY (player_id) REFERENCES accounts (id);
ALTER TABLE message ADD CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES accounts (id);
ALTER TABLE message ADD CONSTRAINT fk_message_adventure FOREIGN KEY (adventure_id) REFERENCES adventure (id);
ALTER TABLE rating ADD CONSTRAINT fk_rating_emitter FOREIGN KEY (emitter_id) REFERENCES accounts (id);
ALTER TABLE rating ADD CONSTRAINT fk_rating_target FOREIGN KEY (target_id) REFERENCES accounts (id);
ALTER TABLE planning ADD CONSTRAINT fk_planning_adventure FOREIGN KEY (adventure_id) REFERENCES adventure (id);
ALTER TABLE availability ADD CONSTRAINT fk_availability_account FOREIGN KEY (account_id) REFERENCES accounts (id);
ALTER TABLE availability ADD CONSTRAINT fk_availability_planning FOREIGN KEY (planning_id) REFERENCES planning (id);
ALTER TABLE master_claims ADD CONSTRAINT fk_master_claims_candidate FOREIGN KEY (candidate_id) REFERENCES accounts (id);
ALTER TABLE master_claim_votes ADD CONSTRAINT fk_master_claim_votes_voter FOREIGN KEY (voter_id) REFERENCES accounts (id);

-- Checks followed by writes (join, rate, generate a code) are two requests: the database has the last word.
ALTER TABLE adventure_players ADD CONSTRAINT uq_adventure_players UNIQUE (adventure_id, player_id);
ALTER TABLE rating ADD CONSTRAINT uq_rating_emitter_target UNIQUE (emitter_id, target_id);
ALTER TABLE adventure ADD CONSTRAINT uq_adventure_invitation_code UNIQUE (invitation_code);

-- Legacy name left by Hibernate's defaults. The collection tables (spell list, propositions, availability dates)
-- keep no primary key on purpose: a list may hold the same value twice.
ALTER TABLE spell_book_entity_spell_list RENAME TO spellbook_spells;
ALTER TABLE spellbook_spells RENAME COLUMN spell_book_entity_id TO spellbook_id;
ALTER TABLE spellbook_spells RENAME COLUMN spell_list TO spell;
ALTER TABLE spellbook_spells RENAME CONSTRAINT fk_spell_list_spellbook TO fk_spellbook_spells_spellbook;
