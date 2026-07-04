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

-- 用户信息表
CREATE TABLE IF NOT EXISTS user_info (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    nickname    VARCHAR(50)     NOT NULL UNIQUE,
    password    VARCHAR(50)     NOT NULL,
    created_at  DATETIME        NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 积分表
CREATE TABLE IF NOT EXISTS score (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL UNIQUE,
    total_score INT             DEFAULT 0,
    wins        INT             DEFAULT 0,
    losses      INT             DEFAULT 0,
    draws       INT             DEFAULT 0,
    created_at  DATETIME        NULL,
    updated_at  DATETIME        NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 完成提示
SELECT '✅ gobang_db 数据库和表已创建成功！' AS message;
