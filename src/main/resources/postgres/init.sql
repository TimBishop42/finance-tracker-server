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
  create_time BIGINT NOT NULL,
  transaction_type VARCHAR(20) DEFAULT 'EXPENSE'
);

CREATE TABLE IF NOT EXISTS category(
  category_name VARCHAR(50) NOT NULL,
  create_date BIGINT NOT NULL,
  PRIMARY KEY (category_name)
);

CREATE TABLE user_settings (
    id BIGINT PRIMARY KEY,
    max_spend_value DECIMAL(10,2)
);

CREATE TABLE IF NOT EXISTS excluded_merchants (
    merchant_key VARCHAR(200) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS salary_history (
    id BIGSERIAL PRIMARY KEY,
    effective_date VARCHAR(20) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    note VARCHAR(200)
);

-- ---------------------------------------------------------------------------
-- Total Wealth / Net Worth tracker (feature §3)
-- ---------------------------------------------------------------------------

-- Generic asset/liability line item (everything that isn't a traded holding)
CREATE TABLE IF NOT EXISTS wealth_items (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    asset_class   VARCHAR(30)  NOT NULL,          -- CASH | SHARES | SUPER | PROPERTY | OTHER_ASSET | LIABILITY
    kind          VARCHAR(10)  NOT NULL,          -- ASSET | LIABILITY
    currency      VARCHAR(3)   NOT NULL DEFAULT 'AUD',
    current_value NUMERIC(15,2),
    note          VARCHAR(200),
    archived      BOOLEAN NOT NULL DEFAULT FALSE,
    create_time   BIGINT NOT NULL,
    update_time   BIGINT
);

-- Securities the owner trades (shares/ETFs)
CREATE TABLE IF NOT EXISTS securities (
    id            BIGSERIAL PRIMARY KEY,
    ticker        VARCHAR(20) NOT NULL,
    name          VARCHAR(120),
    exchange      VARCHAR(20),
    currency      VARCHAR(3) NOT NULL DEFAULT 'AUD',
    price_source  VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    UNIQUE (ticker, exchange)
);

-- Buy/sell trades; holdings are DERIVED from these
CREATE TABLE IF NOT EXISTS share_trades (
    id            BIGSERIAL PRIMARY KEY,
    security_id   BIGINT NOT NULL REFERENCES securities(id),
    side          VARCHAR(4) NOT NULL,            -- BUY | SELL
    quantity      NUMERIC(18,6) NOT NULL,
    price         NUMERIC(15,4) NOT NULL,
    fee           NUMERIC(10,2) NOT NULL DEFAULT 0,
    trade_date    VARCHAR(20) NOT NULL,           -- yyyy-MM-dd
    note          VARCHAR(200),
    create_time   BIGINT NOT NULL
);

-- Price points: manual entries today, API-fetched rows later (same table)
CREATE TABLE IF NOT EXISTS security_prices (
    id            BIGSERIAL PRIMARY KEY,
    security_id   BIGINT NOT NULL REFERENCES securities(id),
    as_of_date    VARCHAR(20) NOT NULL,           -- yyyy-MM-dd
    price         NUMERIC(15,4) NOT NULL,
    source        VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    UNIQUE (security_id, as_of_date)
);

-- FX rates (AUD<->USD for v1). 1 base_ccy = `rate` quote_ccy.
CREATE TABLE IF NOT EXISTS fx_rates (
    id            BIGSERIAL PRIMARY KEY,
    base_ccy      VARCHAR(3) NOT NULL,            -- 'USD'
    quote_ccy     VARCHAR(3) NOT NULL,            -- 'AUD'
    as_of_date    VARCHAR(20) NOT NULL,           -- yyyy-MM-dd
    rate          NUMERIC(15,6) NOT NULL,
    source        VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    UNIQUE (base_ccy, quote_ccy, as_of_date)
);

-- Monthly net-worth snapshots (stored in base AUD)
CREATE TABLE IF NOT EXISTS net_worth_snapshots (
    id                BIGSERIAL PRIMARY KEY,
    as_of_date        VARCHAR(20) NOT NULL UNIQUE,
    base_ccy          VARCHAR(3) NOT NULL DEFAULT 'AUD',
    total_assets      NUMERIC(15,2) NOT NULL,
    total_liabilities NUMERIC(15,2) NOT NULL,
    net_worth         NUMERIC(15,2) NOT NULL,
    breakdown_json    TEXT
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