--  SqLite
create table transactions(
  transaction_id INTEGER PRIMARY KEY AUTOINCREMENT not null,
  category TEXT not null,
  amount REAL not null,
  transaction_date TEXT not null,
  transaction_date_time INTEGER UNSIGNED not null,
  comment TEXT,
  essential INTEGER not null,
  create_time INTEGER UNSIGNED not null
  );

 CREATE TABLE category(
  category_name TEXT NOT NULL,
  create_date INTEGER UNSIGNED not null,
  PRIMARY KEY (category_name)
  );
