@echo off
TITLE IPL Ticketing Arena - Microservices Launcher
echo =====================================================================
echo           IPL TICKET ARENA - FULL SYSTEM LAUNCHER
echo =====================================================================
echo.

REM 1. Ensure Docker Infrastructure is Running
echo [1/3] Checking Docker Containers (Postgres, Redis, Kafka)...
docker compose up -d

echo.
echo [2/3] Setting Maven JVM limits for smooth multi-service execution...
set MAVEN_OPTS=-Xms128m -Xmx384m

echo.
echo [3/3] Spawning 5 Microservices and React Frontend in dedicated consoles...

start "IPL-1: Auth Service (Port 8081)" cmd /k "cd /d C:\Users\ashok\OneDrive\Desktop\ipl-ticket-booking\ipl-auth-service && set MAVEN_OPTS=-Xms128m -Xmx384m && mvn spring-boot:run"
ping -n 3 127.0.0.1 >nul

start "IPL-2: Event Service (Port 8082)" cmd /k "cd /d C:\Users\ashok\OneDrive\Desktop\ipl-ticket-booking\ipl-event-service && set MAVEN_OPTS=-Xms128m -Xmx384m && mvn spring-boot:run"
ping -n 3 127.0.0.1 >nul

start "IPL-3: Order Service (Port 8083)" cmd /k "cd /d C:\Users\ashok\OneDrive\Desktop\ipl-ticket-booking\ipl-order-service && set MAVEN_OPTS=-Xms128m -Xmx384m && mvn spring-boot:run"
ping -n 3 127.0.0.1 >nul

start "IPL-4: Notification Service (Port 8084)" cmd /k "cd /d C:\Users\ashok\OneDrive\Desktop\ipl-ticket-booking\ipl-notification-service && set MAVEN_OPTS=-Xms128m -Xmx384m && mvn spring-boot:run"
ping -n 3 127.0.0.1 >nul

start "IPL-5: API Gateway (Port 8090)" cmd /k "cd /d C:\Users\ashok\OneDrive\Desktop\ipl-ticket-booking\ipl-api-gateway && set MAVEN_OPTS=-Xms128m -Xmx384m && mvn spring-boot:run"
ping -n 3 127.0.0.1 >nul

start "IPL-6: React Frontend (Port 3000)" cmd /k "cd /d C:\Users\ashok\OneDrive\Desktop\ipl-ticket-booking\ipl-frontend && npm run dev"

echo.
echo =====================================================================
echo  ALL 6 COMPONENTS ARE LAUNCHING IN DEDICATED CONSOLES!
echo.
echo  - Frontend Portal:         http://localhost:3000
echo  - API Gateway:             http://localhost:8090
echo  - Auth Service:            http://localhost:8081
echo  - Event Service:           http://localhost:8082
echo  - Order Service:           http://localhost:8083
echo  - Notification Service:    http://localhost:8084
echo.
echo  - PostgreSQL (event_db):   localhost:5432
echo  - Redis:                   localhost:6379
echo  - Kafka:                   localhost:9092
echo =====================================================================
