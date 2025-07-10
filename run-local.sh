#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to handle cleanup
cleanup() {
    echo -e "\n${YELLOW}Shutting down local services...${NC}"
    
    # Kill UI process
    if [ ! -z "$UI_PID" ]; then
        echo "Stopping UI (PID: $UI_PID)"
        kill $UI_PID 2>/dev/null
        wait $UI_PID 2>/dev/null
    fi
    
    # Kill Java server process
    if [ ! -z "$JAVA_PID" ]; then
        echo "Stopping Java server (PID: $JAVA_PID)"
        kill $JAVA_PID 2>/dev/null
        wait $JAVA_PID 2>/dev/null
    fi
    
    echo -e "${GREEN}Local services stopped${NC}"
    exit 0
}

# Set up signal handlers
trap cleanup SIGINT SIGTERM

echo -e "${GREEN}Starting Finance Tracker local development environment...${NC}"
echo -e "${YELLOW}This will start:${NC}"
echo -e "  - Postgres database (Docker, port 5432)"
echo -e "  - ML service (Docker, port 8000)"
echo -e "  - Java server (local, port 40010)"
echo -e "  - React UI (local, port 3000)"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

# Check if npm is available
if ! command -v npm >/dev/null 2>&1; then
    echo -e "${RED}Error: npm is not installed. Please install Node.js and npm.${NC}"
    exit 1
fi

# Check if UI dependencies are installed
if [ ! -d "../finance-tracker-ui/node_modules" ]; then
    echo -e "${YELLOW}UI dependencies not found. Installing...${NC}"
    cd ../finance-tracker-ui
    npm install
    cd ../finance-tracker-server
fi

echo -e "${GREEN}Prerequisites check passed!${NC}"

# Start Docker services
echo -e "${YELLOW}Starting Docker services (Postgres + ML)...${NC}"
docker-compose -f docker-compose.local.yaml up -d

# Wait for services to be healthy
echo -e "${YELLOW}Waiting for services to be ready...${NC}"
sleep 10

# Check if services are ready
echo "Checking Postgres..."
until docker-compose -f docker-compose.local.yaml exec postgres pg_isready -U finance-user -d finance >/dev/null 2>&1; do
    echo "Waiting for Postgres to be ready..."
    sleep 2
done

echo "Checking ML service..."
until curl -f http://localhost:8000/health >/dev/null 2>&1; do
    echo "Waiting for ML service to be ready..."
    sleep 2
done

echo -e "${GREEN}Docker services are ready!${NC}"

# Start UI
echo -e "${YELLOW}Starting UI...${NC}"
cd ../finance-tracker-ui
npm start &
UI_PID=$!
echo "UI started with PID: $UI_PID"

# Give UI a moment to start
sleep 5

# Start Java server
echo -e "${YELLOW}Starting Java server...${NC}"
cd ../finance-tracker-server
./gradlew bootRun --args='--spring.profiles.active=local' &
JAVA_PID=$!
echo "Java server started with PID: $JAVA_PID"

echo -e "${GREEN}All services started successfully!${NC}"
echo -e "${GREEN}Access the application at: http://localhost:3000${NC}"
echo -e "${GREEN}Java server API: http://localhost:40010${NC}"
echo -e "${GREEN}ML service API: http://localhost:8000${NC}"
echo -e "${GREEN}Postgres: localhost:5432${NC}"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"

# Wait for processes to finish
wait $UI_PID $JAVA_PID 