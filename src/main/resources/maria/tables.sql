
--Mysql

--Create DB
create database finance;

create table transactions(
  transaction_id INTEGER PRIMARY KEY AUTO_INCREMENT not null,
  category VARCHAR(50) not null,
  amount FLOAT(2) not null,
  transaction_date VARCHAR(20) not null,
  transaction_date_time BIGINT(40) not null,
  comment VARCHAR(50),
  essential INTEGER not null,
  create_time BIGINT(40) UNSIGNED not null
  );

 CREATE TABLE category(
  category_name VARCHAR(50) NOT NULL,
  create_date BIGINT(40) not null,
  PRIMARY KEY (category_name)
  );

