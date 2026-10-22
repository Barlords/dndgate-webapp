-- Notices sent to the owner of an address somebody tried to register with (review v2, point 31): what the
-- "one notice per address per window" limit counts. The address is kept as a SHA-256 digest, never in clear, and
-- a row is only useful for the length of the window: the nightly purge removes the older ones.
CREATE TABLE signup_notices (
    email_hash varchar(64) PRIMARY KEY,
    sent_at timestamp(6) with time zone NOT NULL
);

CREATE INDEX idx_signup_notices_sent_at ON signup_notices (sent_at);
