package com.clientFX;

import javafx.animation.KeyFrame;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.shared.GameObject;

public class CtrlGame {

    @FXML private Canvas canvas;
    @FXML private Label lblPlayerName;
    @FXML private Label lblYourRole;
    @FXML private Label lblOpponentName;
    @FXML private Label lblOpponentRole;
    @FXML private Label lblTurnIndicator;
    @FXML private Pane paneYourPieces;
    @FXML private Pane paneOpponentPieces;

    private String clientName = "";
    private String role = "";
    private JSONObject gameState;
    private Map<String, double[]> opponentMouse = new HashMap<>();
    private List<GameObject> availablePieces = new ArrayList<>();
    private boolean[][] winningCells = new boolean[6][7];

    private final int rows = 6;
    private final int cols = 7;
    private final double cellSize = 80;
    private int lastMoveRow = -1;
    private int lastMoveCol = -1;
    private Timeline dropAnimation;

    @FXML
    public void initialize() {
        canvas.setWidth(cols * cellSize);
        canvas.setHeight(rows * cellSize);

        canvas.setOnMouseMoved(this::handleHover);
        canvas.setOnMouseClicked(this::handleClick);
        canvas.widthProperty().addListener((obs, old, newVal) -> redraw());
        canvas.heightProperty().addListener((obs, old, newVal) -> redraw());
    }

    private void handleHover(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        sendMouse(mouseX, mouseY);
        redraw();
    }

    private void handleClick(MouseEvent e) {
        if (gameState == null || Main.wsClient == null) return;
        if (!clientName.equals(gameState.optString("turn"))) return;

        int col = (int) (e.getX() / cellSize);
        if (col >= 0 && col < cols) {
            sendPlay(col);
        }
    }

    private void handleDrag(MouseEvent e) {
        if (dragging && draggedPiece != null) {
            draggedPiece.setLayoutX(e.getSceneX() - dragOffsetX);
            draggedPiece.setLayoutY(e.getSceneY() - dragOffsetY);
        }
    }

    private void handleRelease(MouseEvent e) {
        if (dragging && draggedPiece != null) {
            int col = (int) (e.getX() / cellSize);
            if (col >= 0 && col < cols && clientName.equals(gameState.optString("turn"))) {
                sendPlay(col);
                paneYourPieces.getChildren().remove(draggedPiece);
            } else {
                resetDraggedPiece();
            }
        }
        dragging = false;
        draggedPiece = null;
    }

    private void resetDraggedPiece() {
        if (draggedPiece != null) {
            draggedPiece.setTranslateX(0);
            draggedPiece.setTranslateY(0);
            draggedPiece.setMouseTransparent(false);
        }
    }

    private void sendPlay(int col) {
        if (Main.wsClient == null) return;
        JSONObject obj = new JSONObject();
        obj.put("type", "clientPlay");
        obj.put("col", col);
        Main.wsClient.safeSend(obj.toString());
    }

    private void sendMouse(double x, double y) {
        if (Main.wsClient == null) return;
        JSONObject obj = new JSONObject();
        obj.put("type", "clientMouseMoving");
        obj.put("x", x);
        obj.put("y", y);
        Main.wsClient.safeSend(obj.toString());
    }

    public void handleMessage(JSONObject msg) {
        String type = msg.optString("type", "");
        Platform.runLater(() -> {
            switch (type) {
                case "serverData" -> {
                    clientName = msg.optString("clientName", "");
                    role = msg.optString("role", "");
                    gameState = msg.optJSONObject("game");

                    lblPlayerName.setText(clientName);
                    lblYourRole.setText("(" + role + ")");
                    lblOpponentName.setText(msg.optString("opponent", "---"));
                    lblOpponentRole.setText(role.equals("R") ? "(Y)" : "(R)");

                    JSONArray objectsList = msg.optJSONArray("objectsList");
                    if (objectsList != null) {
                        availablePieces.clear();
                        paneYourPieces.getChildren().clear();
                        for (int i = 0; i < objectsList.length(); i++) {
                            JSONObject obj = objectsList.getJSONObject(i);
                            GameObject go = GameObject.fromJSON(obj);
                            availablePieces.add(go);

                            Circle circle = new Circle(20);
                            circle.setFill(role.equals("R") ? Color.RED : Color.YELLOW);
                            circle.setUserData(go.getId());
                            paneYourPieces.getChildren().add(circle);
                        }
                    }
                    redraw();
                }
                case "updateBoard" -> {
                    gameState = msg.optJSONObject("game");
                    redraw();
                    updateTurnIndicator();
                }
                case "winner" -> {
                    String winner = msg.optString("winner");
                    highlightWinning(msg.optJSONArray("cells"));
                    lblTurnIndicator.setText(winner.equals(clientName) ? "HAS GUANYAT 🎉" : "HAS PERDUT 😞");

                    Timeline t = new Timeline(new KeyFrame(Duration.seconds(3),
                            e -> UtilsViews.setView("ViewResult")));
                    t.play();
                }
                case "clientMouseMoving" -> {
                    String player = msg.optString("player");
                    double x = msg.optDouble("x");
                    double y = msg.optDouble("y");
                    opponentMouse.put(player, new double[]{x, y});
                    redraw();
                }
            }
        });
    }

    private void updateTurnIndicator() {
        if (gameState == null) return;
        String turn = gameState.optString("turn", "");
        if (turn.equals(clientName)) {
            lblTurnIndicator.setText("El teu torn!");
            lblTurnIndicator.setTextFill(Color.GREEN);
        } else {
            lblTurnIndicator.setText("Torn del rival...");
            lblTurnIndicator.setTextFill(Color.GRAY);
        }
    }

    private void highlightWinning(JSONArray cells) {
        if (cells == null) return;
        for (int i = 0; i < cells.length(); i++) {
            JSONArray pair = cells.getJSONArray(i);
            int r = pair.getInt(0);
            int c = pair.getInt(1);
            winningCells[r][c] = true;
        }
        redraw();
    }

    private void redraw() {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Dibujar fondo tablero
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, cols * cellSize, rows * cellSize);

        // Dibujar celdas
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double x = c * cellSize;
                double y = r * cellSize;
                gc.setFill(Color.WHITE);
                gc.fillOval(x + 10, y + 10, cellSize - 20, cellSize - 20);

                if (winningCells[r][c]) {
                    gc.setStroke(Color.GOLD);
                    gc.setLineWidth(5);
                    gc.strokeOval(x + 10, y + 10, cellSize - 20, cellSize - 20);
                }
            }
        }

        // Dibujar fichas desde gameState
        if (gameState != null && gameState.has("board")) {
            JSONArray board = gameState.getJSONArray("board");
            for (int r = 0; r < rows; r++) {
                JSONArray row = board.getJSONArray(r);
                for (int c = 0; c < cols; c++) {
                    String cell = row.getString(c);
                    if (cell.equals("R") || cell.equals("Y")) {
                        gc.setFill(cell.equals("R") ? Color.RED : Color.YELLOW);
                        gc.fillOval(c * cellSize + 10, r * cellSize + 10, cellSize - 20, cellSize - 20);
                    }
                }
            }
        }
    }

    private void drawAvailablePieces() {
        // Limpiar
        paneYourPieces.getChildren().clear();
        paneOpponentPieces.getChildren().clear();

        if (gameState == null) return;

        // Contar fichas jugadas
        int redPlayed = 0, yellowPlayed = 0;
        JSONArray board = gameState.getJSONArray("board");
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                String v = board.getJSONArray(r).getString(c);
                if ("R".equals(v)) redPlayed++;
                else if ("Y".equals(v)) yellowPlayed++;
            }
        }

        int yourRemaining = 21 - ("R".equals(role) ? redPlayed : yellowPlayed);
        int oppRemaining = 21 - ("R".equals(role) ? yellowPlayed : redPlayed);

        // Dibujar tus fichas
        drawFichas(paneYourPieces, "R".equals(role) ? Color.RED : Color.YELLOW, yourRemaining, false);
        // Dibujar fichas del rival (grisadas)
        drawFichas(paneOpponentPieces, "R".equals(role) ? Color.YELLOW : Color.RED, oppRemaining, true);
    }

    private void drawFichas(Pane pane, Color color, int count, boolean disabled) {
        double size = 30;
        double margin = 5;
        int cols = 4; // 4 fichas por fila

        for (int i = 0; i < count; i++) {
            javafx.scene.shape.Circle c = new javafx.scene.shape.Circle(size / 2);
            c.setFill(disabled ? Color.LIGHTGRAY : color);
            c.setStroke(Color.BLACK);
            c.setStrokeWidth(1);

            double x = margin + (i % cols) * (size + margin);
            double y = margin + (i / cols) * (size + margin);

            // ✅ Usa setLayoutX/Y (no translate)
            c.setLayoutX(x);
            c.setLayoutY(y);

            pane.getChildren().add(c);
        }
    }

    private String getPieceAt(int row, int col) {
        if (gameState == null) return " ";
        return gameState.getJSONArray("board").getJSONArray(row).getString(col);
    }

    private boolean isWinningCell(int row, int col) {
        return "win".equals(gameState.getString("status"));
    }
}
