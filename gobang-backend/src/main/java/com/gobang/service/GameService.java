package com.gobang.service;

import com.gobang.ai.GameBoard;
import com.gobang.dto.GameStateDTO;
import com.gobang.entity.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    private static final int LOCK_STRIPES = 64;

    private final ScoreSettlementService scoreSettlementService;
    private final AiMoveService aiMoveService;
    private final GameStateStore stateStore;
    private final ReentrantLock[] gameLocks = new ReentrantLock[LOCK_STRIPES];

    /** 存储每个用户的游戏状态 (userId -> GameState) */
    private final Map<Long, GameSession> games = new ConcurrentHashMap<>();

    // ==================== 障碍物模式配置参数 ====================
    private static final int SPAWN_MIN_INTERVAL = 5;   // 最小刷新间隔（步数）
    private static final int SPAWN_MAX_INTERVAL = 15;  // 最大刷新间隔（步数）
    private static final int SPAWN_MIN_COUNT = 1;      // 每次最少刷新数量
    private static final int SPAWN_MAX_COUNT = 3;      // 每次最多刷新数量

    private final Random random = new Random();

    public GameService(ScoreSettlementService scoreSettlementService, AiMoveService aiMoveService,
            GameStateStore stateStore) {
        this.scoreSettlementService = scoreSettlementService;
        this.aiMoveService = aiMoveService;
        this.stateStore = stateStore;
        for (int i = 0; i < gameLocks.length; i++) {
            gameLocks[i] = new ReentrantLock(true);
        }
    }

    /**
     * 开始新游戏
     * @param gameMode "TWO"=双人模式, "THREE"=三人模式, "OBSTACLE"=障碍物模式, "THREE_OBSTACLE"=三人障碍物模式, "DYNAMIC"=动态棋盘模式
     * @param obstacleMode 是否障碍物模式（OBSTACLE/THREE_OBSTACLE gameMode 下强制为 true）
     */
    @Transactional
    public GameStateDTO startGame(UserInfo user, String difficulty, String gameMode) {
        ReentrantLock lock = gameLock(user.getId());
        lock.lock();
        try {
            GameSession session = new GameSession();
            session.gameId = UUID.randomUUID().toString();
            session.userId = user.getId();
            session.gameMode = gameMode == null ? "TWO" : gameMode.toUpperCase();
            session.obstacleMode = "OBSTACLE".equals(session.gameMode) || "THREE_OBSTACLE".equals(session.gameMode);
            session.dynamicMode = "DYNAMIC".equals(session.gameMode);

        // 三人模式、三人障碍物模式和动态棋盘模式使用15x15棋盘
            int boardSize = ("THREE".equals(session.gameMode) || "THREE_OBSTACLE".equals(session.gameMode)) ? 25 : 15;
            session.board = new GameBoard(boardSize);
            session.difficulty = difficulty.toUpperCase();
            session.undoRemaining = session.obstacleMode ? 3 : 2;  // 障碍物模式多给1次悔棋
            session.status = "PLAYER_TURN";
            session.currentTurn = GameBoard.BLACK;

        // 障碍物模式：初始化刷新参数并生成首批地形
            if (session.obstacleMode) {
                session.nextSpawnInterval = SPAWN_MIN_INTERVAL + random.nextInt(SPAWN_MAX_INTERVAL - SPAWN_MIN_INTERVAL + 1);
                session.movesSinceSpawn = 1;  // 第1步后刷新
                session.board.spawnTerrain(SPAWN_MAX_COUNT);
            }

        // 动态棋盘模式：初始化区块状态
            if (session.dynamicMode) {
                session.board.initDynamicBlocks();
                session.unlockTokens = 0;              // 初始无解锁令牌
                session.totalMovesSinceUnlock = 0;     // 总落子计数
                session.totalMovesForSeal = 0;         // 封印计数
                session.nextUnlockIsPlayer = true;     // 第一个解锁令牌归玩家
            }

            games.put(user.getId(), session);

            return toDTO(session, "游戏开始，请落子");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 玩家落子
     * 障碍物模式和动态棋盘模式走完全独立分支，不影响原有 TWO/THREE 逻辑
     */
    @Transactional
    public GameStateDTO playerMove(UserInfo user, int row, int col) {
        ReentrantLock lock = gameLock(user.getId());
        lock.lock();
        GameSession session = null;
        int snapshotDepth = -1;
        try {
            session = getSession(user.getId());
            if (session == null) {
                return errorDTO("游戏未开始");
            }
            snapshotDepth = session.undoHistory.size();

        // ========== 动态棋盘模式：独立分支 ==========
        if (session.dynamicMode) {
            return playerMoveDynamic(session, row, col, user.getId());
        }

        // ========== 三人障碍物模式：独立分支 ==========
        if ("THREE_OBSTACLE".equals(session.gameMode)) {
            return playerMoveThreeObstacle(session, row, col, user.getId());
        }

        // ========== 障碍物模式：独立分支 ==========
        if (session.obstacleMode) {
            return playerMoveObstacle(session, row, col, user.getId());
        }

        // ========== 原有 TWO/THREE 模式（完全不变） ==========
        if ("PAUSED".equals(session.status)) {
            return toDTO(session, "游戏已暂停，请先恢复");
        }

        if (!"PLAYER_TURN".equals(session.status)) {
            return toDTO(session, "现在不是您的回合");
        }

        GameBoard board = session.board;

        if (!board.isValidPosition(row, col) || board.getStone(row, col) != GameBoard.EMPTY) {
            return toDTO(session, "无效的落子位置");
        }

        // 禁手检查（仅黑子）
        if (board.isForbidden(row, col, GameBoard.BLACK)) {
            return toDTO(session, "禁手！不能在此位置落子");
        }

        // 放置黑子
        session.saveTurnSnapshot();
        board.placeStone(row, col, GameBoard.BLACK);
        session.currentTurn = GameBoard.BLACK;
        session.revision++;

        // 检查是否胜利
        if (board.checkWin(row, col)) {
            finishGame(session, user.getId(), "PLAYER_WIN", ScoreSettlementService.Result.WIN);
            return toDTO(session, "恭喜你赢了！");
        }

        // 检查平局
        if (board.isFull()) {
            finishGame(session, user.getId(), "DRAW", ScoreSettlementService.Result.DRAW);
            return toDTO(session, "棋盘已满，平局！");
        }

        // --- 三人模式：改为两步流程，先返回 AI_TURN，前端再请求 AI 落子 ---
        if ("THREE".equals(session.gameMode)) {
            session.status = "AI_TURN";
            return toDTO(session, "白子AI思考中...");
        } else if ("TWO".equals(session.gameMode) || "OBSTACLE".equals(session.gameMode)) {
            // --- 双人/障碍物模式：改为两步流程，先返回 AI_TURN，前端再请求 AI 落子 ---
            session.status = "AI_TURN";
            return toDTO(session, "白子AI思考中...");
        }

        session.status = "PLAYER_TURN";
        return toDTO(session, "请落子");
        } catch (RuntimeException exception) {
            rollbackFailedTurn(session, snapshotDepth);
            throw exception;
        } finally {
            lock.unlock();
        }
    }

    // ==================== 障碍物模式：独立下棋逻辑 ====================

    /**
     * 障碍物模式 - 玩家落子（完全独立的方法，不影响原有逻辑）
     */
    private GameStateDTO playerMoveObstacle(GameSession session, int row, int col, Long userId) {
        try {
            if ("PAUSED".equals(session.status)) {
                return toDTO(session, "游戏已暂停，请先恢复");
            }

            if (!"PLAYER_TURN".equals(session.status)) {
                return toDTO(session, "现在不是您的回合");
            }

            GameBoard board = session.board;

            // 1. 检查地形：假山处不能落子
            if (board.getTerrainAt(row, col) == GameBoard.OBSTACLE) {
                return toDTO(session, "此处是假山，无法落子！");
            }

            // 2. 检查位置是否有效
            if (!board.isValidPosition(row, col)) {
                return toDTO(session, "无效的落子位置");
            }

            if (board.getStone(row, col) != GameBoard.EMPTY && board.getTerrainAt(row, col) != GameBoard.WATER) {
                return toDTO(session, "此处已有棋子");
            }

            // 3. 禁手检查（仅黑子）
            if (board.isForbidden(row, col, GameBoard.BLACK)) {
                return toDTO(session, "禁手！不能在此位置落子");
            }

            // 4. 障碍物模式落子（水潭处自动滑动）
            session.saveTurnSnapshot();
            int[] actualPos = board.placeStoneObstacle(row, col, GameBoard.BLACK);
            if (actualPos == null) {
                session.undoHistory.removeLast();
                return toDTO(session, "落子失败");
            }
            session.revision++;

            int actualRow = actualPos[0], actualCol = actualPos[1];
            String waterMsg = (actualRow != row || actualCol != col) ? "棋子滑入了水潭！" : null;

            // 5. 检查地形是否被包围（落子后四邻地形检查）
            List<GameBoard.TerrainRecord> removed = board.checkAndRemoveObstacles(actualRow, actualCol);

            // 6. 检查是否胜利
            if (board.checkWin(actualRow, actualCol)) {
                session.status = "PLAYER_WIN";
                updateScore(userId, "WIN");
                String msg = waterMsg != null ? waterMsg + " 恭喜你赢了！" : "恭喜你赢了！";
                if (!removed.isEmpty()) msg = msg.replace("赢了！", "赢了！（消除了" + removed.size() + "个地形）");
                return toDTO(session, msg);
            }

            // 7. 检查平局（障碍物模式专用满盘判断）
            if (board.isFullObstacle()) {
                session.status = "DRAW";
                updateScore(userId, "DRAW");
                return toDTO(session, "棋盘已满，平局！");
            }

            // 8. 地形刷新计数器
            session.movesSinceSpawn--;
            if (session.movesSinceSpawn <= 0) {
                int spawnCount = SPAWN_MIN_COUNT + random.nextInt(SPAWN_MAX_COUNT - SPAWN_MIN_COUNT + 1);
                List<GameBoard.TerrainRecord> spawned = board.spawnTerrain(spawnCount);
                // 重置随机间隔
                session.nextSpawnInterval = SPAWN_MIN_INTERVAL + random.nextInt(SPAWN_MAX_INTERVAL - SPAWN_MIN_INTERVAL + 1);
                session.movesSinceSpawn = session.nextSpawnInterval;

                // 如果地形占满了所有空位导致无法落子，视为平局
                if (board.isFullObstacle()) {
                    session.status = "DRAW";
                    updateScore(userId, "DRAW");
                    return toDTO(session, "地形覆盖全部空位，平局！");
                }
            }

            // 9. 改为两步流程：先返回 AI_TURN，前端再请求 AI 落子（实现延迟效果）
            session.status = "AI_TURN";
            StringBuilder responseMsg = new StringBuilder("白子AI思考中...");
            if (waterMsg != null) responseMsg.insert(0, waterMsg + " ");
            if (!removed.isEmpty()) responseMsg.append("（消除了").append(removed.size()).append("个地形）");
            return toDTO(session, responseMsg.toString());

        } catch (RuntimeException exception) {
            throw new IllegalStateException("障碍物模式处理失败", exception);
        }
    }

    // ==================== 动态棋盘模式：独立下棋逻辑 ====================

    /**
     * 动态棋盘模式 - 玩家落子（完全独立的方法）
     * 实现区块锁定/解锁/封印机制
     */
    private GameStateDTO playerMoveDynamic(GameSession session, int row, int col, Long userId) {
        try {
            if ("PAUSED".equals(session.status)) {
                return toDTO(session, "游戏已暂停，请先恢复");
            }

            if (!"PLAYER_TURN".equals(session.status)) {
                return toDTO(session, "现在不是您的回合");
            }

            GameBoard board = session.board;

            // 1. 检查位置是否有效
            if (!board.isValidPosition(row, col)) {
                return toDTO(session, "无效的落子位置");
            }

            // 2. 检查是否在可落子的区块内
            if (!board.isBlockPlayable(row, col)) {
                int blockRow = row / 3;
                int blockCol = col / 3;
                if (board.getBlockStates() != null && board.getBlockStates()[blockRow][blockCol] == GameBoard.BLOCK_SEALED) {
                    return toDTO(session, "此区块已被封印，无法落子！");
                }
                return toDTO(session, "此区块未解锁，无法落子！");
            }

            // 3. 检查位置是否已有棋子
            if (board.getStone(row, col) != GameBoard.EMPTY) {
                return toDTO(session, "此处已有棋子");
            }

            // 4. 禁手检查（仅黑子）
            if (board.isForbidden(row, col, GameBoard.BLACK)) {
                return toDTO(session, "禁手！不能在此位置落子");
            }

            // 5. 放置黑子
            session.saveTurnSnapshot();
            board.placeStoneDynamic(row, col, GameBoard.BLACK);
            session.currentTurn = GameBoard.BLACK;
            session.revision++;

            // 6. 检查是否胜利
            if (board.checkWin(row, col)) {
                session.status = "PLAYER_WIN";
                updateScore(userId, "WIN");
                return toDTO(session, "恭喜你赢了！");
            }

            // 7. 检查平局
            if (board.isFull()) {
                session.status = "DRAW";
                updateScore(userId, "DRAW");
                return toDTO(session, "棋盘已满，平局！");
            }

            // 8. 更新计数（总步数和封印计数）
            session.totalMovesSinceUnlock++;
            session.totalMovesForSeal++;
            
            // 检查是否需要封印区块（每8回合）
            StringBuilder sealMsg = new StringBuilder();
            if (session.totalMovesForSeal % GameSession.SEAL_INTERVAL == 0) {
                boolean sealed = board.sealRandomBlock();
                if (sealed) {
                    sealMsg.append("（随机封印了一个区块！）");
                }
            }

            // 10. 改为两步流程：先返回 AI_TURN，前端再请求 AI 落子
            session.status = "AI_TURN";
            StringBuilder responseMsg = new StringBuilder("白子AI思考中...");
            if (session.unlockTokens > 0) {
                responseMsg.append("（可获得").append(session.unlockTokens).append("次解锁机会）");
            }
            if (sealMsg.length() > 0) {
                responseMsg.append(sealMsg);
            }
            return toDTO(session, responseMsg.toString());

        } catch (RuntimeException exception) {
            throw new IllegalStateException("动态棋盘模式处理失败", exception);
        }
    }

    /**
     * 解锁指定区块
     */
    @Transactional
    public GameStateDTO unlockBlock(UserInfo user, int blockRow, int blockCol) {
        ReentrantLock lock = gameLock(user.getId());
        lock.lock();
        try {
        GameSession session = getSession(user.getId());
        if (session == null) {
            return errorDTO("游戏未开始");
        }

        if (!session.dynamicMode) {
            return errorDTO("非动态棋盘模式");
        }

        if (!"PLAYER_TURN".equals(session.status)) {
            return toDTO(session, "当前回合不能解锁区块");
        }

        if (session.unlockTokens <= 0) {
            return toDTO(session, "没有可用的解锁令牌");
        }

        if (session.board.unlockBlock(blockRow, blockCol)) {
            session.unlockTokens--;
            session.revision++;
            return toDTO(session, "成功解锁区块(" + blockRow + "," + blockCol + ")");
        } else {
            return toDTO(session, "无法解锁此区块（该区块已解锁）");
        }
        } finally {
            lock.unlock();
        }
    }

    // ==================== 三人障碍物模式：独立下棋逻辑 ====================

    /**
     * 三人障碍物模式 - 玩家落子（完全独立的方法）
     * 合并三人回合流转与障碍物逻辑：玩家落子→地形处理→白AI落子→地形处理→返回GRAY_TURN
     */
    private GameStateDTO playerMoveThreeObstacle(GameSession session, int row, int col, Long userId) {
        try {
            if ("PAUSED".equals(session.status)) {
                return toDTO(session, "游戏已暂停，请先恢复");
            }

            if (!"PLAYER_TURN".equals(session.status)) {
                return toDTO(session, "现在不是您的回合");
            }

            GameBoard board = session.board;

            // 1. 检查地形：假山处不能落子
            if (board.getTerrainAt(row, col) == GameBoard.OBSTACLE) {
                return toDTO(session, "此处是假山，无法落子！");
            }

            // 2. 检查位置是否有效
            if (!board.isValidPosition(row, col)) {
                return toDTO(session, "无效的落子位置");
            }

            if (board.getStone(row, col) != GameBoard.EMPTY && board.getTerrainAt(row, col) != GameBoard.WATER) {
                return toDTO(session, "此处已有棋子");
            }

            // 3. 禁手检查（仅黑子）
            if (board.isForbidden(row, col, GameBoard.BLACK)) {
                return toDTO(session, "禁手！不能在此位置落子");
            }

            // 4. 障碍物模式落子（水潭处自动滑动）
            session.saveTurnSnapshot();
            int[] actualPos = board.placeStoneObstacle(row, col, GameBoard.BLACK);
            if (actualPos == null) {
                session.undoHistory.removeLast();
                return toDTO(session, "落子失败");
            }
            session.revision++;

            int actualRow = actualPos[0], actualCol = actualPos[1];
            String waterMsg = (actualRow != row || actualCol != col) ? "棋子滑入了水潭！" : null;

            // 5. 检查地形是否被包围（落子后四邻地形检查）
            List<GameBoard.TerrainRecord> removed = board.checkAndRemoveObstacles(actualRow, actualCol);

            // 6. 检查是否胜利
            if (board.checkWin(actualRow, actualCol)) {
                session.status = "PLAYER_WIN";
                updateScore(userId, "WIN");
                String msg = waterMsg != null ? waterMsg + " 恭喜你赢了！" : "恭喜你赢了！";
                if (!removed.isEmpty()) msg = msg.replace("赢了！", "赢了！（消除了" + removed.size() + "个地形）");
                return toDTO(session, msg);
            }

            // 7. 检查平局
            if (board.isFullObstacle()) {
                session.status = "DRAW";
                updateScore(userId, "DRAW");
                return toDTO(session, "棋盘已满，平局！");
            }

            // 8. 地形刷新计数器
            session.movesSinceSpawn--;
            if (session.movesSinceSpawn <= 0) {
                int spawnCount = SPAWN_MIN_COUNT + random.nextInt(SPAWN_MAX_COUNT - SPAWN_MIN_COUNT + 1);
                board.spawnTerrain(spawnCount);
                session.nextSpawnInterval = SPAWN_MIN_INTERVAL + random.nextInt(SPAWN_MAX_INTERVAL - SPAWN_MIN_INTERVAL + 1);
                session.movesSinceSpawn = session.nextSpawnInterval;

                if (board.isFullObstacle()) {
                    session.status = "DRAW";
                    updateScore(userId, "DRAW");
                    return toDTO(session, "地形覆盖全部空位，平局！");
                }
            }

            // 9. 改为两步流程：先返回 AI_TURN，前端再请求白子AI落子
            session.status = "AI_TURN";
            StringBuilder responseMsg = new StringBuilder("白子AI思考中...");
            if (waterMsg != null) responseMsg.insert(0, waterMsg + " ");
            if (!removed.isEmpty()) responseMsg.append("（消除了").append(removed.size()).append("个地形）");
            return toDTO(session, responseMsg.toString());

        } catch (RuntimeException exception) {
            throw new IllegalStateException("三人障碍物模式处理失败", exception);
        }
    }

    /**
     * AI回合（三人模式下灰子AI落子；三人障碍物模式也走此分支，灰AI难度降低一级）
     */
    @Transactional
    public GameStateDTO aiMove(UserInfo user) {
        ReentrantLock lock = gameLock(user.getId());
        lock.lock();
        GameSession session = null;
        GameSnapshot operationSnapshot = null;
        try {
            session = getSession(user.getId());
            if (session == null) return errorDTO("游戏未开始");
            operationSnapshot = session.snapshot();

        // 处理 AI_TURN 状态（白子AI落子）
        if ("AI_TURN".equals(session.status)) {
            GameBoard board = session.board;
            int[] aiMove = aiMoveService.calculate(
                    board, session.difficulty, session.gameMode, GameBoard.WHITE);

            // 障碍物模式：白子AI落子后，处理地形刷新和胜利检查
            if ("OBSTACLE".equals(session.gameMode)) {
                if (aiMove == null) {
                    if (board.isFullObstacle()) {
                        session.status = "DRAW";
                        updateScore(user.getId(), "DRAW");
                        return toDTO(session, "棋盘已满，平局！");
                    }
                    session.status = "PLAYER_TURN";
                    return toDTO(session, "AI无法落子，请继续");
                }

                aiMove = applyAiMove(session, aiMove, GameBoard.WHITE);
                session.currentTurn = GameBoard.WHITE;
                session.revision++;

                // 检查AI是否胜利
                if (board.checkWin(aiMove[0], aiMove[1])) {
                    session.status = "AI_WIN";
                    updateScore(user.getId(), "LOSS");
                    return toDTO(session, "AI赢了，再接再厉！");
                }

                if (board.isFullObstacle()) {
                    session.status = "DRAW";
                    updateScore(user.getId(), "DRAW");
                    return toDTO(session, "棋盘已满，平局！");
                }

                // 玩家回合，递减地形刷新计数器
                session.movesSinceSpawn--;
                if (session.movesSinceSpawn <= 0) {
                    int spawnCount = SPAWN_MIN_COUNT + random.nextInt(SPAWN_MAX_COUNT - SPAWN_MIN_COUNT + 1);
                    board.spawnTerrain(spawnCount);
                    session.nextSpawnInterval = SPAWN_MIN_INTERVAL + random.nextInt(SPAWN_MAX_INTERVAL - SPAWN_MIN_INTERVAL + 1);
                    session.movesSinceSpawn = session.nextSpawnInterval;

                    if (board.isFullObstacle()) {
                        session.status = "DRAW";
                        updateScore(user.getId(), "DRAW");
                        return toDTO(session, "地形覆盖全部空位，平局！");
                    }
                }

                session.status = "PLAYER_TURN";
                return toDTO(session, "请落子");
            }

            if (aiMove == null) {
                if (board.isFullObstacle() || board.isFull()) {
                    session.status = "DRAW";
                    updateScore(user.getId(), "DRAW");
                    return toDTO(session, "棋盘已满，平局！");
                }
                session.status = "PLAYER_TURN";
                return toDTO(session, "AI无法落子，请继续");
            }

            aiMove = applyAiMove(session, aiMove, GameBoard.WHITE);
            session.currentTurn = GameBoard.WHITE;
            session.revision++;

            if (board.checkWin(aiMove[0], aiMove[1])) {
                session.status = "AI_WIN";
                updateScore(user.getId(), "LOSS");
                return toDTO(session, "AI赢了，再接再厉！");
            }

            if (board.isFullObstacle() || board.isFull()) {
                session.status = "DRAW";
                updateScore(user.getId(), "DRAW");
                return toDTO(session, "棋盘已满，平局！");
            }

            // 动态棋盘模式：每2回合（4步）发放一枚令牌，玩家与AI轮流获得
            if (session.dynamicMode) {
                session.totalMovesSinceUnlock++;
                if (session.totalMovesSinceUnlock >= GameSession.UNLOCK_TOKEN_INTERVAL) {
                    if (session.nextUnlockIsPlayer) {
                        // 玩家获得令牌
                        session.unlockTokens++;
                        session.lastAiUnlockMsg = null;  // 玩家获得令牌，不显示AI解锁
                    } else {
                        // AI自动解锁一个区块
                        int[] unlocked = session.board.unlockRandomBlock();
                        if (unlocked != null) {
                            session.lastAiUnlockMsg = " | AI解锁了区块(" + unlocked[0] + "," + unlocked[1] + ")";
                        }
                    }
                    session.totalMovesSinceUnlock = 0;
                    session.nextUnlockIsPlayer = !session.nextUnlockIsPlayer;  // 轮流
                }
            }

            // 三人/三人障碍物模式：白子AI落子后，返回 GRAY_TURN 状态
            if ("THREE".equals(session.gameMode) || "THREE_OBSTACLE".equals(session.gameMode)) {
                session.status = "GRAY_TURN";
                return toDTO(session, "白子落子完毕，灰子思考中...");
            }

            // 双人/障碍物/动态棋盘模式：白子AI落子后，返回 PLAYER_TURN 状态
            session.status = "PLAYER_TURN";
            String turnMsg = "请落子";
            if (session.dynamicMode && session.lastAiUnlockMsg != null) {
                turnMsg = "请落子" + session.lastAiUnlockMsg;
                session.lastAiUnlockMsg = null;  // 用完清除
            }
            return toDTO(session, turnMsg);
        }

        // 障碍物模式（非三人障碍物）不支持三人灰子AI
        if (session.obstacleMode && !"THREE_OBSTACLE".equals(session.gameMode)) {
            return toDTO(session, "障碍物模式为双人模式");
        }

        GameBoard board = session.board;

        if (!"GRAY_TURN".equals(session.status)) {
            return toDTO(session, "现在不是AI的回合");
        }

        // 灰子AI落子（三人障碍物模式使用支持地形的AI，且难度降低一级）
        int[] aiGrayMove;
        if ("THREE_OBSTACLE".equals(session.gameMode)) {
            // 灰子AI难度降低一级
            String grayDifficulty = session.difficulty;
            if ("HARD".equals(session.difficulty)) grayDifficulty = "MEDIUM";
            else if ("MEDIUM".equals(session.difficulty)) grayDifficulty = "EASY";
            // EASY stays EASY

            aiGrayMove = aiMoveService.calculate(board, grayDifficulty, session.gameMode, GameBoard.GRAY);
        } else {
            aiGrayMove = aiMoveService.calculate(board, session.difficulty, session.gameMode, GameBoard.GRAY);
        }

        if (aiGrayMove == null) {
            // AI无法落子
            if (session.obstacleMode && board.isFullObstacle()) {
                session.status = "DRAW";
                updateScore(user.getId(), "DRAW");
                return toDTO(session, "棋盘已满，平局！");
            }
            if (!session.obstacleMode && board.isFull()) {
                session.status = "DRAW";
                updateScore(user.getId(), "DRAW");
                return toDTO(session, "棋盘已满，平局！");
            }
            session.status = "PLAYER_TURN";
            session.currentTurn = GameBoard.BLACK;
            return toDTO(session, "灰子AI无法落子，请继续");
        }

        aiGrayMove = applyAiMove(session, aiGrayMove, GameBoard.GRAY);
        session.currentTurn = GameBoard.GRAY;
        session.revision++;

        if (board.checkWin(aiGrayMove[0], aiGrayMove[1])) {
            session.status = "GRAY_WIN";
            updateScore(user.getId(), "LOSS");
            return toDTO(session, "灰子AI赢了！");
        }

        if (session.obstacleMode && board.isFullObstacle()) {
            session.status = "DRAW";
            updateScore(user.getId(), "DRAW");
            return toDTO(session, "棋盘已满，平局！");
        }

        if (!session.obstacleMode && board.isFull()) {
            session.status = "DRAW";
            updateScore(user.getId(), "DRAW");
            return toDTO(session, "棋盘已满，平局！");
        }

        // 三人障碍物模式：灰子AI落子后，地形刷新计数器递减
        if ("THREE_OBSTACLE".equals(session.gameMode)) {
            session.movesSinceSpawn--;
            if (session.movesSinceSpawn <= 0) {
                int spawnCount = SPAWN_MIN_COUNT + random.nextInt(SPAWN_MAX_COUNT - SPAWN_MIN_COUNT + 1);
                board.spawnTerrain(spawnCount);
                session.nextSpawnInterval = SPAWN_MIN_INTERVAL + random.nextInt(SPAWN_MAX_INTERVAL - SPAWN_MIN_INTERVAL + 1);
                session.movesSinceSpawn = session.nextSpawnInterval;

                if (board.isFullObstacle()) {
                    session.status = "DRAW";
                    updateScore(user.getId(), "DRAW");
                    return toDTO(session, "地形覆盖全部空位，平局！");
                }
            }
        }

        session.status = "PLAYER_TURN";
        session.currentTurn = GameBoard.BLACK;
        return toDTO(session, "灰子落子完毕，轮到你了！");
        } catch (RuntimeException exception) {
            if (session != null && operationSnapshot != null) {
                session.restore(operationSnapshot);
            }
            throw exception;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 悔棋 - 双人模式撤回玩家+AI各一步，三人模式撤回三部，障碍物模式撤回玩家+AI各一步+地形回退
     */
    @Transactional
    public GameStateDTO undo(UserInfo user) {
        ReentrantLock lock = gameLock(user.getId());
        lock.lock();
        try {
            GameSession session = getSession(user.getId());
            if (session == null) return errorDTO("游戏未开始");
            if (session.undoRemaining <= 0) return toDTO(session, "悔棋次数已用完");
            if (!"PLAYER_TURN".equals(session.status) && !"GRAY_TURN".equals(session.status)) {
                return toDTO(session, "当前回合无法悔棋");
            }

            GameSnapshot snapshot = session.undoHistory.pollLast();
            if (snapshot == null) return toDTO(session, "无法悔棋");

            int remaining = session.undoRemaining - 1;
            long nextRevision = session.revision + 1;
            session.restore(snapshot);
            session.undoRemaining = remaining;
            session.revision = nextRevision;
            return toDTO(session, "已悔棋，剩余次数: " + remaining);
        } finally {
            lock.unlock();
        }
    }


    /**
     * 暂停游戏
     */
    @Transactional
    public GameStateDTO pause(UserInfo user) {
        ReentrantLock lock = gameLock(user.getId());
        lock.lock();
        try {
        GameSession session = getSession(user.getId());
        if (session == null) return errorDTO("游戏未开始");
        if (!"PLAYER_TURN".equals(session.status)) {
            return toDTO(session, "当前状态无法暂停");
        }
        session.prePauseStatus = session.status;
        session.board.setPaused(true);
        session.status = "PAUSED";
        session.revision++;
        return toDTO(session, "游戏已暂停");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 恢复游戏
     */
    @Transactional
    public GameStateDTO resume(UserInfo user) {
        ReentrantLock lock = gameLock(user.getId());
        lock.lock();
        try {
        GameSession session = getSession(user.getId());
        if (session == null) return errorDTO("游戏未开始");
        if (!"PAUSED".equals(session.status)) return toDTO(session, "游戏未暂停");

        session.board.setPaused(false);
        session.status = session.prePauseStatus;
        session.prePauseStatus = null;
        session.revision++;
        return toDTO(session, "游戏已恢复，请落子");
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前游戏状态
     */
    @Transactional
    public GameStateDTO getState(UserInfo user) {
        ReentrantLock lock = gameLock(user.getId());
        lock.lock();
        try {
        GameSession session = getSession(user.getId());
        if (session == null) return errorDTO("游戏未开始");
        return toDTO(session, null);
        } finally {
            lock.unlock();
        }
    }

    // ============ 私有方法 ============

    private ReentrantLock gameLock(Long userId) {
        return gameLocks[Math.floorMod(userId.hashCode(), gameLocks.length)];
    }

    private GameSession getSession(Long userId) {
        GameSession loaded = stateStore.load(userId)
                .map(GameSession::fromStoredState).orElse(null);
        if (loaded == null) games.remove(userId);
        else games.put(userId, loaded);
        return loaded;
    }

    private int[] applyAiMove(GameSession session, int[] position, int color) {
        GameBoard board = session.board;
        if (session.obstacleMode) {
            int[] actual = board.placeStoneObstacle(position[0], position[1], color);
            if (actual == null) throw new IllegalStateException("AI selected an illegal terrain move");
            board.checkAndRemoveObstacles(actual[0], actual[1]);
            return actual;
        }
        boolean placed = session.dynamicMode
                ? board.placeStoneDynamic(position[0], position[1], color)
                : board.placeStone(position[0], position[1], color);
        if (!placed) throw new IllegalStateException("AI selected an illegal move");
        return position;
    }

    private void finishGame(
            GameSession session, Long userId, String status, ScoreSettlementService.Result result) {
        scoreSettlementService.settle(session.gameId, userId, result);
        session.status = status;
    }

    private void updateScore(Long userId, String result) {
        GameSession session = games.get(userId);
        if (session == null) throw new IllegalStateException("Game disappeared during settlement");
        scoreSettlementService.settle(
                session.gameId, userId, ScoreSettlementService.Result.valueOf(result));
    }

    private void rollbackFailedTurn(GameSession session, int priorSnapshotDepth) {
        if (session != null && session.undoHistory.size() > priorSnapshotDepth) {
            session.restore(session.undoHistory.removeLast());
        }
    }

    private GameStateDTO toDTO(GameSession session, String message) {
        stateStore.save(session.exportState());
        GameStateDTO dto = new GameStateDTO();
        dto.setGameId(session.gameId);
        dto.setRevision(session.revision);
        dto.setBoard(copyBoard(session.board.getBoard()));
        dto.setTerrain(copyBoard(session.board.getTerrain()));  // 传输地形数据
        dto.setStatus(session.status);
        dto.setDifficulty(session.difficulty);
        dto.setUndoRemaining(session.undoRemaining);
        dto.setPaused(session.board.isPaused());
        dto.setMessage(message);
        dto.setGameMode(session.gameMode);
        dto.setCurrentTurn(session.currentTurn);
        
        // 动态棋盘模式：传输区块状态和解锁令牌数
        if (session.dynamicMode) {
            dto.setBlockStates(copyBoard(session.board.getBlockStates()));
            dto.setUnlockTokens(session.unlockTokens);
        }
        
        return dto;
    }

    private GameStateDTO errorDTO(String message) {
        GameStateDTO dto = new GameStateDTO();
        dto.setStatus("ERROR");
        dto.setMessage(message);
        return dto;
    }

    private int[][] copyBoard(int[][] source) {
        if (source == null) return null;
        int size = source.length;
        int[][] copy = new int[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(source[i], 0, copy[i], 0, size);
        }
        return copy;
    }

    /** 游戏会话内部类 */
    private static class GameSession {
        String gameId;
        Long userId;
        GameBoard board;
        long revision;
        String prePauseStatus;
        final Deque<GameSnapshot> undoHistory = new ArrayDeque<>();
        String difficulty;
        String status;      // PLAYER_TURN, AI_THINKING, PLAYER_WIN, AI_WIN, WHITE_WIN, GRAY_WIN, DRAW, PAUSED
        int undoRemaining;
        String gameMode;    // "TWO", "THREE", "OBSTACLE", "THREE_OBSTACLE" or "DYNAMIC"
        int currentTurn;    // BLACK, WHITE, or GRAY (三人模式用)
        boolean obstacleMode;           // 是否障碍物模式
        int movesSinceSpawn;            // 距离下次地形刷新的剩余步数
        int nextSpawnInterval;          // 下次刷新间隔
        
        // 动态棋盘模式新增字段
        boolean dynamicMode;            // 是否动态棋盘模式
        int unlockTokens;               // 玩家可用的解锁令牌数
        int totalMovesSinceUnlock;      // 自上次发令牌后的总落子数（玩家+AI）
        int totalMovesForSeal;          // 玩家落子计数（用于触发封印）
        boolean nextUnlockIsPlayer;     // 下一个解锁令牌归谁（true=玩家，false=AI）
        String lastAiUnlockMsg;         // AI最近一次自动解锁的消息
        static final int SEAL_INTERVAL = 8;  // 封印间隔（8回合）
        static final int UNLOCK_TOKEN_INTERVAL = 4;  // 发令牌间隔（4步=2回合）

        void saveTurnSnapshot() {
            undoHistory.addLast(snapshot());
        }

        GameSnapshot snapshot() {
            return new GameSnapshot(board.copy(), status, currentTurn, undoRemaining,
                    movesSinceSpawn, nextSpawnInterval, unlockTokens, totalMovesSinceUnlock,
                    totalMovesForSeal, nextUnlockIsPlayer, lastAiUnlockMsg, prePauseStatus, revision);
        }

        void restore(GameSnapshot snapshot) {
            board = snapshot.board().copy();
            status = snapshot.status();
            currentTurn = snapshot.currentTurn();
            undoRemaining = snapshot.undoRemaining();
            movesSinceSpawn = snapshot.movesSinceSpawn();
            nextSpawnInterval = snapshot.nextSpawnInterval();
            unlockTokens = snapshot.unlockTokens();
            totalMovesSinceUnlock = snapshot.totalMovesSinceUnlock();
            totalMovesForSeal = snapshot.totalMovesForSeal();
            nextUnlockIsPlayer = snapshot.nextUnlockIsPlayer();
            lastAiUnlockMsg = snapshot.lastAiUnlockMsg();
            prePauseStatus = snapshot.prePauseStatus();
            revision = snapshot.revision();
        }

        GameStateStore.StoredGame exportState() {
            List<GameStateStore.StoredSnapshot> snapshots = new ArrayList<>(undoHistory.size());
            for (GameSnapshot snapshot : undoHistory) {
                snapshots.add(snapshot.exportState());
            }
            return new GameStateStore.StoredGame(gameId, userId, board.exportState(), revision,
                    prePauseStatus, snapshots, difficulty, status, undoRemaining, gameMode,
                    currentTurn, obstacleMode, movesSinceSpawn, nextSpawnInterval, dynamicMode,
                    unlockTokens, totalMovesSinceUnlock, totalMovesForSeal,
                    nextUnlockIsPlayer, lastAiUnlockMsg);
        }

        static GameSession fromStoredState(GameStateStore.StoredGame saved) {
            GameSession session = new GameSession();
            session.gameId = saved.gameId();
            session.userId = saved.userId();
            session.board = GameBoard.fromState(saved.board());
            session.revision = saved.revision();
            session.prePauseStatus = saved.prePauseStatus();
            for (GameStateStore.StoredSnapshot snapshot : saved.undoHistory()) {
                session.undoHistory.addLast(GameSnapshot.fromStoredState(snapshot));
            }
            session.difficulty = saved.difficulty();
            session.status = saved.status();
            session.undoRemaining = saved.undoRemaining();
            session.gameMode = saved.gameMode();
            session.currentTurn = saved.currentTurn();
            session.obstacleMode = saved.obstacleMode();
            session.movesSinceSpawn = saved.movesSinceSpawn();
            session.nextSpawnInterval = saved.nextSpawnInterval();
            session.dynamicMode = saved.dynamicMode();
            session.unlockTokens = saved.unlockTokens();
            session.totalMovesSinceUnlock = saved.totalMovesSinceUnlock();
            session.totalMovesForSeal = saved.totalMovesForSeal();
            session.nextUnlockIsPlayer = saved.nextUnlockIsPlayer();
            session.lastAiUnlockMsg = saved.lastAiUnlockMsg();
            return session;
        }
    }

    private record GameSnapshot(
            GameBoard board, String status, int currentTurn, int undoRemaining,
            int movesSinceSpawn, int nextSpawnInterval, int unlockTokens,
            int totalMovesSinceUnlock, int totalMovesForSeal, boolean nextUnlockIsPlayer,
            String lastAiUnlockMsg, String prePauseStatus, long revision) {
        GameStateStore.StoredSnapshot exportState() {
            return new GameStateStore.StoredSnapshot(board.exportState(), status, currentTurn,
                    undoRemaining, movesSinceSpawn, nextSpawnInterval, unlockTokens,
                    totalMovesSinceUnlock, totalMovesForSeal, nextUnlockIsPlayer,
                    lastAiUnlockMsg, prePauseStatus, revision);
        }

        static GameSnapshot fromStoredState(GameStateStore.StoredSnapshot saved) {
            return new GameSnapshot(GameBoard.fromState(saved.board()), saved.status(),
                    saved.currentTurn(), saved.undoRemaining(), saved.movesSinceSpawn(),
                    saved.nextSpawnInterval(), saved.unlockTokens(), saved.totalMovesSinceUnlock(),
                    saved.totalMovesForSeal(), saved.nextUnlockIsPlayer(), saved.lastAiUnlockMsg(),
                    saved.prePauseStatus(), saved.revision());
        }
    }
}
