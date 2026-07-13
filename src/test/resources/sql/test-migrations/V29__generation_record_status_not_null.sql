UPDATE generation_record
SET status = 'QUEUED'
WHERE status IS NULL;

ALTER TABLE generation_record
    ALTER COLUMN status SET NOT NULL;
