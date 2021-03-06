package de.golfgl.lightblocks.server;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.Queue;

import javax.annotation.Nullable;

import de.golfgl.lightblocks.model.GameModel;
import de.golfgl.lightblocks.model.GameScore;
import de.golfgl.lightblocks.model.IGameModelListener;
import de.golfgl.lightblocks.model.ServerMultiplayerModel;
import de.golfgl.lightblocks.model.Tetromino;
import de.golfgl.lightblocks.multiplayer.ai.ArtificialPlayer;
import de.golfgl.lightblocks.server.model.InGameMessage;
import de.golfgl.lightblocks.server.model.MatchInfo;
import de.golfgl.lightblocks.state.InitGameParameters;

public class Match {
    public static final float WAIT_TIME_GAME_OVER = 5f;
    private final InitGameParameters gameParams;
    private final LightblocksServer server;
    private final Queue<InGameMessage> p1Queue = new Queue<>();
    private final Queue<InGameMessage> p2Queue = new Queue<>();
    private Player player1;
    private Player player2;
    private ServerMultiplayerModel gameModel;
    private float waitGameOver = WAIT_TIME_GAME_OVER;

    public Match(LightblocksServer server) {
        this.server = server;
        gameParams = new InitGameParameters();
        gameParams.setBeginningLevel(server.serverConfig.beginningLevel);
        int modeType = server.serverConfig.modeType;
        if (modeType == InitGameParameters.TYPE_MIX) {
            modeType = MathUtils.randomBoolean() ? InitGameParameters.TYPE_CLASSIC : InitGameParameters.TYPE_MODERN;
        }
        gameParams.setModeType(modeType);
    }

    public void update(float delta) {
        if (getConnectedPlayerNum() == 0) {
            return;
        }

        // update game model
        if (gameModel == null) {
            initGameModel();
            sendFullInformation();
        }

        gameModel.setAiEnabled(player1 == null);
        gameModel.getSecondGameModel().setAiEnabled(player2 == null);

        // process the queues
        synchronized (p1Queue) {
            if (player1 == null)
                p1Queue.clear();
            else
                processQueue(gameModel, p1Queue);
        }
        synchronized (p2Queue) {
            if (player2 == null)
                p2Queue.clear();
            else
                processQueue(gameModel.getSecondGameModel(), p2Queue);
        }

        gameModel.update(delta);

        if (gameModel.isGameOver()) {
            if (waitGameOver > 0)
                waitGameOver = waitGameOver - delta;
            else {
                gameModel = null;
                waitGameOver = WAIT_TIME_GAME_OVER;
            }
        }
    }

    private void processQueue(ServerMultiplayerModel gameModel, Queue<InGameMessage> queue) {
        while (!queue.isEmpty()) {
            InGameMessage igm = queue.removeFirst();
            switch (igm.message) {
                case "SML":
                    gameModel.inputStartMoveHorizontal(null, true);
                    break;
                case "SMR":
                    gameModel.inputStartMoveHorizontal(null, false);
                    break;
                case "SMH":
                    gameModel.inputEndMoveHorizontal(null, true);
                    gameModel.inputEndMoveHorizontal(null, false);
                    break;
                case "HAT":
                    gameModel.inputHoldActiveTetromino(null);
                    break;
                case "ROR":
                    gameModel.inputRotate(null, true);
                    break;
                case "ROL":
                    gameModel.inputRotate(null, false);
                    break;
                case "DRN":
                    gameModel.inputSetSoftDropFactor(null, GameModel.FACTOR_NO_DROP);
                    break;
                case "DRS":
                    gameModel.inputSetSoftDropFactor(null, GameModel.FACTOR_SOFT_DROP);
                    break;
                case "DRH":
                    gameModel.inputSetSoftDropFactor(null, GameModel.FACTOR_HARD_DROP);
                    break;
                default:
                    Gdx.app.log("Match", "Unrecognized game message: " + igm.message);
            }
        }
    }

    private void initGameModel() {
        gameModel = new ServerMultiplayerModel();
        gameModel.startNewGame(gameParams);
        ServerMultiplayerModel secondGameModel = gameModel.getSecondGameModel();

        gameModel.setAiPlayer(new ArtificialPlayer(gameModel, secondGameModel));
        secondGameModel.setAiPlayer(new ArtificialPlayer(secondGameModel, gameModel));

        gameModel.setUserInterface(new Listener(true));
        secondGameModel.setUserInterface(new Listener(false));
    }

    public boolean connectPlayer(Player player) {
        synchronized (this) {
            if (player1 == null) {
                player1 = player;
                return true;
            }
            if (player2 == null) {
                player2 = player;
                return true;
            }
            return false;
        }
    }

    public void playerDisconnected(Player player) {
        synchronized (this) {
            if (player == player1) {
                player1 = null;
            } else if (player == player2) {
                player2 = null;
            }
        }
        sendFullInformation();
    }

    public int getConnectedPlayerNum() {
        return (player1 != null ? 1 : 0) + (player2 != null ? 1 : 0);
    }

    public void sendFullInformation() {
        if (gameModel == null || getConnectedPlayerNum() == 0)
            return;

        // send the full match information to the players after a connect or disconnect
        // gameboard, score, nick names, ...
        MatchInfo matchInfo1 = new MatchInfo();
        MatchInfo matchInfo2 = new MatchInfo();

        MatchInfo.PlayerInfo player1 = new MatchInfo.PlayerInfo();
        MatchInfo.PlayerInfo player2 = new MatchInfo.PlayerInfo();
        matchInfo1.player1 = player1;
        matchInfo1.player2 = player2;
        matchInfo1.isModern = gameModel.isModernRotation();
        matchInfo2.player1 = player2;
        matchInfo2.player2 = player1;
        matchInfo2.isModern = gameModel.isModernRotation();

        player1.score = new MatchInfo.ScoreInfo(gameModel.getScore());
        player2.score = new MatchInfo.ScoreInfo(gameModel.getSecondGameModel().getScore());

        player1.nickname = this.player1 != null ? this.player1.nickName : "AI";
        player2.nickname = this.player2 != null ? this.player2.nickName : "AI";

        player1.gameboard = gameModel.getSerializedGameboard();
        player2.gameboard = gameModel.getSecondGameModel().getSerializedGameboard();

        player1.holdPiece = serializeTetromino(gameModel.getHoldTetromino(), true);
        player2.holdPiece = serializeTetromino(gameModel.getSecondGameModel().getHoldTetromino(), true);

        player1.activePiece = serializeTetromino(gameModel.getActiveTetromino(), false);
        player2.activePiece = serializeTetromino(gameModel.getSecondGameModel().getActiveTetromino(), false);

        player1.nextPiece = serializeTetromino(gameModel.getNextTetromino(), true);
        player2.nextPiece = serializeTetromino(gameModel.getSecondGameModel().getNextTetromino(), true);

        if (this.player1 != null)
            this.player1.send(server.serializer.serialize(matchInfo1));
        if (this.player2 != null)
            this.player2.send(server.serializer.serialize(matchInfo2));
    }

    protected String serializeTetromino(Tetromino tetromino, boolean relative) {
        if (tetromino == null)
            return null;

        StringBuilder builder = new StringBuilder();
        sendPiecePositions(relative ? tetromino.getRelativeBlockPositions() : tetromino.getCurrentBlockPositions(), builder);
        builder.append(tetromino.getTetrominoType());
        return builder.toString();
    }

    protected void sendPiecePositions(Integer[][] piecePos, StringBuilder builder) {
        for (Integer[] piece : piecePos) {
            builder.append(piece[0]).append('-').append(piece[1]).append('-');
        }
    }

    public void gotMessage(Player player, InGameMessage igm) {
        if (player == player1)
            synchronized (p1Queue) {
                p1Queue.addLast(igm);
            }
        else if (player == player2)
            synchronized (p2Queue) {
                p2Queue.addLast(igm);
            }
    }

    private class Listener implements IGameModelListener {
        private final boolean first;
        private int lastGarbageAmountReported = 0;
        private String lastSentScore;

        public Listener(boolean first) {
            this.first = first;
        }

        private void sendPlayer(String msg) {
            if (first) {
                if (player1 != null)
                    player1.send("Y" + msg);
                if (player2 != null)
                    player2.send("O" + msg);
            } else {
                if (player1 != null)
                    player1.send("O" + msg);
                if (player2 != null)
                    player2.send("Y" + msg);
            }
        }

        private boolean hasPlayer() {
            return player1 != null || player2 != null;
        }

        @Override
        public void insertNewBlock(int x, int y, int blockType) {
            // only used on game start, sendFullInformation will handle this
        }

        @Override
        public void moveTetro(Integer[][] v, int dx, int dy, int ghostPieceDistance) {
            if (hasPlayer()) {
                sendPlayer("MOV|" + dx + "|" + dy + "|" + ghostPieceDistance);
            }
        }

        @Override
        public void rotateTetro(Integer[][] vOld, Integer[][] vNew, int ghostPieceDistance) {
            if (hasPlayer()) {
                StringBuilder builder = new StringBuilder();
                builder.append("ROT-");
                sendPiecePositions(vNew, builder);
                builder.append(ghostPieceDistance);
                sendPlayer(builder.toString());
            }
        }

        @Override
        public void clearAndInsertLines(IntArray linesToRemove, boolean special, int[] garbageHolePosition) {
            int linesToInsert = (garbageHolePosition == null ? 0 : garbageHolePosition.length);
            if (linesToRemove.size <= 0 && linesToInsert <= 0)
                return;

            if (hasPlayer()) {
                StringBuilder builder = new StringBuilder();
                builder.append("CLR-");
                for (int i = 0; i < linesToRemove.size; i++) {
                    builder.append(linesToRemove.get(i));
                    if (i < linesToRemove.size - 1)
                        builder.append('|');
                }
                builder.append('-').append(special ? 'S' : 'N');
                for (int gap : garbageHolePosition) {
                    builder.append('|').append(gap);
                }
                sendPlayer(builder.toString());
            }
        }

        @Override
        public void markAndMoveFreezedLines(boolean playSoundAndMove, IntArray removedLines, IntArray fullLines) {
            // not supported
        }

        @Override
        public void setGameOver() {
            if (hasPlayer()) {
                sendPlayer("GOV");
            }
        }

        @Override
        public void showNextTetro(Integer[][] relativeBlockPositions, int blockType) {
            if (hasPlayer()) {
                StringBuilder builder = new StringBuilder();
                builder.append("NXT-");
                sendPiecePositions(relativeBlockPositions, builder);
                builder.append(blockType);
                sendPlayer(builder.toString());
            }
        }

        @Override
        public void activateNextTetro(Integer[][] boardBlockPositions, int blockType, int ghostPieceDistance) {
            if (hasPlayer()) {
                StringBuilder builder = new StringBuilder();
                builder.append("ANT-");
                sendPiecePositions(boardBlockPositions, builder);
                builder.append(blockType).append('-').append(ghostPieceDistance);
                sendPlayer(builder.toString());
            }
        }

        @Override
        public void swapHoldAndActivePiece(Integer[][] newHoldPiecePositions, Integer[][] oldActivePiecePositions, Integer[][] newActivePiecePositions, int ghostPieceDistance, int holdBlockType) {
            if (hasPlayer()) {
                StringBuilder builder = new StringBuilder();
                builder.append("HLD-");
                sendPiecePositions(newHoldPiecePositions, builder);
                builder.append(ghostPieceDistance).append('-');
                if (newActivePiecePositions != null) {
                    sendPiecePositions(newActivePiecePositions, builder);
                }
                sendPlayer(builder.toString());
            }
        }

        @Override
        public void pinTetromino(Integer[][] currentBlockPositions) {
            if (hasPlayer()) {
                sendPlayer("PIN");
            }
        }

        @Override
        public void updateScore(GameScore score, int gainedScore) {
            if (hasPlayer()) {
                MatchInfo.ScoreInfo scoreInfo = new MatchInfo.ScoreInfo(score);
                String serialized = server.serializer.serialize(scoreInfo);
                if (!serialized.equals(lastSentScore)) {
                    lastSentScore = serialized;
                    sendPlayer(serialized);
                }
            }
        }

        @Override
        public void markConflict(int x, int y) {
            if (hasPlayer()) {
                sendPlayer("CNF-" + x + "-" + y);
            }
        }

        @Override
        public void showMotivation(MotivationTypes achievement, @Nullable String extra) {
            String motivationMessage;
            switch (achievement) {
                case tSpin:
                    motivationMessage = "T-Spin";
                    break;
                case boardCleared:
                    motivationMessage = "Clean Complete";
                    break;
                case gameOver:
                    motivationMessage = "Game over";
                    break;
                case gameWon:
                    motivationMessage = "Won!";
                    break;
                case prepare:
                    motivationMessage = "Prepare to play!";
                    break;
                default:
                    motivationMessage = null;
            }
            if (motivationMessage != null)
                sendPlayer("MTV-" + motivationMessage);
        }

        @Override
        public void showGarbageAmount(int lines) {
            if (hasPlayer()) {
                if (lines != lastGarbageAmountReported) {
                    sendPlayer("GBG-" + lines);
                    lastGarbageAmountReported = lines;
                }
            } else {
                lastGarbageAmountReported = 0;
            }
        }

        @Override
        public void showComboHeight(int comboHeight) {

        }

        @Override
        public void emphasizeTimeLabel() {
            // not used
        }
    }
}
