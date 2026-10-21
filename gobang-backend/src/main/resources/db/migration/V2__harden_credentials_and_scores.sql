-- BCrypt hashes are 60 characters today; 255 leaves room for future encoders.
ALTER TABLE user_info
    MODIFY COLUMN password VARCHAR(255) NOT NULL;

-- Repair legacy nullable rows before enforcing the invariants used by Java code.
UPDATE score
SET total_score = COALESCE(total_score, 0),
    wins = COALESCE(wins, 0),
    losses = COALESCE(losses, 0),
    draws = COALESCE(draws, 0);

ALTER TABLE score
    MODIFY COLUMN total_score INT NOT NULL DEFAULT 0;

ALTER TABLE score
    MODIFY COLUMN wins INT NOT NULL DEFAULT 0;

ALTER TABLE score
    MODIFY COLUMN losses INT NOT NULL DEFAULT 0;

ALTER TABLE score
    MODIFY COLUMN draws INT NOT NULL DEFAULT 0;
