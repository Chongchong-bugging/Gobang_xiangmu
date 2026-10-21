package com.gobang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.dto.GameStateDTO;
import com.gobang.entity.UserInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GameStateStoreTest {

    @Test
    void persistsAndRestoresGameFromJdbcAfterServiceRecreation() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:game_state;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO user_info (id, nickname, password) VALUES (?, ?, ?)",
                42L, "player", "bcrypt-placeholder");

        GameStateStore store = new GameStateStore(jdbc, new ObjectMapper());
        UserInfo user = new UserInfo();
        user.setId(42L);
        user.setNickname("player");
        GameService first = service(store);
        GameStateDTO initial = first.startGame(user, "EASY", "DYNAMIC");
        first.playerMove(user, 7, 7);

        GameService afterRestart = service(store);
        GameStateDTO restored = afterRestart.getState(user);
        assertThat(restored.getGameId()).isEqualTo(initial.getGameId());
        assertThat(restored.getBoard()[7][7]).isEqualTo(1);
        assertThat(restored.getRevision()).isEqualTo(1);

        afterRestart.aiMove(user);
        GameStateDTO undone = afterRestart.undo(user);
        assertThat(undone.getBoard()).isDeepEqualTo(initial.getBoard());
        assertThat(undone.getBlockStates()).isDeepEqualTo(initial.getBlockStates());
        assertThat(jdbc.queryForObject(
                "SELECT revision FROM active_game WHERE user_id = ?", Long.class, 42L))
                .isEqualTo(undone.getRevision());
    }

    private static GameService service(GameStateStore store) {
        return new GameService(mock(ScoreSettlementService.class),
                new AiMoveService(new SimpleMeterRegistry()), store);
    }
}
