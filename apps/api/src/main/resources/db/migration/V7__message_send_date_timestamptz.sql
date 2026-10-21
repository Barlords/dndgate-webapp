-- The send date of a message is fixed by the server as an instant. Existing values were written by the server
-- clock in UTC (or chosen by clients, without a zone): they are read as UTC, whatever the session time zone.
ALTER TABLE message
    ALTER COLUMN send_date TYPE timestamp(6) with time zone USING send_date AT TIME ZONE 'UTC';

CREATE INDEX message_adventure_send_date_idx ON message (adventure_id, send_date DESC);
