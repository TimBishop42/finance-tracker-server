
##Dependencies

## Database
- SqLite DB running on ubuntu server
  - Login to ubuntu server, navigate to /local/db, run sqlite3 finance.db
    

##Pending Tasks
- Add paging to transactions call
- Add functionality to delete Transaction entries
- Add new page to display some use data aggregation
- Refactor aggregation into separate batching service

##Deploying
- Build java app
- SCP file to Ubuntu server with scp FinanceTracker-0.0.1-SNAPSHOT.jar ubuntu@192.168.0.184:/home/ubuntu 
- SCP compiled JS files to server with scp build.zip ubuntu@192.168.0.184:/home/ubuntu/react-app
- Delete old FE files: sudo rm -r /var/www/html/build/
- Copy new files to apache: sudo mv /home/ubuntu/react-app/build/ /var/www/html/
- Restart server: sudo systemctl restart finance-tracker-server
- Restart apache for front end: sudo systemctl start apache2
  
    