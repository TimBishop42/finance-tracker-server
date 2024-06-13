
##Dependencies

## Database
- SqLite DB running on ubuntu server
  - Login to ubuntu server, navigate to /local/db, run sqlite3 finance.db
    

# Pending Tasks
- Add paging to transactions call
- Add functionality to delete Transaction entries
- Add new page to display some use data aggregation
- Refactor aggregation into separate batching service
- Add ability to edit transactions in UI
- Add poller to transactions cache on server side

# Deploying Raspi
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

# Run config on server
- Service config: `/etc/systemd/system/finance-tracker-server.service`

# Docker build and deploy
- docker build -t finance/server .
- docker tag finance/server tbished/finance-server:v4
- docker push tbished/finance-server:v4

# Deploying to synology
- log into synology on local netork
- stop finance project
- update yaml to pull new tag
  
    