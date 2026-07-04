package com.gobang.ai;

import java.util.*;

/**
 * 五子棋AI引擎 - 三种难度
 * EASY: 随机防守
 * MEDIUM: 评分算法
 * HARD: Minimax + Alpha-Beta剪枝
 * 支持指定棋子颜色，用于三人模式
 */
public class GobangAI {

    private static final int WIN_SCORE = 1000000;
    private static final int MAX_DEPTH_HARD = 3;

    /**
     * 障碍物模式：生成指定颜色棋子的落子位置（过滤假山、感知水潭）
     */
    public static int[] generateMoveForObstacle(GameBoard board, String difficulty, int stoneColor) {
        // 收集所有可落子的空位（排除假山位置）
        int size = board.getSize();
        List<int[]> playable = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board.isPlayable(r, c)) {
                    playable.add(new int[]{r, c});
                }
            }
        }
        if (playable.isEmpty()) return null;

        // 基于难度选择策略
        switch (difficulty.toUpperCase()) {
            case "EASY":
                return obstacleEasyMove(board, stoneColor, playable);
            case "MEDIUM":
                return obstacleMediumMove(board, stoneColor, playable);
            case "HARD":
                return obstacleHardMove(board, stoneColor, playable);
            default:
                return obstacleEasyMove(board, stoneColor, playable);
        }
    }

    /**
     * 障碍物模式初级AI：在可落子位置中随机选择
     */
    private static int[] obstacleEasyMove(GameBoard b, int stoneColor, List<int[]> playable) {
        int size = b.getSize();
        int center = size / 2;

        // 1. 先检查是否能直接获胜
        for (int[] pos : playable) {
            b.placeStone(pos[0], pos[1], stoneColor);
            boolean win = b.checkWin(pos[0], pos[1]);
            b.undo();
            if (win) return pos;
        }

        // 2. 尝试阻挡对方的四连
        List<Integer> opponents = getOpponents(b, stoneColor);
        List<int[]> dangerMoves = new ArrayList<>();
        for (int[] pos : playable) {
            for (int opponent : opponents) {
                b.placeStone(pos[0], pos[1], opponent);
                boolean danger = b.checkWin(pos[0], pos[1]);
                b.undo();
                if (danger) {
                    dangerMoves.add(pos);
                    break;
                }
            }
        }
        if (!dangerMoves.isEmpty()) {
            return dangerMoves.get(new Random().nextInt(dangerMoves.size()));
        }

        // 3. 优先在已有棋子附近落子（避免水潭位置）
        List<int[]> safeMoves = new ArrayList<>();
        List<int[]> waterMoves = new ArrayList<>();
        for (int[] pos : playable) {
            if (hasNeighbor(b, pos[0], pos[1], 1)) {
                if (b.getTerrainAt(pos[0], pos[1]) == GameBoard.WATER) {
                    waterMoves.add(pos);
                } else {
                    safeMoves.add(pos);
                }
            }
        }
        if (!safeMoves.isEmpty()) {
            return safeMoves.get(new Random().nextInt(safeMoves.size()));
        }
        if (!waterMoves.isEmpty()) {
            return waterMoves.get(new Random().nextInt(waterMoves.size()));
        }

        // 4. 中心
        if (b.isPlayable(center, center)) return new int[]{center, center};
        return playable.get(new Random().nextInt(playable.size()));
    }

    /**
     * 障碍物模式中级AI：评分算法感知地形
     */
    private static int[] obstacleMediumMove(GameBoard b, int stoneColor, List<int[]> playable) {
        int size = b.getSize();
        int center = size / 2;
        int[] best = new int[]{center, center};
        int bestScore = -1;

        for (int[] pos : playable) {
            if (!hasNeighbor(b, pos[0], pos[1], 2)) continue;

            int score = evaluatePosition(b, pos[0], pos[1], stoneColor);
            // 水潭处落子有一定风险，降低评分
            if (b.getTerrainAt(pos[0], pos[1]) == GameBoard.WATER) {
                score = (int)(score * 0.7);
            }
            for (int opponent : getOpponents(b, stoneColor)) {
                score += evaluatePosition(b, pos[0], pos[1], opponent);
            }
            if (score > bestScore || (score == bestScore && best[0] == center && best[1] == center)) {
                bestScore = score;
                best = pos;
            }
        }
        return best;
    }

    /**
     * 障碍物模式高级AI：Minimax感知地形
     */
    private static int[] obstacleHardMove(GameBoard board, int stoneColor, List<int[]> playable) {
        long startTime = System.currentTimeMillis();
        int center = board.getSize() / 2;
        int[] bestMove = playable.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (int depth = 1; depth <= MAX_DEPTH_HARD; depth++) {
            int alpha = Integer.MIN_VALUE;
            int beta = Integer.MAX_VALUE;
            int[] currentBest = playable.get(0);
            int currentBestScore = Integer.MIN_VALUE;

            for (int[] move : playable) {
                if (System.currentTimeMillis() - startTime > 1800) break;

                board.placeStone(move[0], move[1], stoneColor);
                if (board.checkWin(move[0], move[1])) {
                    board.undo();
                    return move;
                }
                int score = minimax(board, depth - 1, alpha, beta, false, stoneColor);
                board.undo();

                if (score > currentBestScore) {
                    currentBestScore = score;
                    currentBest = move;
                }
                alpha = Math.max(alpha, score);
            }

            if (System.currentTimeMillis() - startTime > 1800) break;
            bestMove = currentBest;
            bestScore = currentBestScore;
        }
        return bestMove;
    }

    /**
     * 生成AI的落子位置（默认白子，向后兼容）
     */
    public static int[] generateMove(GameBoard board, String difficulty) {
        return generateMove(board, difficulty, GameBoard.WHITE);
    }

    /**
     * 生成指定颜色棋子的落子位置（支持三人模式）
     */
    public static int[] generateMove(GameBoard board, String difficulty, int stoneColor) {
        switch (difficulty.toUpperCase()) {
            case "EASY": return easyMove(board, stoneColor);
            case "MEDIUM": return mediumMove(board, stoneColor);
            case "HARD": return hardMove(board, stoneColor);
            default: return easyMove(board, stoneColor);
        }
    }

    /**
     * 获取对手颜色列表（用于三人模式）
     */
    private static List<Integer> getOpponents(GameBoard board, int stoneColor) {
        Set<Integer> opponents = new HashSet<>();
        int size = board.getSize();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int stone = board.getStone(r, c);
                if (stone != GameBoard.EMPTY && stone != stoneColor) {
                    opponents.add(stone);
                }
            }
        }
        return new ArrayList<>(opponents);
    }

    // ============ 初级: 随机防守 ============
    private static int[] easyMove(GameBoard b, int stoneColor) {
        List<int[]> moves = new ArrayList<>();
        List<Integer> opponents = getOpponents(b, stoneColor);
        int size = b.getSize();
        int center = size / 2;

        // 1. 先检查是否能直接获胜
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (b.getStone(r, c) != GameBoard.EMPTY) continue;
                b.placeStone(r, c, stoneColor);
                boolean win = b.checkWin(r, c);
                b.undo();
                if (win) return new int[]{r, c};
            }
        }

        // 2. 尝试阻挡对方的四连
        for (int opponent : opponents) {
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    if (b.getStone(r, c) != GameBoard.EMPTY) continue;
                    b.placeStone(r, c, opponent);
                    boolean danger = b.checkWin(r, c);
                    b.undo();
                    if (danger) {
                        moves.add(new int[]{r, c});
                    }
                }
            }
        }
        if (!moves.isEmpty()) return moves.get(new Random().nextInt(moves.size()));

        // 3. 在已有棋子附近随机落子
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (b.getStone(r, c) != GameBoard.EMPTY) continue;
                if (hasNeighbor(b, r, c, 1)) {
                    moves.add(new int[]{r, c});
                }
            }
        }
        if (!moves.isEmpty()) return moves.get(new Random().nextInt(moves.size()));

        // 4. 中心
        return new int[]{center, center};
    }

    // ============ 中级: 评分算法 ============
    private static int[] mediumMove(GameBoard b, int stoneColor) {
        int size = b.getSize();
        int center = size / 2;
        int[] best = new int[]{center, center};
        int bestScore = -1;
        List<Integer> opponents = getOpponents(b, stoneColor);

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (b.getStone(r, c) != GameBoard.EMPTY) continue;
                if (!hasNeighbor(b, r, c, 2)) continue;

                int score = evaluatePosition(b, r, c, stoneColor);
                for (int opponent : opponents) {
                    score += evaluatePosition(b, r, c, opponent);
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = new int[]{r, c};
                }
            }
        }
        return best;
    }

    private static int evaluatePosition(GameBoard b, int row, int col, int stone) {
        if (b.getStone(row, col) != GameBoard.EMPTY) return 0;
        b.placeStone(row, col, stone);
        boolean win = b.checkWin(row, col);
        if (win) {
            b.undo();
            return stone == GameBoard.WHITE ? WIN_SCORE : WIN_SCORE / 2;
        }
        int score = evaluatePoint(b, row, col, stone);
        b.undo();
        return score;
    }

    private static int evaluatePoint(GameBoard b, int row, int col, int stone) {
        int score = 0;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

        for (int[] d : dirs) {
            int count = 1;
            int openLeft = 0, openRight = 0;

            for (int i = 1; i < 5; i++) {
                int r = row + d[0] * i, c = col + d[1] * i;
                if (b.isValidPosition(r, c) && b.getStone(r, c) == stone) count++;
                else { if (b.isValidPosition(r, c) && b.getStone(r, c) == GameBoard.EMPTY) openRight = 1; break; }
            }
            for (int i = 1; i < 5; i++) {
                int r = row - d[0] * i, c = col - d[1] * i;
                if (b.isValidPosition(r, c) && b.getStone(r, c) == stone) count++;
                else { if (b.isValidPosition(r, c) && b.getStone(r, c) == GameBoard.EMPTY) openLeft = 1; break; }
            }

            int open = openLeft + openRight;
            switch (count) {
                case 5: score += 100000; break;
                case 4: score += open == 2 ? 10000 : 1000; break;
                case 3: score += open == 2 ? 1000 : 100; break;
                case 2: score += open == 2 ? 100 : 10; break;
                case 1: score += open == 2 ? 10 : 1; break;
            }
        }
        return score;
    }

    // ============ 高级: Minimax + Alpha-Beta剪枝 ============
    private static int[] hardMove(GameBoard board, int stoneColor) {
        long startTime = System.currentTimeMillis();
        int center = board.getSize() / 2;
        int[] bestMove = new int[]{center, center};
        int bestScore = Integer.MIN_VALUE;

        List<int[]> candidates = getCandidateMoves(board, stoneColor);

        for (int depth = 1; depth <= MAX_DEPTH_HARD; depth++) {
            int alpha = Integer.MIN_VALUE;
            int beta = Integer.MAX_VALUE;
            int[] currentBest = new int[]{center, center};
            int currentBestScore = Integer.MIN_VALUE;

            for (int[] move : candidates) {
                if (System.currentTimeMillis() - startTime > 1800) break;

                board.placeStone(move[0], move[1], stoneColor);
                if (board.checkWin(move[0], move[1])) {
                    board.undo();
                    return move;
                }
                int score = minimax(board, depth - 1, alpha, beta, false, stoneColor);
                board.undo();

                if (score > currentBestScore) {
                    currentBestScore = score;
                    currentBest = move;
                }
                alpha = Math.max(alpha, score);
            }

            if (System.currentTimeMillis() - startTime > 1800) break;
            bestMove = currentBest;
            bestScore = currentBestScore;
        }
        return bestMove;
    }

    private static int minimax(GameBoard board, int depth, int alpha, int beta, boolean isMaximizing, int stoneColor) {
        if (depth == 0) {
            return evaluateBoard(board, stoneColor);
        }

        List<int[]> candidates = getCandidateMoves(board, stoneColor);
        List<Integer> opponents = getOpponents(board, stoneColor);

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : candidates) {
                board.placeStone(move[0], move[1], stoneColor);
                if (board.checkWin(move[0], move[1])) {
                    board.undo();
                    return WIN_SCORE / depth;
                }
                int eval = minimax(board, depth - 1, alpha, beta, false, stoneColor);
                board.undo();
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            // 简化：使用第一个对手作为 minimax 的对手方
            int opponent = opponents.isEmpty() ?
                    (stoneColor == GameBoard.BLACK ? GameBoard.WHITE : GameBoard.BLACK) :
                    opponents.get(0);
            int minEval = Integer.MAX_VALUE;
            for (int[] move : candidates) {
                board.placeStone(move[0], move[1], opponent);
                if (board.checkWin(move[0], move[1])) {
                    board.undo();
                    return -WIN_SCORE / depth;
                }
                int eval = minimax(board, depth - 1, alpha, beta, true, stoneColor);
                board.undo();
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    private static int evaluateBoard(GameBoard board, int stoneColor) {
        List<Integer> opponents = getOpponents(board, stoneColor);
        int size = board.getSize();
        int stoneScore = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board.getStone(r, c) == stoneColor) {
                    stoneScore += evaluatePoint(board, r, c, stoneColor);
                }
            }
        }
        int opponentScore = 0;
        for (int opponent : opponents) {
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    if (board.getStone(r, c) == opponent) {
                        opponentScore += evaluatePoint(board, r, c, opponent);
                    }
                }
            }
        }
        return stoneScore - opponentScore;
    }

    private static List<int[]> getCandidateMoves(GameBoard board, int stoneColor) {
        List<int[]> moves = new ArrayList<>();
        int size = board.getSize();
        int center = size / 2;

        if (board.moveCount() == 0) {
            moves.add(new int[]{center, center});
            return moves;
        }

        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                // 使用 isPlayable 过滤假山位置
                if (!board.isPlayable(r, c)) continue;
                if (hasNeighbor(board, r, c, 2)) {
                    moves.add(new int[]{r, c});
                }
            }
        }

        moves.sort((a, b) -> {
            List<Integer> opponents = getOpponents(board, stoneColor);
            int sa = evaluatePosition(board, a[0], a[1], stoneColor);
            int sb = evaluatePosition(board, b[0], b[1], stoneColor);
            for (int opponent : opponents) {
                sa += evaluatePosition(board, a[0], a[1], opponent);
                sb += evaluatePosition(board, b[0], b[1], opponent);
            }
            return sb - sa;
        });

        return moves.size() > 15 ? moves.subList(0, 15) : moves;
    }

    private static boolean hasNeighbor(GameBoard board, int row, int col, int distance) {
        for (int dr = -distance; dr <= distance; dr++) {
            for (int dc = -distance; dc <= distance; dc++) {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr, c = col + dc;
                if (board.isValidPosition(r, c) && board.getStone(r, c) != GameBoard.EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }
}
