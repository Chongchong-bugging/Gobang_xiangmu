package com.gobang.service;

import com.gobang.dto.GameStateDTO;
import com.gobang.entity.UserInfo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;

class GameServiceTest {

    private final Map<Long, GameStateStore.StoredGame> savedGames = new ConcurrentHashMap<>();
    private final GameStateStore stateStore = mock(GameStateStore.class);
    private final GameService service = createService();
    private final UserInfo user = user(42L);

    private GameService createService() {
        when(stateStore.load(anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(savedGames.get(invocation.getArgument(0))));
        doAnswer(invocation -> {
            GameStateStore.StoredGame saved = invocation.getArgument(0);
            savedGames.put(saved.userId(), saved);
            return null;
        }).when(stateStore).save(any());
        return new GameService(mock(ScoreSettlementService.class),
                new AiMoveService(new SimpleMeterRegistry()), stateStore);
    }

    @Test
    void concurrentPlayerMovesCommitOnlyOneTurn() throws Exception {
        service.startGame(user, "EASY", "TWO");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<GameStateDTO>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.playerMove(user, 7, 7);
                }));
            }
            ready.await();
            start.countDown();
            for (Future<GameStateDTO> result : results) result.get();

            GameStateDTO state = service.getState(user);
            assertThat(state.getBoard()[7][7]).isEqualTo(1);
            assertThat(stoneCount(state.getBoard())).isEqualTo(1);
            assertThat(state.getStatus()).isEqualTo("AI_TURN");
            assertThat(state.getRevision()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void pauseCannotSkipAiTurnAndCannotReopenFinishedGame() {
        service.startGame(user, "EASY", "TWO");
        service.playerMove(user, 7, 7);

        assertThat(service.pause(user).getStatus()).isEqualTo("AI_TURN");
        assertThat(service.resume(user).getStatus()).isEqualTo("AI_TURN");
        assertThat(service.aiMove(user).getStatus()).isEqualTo("PLAYER_TURN");
    }

    @Test
    void dynamicUndoRestoresWholeTurnSnapshot() {
        GameStateDTO initial = service.startGame(user, "EASY", "DYNAMIC");
        service.playerMove(user, 7, 7);
        GameStateDTO afterAi = service.aiMove(user);
        assertThat(afterAi.getStatus()).isEqualTo("PLAYER_TURN");

        GameStateDTO restored = service.undo(user);
        assertThat(restored.getBoard()).isDeepEqualTo(initial.getBoard());
        assertThat(restored.getBlockStates()).isDeepEqualTo(initial.getBlockStates());
        assertThat(restored.getUnlockTokens()).isEqualTo(initial.getUnlockTokens());
        assertThat(restored.getUndoRemaining()).isEqualTo(initial.getUndoRemaining() - 1);
        assertThat(restored.getRevision()).isGreaterThan(afterAi.getRevision());
    }

    @Test
    void reloadsGameAndUndoHistoryAfterBackendRestart() {
        GameStateDTO initial = service.startGame(user, "EASY", "DYNAMIC");
        service.playerMove(user, 7, 7);
        GameService restarted = createService();
        GameStateDTO restored = restarted.getState(user);
        assertThat(restored.getGameId()).isEqualTo(initial.getGameId());
        assertThat(restored.getBoard()[7][7]).isEqualTo(1);
        assertThat(restored.getStatus()).isEqualTo("AI_TURN");

        restarted.aiMove(user);
        GameStateDTO undone = restarted.undo(user);
        assertThat(undone.getBoard()).isDeepEqualTo(initial.getBoard());
        assertThat(undone.getBlockStates()).isDeepEqualTo(initial.getBlockStates());
    }

    private static int stoneCount(int[][] board) {
        int count = 0;
        for (int[] row : board) {
            for (int cell : row) if (cell != 0) count++;
        }
        return count;
    }

    private static UserInfo user(Long id) {
        UserInfo user = new UserInfo();
        user.setId(id);
        user.setNickname("player");
        return user;
    }
}
