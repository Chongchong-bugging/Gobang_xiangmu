package com.gobang.service;

import com.gobang.ai.GameBoard;
import com.gobang.ai.GobangAI;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AiMoveService {

    private static final Logger log = LoggerFactory.getLogger(AiMoveService.class);
    private static final long TIMEOUT_MILLIS = 2_500;
    private final MeterRegistry registry;
    private final ThreadPoolExecutor executor;

    public AiMoveService(MeterRegistry registry) {
        this.registry = registry;
        AtomicInteger threadNumber = new AtomicInteger();
        int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
        this.executor = new ThreadPoolExecutor(
                threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                task -> {
                    Thread thread = new Thread(task, "gobang-ai-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public int[] calculate(GameBoard board, String difficulty, String gameMode, int color) {
        GameBoard snapshot = board.copy();
        Timer.Sample sample = Timer.start(registry);
        Future<int[]> task;
        try {
            task = executor.submit(() -> choose(snapshot, difficulty, gameMode, color));
        } catch (RejectedExecutionException exception) {
            registry.counter("gobang.ai.fallback", "reason", "capacity").increment();
            sample.stop(registry.timer("gobang.ai.duration", "mode", gameMode, "difficulty", difficulty));
            return firstPlayable(board);
        }
        try {
            return task.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            registry.counter("gobang.ai.fallback", "reason", "timeout").increment();
            log.warn("AI search exceeded {} ms in mode {}", TIMEOUT_MILLIS, gameMode);
            return firstPlayable(board);
        } catch (InterruptedException exception) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI search interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("AI search failed", exception.getCause());
        } finally {
            sample.stop(registry.timer("gobang.ai.duration", "mode", gameMode, "difficulty", difficulty));
        }
    }

    private static int[] choose(GameBoard board, String difficulty, String gameMode, int color) {
        if ("DYNAMIC".equals(gameMode)) {
            return GobangAI.generateMoveForDynamic(board, difficulty, color);
        }
        if ("OBSTACLE".equals(gameMode) || "THREE_OBSTACLE".equals(gameMode)) {
            return GobangAI.generateMoveForObstacle(board, difficulty, color);
        }
        return GobangAI.generateMove(board, difficulty, color);
    }

    private static int[] firstPlayable(GameBoard board) {
        for (int row = 0; row < board.getSize(); row++) {
            for (int col = 0; col < board.getSize(); col++) {
                if (board.isPlayable(row, col)) return new int[]{row, col};
            }
        }
        return null;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
