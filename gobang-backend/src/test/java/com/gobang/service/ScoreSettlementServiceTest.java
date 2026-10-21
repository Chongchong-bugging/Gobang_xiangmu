package com.gobang.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:score_settlement;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class ScoreSettlementServiceTest {

    @Autowired private ScoreSettlementService settlementService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void repeatedResultChangesScoreOnlyOnce() {
        jdbc.update("INSERT INTO user_info (nickname, password) VALUES (?, ?)",
                "player_" + UUID.randomUUID().toString().substring(0, 8), "unused");
        Long userId = jdbc.queryForObject("SELECT MAX(id) FROM user_info", Long.class);
        jdbc.update("INSERT INTO score (user_id, total_score, wins, losses, draws) VALUES (?, 0, 0, 0, 0)", userId);
        String gameId = UUID.randomUUID().toString();

        assertThat(settlementService.settle(gameId, userId, ScoreSettlementService.Result.WIN)).isTrue();
        assertThat(settlementService.settle(gameId, userId, ScoreSettlementService.Result.WIN)).isFalse();
        assertThat(jdbc.queryForObject("SELECT wins FROM score WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_score FROM score WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_result WHERE game_id = ?", Integer.class, gameId))
                .isEqualTo(1);
    }
}
