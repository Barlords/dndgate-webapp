-- "disponibility" was a misspelling of "availability": the tables, columns and constraints follow the code.
ALTER TABLE disponibility RENAME TO availability;
ALTER TABLE disponibility_dates RENAME TO availability_dates;

ALTER TABLE availability_dates RENAME COLUMN disponibility_id TO availability_id;
ALTER TABLE availability_dates RENAME COLUMN disponibility_dates TO availability_dates;

ALTER TABLE availability RENAME CONSTRAINT disponibility_pkey TO availability_pkey;
ALTER TABLE availability_dates RENAME CONSTRAINT fk_disponibility_dates_disponibility TO fk_availability_dates_availability;
ALTER INDEX idx_disponibility_planning RENAME TO idx_availability_planning;
