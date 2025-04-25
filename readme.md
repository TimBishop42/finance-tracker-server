


## Env Setup
### Local
#### Dependencies
- Gradle
- Docker (if wanting to build and push an image)

### Raspberry Pi
#### Database
- SqLite DB - need to change DB config in application.yaml (dbUrl, driverClassName) - similar to application-local.yaml
- Run some basic steps on sqlite DB to create new user
- Run [sqlite schema script](./src/main/resources/sqlite/tables.sql)

#### Java Service
- Build app with gradle build
- Copy jar file to raspi (or any linux server)
- Setup to run as a service using systemd
- start service

### Synology Nas
** Just use the docker-compose portable file**
Alternatively, set up a MariaDB instance
#### Database
- MariaDB works well on synology - download from package centre
- Set up a new USer + DB in Maria
- Add DB details to apllication.yaml
- Run [mariaDB schema script](./src/main/resources/maria/tables.sql)

#### Java Service
- Using Synology container manager
- Ensure you've got an image of the java service available in Docker (there is a dockerfile in this repo for building)
  - See example steps below at `Docker build and deploy`, will need own docker details (and have docker installed)
- Create a docker compose to setup dependencies (can us [docker-compose.yaml](./docker-compose-syno.yml)) as a base
- Might have to do some tinkering for the dockerized app to access the DB (by default MariaDB has IP address restrictions)
- update yaml to pull correct version of app image
- run `docker-dompose up`


# Pending Tasks
- Add paging to transactions call
- Add functionality to delete Transaction entries
- Add new page to display some yearly aggregation
- Refactor aggregation into separate batching service
- Add ability to edit transactions in UI
- Add poller to transactions cache on server side

# Deploying to Raspberry Pi
- Build java app
- SCP file to Ubuntu server with: `scp FinanceTracker-0.0.1-SNAPSHOT.jar ubuntu@192.168.1.101:/home/ubuntu/springboot`
- Delete existing jar file: `sudo rm /local/app/java/FinanceTracker-0.0.1-SNAPSHOT.jar`
- Move new Java app to app dir: `sudo mv /home/ubuntu/springboot/FinanceTracker-0.0.1-SNAPSHOT.jar /local/app/java/`
- SCP compiled JS files to server: `scp build.zip ubuntu@192.168.1.101:/home/ubuntu/react-app`
- Stop apache for front end: `sudo systemctl stop apache2`
- Delete old FE files: `sudo rm -r /var/www/html/build/`
- Unzip js bundle files: `unzip build.zip`
- Delete zip archive: `sudo rm /home/ubuntu/react-app/build.zip`
- Copy new files to apache: `sudo mv /home/ubuntu/react-app/build/ /var/www/html/`
- Restart server: `sudo systemctl restart finance-tracker-server`
- Restart apache for front end: `sudo systemctl start apache2`

# Run config on server (raspi)
- Service config: `/etc/systemd/system/finance-tracker-server.service`gra

# Docker build and deploy
- gradle build
- docker build --platform linux/amd64 -t finance/server . 
- docker tag finance/server tbished/finance-server:latest
- docker push tbished/finance-server:latest

# Embedded Postgres
docker-compose-portable contains config for an embedded postgres image, so that no server side DB setup is required.

To make changes to the postgres images base schema, modify the schema files in [postgres-schema](src/main/resources/postgres/init.sql)

Then rebuild the custom postgres image and push to repo:
- docker buildx build --platform linux/amd64 -t tbished/finance-postgres:latest -f Dockerfile.postgres .
- docker push tbished/finance-postgres:latest


  
    