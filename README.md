# Gobang v3.1

Vue 3 + Spring Boot + MySQL 的五子棋 AI 对战项目。

## 全栈 Docker 架构

```text
浏览器 -> Nginx/Vue -> Spring Boot -> MySQL 8.4
             /api       8080        3306
```

- `frontend`：Node 22 构建 Vue，Nginx 提供静态页面和 `/api` 同源反向代理。
- `backend`：Java 21 非 root 运行，负责业务、认证、Flyway 迁移和健康检查。
- `mysql`：数据保存在 `gobang_mysql_data` 命名卷中。

三个服务均由 Compose 管理，并按 `mysql -> backend -> frontend` 的健康状态依次启动。

## 一键启动（推荐）

环境只需 Docker Desktop + WSL 2，不需要在本机安装 Java、Maven 或 Node.js。

首次使用时复制配置模板，并修改两个密码：

```powershell
Copy-Item .env.example .env
notepad .env
.\start-docker.bat
```

启动成功后：

- 前端：<http://localhost:5173>
- 后端健康检查：<http://localhost:8080/actuator/health>
- MySQL：`127.0.0.1:3307`

修改代码后重新构建：

```powershell
docker compose up -d --build --wait
```

查看状态和日志：

```powershell
docker compose ps
docker compose logs -f backend
docker compose logs -f frontend
```

停止服务但保留数据：

```powershell
docker compose down
```

`docker compose down -v` 会同时删除数据库卷和全部业务数据，仅在确定要重置数据时使用。

## 配置说明

敏感配置保存在被 Git 忽略的 `.env` 中。主要变量：

- `FRONTEND_PORT`、`BACKEND_PORT`、`MYSQL_PORT`：本机绑定端口。
- `IMAGE_REGISTRY`：默认为 `docker.io`；Docker Hub 网络受限时可换成可信的镜像前缀。
- `MYSQL_ROOT_PASSWORD`：MySQL root 密码。
- `DB_USERNAME`、`DB_PASSWORD`：后端数据库账号。
- `CORS_ALLOWED_ORIGINS`：直接访问后端时允许的前端来源。
- `SESSION_COOKIE_SECURE`：本地 HTTP 使用 `false`，部署 HTTPS 后改为 `true`。
- `FLYWAY_BASELINE_ON_MIGRATE`：旧库首次接管时使用 `true`，完成接管后建议改为 `false`。

升级已有数据库前，请先备份 MySQL 数据卷或做一次逻辑导出。旧库首次接管 Flyway
时才使用 `FLYWAY_BASELINE_ON_MIGRATE=true`；迁移记录建立后应改为 `false`。
新库请保持 `false`。本次数据库升级到 V5，新增对局结果、HTTP Session 和进行中棋局表；
迁移不会主动删除现有业务数据。若旧库存在无对应用户的积分记录，迁移会报错，
需先核对数据，再决定如何修复。

Compose 会在容器内自动使用 `mysql:3306`，不会把宿主机的 `localhost:3307`
误传给后端容器。MySQL 的初始账号变量只在空数据卷首次创建时生效；
修改旧 `.env` 不会自动轮换已有数据库账号密码。

## 本机开发方式

如果不使用全栈 Docker，需要 JDK 21+、Node.js 18+ 和 MySQL 8+。PowerShell 示例：

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "你的 MySQL 密码"
$env:MYSQL_BIN = "D:\path\to\mysql\bin" # mysql 已在 PATH 时可省略
.\start.bat
```

首次启动时，`start.bat` 只创建 `gobang_db` 数据库；数据表及后续结构升级由
Flyway 自动执行。

## 测试与构建

```powershell
cd gobang-backend
.\mvnw.cmd test

cd ..\gobang-frontend
npm.cmd ci
npm.cmd run build
```

后端 Docker 构建阶段也会运行完整 Maven `verify`；测试失败时不会产生运行镜像。

## 安全基线

- Spring Security Session 认证与 Session ID 轮换。
- BCrypt 密码存储；旧明文账号首次成功登录后自动升级。
- Cookie CSRF 防护，前端通过 `XSRF-TOKEN` / `X-XSRF-TOKEN` 自动提交令牌。
- 用户接口只返回安全 DTO，不暴露密码字段。
- Bean Validation、统一异常响应和真实 HTTP 401/403。
- Flyway 版本化数据库迁移，Hibernate 仅校验表结构。
- Actuator 健康检查与基础指标端点。
- 登录/注册按 IP 与账户限流；AI 计算设时间和队列上限，并记录耗时与降级指标。
- 对局结算按 `game_id` 幂等入账；进行中棋局、悔棋历史和登录 Session 持久化到 MySQL。
- Docker 服务仅绑定 `127.0.0.1`，并配置健康检查、自动重启和日志轮换。

单个后端容器重启后，登录 Session 与进行中对局可从 MySQL 恢复。当前 Compose
只运行一个后端副本；若要横向扩容，需额外验证跨实例的新局创建与事务并发行为。

## 简历表述（完成真实部署验证后使用）

负责 Vue 3 + Spring Boot 3 + MySQL 五子棋项目的容器化与后端可靠性优化：使用
Docker Compose 编排前端、后端和数据库，以 Flyway 管理版本化迁移；引入基于 MySQL
的登录 Session 与对局状态持久化，支持服务重启后恢复；用数据库唯一键和事务实现
对局结果幂等结算，补齐并发落子保护、完整悔棋快照、AI 限时降级及认证限流，
并编写自动化测试覆盖核心流程。不要添加未经实测的吞吐量或性能提升百分比。

> 仓库历史中曾出现过本地数据库密码。请轮换原密码；仅删除当前文件中的密码
> 不能使旧提交中的密码失效。
