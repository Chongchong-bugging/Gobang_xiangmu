package com.gobang.service;

import com.gobang.ai.GameBoard;
import com.gobang.ai.GobangAI;
import com.gobang.dto.GameStateDTO;
import com.gobang.entity.Score;
import com.gobang.entity.UserInfo;
import com.gobang.repository.ScoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    @Autowired private ScoreRepository scoreRepo;

    /** 存储每个用户的游戏状态 (userId -> GameState) */
    private final Map<Long, GameSession> games = new ConcurrentHashMap<>();

    // ==================== 障碍物模式配置参数 ====================
    private static final int SPAWN_MIN_INTERVAL = 5;   // 最小刷新间隔（步数）
    private static final int SPAWN_MAX_INTERVAL = 15;  // 最大刷新间隔（步数）
    private static final int SPAWN_MIN_COUNT = 1;      // 每次最少刷新数量
    private static final int SPAWN_MAX_COUNT = 3;      // 每次最多刷新数量

    private final Random random = new Random();

    /**
     * 开始新游戏
     * @param gameMode "TWO"=双人模式, "THREE"=三人模式, "OBSTACLE"=障碍物模式, "THREE_OBSTACLE"=三人障碍物模式
     * @param obstacleMode 是否障碍物模式（OBSTACLE/THREE_OBSTACLE gameMode 下强制为 true）
     */
    public GameStateDTO startGame(UserInfo user, String difficulty, String gameMode) {
        GameSession session = new GameSession();
        session.userId = user.getId();
        session.gameMode = gameMode == null ? "TWO" : gameMode.toUpperCase();
        session.obstacleMode = "OBSTACLE".equals(session.gameMode) || "THREE_OBSTACLE".equals(session.gameMode);

        // 三人模式和三人障碍物模式使用25x25棋盘
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

        games.put(user.getId(), session);

        return toDTO(session, "游戏开始，请落子");
    }

    /**
     * 玩家落子
     * 障碍物模式走完全独立分支，不影响原有 TWO/THREE 逻辑
     */
    public GameStateDTO playerMove(UserInfo user, int row, int col) {
        GameSession session = games.get(user.getId());
        if (session == null) {
            return errorDTO("游戏未开始");
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
        board.placeStone(row, col, GameBoard.BLACK);
        session.currentTurn = GameBoard.BLACK;

        // 检查是否胜利
        if (board.checkWin(row, col)) {
            session.status = "PLAYER_WIN";
            updateScore(user.getId(), "WIN");
            return toDTO(session, "恭喜你赢了！");
        }

        // 检查平局
        if (board.isFull()) {
            session.status = "DRAW";
            updateScore(user.getId(), "DRAW");
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
            int[] actualPos = board.placeStoneObstacle(row, col, GameBoard.BLACK);
            if (actualPos == null) {
                return toDTO(session, "落子失败");
            }

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

        } catch (Exception e) {
            // 障碍物模式异常保护：不影响原有模式
            e.printStackTrace();
            // 尝试恢复状态
            try {
                session.status = "PLAYER_TURN";
            } catch (Exception ignored) {}
            return toDTO(session, "障碍物模式处理异常，请重试");
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
            int[] actualPos = board.placeStoneObstacle(row, col, GameBoard.BLACK);
            if (actualPos == null) {
                return toDTO(session, "落子失败");
            }

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

        } catch (Exception e) {
            e.printStackTrace();
            try {
                session.status = "PLAYER_TURN";
            } catch (Exception ignored) {}
            return toDTO(session, "三人障碍物模式处理异常，请重试");
        }
    }

    /**
     * AI回合（三人模式下灰子AI落子；三人障碍物模式也走此分支，灰AI难度降低一级）
     */
    public GameStateDTO aiMove(UserInfo user) {
        GameSession session = games.get(user.getId());
        if (session == null) return errorDTO("游戏未开始");

        // 处理 AI_TURN 状态（白子AI落子）
        if ("AI_TURN".equals(session.status)) {
            GameBoard board = session.board;
            int[] aiMove = null;

            // 三人障碍物模式：白子AI落子（支持地形）
            if ("THREE_OBSTACLE".equals(session.gameMode)) {
                aiMove = GobangAI.generateMoveForObstacle(board, session.difficulty, GameBoard.WHITE);
            }
            // 三人模式：白子AI落子
            else if ("THREE".equals(session.gameMode)) {
                aiMove = GobangAI.generateMove(board, session.difficulty, GameBoard.WHITE);
            }
            // 障碍物模式：支持地形的AI落子
            else if ("OBSTACLE".equals(session.gameMode)) {
                aiMove = GobangAI.generateMoveForObstacle(board, session.difficulty, GameBoard.WHITE);
            }
            // 双人模式：普通AI落子
            else {
                aiMove = GobangAI.generateMove(board, session.difficulty, GameBoard.WHITE);
            }

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

                board.placeStone(aiMove[0], aiMove[1], GameBoard.WHITE);
                session.currentTurn = GameBoard.WHITE;

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

            board.placeStone(aiMove[0], aiMove[1], GameBoard.WHITE);
            session.currentTurn = GameBoard.WHITE;

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

            // 三人/三人障碍物模式：白子AI落子后，返回 GRAY_TURN 状态
            if ("THREE".equals(session.gameMode) || "THREE_OBSTACLE".equals(session.gameMode)) {
                session.status = "GRAY_TURN";
                return toDTO(session, "白子落子完毕，灰子思考中...");
            }

            // 双人/障碍物模式：白子AI落子后，返回 PLAYER_TURN 状态
            session.status = "PLAYER_TURN";
            return toDTO(session, "请落子");
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

            aiGrayMove = GobangAI.generateMoveForObstacle(board, grayDifficulty, GameBoard.GRAY);
        } else {
            aiGrayMove = GobangAI.generateMove(board, session.difficulty, GameBoard.GRAY);
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

        board.placeStone(aiGrayMove[0], aiGrayMove[1], GameBoard.GRAY);
        session.currentTurn = GameBoard.GRAY;

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
    }

    /**
     * 悔棋 - 双人模式撤回玩家+AI各一步，三人模式撤回三部，障碍物模式撤回玩家+AI各一步+地形回退
     */
    public GameStateDTO undo(UserInfo user) {
        GameSession session = games.get(user.getId());
        if (session == null) return errorDTO("游戏未开始");
        if (session.undoRemaining <= 0) return toDTO(session, "悔棋次数已用完");

        // ========== 三人障碍物模式：独立悔棋逻辑 ==========
        if ("THREE_OBSTACLE".equals(session.gameMode)) {
            // 至少要有玩家+白AI+灰AI共3步
            if (session.board.moveCount() < 3) return toDTO(session, "无法悔棋");

            boolean canUndo = "PLAYER_TURN".equals(session.status)
                    || "AI_WIN".equals(session.status)
                    || "WHITE_WIN".equals(session.status)
                    || "GRAY_WIN".equals(session.status);
            if (!canUndo) {
                return toDTO(session, "现在无法悔棋");
            }

            // 如果AI已赢，回退状态
            if ("AI_WIN".equals(session.status) || "WHITE_WIN".equals(session.status) || "GRAY_WIN".equals(session.status)) {
                session.status = "PLAYER_TURN";
            }

            // 撤回灰AI、白AI、玩家黑子各一步
            session.board.undo();
            session.board.undo();
            session.board.undo();

            session.undoRemaining--;
            session.status = "PLAYER_TURN";
            session.currentTurn = GameBoard.BLACK;

            // 重置换新计数器
            session.nextSpawnInterval = SPAWN_MIN_INTERVAL + random.nextInt(SPAWN_MAX_INTERVAL - SPAWN_MIN_INTERVAL + 1);
            session.movesSinceSpawn = session.nextSpawnInterval;

            return toDTO(session, "已悔棋，剩余次数: " + session.undoRemaining);
        }

        // ========== 障碍物模式：独立悔棋逻辑 ==========
        if (session.obstacleMode) {
            // 至少要有玩家+AI共2步
            if (session.board.moveCount() < 2) return toDTO(session, "无法悔棋");

            boolean canUndo = "PLAYER_TURN".equals(session.status)
                    || "AI_WIN".equals(session.status);
            if (!canUndo) {
                return toDTO(session, "现在无法悔棋");
            }

            // 如果AI已赢，回退状态
            if ("AI_WIN".equals(session.status)) {
                session.status = "PLAYER_TURN";
            }

            // 撤回AI白子
            session.board.undo();
            // 撤回玩家黑子
            session.board.undo();

            session.undoRemaining--;
            session.status = "PLAYER_TURN";
            session.currentTurn = GameBoard.BLACK;

            // 重置换新计数器（从当前棋盘状态出发）
            session.nextSpawnInterval = SPAWN_MIN_INTERVAL + random.nextInt(SPAWN_MAX_INTERVAL - SPAWN_MIN_INTERVAL + 1);
            session.movesSinceSpawn = session.nextSpawnInterval;

            return toDTO(session, "已悔棋，剩余次数: " + session.undoRemaining);
        }

        // ========== 原有 TWO/THREE 悔棋逻辑（完全不变） ==========
        // 三人模式：灰子回合时至少2步，玩家回合时至少3步；双人模式至少2步
        int minSteps;
        if ("THREE".equals(session.gameMode)) {
            minSteps = "GRAY_TURN".equals(session.status) ? 2 : 3;
        } else {
            minSteps = 2;
        }
        if (session.board.moveCount() < minSteps) return toDTO(session, "无法悔棋");

        // 允许悔棋的状态
        boolean canUndo = "PLAYER_TURN".equals(session.status)
                || "GRAY_TURN".equals(session.status)
                || "AI_WIN".equals(session.status)
                || "WHITE_WIN".equals(session.status)
                || "GRAY_WIN".equals(session.status);
        if (!canUndo) {
            return toDTO(session, "现在无法悔棋");
        }

        // 如果游戏已结束，回溯状态
        if ("AI_WIN".equals(session.status) || "WHITE_WIN".equals(session.status) || "GRAY_WIN".equals(session.status)) {
            session.status = "PLAYER_TURN";
        }

        if ("THREE".equals(session.gameMode)) {
            if ("GRAY_TURN".equals(session.status)) {
                // 灰子还未落子，撤回白子和黑子各一步
                session.board.undo();  // 撤回白子
                session.board.undo();  // 撤回黑子
            } else {
                // 灰子已落子，撤回灰子、白子、黑子各一步
                session.board.undo();  // 撤回灰子
                session.board.undo();  // 撤回白子
                session.board.undo();  // 撤回黑子
            }
        } else {
            // 双人模式：撤回白子、黑子各一步
            session.board.undo();  // 撤回白子
            session.board.undo();  // 撤回黑子
        }

        session.undoRemaining--;
        session.status = "PLAYER_TURN";
        session.currentTurn = GameBoard.BLACK;

        return toDTO(session, "已悔棋，剩余次数: " + session.undoRemaining);
    }

    /**
     * 暂停游戏
     */
    public GameStateDTO pause(UserInfo user) {
        GameSession session = games.get(user.getId());
        if (session == null) return errorDTO("游戏未开始");
        session.board.setPaused(true);
        session.status = "PAUSED";
        return toDTO(session, "游戏已暂停");
    }

    /**
     * 恢复游戏
     */
    public GameStateDTO resume(UserInfo user) {
        GameSession session = games.get(user.getId());
        if (session == null) return errorDTO("游戏未开始");
        if (!"PAUSED".equals(session.status)) return toDTO(session, "游戏未暂停");

        session.board.setPaused(false);
        session.status = "PLAYER_TURN";
        return toDTO(session, "游戏已恢复，请落子");
    }

    /**
     * 获取当前游戏状态
     */
    public GameStateDTO getState(UserInfo user) {
        GameSession session = games.get(user.getId());
        if (session == null) return errorDTO("游戏未开始");
        return toDTO(session, null);
    }

    // ============ 私有方法 ============

    private void updateScore(Long userId, String result) {
        Score score = scoreRepo.findByUserId(userId).orElseGet(() -> {
            Score s = new Score();
            s.setUserId(userId);
            return s;
        });

        switch (result) {
            case "WIN":
                score.setWins(score.getWins() + 1);
                score.setTotalScore(score.getTotalScore() + 3);
                break;
            case "LOSS":
                score.setLosses(score.getLosses() + 1);
                score.setTotalScore(score.getTotalScore() - 1);
                break;
            case "DRAW":
                score.setDraws(score.getDraws() + 1);
                score.setTotalScore(score.getTotalScore() + 1);
                break;
        }
        scoreRepo.save(score);
    }

    private GameStateDTO toDTO(GameSession session, String message) {
        GameStateDTO dto = new GameStateDTO();
        dto.setGameId(session.userId);
        dto.setBoard(copyBoard(session.board.getBoard()));
        dto.setTerrain(copyBoard(session.board.getTerrain()));  // 传输地形数据
        dto.setStatus(session.status);
        dto.setDifficulty(session.difficulty);
        dto.setUndoRemaining(session.undoRemaining);
        dto.setPaused(session.board.isPaused());
        dto.setMessage(message);
        dto.setGameMode(session.gameMode);
        dto.setCurrentTurn(session.currentTurn);
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
        Long userId;
        GameBoard board;
        String difficulty;
        String status;      // PLAYER_TURN, AI_THINKING, PLAYER_WIN, AI_WIN, WHITE_WIN, GRAY_WIN, DRAW, PAUSED
        int undoRemaining;
        String gameMode;    // "TWO", "THREE", "OBSTACLE" or "THREE_OBSTACLE"
        int currentTurn;    // BLACK, WHITE, or GRAY (三人模式用)
        boolean obstacleMode;           // 是否障碍物模式
        int movesSinceSpawn;            // 距离下次地形刷新的剩余步数
        int nextSpawnInterval;          // 下次刷新间隔
    }
}
