package com.gobang.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 五子棋棋盘 - 支持自定义尺寸 (默认15x15, 三人模式25x25)
 * 0=空, 1=黑子(玩家), 2=白子(AI), 3=灰子(第三人)
 * 4=假山(OBSTACLE, 不可落子, 四邻全满消除), 5=水潭(WATER, 可落子但随机滑动)
 */
public class GameBoard {
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;
    public static final int GRAY = 3;
    public static final int OBSTACLE = 4;  // 假山
    public static final int WATER = 5;     // 水潭

    private final int size;
    private int[][] board;
    private int[][] terrain;     // 地形数组: 0=无, 4=假山, 5=水潭
    private List<int[]> history;   // 落子历史 [row, col]
    private List<TerrainRecord> terrainHistory;  // 地形变化历史
    private int moveCount;
    private boolean paused;
    private final Random random = new Random();

    /** 地形变化记录 */
    public static class TerrainRecord {
        public int row, col, oldTerrain;
        public TerrainRecord(int row, int col, int oldTerrain) {
            this.row = row; this.col = col; this.oldTerrain = oldTerrain;
        }
    }

    /** 默认15x15棋盘 */
    public GameBoard() {
        this(15);
    }

    /** 自定义尺寸棋盘 */
    public GameBoard(int size) {
        this.size = size;
        board = new int[size][size];
        terrain = new int[size][size];   // 默认全0=无地形
        history = new ArrayList<>();
        terrainHistory = new ArrayList<>();
        moveCount = 0;
        paused = false;
    }

    public GameBoard(GameBoard other) {
        this.size = other.size;
        board = new int[size][size];
        terrain = new int[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(other.board[i], 0, board[i], 0, size);
            System.arraycopy(other.terrain[i], 0, terrain[i], 0, size);
        }
        history = new ArrayList<>(other.history);
        terrainHistory = new ArrayList<>(other.terrainHistory);
        moveCount = other.moveCount;
        paused = other.paused;
    }

    /** 获取棋盘尺寸 */
    public int getSize() { return size; }

    /**
     * 落子（普通模式）。障碍物模式请使用 placeStoneObstacle()。
     */
    public boolean placeStone(int row, int col, int stone) {
        if (!isValidPosition(row, col) || board[row][col] != EMPTY) return false;
        board[row][col] = stone;
        history.add(new int[]{row, col});
        moveCount++;
        return true;
    }

    /**
     * 障碍物模式落子：检查地形后落子。
     * 假山处不可落子；水潭处落子后棋子随机滑动到相邻空位。
     * @return 实际落子位置 [row, col]，落子失败返回 null
     */
    public int[] placeStoneObstacle(int row, int col, int stone) {
        if (!isValidPosition(row, col)) return null;

        // 假山处不能落子
        if (terrain[row][col] == OBSTACLE) return null;

        // 位置已有棋子，不能落子
        if (board[row][col] != EMPTY) return null;

        // 水潭处理：落子后随机滑动到相邻空位
        if (terrain[row][col] == WATER) {
            int[] slidePos = findRandomAdjacentEmpty(row, col);
            if (slidePos != null) {
                // 滑动到相邻空位
                board[slidePos[0]][slidePos[1]] = stone;
                history.add(new int[]{slidePos[0], slidePos[1]});
                moveCount++;
                return slidePos;
            }
            // 无相邻空位，直接落在水潭位置
        }

        // 正常落子
        board[row][col] = stone;
        history.add(new int[]{row, col});
        moveCount++;
        return new int[]{row, col};
    }

    /**
     * 在水潭位置落子后，随机滑到相邻空位
     */
    private int[] findRandomAdjacentEmpty(int row, int col) {
        List<int[]> empties = new ArrayList<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0], nc = col + d[1];
            if (isValidPosition(nr, nc) && board[nr][nc] == EMPTY) {
                empties.add(new int[]{nr, nc});
            }
        }
        if (empties.isEmpty()) return null;
        return empties.get(random.nextInt(empties.size()));
    }

    /**
     * 检查并移除被包围的地形（四邻域全被棋子填满）
     * 在每次落子后调用，检查落子位置四邻的地形
     */
    public List<TerrainRecord> checkAndRemoveObstacles(int row, int col) {
        List<TerrainRecord> removed = new ArrayList<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0], nc = col + d[1];
            if (!isValidPosition(nr, nc)) continue;
            int t = terrain[nr][nc];
            if (t != OBSTACLE && t != WATER) continue;

            // 检查该地形的四邻是否全被棋子填满
            if (isSurrounded(nr, nc)) {
                removed.add(new TerrainRecord(nr, nc, t));
                terrain[nr][nc] = EMPTY;
                terrainHistory.add(new TerrainRecord(nr, nc, t));
            }
        }
        return removed;
    }

    /**
     * 检查某位置的四个邻居是否全被棋子占据
     */
    private boolean isSurrounded(int row, int col) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0], nc = col + d[1];
            if (!isValidPosition(nr, nc)) continue;
            if (board[nr][nc] == EMPTY) return false;
        }
        return true;
    }

    /**
     * 在随机空位生成地形
     * @param maxCount 最多生成数量，实际随机 1~maxCount
     */
    public List<TerrainRecord> spawnTerrain(int maxCount) {
        List<TerrainRecord> spawned = new ArrayList<>();
        if (maxCount <= 0) return spawned;

        // 收集所有可用空位（无棋子且无地形）
        List<int[]> available = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] == EMPTY && terrain[r][c] == EMPTY) {
                    available.add(new int[]{r, c});
                }
            }
        }
        if (available.isEmpty()) return spawned;

        // 随机数量 (1 ~ maxCount，但不超过可用空位数)
        int count = Math.min(1 + random.nextInt(maxCount), available.size());

        // Fisher-Yates 部分洗牌
        for (int i = 0; i < count; i++) {
            int idx = i + random.nextInt(available.size() - i);
            int[] temp = available.get(i);
            available.set(i, available.get(idx));
            available.set(idx, temp);

            int[] pos = available.get(i);
            // 随机类型：假山 或 水潭（50/50）
            int type = random.nextBoolean() ? OBSTACLE : WATER;
            terrain[pos[0]][pos[1]] = type;
            spawned.add(new TerrainRecord(pos[0], pos[1], EMPTY));
            terrainHistory.add(new TerrainRecord(pos[0], pos[1], EMPTY));
        }
        return spawned;
    }

    public boolean undo() {
        if (history.isEmpty()) return false;
        int[] last = history.remove(history.size() - 1);
        board[last[0]][last[1]] = EMPTY;
        moveCount--;
        return true;
    }

    /**
     * 悔棋（含地形回退）。障碍物模式使用此方法。
     * @param terrainStepsToUndo 需要回退的地形变化步数
     */
    public boolean undoWithTerrain(int terrainStepsToUndo) {
        if (history.isEmpty()) return false;
        int[] last = history.remove(history.size() - 1);
        board[last[0]][last[1]] = EMPTY;
        moveCount--;

        // 回退地形变化
        for (int i = 0; i < terrainStepsToUndo && !terrainHistory.isEmpty(); i++) {
            TerrainRecord rec = terrainHistory.remove(terrainHistory.size() - 1);
            terrain[rec.row][rec.col] = rec.oldTerrain;
        }
        return true;
    }

    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    public int moveCount() { return moveCount; }
    public int[][] getBoard() { return board; }
    public int[][] getTerrain() { return terrain; }

    public int getStone(int row, int col) {
        if (!isValidPosition(row, col)) return -1;
        return board[row][col];
    }

    public int getTerrainAt(int row, int col) {
        if (!isValidPosition(row, col)) return -1;
        return terrain[row][col];
    }

    /**
     * 判断某位置是否可落子（含地形检查）
     */
    public boolean isPlayable(int row, int col) {
        if (!isValidPosition(row, col)) return false;
        if (board[row][col] != EMPTY) return false;
        if (terrain[row][col] == OBSTACLE) return false;  // 假山不可落子
        return true;
    }

    public boolean isValidPosition(int row, int col) {
        return row >= 0 && row < size && col >= 0 && col < size;
    }

    /**
     * 普通模式满盘判断
     */
    public boolean isFull() {
        return moveCount >= size * size;
    }

    /**
     * 障碍物模式满盘判断（排除假山占据的不可落子位置）
     */
    public boolean isFullObstacle() {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (board[r][c] == EMPTY && terrain[r][c] != OBSTACLE) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 复制棋盘（含地形）
     */
    public int[][] copyTerrain() {
        int[][] copy = new int[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(terrain[i], 0, copy[i], 0, size);
        }
        return copy;
    }

    /**
     * 检查指定位置是否形成五连
     */
    public boolean checkWin(int row, int col) {
        int stone = board[row][col];
        if (stone == EMPTY) return false;

        // 四个方向的增量
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : directions) {
            int count = 1;
            // 正方向
            for (int i = 1; i < 5; i++) {
                int r = row + d[0] * i, c = col + d[1] * i;
                if (isValidPosition(r, c) && board[r][c] == stone) count++; else break;
            }
            // 反方向
            for (int i = 1; i < 5; i++) {
                int r = row - d[0] * i, c = col - d[1] * i;
                if (isValidPosition(r, c) && board[r][c] == stone) count++; else break;
            }
            if (count >= 5) return true;
        }
        return false;
    }

    /**
     * 检查禁手规则 - 仅黑子需要检查
     * 禁手包括: 长连(>5)、双三、双四
     */
    public boolean isForbidden(int row, int col, int stone) {
        if (stone != BLACK) return false;

        // 模拟落子来检查
        board[row][col] = BLACK;

        // 长连检查: 六连或以上
        if (checkLongChain(row, col)) {
            board[row][col] = EMPTY;
            return true;
        }

        // 统计活三和活四数量
        int openThrees = countOpenThrees(row, col);
        int openFours = countOpenFours(row, col);

        board[row][col] = EMPTY;

        return openThrees >= 2 || openFours >= 2;
    }

    private boolean checkLongChain(int row, int col) {
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : directions) {
            int count = 1;
            for (int i = 1; i <= 5; i++) {
                int r = row + d[0] * i, c = col + d[1] * i;
                if (isValidPosition(r, c) && board[r][c] == BLACK) count++; else break;
            }
            for (int i = 1; i <= 5; i++) {
                int r = row - d[0] * i, c = col - d[1] * i;
                if (isValidPosition(r, c) && board[r][c] == BLACK) count++; else break;
            }
            if (count > 5) return true;
        }
        return false;
    }

    private int countOpenThrees(int row, int col) {
        int count = 0;
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : directions) {
            if (isOpenThree(row, col, d)) count++;
        }
        return count;
    }

    private int countOpenFours(int row, int col) {
        int count = 0;
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : directions) {
            if (isOpenFour(row, col, d)) count++;
        }
        return count;
    }

    private boolean isOpenThree(int row, int col, int[] dir) {
        // 简化活三检查: 某方向上有3连且两端空
        int count = 1;
        int[] ends = new int[4]; // 两端位置

        for (int i = 1; i <= 3; i++) {
            int r = row + dir[0] * i, c = col + dir[1] * i;
            if (isValidPosition(r, c) && board[r][c] == BLACK) count++;
            else { ends[0] = r; ends[1] = c; break; }
        }
        for (int i = 1; i <= 3; i++) {
            int r = row - dir[0] * i, c = col - dir[1] * i;
            if (isValidPosition(r, c) && board[r][c] == BLACK) count++;
            else { ends[2] = r; ends[3] = c; break; }
        }

        if (count != 3) return false;

        return isValidPosition(ends[0], ends[1]) && board[ends[0]][ends[1]] == EMPTY
                && isValidPosition(ends[2], ends[3]) && board[ends[2]][ends[3]] == EMPTY;
    }

    private boolean isOpenFour(int row, int col, int[] dir) {
        int count = 1;
        int[] ends = new int[4];

        for (int i = 1; i <= 4; i++) {
            int r = row + dir[0] * i, c = col + dir[1] * i;
            if (isValidPosition(r, c) && board[r][c] == BLACK) count++;
            else { ends[0] = r; ends[1] = c; break; }
        }
        for (int i = 1; i <= 4; i++) {
            int r = row - dir[0] * i, c = col - dir[1] * i;
            if (isValidPosition(r, c) && board[r][c] == BLACK) count++;
            else { ends[2] = r; ends[3] = c; break; }
        }

        if (count != 4) return false;

        return isValidPosition(ends[0], ends[1]) && board[ends[0]][ends[1]] == EMPTY
                && isValidPosition(ends[2], ends[3]) && board[ends[2]][ends[3]] == EMPTY;
    }
}
