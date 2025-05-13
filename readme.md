


## Env Setup
### Local
#### Dependencies
- Gradle
- Docker (if wanting to build and push an image)

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

# Docker build and deploy
- ./gradlew build
- docker build --platform linux/amd64 -t finance/server . 
- docker tag finance/server tbished/finance-server:latest
- docker push tbished/finance-server:latest

# Embedded Postgres
docker-compose-portable contains config for an embedded postgres image, so that no server side DB setup is required.

To make changes to the postgres images base schema, modify the schema files in [postgres-schema](src/main/resources/postgres/init.sql)

Then rebuild the custom postgres image and push to repo:
- docker build --platform linux/amd64 -t tbished/finance-postgres:latest -f Dockerfile.postgres .
- docker push tbished/finance-postgres:latest

## Endpoints
### Batch Prediction Endpoint

A new endpoint has been added to predict categories for a batch of transactions:

`POST /api/transactions/predict-batch`

#### Request Format
```json
[
  {
    "transactionDate": "2024-03-20T10:00:00Z",
    "transactionAmount": 25.50,
    "transactionBusiness": "STARBUCKS"
  },
  {
    "transactionDate": "2024-03-20T11:00:00Z",
    "transactionAmount": 45.00,
    "transactionBusiness": "UBER"
  }
]
```

#### Response Format
```json
[
  {
    "transactionId": 1,
    "date": "2024-03-20T10:00:00Z",
    "amount": 25.50,
    "businessName": "STARBUCKS",
    "comment": "Coffee purchase",
    "predictedCategory": "FOOD",
    "confidenceScore": 0.95
  },
  {
    "transactionId": 2,
    "date": "2024-03-20T11:00:00Z",
    "amount": 45.00,
    "businessName": "UBER",
    "comment": "Ride to work",
    "predictedCategory": "TRANSPORT",
    "confidenceScore": 0.85
  }
]
```

#### Sample Curl Command (Local Mode)
```bash
curl -X POST http://localhost:8080/api/transactions/predict-batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "transactionDate": "2024-03-20T10:00:00Z",
      "transactionAmount": 25.50,
      "transactionBusiness": "STARBUCKS"
    },
    {
      "transactionDate": "2024-03-20T11:00:00Z",
      "transactionAmount": 45.00,
      "transactionBusiness": "UBER"
    }
  ]'
```


  
    