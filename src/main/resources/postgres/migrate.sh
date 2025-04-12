#!/bin/bash

# Export from MariaDB
echo "Exporting data from MariaDB..."
mysqldump -u root -p finance transactions category > mariadb_dump.sql

# Transform the data
echo "Transforming data for PostgreSQL..."
sed -i '' 's/INSERT INTO `transactions`/INSERT INTO transactions/g' mariadb_dump.sql
sed -i '' 's/INSERT INTO `category`/INSERT INTO category/g' mariadb_dump.sql
sed -i '' 's/`//g' mariadb_dump.sql

# Import into PostgreSQL
echo "Importing data into PostgreSQL..."
psql -U finance-user -d finance -f mariadb_dump.sql

echo "Migration complete!" 