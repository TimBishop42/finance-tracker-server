-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS finance;

-- Set search path
SET search_path TO finance;

-- Create tables if they don't exist
CREATE TABLE IF NOT EXISTS transactions(
  transaction_id SERIAL PRIMARY KEY,
  category VARCHAR(50) NOT NULL,
  amount NUMERIC(10,2) NOT NULL,
  transaction_date VARCHAR(20) NOT NULL,
  transaction_date_time BIGINT NOT NULL,
  comment VARCHAR(50),
  essential INTEGER NOT NULL,
  business_name VARCHAR(200),
  create_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS category(
  category_name VARCHAR(50) NOT NULL,
  create_date BIGINT NOT NULL,
  PRIMARY KEY (category_name)
);

-- Create migration tracking table
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(50) PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Insert initial migration record if not exists
INSERT INTO schema_migrations (version)
SELECT '1.0.0'
WHERE NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '1.0.0'); 