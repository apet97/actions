ALTER TABLE actions ADD CONSTRAINT chk_retry_count CHECK (retry_count >= 0 AND retry_count <= 10);

-- For high-volume production deployments, consider:
-- 1. Convert execution_logs to a partitioned table (by executed_at, monthly)
-- 2. Use pg_partman for automatic partition management
-- 3. This replaces the application-level cleanup cron
