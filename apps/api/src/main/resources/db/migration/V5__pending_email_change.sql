-- A new e-mail address only replaces the current one once its owner followed the link sent to it.
-- Until then it waits here, with its own code (never the registration code) and its own expiry.
ALTER TABLE accounts
    ADD COLUMN pending_email            VARCHAR(255),
    ADD COLUMN pending_email_code       VARCHAR(255),
    ADD COLUMN pending_email_expires_at TIMESTAMP WITH TIME ZONE;

-- The link carries the new address: it is how the pending change is found.
CREATE INDEX accounts_pending_email_idx ON accounts (pending_email);
