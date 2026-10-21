package com.gobang.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GobangAITest {

    @Test
    void choosesTheCenterOnAnEmptyBoard() {
        GameBoard board = new GameBoard();

        assertThat(GobangAI.generateMove(board, "EASY", GameBoard.WHITE))
                .containsExactly(7, 7);
    }

    @Test
    void takesAnImmediateWinningMove() {
        GameBoard board = new GameBoard();
        board.placeStone(7, 3, GameBoard.BLACK);
        for (int col = 4; col <= 7; col++) {
            board.placeStone(7, col, GameBoard.WHITE);
        }

        assertThat(GobangAI.generateMove(board, "EASY", GameBoard.WHITE))
                .containsExactly(7, 8);
    }

    @Test
    void blocksAnOpponentsOnlyWinningMove() {
        GameBoard board = new GameBoard();
        board.placeStone(7, 3, GameBoard.WHITE);
        for (int col = 4; col <= 7; col++) {
            board.placeStone(7, col, GameBoard.BLACK);
        }

        assertThat(GobangAI.generateMove(board, "EASY", GameBoard.WHITE))
                .containsExactly(7, 8);
    }

    @Test
    void moveSearchDoesNotMutateTheBoard() {
        GameBoard board = new GameBoard();
        board.placeStone(7, 7, GameBoard.BLACK);
        int[][] before = copy(board.getBoard());
        int moveCountBefore = board.moveCount();

        int[] move = GobangAI.generateMove(board, "MEDIUM", GameBoard.WHITE);

        assertThat(move).isNotNull();
        assertThat(board.getBoard()).isDeepEqualTo(before);
        assertThat(board.moveCount()).isEqualTo(moveCountBefore);
    }

    @Test
    void returnsNullWhenNoMoveExists() {
        GameBoard board = new GameBoard(3);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                board.placeStone(row, col, (row + col) % 2 == 0 ? GameBoard.BLACK : GameBoard.WHITE);
            }
        }

        assertThat(GobangAI.generateMove(board, "EASY", GameBoard.WHITE)).isNull();
    }

    @Test
    void dynamicAiOnlyChoosesAnUnlockedBlock() {
        GameBoard board = new GameBoard();
        board.initDynamicBlocks();

        int[] move = GobangAI.generateMoveForDynamic(board, "EASY", GameBoard.WHITE);

        assertThat(move).isNotNull();
        assertThat(move[0]).isBetween(6, 8);
        assertThat(move[1]).isBetween(6, 8);
    }

    @Test
    void dynamicHardSearchKeepsLockedBlocksAndSourceBoardUntouched() {
        GameBoard board = new GameBoard();
        board.initDynamicBlocks();
        board.placeStoneDynamic(7, 7, GameBoard.BLACK);
        int[][] original = copy(board.getBoard());

        int[] move = GobangAI.generateMoveForDynamic(board, "HARD", GameBoard.WHITE);

        assertThat(move).isNotNull();
        assertThat(board.isBlockPlayable(move[0], move[1])).isTrue();
        assertThat(board.getBoard()).isDeepEqualTo(original);
    }

    private int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i].clone();
        }
        return result;
    }
}
