CREATE TABLE active_game (
    user_id BIGINT NOT NULL PRIMARY KEY,
    game_id VARCHAR(36) NOT NULL UNIQUE,
    revision BIGINT NOT NULL,
    state_json LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_active_game_user FOREIGN KEY (user_id) REFERENCES user_info(id)
);
