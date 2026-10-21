-- The message rate limit counts the messages one account sent during the last minute.
CREATE INDEX message_sender_send_date_idx ON message (sender_id, send_date DESC);
