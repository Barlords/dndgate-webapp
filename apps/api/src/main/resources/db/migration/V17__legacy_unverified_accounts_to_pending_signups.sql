-- A signup no longer creates an account: it only becomes one when the owner of the address follows the link of the
-- mail (review v2, point 59, see V16). Accounts that were created the old way and never confirmed (activated = false,
-- with a verification code) become pending signups, with their code hashed the way the application hashes it
-- (hexadecimal SHA-256), and are removed from accounts. An unconfirmed account has never been able to log in, so it
-- should own nothing: if one is linked to some data anyway, the migration stops and names it instead of deleting
-- it (fix or remove the account, then start again).
-- An account that is neither activated nor anonymized but has no verification code cannot be converted (nothing to
-- confirm it with) and is not a state the application produced: the migration stops and names it too.
-- If a pending signup already exists for the address (somebody signed up again after V16), the newer signup wins: the
-- legacy account is still removed and the existing signup is kept as it is (its code, not the legacy one, works).
DO $$
DECLARE
    problem text;
BEGIN
    SELECT string_agg(a.id || ' (' || a.email || ')', '; ') INTO problem
    FROM accounts a
    WHERE a.activated = false AND a.verification_code IS NULL AND a.anonymized_at IS NULL;
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'V17 stopped: accounts that are not activated, not anonymized and have no verification code cannot become pending signups (%)', problem;
    END IF;

    SELECT string_agg(found.description, '; ') INTO problem FROM (
        SELECT 'account ' || a.id || ' (' || a.email || ') is linked to ' || linked.what AS description
        FROM accounts a
        JOIN (
            SELECT creator_id AS account_id, 'adventure.creator_id' AS what FROM adventure
            UNION ALL SELECT player_id, 'adventure_players.player_id' FROM adventure_players WHERE player_id IS NOT NULL
            UNION ALL SELECT account_id, 'character.account_id' FROM "character"
            UNION ALL SELECT account_id, 'spellbook.account_id' FROM spellbook
            UNION ALL SELECT sender_id, 'message.sender_id' FROM message
            UNION ALL SELECT emitter_id, 'rating.emitter_id' FROM rating
            UNION ALL SELECT target_id, 'rating.target_id' FROM rating
            UNION ALL SELECT account_id, 'availability.account_id' FROM availability
            UNION ALL SELECT candidate_id, 'master_claims.candidate_id' FROM master_claims
            UNION ALL SELECT voter_id, 'master_claim_votes.voter_id' FROM master_claim_votes
            UNION ALL SELECT account_id, 'sessions.account_id' FROM sessions
        ) linked ON linked.account_id = a.id
        WHERE a.activated = false AND a.verification_code IS NOT NULL AND a.anonymized_at IS NULL
        GROUP BY a.id, a.email, linked.what
    ) found;
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'V17 stopped: unverified accounts own data and cannot become pending signups (%)', problem;
    END IF;
END $$;

-- E-mail addresses were lower-cased by V14, but a row inserted since may not be: pending signups hold normalised ones.
INSERT INTO pending_signups (id, email, pseudo, password_hash, code_hash, created_at, expires_at)
SELECT a.id, lower(a.email), a.pseudo, a.password,
       encode(sha256(convert_to(a.verification_code, 'UTF8')), 'hex'),
       now(),
       COALESCE(a.verification_expires_at, now() + interval '24 hours')
FROM accounts a
WHERE a.activated = false AND a.verification_code IS NOT NULL AND a.anonymized_at IS NULL
ON CONFLICT (email) DO NOTHING;

-- Every legacy unverified account goes: converted by the INSERT above, or superseded by a pending signup that already
-- held its address (ON CONFLICT DO NOTHING kept that one).
DELETE FROM accounts a
WHERE a.activated = false AND a.verification_code IS NOT NULL AND a.anonymized_at IS NULL
  AND (EXISTS (SELECT 1 FROM pending_signups p WHERE p.id = a.id)
       OR EXISTS (SELECT 1 FROM pending_signups p WHERE p.email = lower(a.email)));

-- The code of an address change has its own columns (pending_email_code): nothing reads these two anymore.
ALTER TABLE accounts DROP COLUMN verification_code;
ALTER TABLE accounts DROP COLUMN verification_expires_at;
