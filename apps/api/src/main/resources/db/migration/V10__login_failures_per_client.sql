-- The lockout counts the wrong passwords per (account, client) instead of per account only: pseudos are public,
-- so a lock shared by every client let anybody keep a stranger out of their account (review v2, point 27).
-- client_hash is a SHA-256 of the client address, never the address itself. A row is only useful for the length
-- of the lock window; the nightly purge removes the older ones.
CREATE TABLE login_failures (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    client_hash varchar(64) NOT NULL,
    failed_attempts integer NOT NULL,
    locked_until timestamp(6) with time zone,
    last_failed_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT uq_login_failures_client UNIQUE (account_id, client_hash)
);

CREATE INDEX idx_login_failures_last_failed_at ON login_failures (last_failed_at);

ALTER TABLE accounts DROP COLUMN failed_login_attempts;
ALTER TABLE accounts DROP COLUMN locked_until;
