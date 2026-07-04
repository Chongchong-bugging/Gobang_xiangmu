@echo off
chcp 65001 >nul
title Gobang v3.1 一键启动

echo ========================================
echo    Gobang v3.1 五子棋AI对战游戏
echo ========================================
echo.

:: 设置 MySQL 路径（根据实际安装位置调整）
set "MYSQL_BIN=D:\23627\MySQL\bin"

:: 检查 Java 环境
echo [1/4] 检查 Java 环境...
java --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Java！请安装 JDK 21+
    pause
    exit /b 1
)
echo [OK] Java 已就绪

:: 检查 MySQL 是否运行 + 自动初始化数据库
echo [2/4] 检查 MySQL...
"%MYSQL_BIN%\mysqladmin.exe" ping -h localhost -u root -pw2362760276 --silent >nul 2>&1
if %errorlevel% neq 0 (
    echo [警告] MySQL 未运行或无法连接，将继续尝试...
) else (
    echo [OK] MySQL 已就绪，初始化数据库...
    "%MYSQL_BIN%\mysql.exe" -u root -pw2362760276 < "%~dp0sql\init.sql" 2>nul
    echo [OK] 数据库初始化完成
)

:: 检查 Node.js 环境
echo [3/4] 检查 Node.js...
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到 Node.js！
    pause
    exit /b 1
)
echo [OK] Node.js 已就绪

:: 启动后端
echo [4/4] 启动服务...
echo     启动后端（Spring Boot，首次编译可能较慢）...
start "Gobang-Backend" cmd /k "cd /d "%~dp0gobang-backend" && call mvnw.cmd spring-boot:run"

:: 等待后端启动
echo     等待后端启动（约 20 秒）...
timeout /t 20 /nobreak >nul

:: 启动前端
echo     启动前端...
start "Gobang-Frontend" cmd /k "cd /d "%~dp0gobang-frontend" && npm run dev"

echo.
echo ========================================
echo   [OK] 启动完成！
echo   前端：http://localhost:5173
echo   后端：http://localhost:8080
echo ========================================
echo.
pause
