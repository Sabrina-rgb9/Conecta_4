package com.clientFX;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class CtrlGame {

    @FXML private Canvas canvas;
    @FXML private Label lblPlayerName;
    @FXML private Label lblYourRole;
    @FXML private Label lblOpponentName;
    @FXML private Label lblOpponentRole;
    @FXML private Label lblTurnIndicator;
    @FXML private VBox paneYourPieces;    // en FXML lo tienes como VBox
    @FXML private VBox paneOpponentPieces;

    private String clientName = "";
    private String role = "";
    private JSONObject gameState;
    private double mouseX, mouseY;
    private Map<String, double[]> opponentMouse = new HashMap<>();
    private final int rows = 6;
    private final int cols = 7;
    private final double cellSize = 80;
    private int lastMoveRow = -1;
    private int lastMoveCol = -1;

    @FXML
    public void initialize() {
        if (canvas == null) return;

        canvas.setWidth(cols * cellSize);
        canvas.setHeight(rows * cellSize);

        canvas.setOnMouseMoved(this::handleHover);
        canvas.setOnMouseClicked(this::handleClick);

        canvas.widthProperty().addListener((obs, o, n) -> redraw());
        canvas.heightProperty().addListener((obs, o, n) -> redraw());
    }

    private void handleHover(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        sendMouse(mouseX, mouseY);
        redraw();
    }

    private void handleClick(MouseEvent e) {
        if (gameState == null || Main.wsClient == null) return;
        String turn = gameState.optString("turn", "");
        if (!Main.playerName.equals(turn)) return;

        int col = (int) (e.getX() / cellSize);
        if (col >= 0 && col < cols) sendPlay(col);
    }

    private void sendPlay(int col) {
        if (Main.wsClient == null) return;
        JSONObject j = new JSONObject();
        j.put("type", "clientPlay");
        j.put("column", col); // coincide con tu servidor: column
        Main.wsClient.send(j.toString());
    }

    private void sendMouse(double x, double y) {
        if (Main.wsClient == null) return;
        JSONObject j = new JSONObject();
        j.put("type", "clientMouseMoving");
        j.put("x", x);
        j.put("y", y);
        Main.wsClient.send(j.toString());
    }

    /**
     * handleMessage: acepta varios tipos:
     * - serverData  (contiene "game", "clientsList", "objectsList")
     * - clientMouseMoving (de otro cliente)
     * - countdown / gameResult / opponentDisconnected
     */
    public void handleMessage(JSONObject msg) {
        String type = msg.optString("type", "");
        Platform.runLater(() -> {
            switch (type) {
                case "serverData":
                    // tu servidor manda game en root -> game
                    // pero a veces Main ya pasó directamente game; aceptamos msg.getJSONObject("game")
                    JSONObject game = msg.optJSONObject("game");
                    if (game != null) {
                        // guardar nombre de cliente si no lo teníamos
                        clientName = Main.playerName;
                        this.gameState = new JSONObject();
                        // copio los campos relevantes para compatibilidad con tu controlador antiguo:
                        this.gameState = game;
                    }
                    // actualizar lista de clientes y roles
                    if (msg.has("clientsList")) {
                        JSONArray clients = msg.getJSONArray("clientsList");
                        String opponent = "---";
                        for (int i = 0; i < clients.length(); i++) {
                            JSONObject c = clients.getJSONObject(i);
                            String name = c.optString("name", "");
                            String r = c.optString("role", "");
                            if (name.equals(Main.playerName)) {
                                this.role = r; // R o Y
                            } else {
                                opponent = name;
                            }
                        }
                        updatePlayerUI(opponent);
                    } else {
                        updatePlayerUI("---");
                    }

                    // última jugada animación
                    if (gameState != null && gameState.has("lastMove") && !gameState.isNull("lastMove")) {
                        JSONObject lm = gameState.getJSONObject("lastMove");
                        int r = lm.optInt("row", -1);
                        int c = lm.optInt("col", -1);
                        if (r != lastMoveRow || c != lastMoveCol) {
                            lastMoveRow = r;
                            lastMoveCol = c;
                            animateDrop(c, r);
                            return;
                        }
                    }
                    redraw();
                    break;

                case "clientMouseMoving":
                    String player = msg.optString("player", "");
                    if (!player.equals(Main.playerName)) {
                        opponentMouse.put(player, new double[]{msg.optDouble("x", 0), msg.optDouble("y", 0)});
                        redraw();
                    }
                    break;

                case "opponentDisconnected":
                    lblTurnIndicator.setText("Oponente desconectado");
                    break;

                case "gameResult":
                    // Pasado a Main -> ViewResult
                    break;

                default:
                    // Mensajes varios
                    break;
            }
        });
    }

    private void updatePlayerUI(String opponentName) {
        lblPlayerName.setText(Main.playerName);
        lblYourRole.setText("(" + (role.isEmpty() ? "?" : role) + ")");
        lblYourRole.setStyle("-fx-text-fill: " + ("R".equals(role) ? "red" : "yellow") + ";");

        lblOpponentName.setText(opponentName);
        String oppRole = "R".equals(role) ? "Y" : "R";
        lblOpponentRole.setText("(" + oppRole + ")");
        lblOpponentRole.setStyle("-fx-text-fill: " + ("R".equals(oppRole) ? "red" : "yellow") + ";");

        boolean myTurn = gameState != null && Main.playerName.equals(gameState.optString("turn", ""));
        if (myTurn) {
            lblTurnIndicator.setText("Te toca jugar");
            lblTurnIndicator.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else {
            lblTurnIndicator.setText("Esperando...");
            lblTurnIndicator.setStyle("-fx-text-fill: gray;");
        }
    }

    private void animateDrop(int col, int targetRow) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double startX = col * cellSize + cellSize / 2.0;
        double startY = -cellSize / 2.0;
        double endY = targetRow * cellSize + cellSize / 2.0;
        double[] currentY = {startY};

        Timeline renderLoop = new Timeline(new KeyFrame(Duration.millis(16), e -> {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            redraw();
            String piece = getPieceAt(targetRow, col);
            Color color = "R".equals(piece) ? Color.RED : Color.YELLOW;
            gc.setFill(color);
            gc.fillOval(startX - cellSize / 2 + 5, currentY[0] - cellSize / 2 + 5, cellSize - 10, cellSize - 10);
            if (currentY[0] < endY) {
                currentY[0] += 8;
                if (currentY[0] > endY) currentY[0] = endY;
            }
        }));
        renderLoop.setCycleCount(Timeline.INDEFINITE);

        Timeline stopper = new Timeline(new KeyFrame(Duration.millis(600), e -> {
            renderLoop.stop();
            redraw();
        }));
        stopper.play();
        renderLoop.play();
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Fondo tablero
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        for (int c = 0; c < cols; c++) {
            if (gameState != null && Main.playerName.equals(gameState.optString("turn", ""))) {
                int hoverCol = (int) (mouseX / cellSize);
                if (hoverCol == c) {
                    gc.setFill(Color.rgb(255, 255, 0, 0.2));
                    gc.fillRect(c * cellSize, 0, cellSize, canvas.getHeight());
                }
            }

            for (double[] pos : opponentMouse.values()) {
                int colOpp = (int) (pos[0] / cellSize);
                if (colOpp == c) {
                    gc.setStroke(Color.GRAY);
                    gc.setLineWidth(2);
                    gc.strokeRect(c * cellSize, 0, cellSize, canvas.getHeight());
                }
            }

            for (int r = 0; r < rows; r++) {
                gc.setStroke(Color.GRAY);
                gc.setFill(Color.WHITE);
                gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                gc.strokeOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
            }
        }

        // Fichas jugadas
        if (gameState != null) {
            JSONArray board = gameState.optJSONArray("board");
            if (board != null) {
                for (int r = 0; r < rows; r++) {
                    JSONArray row = board.optJSONArray(r);
                    if (row == null) continue;
                    for (int c = 0; c < cols; c++) {
                        String val = row.optString(c, " ");
                        if ("R".equals(val)) gc.setFill(Color.RED);
                        else if ("Y".equals(val)) gc.setFill(Color.YELLOW);
                        else continue;

                        gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                    }
                }
            }
        }

        // Puntero remoto (líneas)
        for (double[] pos : opponentMouse.values()) {
            gc.setStroke(Color.GRAY);
            gc.setLineWidth(1);
            gc.setLineDashes(5);
            gc.strokeLine(pos[0], 0, pos[0], canvas.getHeight());
            gc.setLineDashes(null);
        }

        // Dibujar paneles de fichas
        drawAvailablePieces();
    }

    private void drawAvailablePieces() {
        if (paneYourPieces == null || paneOpponentPieces == null) return;
        paneYourPieces.getChildren().clear();
        paneOpponentPieces.getChildren().clear();

        if (gameState == null) return;

        // Contar jugadas
        int redPlayed = 0, yellowPlayed = 0;
        JSONArray board = gameState.optJSONArray("board");
        if (board != null) {
            for (int r = 0; r < rows; r++) {
                JSONArray row = board.optJSONArray(r);
                if (row == null) continue;
                for (int c = 0; c < cols; c++) {
                    String v = row.optString(c, " ");
                    if ("R".equals(v)) redPlayed++;
                    else if ("Y".equals(v)) yellowPlayed++;
                }
            }
        }

        int yourRemaining = 21 - ("R".equals(role) ? redPlayed : yellowPlayed);
        int oppRemaining = 21 - ("R".equals(role) ? yellowPlayed : redPlayed);

        drawFichas(paneYourPieces, "R".equals(role) ? Color.RED : Color.YELLOW, yourRemaining, false);
        drawFichas(paneOpponentPieces, "R".equals(role) ? Color.YELLOW : Color.RED, oppRemaining, true);
    }

    private void drawFichas(VBox pane, Color color, int count, boolean disabled) {
        // simplemente añadimos círculos como nodes
        double size = 20;
        int perCol = 3;
        for (int i = 0; i < count && i < 21; i++) {
            javafx.scene.shape.Circle cir = new javafx.scene.shape.Circle(size / 2);
            cir.setFill(disabled ? Color.LIGHTGRAY : color);
            cir.setStroke(Color.BLACK);
            cir.setTranslateX((i % perCol) * (size + 4));
            cir.setTranslateY((i / perCol) * (size + 4));
            pane.getChildren().add(cir);
        }
    }

    private String getPieceAt(int row, int col) {
        if (gameState == null) return " ";
        JSONArray b = gameState.optJSONArray("board");
        if (b == null) return " ";
        JSONArray r = b.optJSONArray(row);
        if (r == null) return " ";
        return r.optString(col, " ");
    }
}
