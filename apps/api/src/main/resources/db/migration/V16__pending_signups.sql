-- Signups waiting for the click on the link of the confirmation mail (review v2, point 59): POST /accounts records one
-- of these instead of creating an account, and the account only exists once the owner of the address confirmed.
-- The address is the normalised one (trimmed, lower-cased) and designates one signup; the code is kept as a SHA-256
-- digest, never in clear. A signup is only useful until it expires: the nightly purge removes the older ones.
CREATE TABLE pending_signups (
    id uuid PRIMARY KEY,
    email text NOT NULL UNIQUE,
    pseudo text NOT NULL,
    password_hash text NOT NULL,
    code_hash text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL
);

CREATE INDEX idx_pending_signups_expires_at ON pending_signups (expires_at);
