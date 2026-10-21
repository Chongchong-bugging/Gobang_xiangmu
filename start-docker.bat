@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Gobang Full-Stack Docker Environment

set "NO_PAUSE="
if /i "%~1"=="--no-pause" set "NO_PAUSE=1"

set "PROJECT_ROOT=%~dp0"
set "ENV_FILE=%PROJECT_ROOT%.env"
set "DOCKER_DESKTOP_EXE=D:\Docker\DockerDesktop\Docker Desktop.exe"
set "DOCKER_CMD=docker"

if not exist "%DOCKER_DESKTOP_EXE%" (
    set "DOCKER_DESKTOP_EXE=C:\Program Files\Docker\Docker\Docker Desktop.exe"
)

if not exist "%ENV_FILE%" (
    echo [ERROR] .env was not found.
    echo         Copy .env.example to .env and set local passwords.
    if not defined NO_PAUSE pause
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    if not "%%A"=="" if not defined %%A set "%%A=%%B"
)

if not defined FRONTEND_PORT set "FRONTEND_PORT=5173"
if not defined BACKEND_PORT set "BACKEND_PORT=8080"
if not defined MYSQL_PORT set "MYSQL_PORT=3307"

if not defined DB_PASSWORD (
    echo [ERROR] DB_PASSWORD is missing from .env.
    if not defined NO_PAUSE pause
    exit /b 1
)

if not defined MYSQL_ROOT_PASSWORD (
    echo [ERROR] MYSQL_ROOT_PASSWORD is missing from .env.
    if not defined NO_PAUSE pause
    exit /b 1
)

where docker >nul 2>&1
if errorlevel 1 (
    if exist "D:\Docker\DockerDesktop\resources\bin\docker.exe" (
        set "DOCKER_CMD=D:\Docker\DockerDesktop\resources\bin\docker.exe"
    ) else (
        echo [ERROR] Docker CLI was not found.
        if not defined NO_PAUSE pause
        exit /b 1
    )
)

call :ensure_docker
if errorlevel 1 (
    if not defined NO_PAUSE pause
    exit /b 1
)

echo [1/2] Building and starting MySQL, Spring Boot, and Nginx...
"%DOCKER_CMD%" compose --env-file "%ENV_FILE%" -f "%PROJECT_ROOT%compose.yaml" up -d --build --wait --remove-orphans
if errorlevel 1 (
    echo [ERROR] The full-stack Docker environment failed to start.
    "%DOCKER_CMD%" compose --env-file "%ENV_FILE%" -f "%PROJECT_ROOT%compose.yaml" ps
    if not defined NO_PAUSE pause
    exit /b 1
)

echo [2/2] Service status:
"%DOCKER_CMD%" compose --env-file "%ENV_FILE%" -f "%PROJECT_ROOT%compose.yaml" ps

echo.
echo ========================================
echo   [OK] Gobang full-stack environment is healthy
echo   Frontend: http://localhost:%FRONTEND_PORT%
echo   Backend:  http://localhost:%BACKEND_PORT%
echo   MySQL:    localhost:%MYSQL_PORT%
echo ========================================
echo.
if not defined NO_PAUSE pause
exit /b 0

:ensure_docker
"%DOCKER_CMD%" info >nul 2>&1
if not errorlevel 1 exit /b 0

if not exist "%DOCKER_DESKTOP_EXE%" (
    echo [ERROR] Docker Desktop is not running and its executable was not found.
    exit /b 1
)

echo Docker Desktop is not running. Starting it now...
start "" "%DOCKER_DESKTOP_EXE%"

for /l %%I in (1,1,24) do (
    timeout /t 5 /nobreak >nul
    "%DOCKER_CMD%" info >nul 2>&1
    if not errorlevel 1 exit /b 0
)

echo [ERROR] Docker Desktop did not become ready within 120 seconds.
echo         On first launch, accept the license and ensure WSL is installed.
exit /b 1
