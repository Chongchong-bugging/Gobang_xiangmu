ALTER TABLE score
    ADD CONSTRAINT fk_score_user
        FOREIGN KEY (user_id) REFERENCES user_info(id) ON DELETE CASCADE;

ALTER TABLE score
    ADD CONSTRAINT chk_score_counts_nonnegative
        CHECK (wins >= 0 AND losses >= 0 AND draws >= 0);

CREATE TABLE game_result (
    game_id     VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    result      VARCHAR(8)   NOT NULL,
    score_delta INT          NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_game_result_user
        FOREIGN KEY (user_id) REFERENCES user_info(id) ON DELETE CASCADE,
    CONSTRAINT chk_game_result_type
        CHECK (result IN ('WIN', 'LOSS', 'DRAW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_game_result_user_created
    ON game_result (user_id, created_at);
