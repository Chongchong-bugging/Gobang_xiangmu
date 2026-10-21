package com.gobang.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialBoardModeTest {

    @Test
    void obstacleRejectsAStone() {
        GameBoard board = new GameBoard();
        board.getTerrain()[7][7] = GameBoard.OBSTACLE;

        assertThat(board.placeStoneObstacle(7, 7, GameBoard.BLACK)).isNull();
        assertThat(board.moveCount()).isZero();
    }

    @Test
    void waterSlidesToTheOnlyAvailableAdjacentPosition() {
        GameBoard board = new GameBoard();
        board.getTerrain()[0][0] = GameBoard.WATER;
        board.placeStone(1, 0, GameBoard.WHITE);

        int[] actualPosition = board.placeStoneObstacle(0, 0, GameBoard.BLACK);

        assertThat(actualPosition).containsExactly(0, 1);
        assertThat(board.getStone(0, 0)).isEqualTo(GameBoard.EMPTY);
        assertThat(board.getStone(0, 1)).isEqualTo(GameBoard.BLACK);
    }

    @Test
    void waterNeverSlidesOntoAnObstacle() {
        GameBoard board = new GameBoard();
        board.getTerrain()[0][0] = GameBoard.WATER;
        board.getTerrain()[0][1] = GameBoard.OBSTACLE;

        assertThat(board.placeStoneObstacle(0, 0, GameBoard.BLACK)).containsExactly(1, 0);
        assertThat(board.getStone(0, 1)).isEqualTo(GameBoard.EMPTY);
    }

    @Test
    void dynamicBoardStartsWithOnlyItsCenterBlockUnlocked() {
        GameBoard board = new GameBoard();
        board.initDynamicBlocks();

        assertThat(board.isBlockPlayable(7, 7)).isTrue();
        assertThat(board.isBlockPlayable(0, 0)).isFalse();
        assertThat(board.placeStoneDynamic(0, 0, GameBoard.BLACK)).isFalse();
        assertThat(board.placeStoneDynamic(7, 7, GameBoard.BLACK)).isTrue();
    }

    @Test
    void unlockingAValidBlockEnablesMovesInsideIt() {
        GameBoard board = new GameBoard();
        board.initDynamicBlocks();

        assertThat(board.unlockBlock(0, 0)).isTrue();
        assertThat(board.placeStoneDynamic(0, 0, GameBoard.BLACK)).isTrue();
        assertThat(board.unlockBlock(0, 0)).isFalse();
    }

    @Test
    void blockStateGetterReturnsADefensiveCopy() {
        GameBoard board = new GameBoard();
        board.initDynamicBlocks();

        int[][] states = board.getBlockStates();
        states[2][2] = GameBoard.BLOCK_LOCKED;

        assertThat(board.isBlockPlayable(7, 7)).isTrue();
    }
}
