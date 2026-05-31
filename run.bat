@echo off
title Ambulance Route Optimization System

chcp 65001 >nul

echo.
echo ============================================================
echo   Ambulance Route Optimization System
echo   Java + HTML/CSS/JavaScript
echo ============================================================
echo.

REM ─────────────────────────────────────────────────────────────
REM KILL ANY PROCESS ALREADY USING PORT 8080
REM ─────────────────────────────────────────────────────────────

echo Checking port 8080...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 2^>nul') do (
    echo Killing process %%a on port 8080...
    taskkill /PID %%a /F >nul 2>&1
)
timeout /t 2 >nul
echo Port 8080 is free
echo.

REM ─────────────────────────────────────────────────────────────
REM CHECK JAVA
REM ─────────────────────────────────────────────────────────────

where javac >nul 2>&1

if %errorlevel% neq 0 (
    echo ERROR: Java JDK not installed
    echo.
    echo Install JDK 17 or higher
    echo https://adoptium.net
    echo.
    pause
    exit /b
)

for /f "tokens=*" %%i in ('javac -version 2^>^&1') do (
    set JAVA_VER=%%i
)

echo Java Found: %JAVA_VER%
echo.

REM ─────────────────────────────────────────────────────────────
REM CREATE OUTPUT FOLDER
REM ─────────────────────────────────────────────────────────────

if not exist out (
    mkdir out
)

echo Output folder ready
echo.

REM ─────────────────────────────────────────────────────────────
REM COMPILE JAVA FILES
REM ─────────────────────────────────────────────────────────────

echo Compiling Java files...
echo.

javac -d out ^
src\mdvrp\Graph.java ^
src\mdvrp\Dijkstra.java ^
src\mdvrp\MapFactory.java ^
src\mdvrp\Server.java ^
src\mdvrp\Main.java

if %errorlevel% neq 0 (
    echo.
    echo Compilation Failed
    echo.
    pause
    exit /b
)

echo Compilation Successful
echo.

REM ─────────────────────────────────────────────────────────────
REM CHECK FRONTEND FILES
REM ─────────────────────────────────────────────────────────────

if not exist frontend\index.html (
    echo ERROR: frontend\index.html not found
    pause
    exit /b
)

if not exist frontend\script.js (
    echo ERROR: frontend\script.js not found
    pause
    exit /b
)

if not exist frontend\style.css (
    echo ERROR: frontend\style.css not found
    pause
    exit /b
)

echo Frontend files found
echo.

REM ─────────────────────────────────────────────────────────────
REM START SERVER
REM ─────────────────────────────────────────────────────────────

echo ============================================================
echo   SERVER STARTED
echo ============================================================
echo.
echo Open Browser:
echo http://localhost:8080
echo.
echo Press CTRL + C to stop server
echo ============================================================
echo.

java -cp out mdvrp.Main 8080

pause