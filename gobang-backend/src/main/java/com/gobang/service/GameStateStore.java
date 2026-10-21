package com.gobang.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gobang.ai.GameBoard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class GameStateStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public GameStateStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<StoredGame> load(Long userId) {
        // The row lock remains held through the calling service transaction,
        // including its commit after the in-process lock is released.
        return jdbc.query("SELECT state_json FROM active_game WHERE user_id = ? FOR UPDATE",
                (rs, row) -> decode(rs.getString(1)), userId).stream().findFirst();
    }

    public void save(StoredGame game) {
        String json;
        try {
            json = mapper.writeValueAsString(game);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize game state", exception);
        }
        int updated = jdbc.update("UPDATE active_game SET game_id = ?, revision = ?, "
                        + "state_json = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                game.gameId(), game.revision(), json, game.userId());
        if (updated == 0) {
            jdbc.update("INSERT INTO active_game (user_id, game_id, revision, state_json) "
                            + "VALUES (?, ?, ?, ?)",
                    game.userId(), game.gameId(), game.revision(), json);
        }
    }

    private StoredGame decode(String json) {
        try {
            return mapper.readValue(json, StoredGame.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize game state", exception);
        }
    }

    public record StoredGame(
            String gameId, Long userId, GameBoard.BoardState board, long revision,
            String prePauseStatus, List<StoredSnapshot> undoHistory, String difficulty,
            String status, int undoRemaining, String gameMode, int currentTurn,
            boolean obstacleMode, int movesSinceSpawn, int nextSpawnInterval,
            boolean dynamicMode, int unlockTokens, int totalMovesSinceUnlock,
            int totalMovesForSeal, boolean nextUnlockIsPlayer, String lastAiUnlockMsg) {}

    public record StoredSnapshot(
            GameBoard.BoardState board, String status, int currentTurn, int undoRemaining,
            int movesSinceSpawn, int nextSpawnInterval, int unlockTokens,
            int totalMovesSinceUnlock, int totalMovesForSeal, boolean nextUnlockIsPlayer,
            String lastAiUnlockMsg, String prePauseStatus, long revision) {}
}
