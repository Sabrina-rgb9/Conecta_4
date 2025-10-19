package com.clientFX;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class CtrlGame {

    @FXML private Canvas canvas;
    @FXML private Label lblStatus;
    @FXML private Label lblTurn;
    @FXML private Pane root;

    private String clientName = "";
    private String role = ""; // "R" o "Y"
    private JSONObject gameState;
    private double mouseX, mouseY;
    private Map<String, double[]> opponentMouse = new HashMap<>();

    private final int rows = 6;
    private final int cols = 7;
    private final double cellSize = 80;

    private int lastMoveRow = -1;
    private int lastMoveCol = -1;
    private String lastWinner = "";

    // ---------------- Gestió de missatges ----------------
    public void handleMessage(JSONObject msg) {
        if ("serverData".equals(msg.getString("type"))) {
            Platform.runLater(() -> {
                clientName = msg.getString("clientName");
                role = msg.getString("role");
                gameState = msg.getJSONObject("game");

                // Actualitzar etiquetes
                String turn = gameState.getString("turn");
                lblTurn.setText(turn);
                String status = gameState.getString("status");
                if ("win".equals(status)) {
                    String winner = gameState.getString("winner");
                    lblStatus.setText(winner.equals(clientName) ? "Has guanyat!" : "Has perdut :(");
                    lastWinner = winner;
                } else if ("draw".equals(status)) {
                    lblStatus.setText("Empat!");
                } else {
                    lblStatus.setText("En joc...");
                }

                // Comprovar si hi ha una nova jugada per animar
                if (gameState.has("lastMove") && !gameState.isNull("lastMove")) {
                    JSONObject lastMove = gameState.getJSONObject("lastMove");
                    int newRow = lastMove.getInt("row");
                    int newCol = lastMove.getInt("col");
                    if (newRow != lastMoveRow || newCol != lastMoveCol) {
                        lastMoveRow = newRow;
                        lastMoveCol = newCol;
                        animateDrop(newCol, newRow);
                    }
                }

                // Desactivar interacció si no és el teu torn
                boolean myTurn = clientName.equals(turn);
                canvas.setDisable(!myTurn);
                if (myTurn) {
                    lblTurn.setText("Et toca jugar");
                }

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

    // ---------------- Animació de caiguda ----------------
    private void animateDrop(int col, int targetRow) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double startX = col * cellSize + cellSize / 2.0;
        double startY = -cellSize / 2.0; // Comença des de dalt
        double endY = targetRow * cellSize + cellSize / 2.0;

        // Variable per l'animació
        double[] currentY = {startY};

        Timeline timeline = new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(500), e -> {
            currentY[0] = endY; // Salt directe (o pots fer interpolació)
            redraw(); // Redibuixa l'estat final
        }));

        // Render loop per l'animació (opcional, per efecte fluid)
        Timeline renderLoop = new Timeline(new KeyFrame(Duration.millis(16), e -> {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            redraw(); // Dibuixa tauler
            // Dibuixa fitxa animada
            String piece = getPieceAt(targetRow, col);
            gc.setFill("R".equals(piece) ? Color.RED : Color.YELLOW);
            double y = currentY[0] + (endY - currentY[0]) * Math.min(1, timeline.getCurrentRate()); // Aproximació
            gc.fillOval(startX - cellSize/2 + 5, y - cellSize/2 + 5, cellSize - 10, cellSize - 10);
        }));
        renderLoop.setCycleCount(1);
        timeline.play();
    }

    // ---------------- Renderitzat ----------------
    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Fons
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Tauler buit
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                gc.setStroke(Color.GRAY);
                gc.setFill(Color.WHITE);
                gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                gc.strokeOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
            }
        }

        // Fitxes del tauler
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
                    // Comprovar si és part de la victòria
                    if (isWinningCell(r, c)) {
                        gc.setGlobalAlpha(0.7);
                        gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                        gc.setGlobalAlpha(1.0);
                        // Efecte d'il·luminació
                        gc.setStroke(Color.WHITE);
                        gc.setLineWidth(3);
                        gc.strokeOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                        gc.setLineWidth(1);
                    } else {
                        gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                    }
                }
            }
        }

        // Punter del contrincant
        for (double[] pos : opponentMouse.values()) {
            gc.setStroke(Color.GRAY);
            gc.setLineWidth(2);
            gc.strokeLine(pos[0], 0, pos[0], canvas.getHeight());
            gc.setLineWidth(1);
        }
    }

    private String getPieceAt(int row, int col) {
        if (gameState == null) return " ";
        JSONArray board = gameState.getJSONArray("board");
        return board.getJSONArray(row).getString(col);
    }

    private boolean isWinningCell(int row, int col) {
        if (!"win".equals(gameState.getString("status"))) return false;
        // Aquí hauríem de rebre les cel·les guanyadores del servidor
        // Per simplicitat, comprovem si la cel·la forma part d'una línia de 4
        // (En un entorn real, el servidor hauria d'enviar les coordenades guanyadores)
        return false; // Placeholder
    }

    @FXML
    private void initialize() {
        canvas.setWidth(cols * cellSize);
        canvas.setHeight(rows * cellSize);

        canvas.setOnMouseMoved(this::handleHover);
        canvas.setOnMouseClicked(this::handleClick);
    }

    private void handleHover(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        sendMouse(mouseX, mouseY);
    }

    private void handleClick(MouseEvent e) {
        int col = (int) (e.getX() / cellSize);
        if (col < 0 || col >= cols) return; // Validació de columna

        // Només enviem si és el nostre torn (doble comprovació)
        if (gameState != null && clientName.equals(gameState.getString("turn"))) {
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
}