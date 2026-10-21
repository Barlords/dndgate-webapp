-- An account votes once per planning. Older versions let POST /planning/availability create a vote on every call:
-- keep one vote per (planning, account), the one with the greatest id, and delete the others with their dates.
DELETE FROM availability_dates
WHERE availability_id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY planning_id, account_id ORDER BY id DESC) AS position
        FROM availability
    ) ranked
    WHERE position > 1
);

DELETE FROM availability
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY planning_id, account_id ORDER BY id DESC) AS position
        FROM availability
    ) ranked
    WHERE position > 1
);

CREATE UNIQUE INDEX availability_planning_account_key ON availability (planning_id, account_id);
