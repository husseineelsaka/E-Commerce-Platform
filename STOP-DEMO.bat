@echo off
REM Team 1 E-Commerce Platform - stop every platform container. Data volumes are kept for next time.
REM   STOP-DEMO.bat         stop and remove containers
REM   STOP-DEMO.bat clean   also delete the volumes (databases, Keycloak, Grafana) for a fresh start
setlocal
cd /d "%~dp0"
set "COMPOSE=docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env"
docker network disconnect -f docker_default ecommerce-control-plane >nul 2>&1
if /i "%~1"=="clean" (
  %COMPOSE% down -v --remove-orphans
) else (
  %COMPOSE% down --remove-orphans
)
echo Stopped.
pause
