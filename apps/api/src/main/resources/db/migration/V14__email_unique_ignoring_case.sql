-- An e-mail address designates exactly one account, whatever its case (review v2, point 32), like the pseudo in V2.
-- The application now lower-cases addresses when they come in: existing ones are brought to the same form, and the
-- database refuses two addresses that differ only by case.
-- If two accounts already hold such addresses, the migration stops with their address: fix or remove one, then start again.
DO $$
DECLARE
    problem text;
BEGIN
    SELECT string_agg(found.address, ', ') INTO problem FROM (
        SELECT lower(email) AS address FROM accounts GROUP BY lower(email) HAVING count(*) > 1
    ) found;
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'V14 stopped: several accounts share an e-mail address that differs only by case (%)', problem;
    END IF;
END $$;

UPDATE accounts SET email = lower(email) WHERE email <> lower(email);
UPDATE accounts SET pending_email = lower(pending_email) WHERE pending_email <> lower(pending_email);

CREATE UNIQUE INDEX accounts_email_lower_key ON accounts (lower(email));
