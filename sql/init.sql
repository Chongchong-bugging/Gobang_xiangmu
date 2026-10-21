-- ============================================================
-- Gobang v3.1 数据库初始化脚本
-- 使用方式：在 MySQL 中执行此脚本
--   mysql -u root -p < init.sql
-- ============================================================

-- 创建数据库（如不存在）
CREATE DATABASE IF NOT EXISTS gobang_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE gobang_db;

-- 表结构和后续升级由 Spring Boot 启动时的 Flyway 迁移统一管理。
SELECT '✅ gobang_db 数据库已创建，数据表将由 Flyway 初始化！' AS message;
