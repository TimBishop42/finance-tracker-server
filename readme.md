


## Env Setup
### Local Development (Quick Start)

To run the entire application locally for development:

```bash
# From the finance-tracker-server directory
just run-local
```

This single command will:
- Start Postgres database (Docker, port 5432)
- Start ML service (Docker, port 8000)  
- Start Java server (local, port 40010)
- Start React UI (local, port 3000)

Access the application at: http://localhost:3000

Other useful commands:
- `just stop-local` - Stop Docker services
- `just logs-local` - View Docker service logs
- `just restart-local` - Restart Docker services

**Prerequisites:**
- Docker installed and running
- Node.js and npm installed
- Java 11+ installed

### Local (Manual Setup)
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

## API Documentation

Interactive API documentation is available via Swagger UI:
- **Swagger UI**: http://localhost:40010/swagger-ui.html  
- **OpenAPI JSON**: http://localhost:40010/v3/api-docs

The Swagger UI provides an interactive interface to explore and test all API endpoints.

For deployed servers, replace `localhost:40010` with your server's address and port.

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
curl -X POST http://localhost:40010/api/transactions/predict-batch \
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

### Submit Transaction Batch
`POST /api/finance/submit-transaction-batch`

Submits a batch of transactions for processing. Can be run in dry-run mode to validate transactions without persisting them.

#### Request Format
```json
{
  "transactionJsonList": [
    {
      "transactionDate": 1710921600000,
      "amount": 25.50,
      "predictedCategory": "FOOD",
      "userCorrectedCategory": "ENTERTAINMENT",
      "comment": "Coffee with friends",
      "essential": false
    },
    {
      "transactionDate": 1710925200000,
      "amount": 45.00,
      "predictedCategory": "TRANSPORT",
      "userCorrectedCategory": null,
      "comment": "Ride to work",
      "essential": true
    }
  ],
  "dryRun": false
}
```

#### Response Format
```json
[
  {
    "transactionId": 1234,
    "status": "SUCCESS",
    "message": "Transaction saved successfully"
  },
  {
    "transactionId": 1235,
    "status": "SUCCESS",
    "message": "Transaction saved successfully"
  }
]
```

#### Sample Curl Command
```bash
curl -X POST http://localhost:40010/api/finance/submit-transaction-batch \
  -H "Content-Type: application/json" \
  -d '{
    "transactionJsonList": [
      {
        "transactionDate": 1710921600000,
        "amount": 25.50,
        "predictedCategory": "FOOD",
        "userCorrectedCategory": "ENTERTAINMENT",
        "comment": "Coffee with friends",
        "essential": false
      }
    ],
    "dryRun": false
  }'
```

Notes:
- `transactionDate` should be provided as Unix timestamp in milliseconds
- `predictedCategory` comes from the ML service prediction
- `userCorrectedCategory` is optional, only present if user manually changed the category
- `dryRun` flag can be used to validate transactions without saving them
- Response includes a status and message for each transaction in the batch

### Train Model
`POST /api/finance/train-model`

Triggers a full ML model training using all available transaction data.

#### Request Format
No request body required.

#### Response Format
```json
{
  "success": true,
  "message": "Model training completed successfully",
  "transactionCount": 1250
}
```

#### Sample Curl Command
```bash
curl -X POST http://localhost:40010/api/finance/train-model \
  -H "Content-Type: application/json"
```

Notes:
- Endpoint triggers a full retraining of the ML model
- Returns success status, message, and count of transactions used for training
- May take several minutes to complete depending on dataset size


  
    