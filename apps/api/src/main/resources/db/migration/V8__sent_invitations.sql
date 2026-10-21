-- Invitation e-mails sent to players: what the per-sender and per-recipient limits count.
-- A row is only useful for the length of the longest limit window; the nightly purge removes the older ones.
CREATE TABLE sent_invitations (
    id uuid PRIMARY KEY,
    sender_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    recipient_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    adventure_id uuid NOT NULL REFERENCES adventure (id) ON DELETE CASCADE,
    sent_at timestamp(6) with time zone NOT NULL
);

CREATE INDEX idx_sent_invitations_sender ON sent_invitations (sender_id, sent_at);
CREATE INDEX idx_sent_invitations_recipient ON sent_invitations (recipient_id, adventure_id, sent_at);
CREATE INDEX idx_sent_invitations_sent_at ON sent_invitations (sent_at);
