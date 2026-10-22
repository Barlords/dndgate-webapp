-- Ratings get a creation date, so that the list a player received has a meaningful order.
-- Existing rows are dated with the time of the migration (their real date was never recorded).
ALTER TABLE rating ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE rating ALTER COLUMN created_at DROP DEFAULT;
