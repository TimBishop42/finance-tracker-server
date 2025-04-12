--PostgreSQL

create table transactions(
  transaction_id SERIAL PRIMARY KEY not null,
  category VARCHAR(50) not null,
  amount NUMERIC(10,2) not null,
  transaction_date VARCHAR(20) not null,
  transaction_date_time BIGINT not null,
  comment VARCHAR(50),
  essential INTEGER not null,
  create_time BIGINT not null
);

CREATE TABLE category(
  category_name VARCHAR(50) NOT NULL,
  create_date BIGINT not null,
  PRIMARY KEY (category_name)
); 