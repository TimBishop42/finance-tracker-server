# Justfile

# Run Gradle build
gradle-build:
    ./gradlew build

# Build Docker image
build:
    docker build --platform linux/amd64 -t finance/server .

# Tag Docker image
tag:
    docker tag finance/server tbished/finance-server:latest

# Push Docker image
push:
    docker push tbished/finance-server:latest

# Full pipeline: Gradle build -> Docker build -> Docker tag -> Docker push
publish: gradle-build build tag push

# Build Postgres image
build-postgres:
    docker build --platform linux/amd64 -t tbished/finance-postgres:latest -f Dockerfile.postgres .

# Push Postgres image
push-postgres:
    docker push tbished/finance-postgres:latest

# Full pipeline for Postgres: Build -> Push
publish-postgres: build-postgres push-postgres