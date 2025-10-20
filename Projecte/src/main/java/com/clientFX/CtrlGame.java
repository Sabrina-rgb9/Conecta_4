package com.clientFX;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class CtrlGame {

    @FXML private Canvas canvas;
    @FXML private Label lblStatus;
    @FXML private Label lblTurn;

    private String clientName = "";
    private String role = "";
    private JSONObject gameState;
    private Map<String, double[]> opponentMouse = new HashMap<>();

    private final int rows = 6;
    private final int cols = 7;
    private final double cellSize = 80;

    @FXML
    private void initialize() {
        canvas.setWidth(cols * cellSize);
        canvas.setHeight(rows * cellSize);
        canvas.setOnMouseMoved(this::handleHover);
        canvas.setOnMouseClicked(this::handleClick);
        redraw(); // Dibuixa tauler buit inicialment
    }

    public void handleMessage(JSONObject msg) {
        if ("serverData".equals(msg.getString("type"))) {
            Platform.runLater(() -> {
                clientName = msg.getString("clientName");
                role = msg.getString("role");
                gameState = msg.getJSONObject("game");
                updateLabels();
                redraw();
            });
        } else if ("clientMouseMoving".equals(msg.getString("type"))) {
            String player = msg.getString("player");
            if (!player.equals(clientName)) {
                Platform.runLater(() -> {
                    opponentMouse.put(player, new double[]{msg.getDouble("x"), msg.getDouble("y")});
                    redraw();
                });
            }
        }
    }

    private void updateLabels() {
        if (gameState == null) return;
        
        String status = gameState.getString("status");
        if ("win".equals(status)) {
            String winner = gameState.getString("winner");
            lblStatus.setText(winner.equals(clientName) ? "Has guanyat!" : "Has perdut :(");
        } else if ("draw".equals(status)) {
            lblStatus.setText("Empat!");
        } else {
            lblStatus.setText("En joc...");
        }

        String turn = gameState.getString("turn");
        lblTurn.setText(clientName.equals(turn) ? "Et toca jugar" : "Torn de: " + turn);
        canvas.setDisable(!clientName.equals(turn));
    }

    private void handleHover(MouseEvent e) {
        sendMouse(e.getX(), e.getY());
    }

    private void handleClick(MouseEvent e) {
        if (gameState == null) return; // Evita NPE
        
        String turn = gameState.getString("turn");
        if (!clientName.equals(turn)) return;
        
        int col = (int) (e.getX() / cellSize);
        if (col >= 0 && col < cols) {
            sendPlay(col);
        }
    }

    private void sendPlay(int col) {
        JSONObject obj = new JSONObject();
        obj.put("type", "clientPlay");
        obj.put("col", col);
        Main.wsClient.safeSend(obj.toString());
    }

    private void sendMouse(double x, double y) {
        JSONObject obj = new JSONObject();
        obj.put("type", "clientMouseMoving");
        obj.put("x", x);
        obj.put("y", y);
        Main.wsClient.safeSend(obj.toString());
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Dibuixa tauler buit
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                gc.setFill(Color.WHITE);
                gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
            }
        }

        // Dibuixa fitxes si hi ha estat
        if (gameState != null) {
            JSONArray board = gameState.getJSONArray("board");
            for (int r = 0; r < rows; r++) {
                JSONArray row = board.getJSONArray(r);
                for (int c = 0; c < cols; c++) {
                    String val = row.getString(c);
                    if ("R".equals(val)) {
                        gc.setFill(Color.RED);
                    } else if ("Y".equals(val)) {
                        gc.setFill(Color.YELLOW);
                    } else {
                        continue;
                    }
                    gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                }
            }
        }

        // Dibuixa punter del contrincant
        for (double[] pos : opponentMouse.values()) {
            gc.setStroke(Color.GRAY);
            gc.setLineWidth(2);
            gc.strokeLine(pos[0], 0, pos[0], canvas.getHeight());
            gc.setLineWidth(1);
        }
    }
}