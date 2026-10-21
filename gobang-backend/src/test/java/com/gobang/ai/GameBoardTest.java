package com.gobang.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GameBoardTest {

    static Stream<Arguments> winningLines() {
        return Stream.of(
                Arguments.of(7, 3, 0, 1),
                Arguments.of(3, 7, 1, 0),
                Arguments.of(3, 3, 1, 1),
                Arguments.of(3, 11, 1, -1));
    }

    @ParameterizedTest
    @MethodSource("winningLines")
    void detectsFiveStonesInEveryDirection(int startRow, int startCol, int rowStep, int colStep) {
        GameBoard board = new GameBoard();

        for (int i = 0; i < 5; i++) {
            assertThat(board.placeStone(
                    startRow + rowStep * i,
                    startCol + colStep * i,
                    GameBoard.BLACK)).isTrue();
        }

        assertThat(board.checkWin(startRow + rowStep * 4, startCol + colStep * 4)).isTrue();
    }

    @Test
    void fourStonesAreNotAWin() {
        GameBoard board = new GameBoard();
        for (int col = 4; col < 8; col++) {
            board.placeStone(7, col, GameBoard.WHITE);
        }

        assertThat(board.checkWin(7, 7)).isFalse();
    }

    @Test
    void invalidRepeatedAndNonStoneMovesDoNotChangeTheBoard() {
        GameBoard board = new GameBoard();

        assertThat(board.placeStone(-1, 0, GameBoard.BLACK)).isFalse();
        assertThat(board.placeStone(0, 0, GameBoard.EMPTY)).isFalse();
        assertThat(board.placeStone(0, 0, GameBoard.BLACK)).isTrue();
        assertThat(board.placeStone(0, 0, GameBoard.WHITE)).isFalse();

        assertThat(board.moveCount()).isEqualTo(1);
        assertThat(board.getStone(0, 0)).isEqualTo(GameBoard.BLACK);
    }

    @Test
    void undoRestoresTheLastPositionAndMoveCount() {
        GameBoard board = new GameBoard();
        board.placeStone(3, 4, GameBoard.BLACK);
        board.placeStone(4, 4, GameBoard.WHITE);

        assertThat(board.undo()).isTrue();
        assertThat(board.getStone(4, 4)).isEqualTo(GameBoard.EMPTY);
        assertThat(board.getStone(3, 4)).isEqualTo(GameBoard.BLACK);
        assertThat(board.moveCount()).isEqualTo(1);
    }

    @Test
    void longChainIsForbiddenAndSimulationLeavesBoardUntouched() {
        GameBoard board = new GameBoard();
        for (int col = 3; col <= 7; col++) {
            board.placeStone(7, col, GameBoard.BLACK);
        }

        assertThat(board.isForbidden(7, 8, GameBoard.BLACK)).isTrue();
        assertThat(board.getStone(7, 8)).isEqualTo(GameBoard.EMPTY);
        assertThat(board.moveCount()).isEqualTo(5);
    }

    @Test
    void forbiddenCheckNeverClearsAnOccupiedPosition() {
        GameBoard board = new GameBoard();
        board.placeStone(7, 7, GameBoard.WHITE);

        assertThat(board.isForbidden(7, 7, GameBoard.BLACK)).isFalse();
        assertThat(board.getStone(7, 7)).isEqualTo(GameBoard.WHITE);
        assertThat(board.moveCount()).isEqualTo(1);
    }

    @Test
    void outOfBoundsWinCheckIsSafe() {
        GameBoard board = new GameBoard();

        assertThat(board.checkWin(-1, 0)).isFalse();
        assertThat(board.checkWin(15, 0)).isFalse();
    }

    @Test
    void reportsACompletelyFilledBoard() {
        GameBoard board = new GameBoard(3);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                board.placeStone(row, col, (row + col) % 2 == 0 ? GameBoard.BLACK : GameBoard.WHITE);
            }
        }

        assertThat(board.isFull()).isTrue();
    }
}
