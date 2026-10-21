CREATE TABLE IF NOT EXISTS user_info (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    nickname    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(50)  NOT NULL,
    created_at  DATETIME     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS score (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL UNIQUE,
    total_score INT          DEFAULT 0,
    wins        INT          DEFAULT 0,
    losses      INT          DEFAULT 0,
    draws       INT          DEFAULT 0,
    created_at  DATETIME     NULL,
    updated_at  DATETIME     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
