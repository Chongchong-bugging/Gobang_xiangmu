package com.gobang.service;

import com.gobang.entity.GameResult;
import com.gobang.repository.GameResultRepository;
import com.gobang.repository.ScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ScoreSettlementService {

    public enum Result {
        WIN(1, 0, 0, 3),
        LOSS(0, 1, 0, -1),
        DRAW(0, 0, 1, 1);

        private final int wins;
        private final int losses;
        private final int draws;
        private final int scoreDelta;

        Result(int wins, int losses, int draws, int scoreDelta) {
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
            this.scoreDelta = scoreDelta;
        }
    }

    private final GameResultRepository gameResultRepository;
    private final ScoreRepository scoreRepository;

    public ScoreSettlementService(
            GameResultRepository gameResultRepository,
            ScoreRepository scoreRepository) {
        this.gameResultRepository = gameResultRepository;
        this.scoreRepository = scoreRepository;
    }

    /**
     * Serializes settlements for one user through the score row. The unique
     * game result ID remains the database-level idempotency guard.
     */
    @Transactional
    public boolean settle(String gameId, Long userId, Result result) {
        scoreRepository.findLockedByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Score account does not exist for user " + userId));
        if (gameResultRepository.existsById(gameId)) {
            return false;
        }

        GameResult gameResult = new GameResult();
        gameResult.setGameId(gameId);
        gameResult.setUserId(userId);
        gameResult.setResult(result.name());
        gameResult.setScoreDelta(result.scoreDelta);
        gameResult.setCreatedAt(LocalDateTime.now());
        gameResultRepository.save(gameResult);

        int updated = scoreRepository.applyResult(
                userId, result.wins, result.losses, result.draws, result.scoreDelta);
        if (updated != 1) {
            throw new IllegalStateException("Score account does not exist for user " + userId);
        }
        return true;
    }
}
