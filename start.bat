@echo off
chcp 65001 >nul
title Gobang v3.1 一键启动

echo ========================================
echo    Gobang v3.1 五子棋AI对战游戏
echo ========================================
echo.

:: 所有敏感配置均从环境变量读取。
:: MYSQL_BIN 可选；未设置时要求 mysql/mysqladmin 已加入 PATH。
if not defined DB_USERNAME set "DB_USERNAME=root"
if not defined DB_URL set "DB_URL=jdbc:mysql://localhost:3306/gobang_db?useUnicode=true^&characterEncoding=UTF-8^&serverTimezone=Asia/Shanghai"
if not defined CORS_ALLOWED_ORIGINS set "CORS_ALLOWED_ORIGINS=http://localhost:5173"

if not defined DB_PASSWORD (
    echo [错误] 未设置 DB_PASSWORD 环境变量！
    echo        请先执行：set DB_PASSWORD=你的MySQL密码
    echo        可参考项目根目录 .env.example
    pause
    exit /b 1
)

if defined MYSQL_BIN (
    set "MYSQLADMIN_CMD=%MYSQL_BIN%\mysqladmin.exe"
    set "MYSQL_CMD=%MYSQL_BIN%\mysql.exe"
) else (
    set "MYSQLADMIN_CMD=mysqladmin"
    set "MYSQL_CMD=mysql"
)

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
set "MYSQL_PWD=%DB_PASSWORD%"
"%MYSQLADMIN_CMD%" ping -h localhost -u "%DB_USERNAME%" --silent >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] MySQL 未运行、客户端不可用或凭据错误。
    set "MYSQL_PWD="
    pause
    exit /b 1
) else (
    echo [OK] MySQL 已就绪，创建数据库（表结构由 Flyway 管理）...
    "%MYSQL_CMD%" -u "%DB_USERNAME%" < "%~dp0sql\init.sql"
    if %errorlevel% neq 0 (
        echo [错误] 数据库创建失败。
        set "MYSQL_PWD="
        pause
        exit /b 1
    )
    echo [OK] 数据库准备完成
)
set "MYSQL_PWD="

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
start "Gobang-Backend" cmd /k "cd /d ""%~dp0gobang-backend"" && call mvnw.cmd spring-boot:run"

:: 等待后端启动
echo     等待后端启动（约 20 秒）...
timeout /t 20 /nobreak >nul

:: 启动前端
echo     启动前端...
start "Gobang-Frontend" cmd /k "cd /d ""%~dp0gobang-frontend"" && npm run dev"

echo.
echo ========================================
echo   [OK] 启动完成！
echo   前端：http://localhost:5173
echo   后端：http://localhost:8080
echo ========================================
echo.
pause
