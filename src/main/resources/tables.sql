create table transaction (
  transaction_id BIGINT UNSIGNED not null,
  category varchar(100) not null,
  amount float(4) not null,
  transaction_date BIGINT UNSIGNED not null,
  comment varchar(100),
  essential bool not null,
  create_time BIGINT UNSIGNED not null,
  PRIMARY KEY(transaction_id)
  );

 CREATE TABLE category (
  category_name VARCHAR(100) NOT NULL,
  create_date BIGINT UNSIGNED not null,
  PRIMARY KEY (category_name)
  );