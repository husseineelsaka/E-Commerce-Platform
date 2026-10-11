@echo off
REM ============================================================================================
REM  Team 1 E-Commerce Platform - start everything for the demo (Windows, double-click or run).
REM
REM    START-DEMO.bat          start the platform (keeps the data from last time)
REM    START-DEMO.bat clean    remove every container AND volume first, then start fresh
REM                            (databases re-seeded, Keycloak realm re-imported, caches empty)
REM
REM  Needs: Docker Desktop installed, and deployment\docker\.env present (it is not in Git).
REM ============================================================================================
setlocal
cd /d "%~dp0"
set "COMPOSE=docker compose -f deployment/docker/docker-compose.yml --env-file deployment/docker/.env"

echo.
echo [1/6] Checking deployment\docker\.env ...
if not exist "deployment\docker\.env" (
  echo     MISSING: deployment\docker\.env. Copy it from a teammate - it holds the passwords. Stopping.
  goto :fail
)

echo [2/6] Waiting for Docker Desktop ...
docker info >nul 2>&1
if errorlevel 1 (
  if exist "C:\Program Files\Docker\Docker\Docker Desktop.exe" start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
  for /l %%i in (1,1,60) do (
    timeout /t 5 /nobreak >nul
    docker info >nul 2>&1 && goto :docker_ok
    echo     still starting... %%i
  )
  echo     Docker did not start in 5 minutes. Open Docker Desktop by hand and run this file again.
  goto :fail
)
:docker_ok
echo     Docker is running.

echo [3/6] Stopping the kind cluster node if it runs (it uses RAM and joins the Compose network) ...
docker network disconnect -f docker_default ecommerce-control-plane >nul 2>&1
docker stop ecommerce-control-plane >nul 2>&1

if /i "%~1"=="clean" (
  echo [4/6] CLEAN: removing all platform containers, networks and volumes ...
  %COMPOSE% down -v --remove-orphans
) else (
  echo [4/6] Removing old platform containers - data volumes are kept ...
  %COMPOSE% down --remove-orphans
)

echo [5/6] Building and starting every service. First run takes several minutes; wait for "Healthy" ...
%COMPOSE% up -d --build --wait
if errorlevel 1 (
  REM Most common cause: Kafka starts while Zookeeper still remembers the broker from the last run
  REM ("node already exists"). That memory expires after about 20 s, so wait and try once more.
  echo.
  echo     A container did not start. Waiting 30 s, then trying once more ...
  timeout /t 30 /nobreak >nul
  REM A "500 Internal Server Error ... dockerDesktopLinuxEngine" means Docker Desktop itself hiccuped; wait for its engine.
  call :wait_engine
  %COMPOSE% up -d --wait
)
if errorlevel 1 (
  echo.
  echo     Still failing. Waiting 60 s for the last try ...
  timeout /t 60 /nobreak >nul
  %COMPOSE% up -d --wait
)
if errorlevel 1 (
  echo.
  echo     Kafka log, last 25 lines:
  %COMPOSE% logs --tail 25 kafka
  echo.
  echo     Some container is not healthy. Status:
  %COMPOSE% ps
  echo     Look at one service with:  docker compose -f deployment/docker/docker-compose.yml logs --tail 80 SERVICE-NAME
  goto :fail
)

echo [6/6] All containers are healthy:
%COMPOSE% ps --format "table {{.Service}}\t{{.Status}}\t{{.Ports}}"

REM Optional dress rehearsal with Git Bash: 80 checks, about 2 minutes.
set "GITBASH=C:\Program Files\Git\bin\bash.exe"
if exist "%GITBASH%" (
  echo.
  choice /c YN /n /t 20 /d N /m "Run the end-to-end test now (80 checks, about 2 min)? [Y/N, default N in 20 s] "
  if not errorlevel 2 "%GITBASH%" scripts/e2e-test.sh
)

echo.
echo  Open in the browser:
echo    Guided tour (voice) ..... http://localhost:8070/tour/
echo    Storefront .............. http://localhost:8070/
echo    Gateway API ............. http://localhost:8080/api/v1/products
echo    Keycloak ................ http://localhost:8180/admin/
echo    Zipkin .................. http://localhost:9411/zipkin/
echo    Prometheus .............. http://localhost:9090/targets
echo    Grafana ................. http://localhost:3000/d/ecommerce-platform
echo    Swagger UI .............. http://localhost:8089/
echo.
REM Open every tool in the default browser, one tab each.
for %%u in (
  "http://localhost:8070/tour/"
  "http://localhost:8070/"
  "http://localhost:8180/admin/"
  "http://localhost:9411/zipkin/"
  "http://localhost:9090/targets"
  "http://localhost:3000/d/ecommerce-platform"
  "http://localhost:8089/"
) do start "" %%u
echo  Done. To stop everything later: STOP-DEMO.bat
pause
exit /b 0

:fail
echo.
pause
exit /b 1

:wait_engine
for /l %%i in (1,1,24) do (
  docker info >nul 2>&1 && exit /b 0
  echo     Docker engine not answering yet... %%i
  timeout /t 5 /nobreak >nul
)
exit /b 0
