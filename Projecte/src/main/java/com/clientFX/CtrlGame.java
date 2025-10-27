package com.clientFX;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.json.JSONArray;
import org.json.JSONObject;

public class CtrlGame {

    @FXML
    private GridPane gridBoard;
    @FXML
    private Label lblTurn;
    @FXML
    private Label lblPlayer1;
    @FXML
    private Label lblPlayer2;

    private static final int ROWS = 6;
    private static final int COLS = 7;

    private String currentTurn = "";
    private String player1 = "";
    private String player2 = "";
    private boolean myTurn = false;

    @FXML
    public void initialize() {
        drawEmptyBoard();
        lblTurn.setText("Esperant l'inici de la partida...");
    }

    public void handleMessage(JSONObject msg) {
        String type = msg.getString("type");

        switch (type) {
            case "serverData" -> updateGameState(msg);
            case "move" -> updateBoardFromServer(msg);
            case "gameResult" -> showResult(msg);
        }
    }

    private void updateGameState(JSONObject msg) {
        JSONObject game = msg.getJSONObject("game");
        JSONArray clients = msg.getJSONArray("clientsList");

        if (clients.length() >= 2) {
            player1 = clients.getJSONObject(0).getString("name");
            player2 = clients.getJSONObject(1).getString("name");
            lblPlayer1.setText("🟡 " + player1);
            lblPlayer2.setText("🔴 " + player2);
        }

        currentTurn = game.getString("turn");
        myTurn = currentTurn.equals(Main.playerName);
        lblTurn.setText("Torn de: " + currentTurn);
        lblTurn.setTextFill(myTurn ? Color.GREEN : Color.GRAY);

        JSONArray board = game.getJSONArray("board");
        Platform.runLater(() -> drawBoard(board));
    }

    private void updateBoardFromServer(JSONObject msg) {
        JSONArray board = msg.getJSONArray("board");
        drawBoard(board);

        currentTurn = msg.getString("turn");
        myTurn = currentTurn.equals(Main.playerName);
        lblTurn.setText("Torn de: " + currentTurn);
        lblTurn.setTextFill(myTurn ? Color.GREEN : Color.GRAY);
    }

    private void drawEmptyBoard() {
        gridBoard.getChildren().clear();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Circle circle = createDisc(Color.WHITE);
                gridBoard.add(circle, c, r);
            }
        }
    }

    private void drawBoard(JSONArray board) {
        gridBoard.getChildren().clear();
        for (int r = 0; r < ROWS; r++) {
            JSONArray row = board.getJSONArray(r);
            for (int c = 0; c < COLS; c++) {
                String cell = row.getString(c);
                Color color = switch (cell) {
                    case "X" -> Color.RED;
                    case "O" -> Color.YELLOW;
                    default -> Color.WHITE;
                };
                Circle disc = createDisc(color);
                gridBoard.add(disc, c, r);
            }
        }
    }

    @FXML
    private void handleBoardClick(MouseEvent event) {
        if (!myTurn) return;

        int col = (int) (event.getX() / (gridBoard.getWidth() / COLS));

        JSONObject move = new JSONObject();
        move.put("type", "move");
        move.put("col", col);
        move.put("player", Main.playerName);

        Main.wsClient.sendMessage(move.toString());
    }

    private Circle createDisc(Color color) {
        Circle circle = new Circle(35);
        circle.setFill(color);
        circle.setStroke(Color.DARKBLUE);
        circle.setStrokeWidth(2);
        return circle;
    }

    private void showResult(JSONObject msg) {
        String winner = msg.getString("winner");
        if (winner.equals(Main.playerName)) {
            lblTurn.setText("🎉 Has guanyat!");
            lblTurn.setTextFill(Color.GREEN);
        } else if (winner.equals("draw")) {
            lblTurn.setText("🤝 Empat!");
            lblTurn.setTextFill(Color.ORANGE);
        } else {
            lblTurn.setText("❌ Has perdut!");
            lblTurn.setTextFill(Color.RED);
        }

        Main.pauseDuring(2500, () -> UtilsViews.setView("ViewResult"));
    }
}
