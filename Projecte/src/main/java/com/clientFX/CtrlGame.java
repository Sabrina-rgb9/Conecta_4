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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

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
    private String role = ""; // "R" o "Y"
    private JSONObject gameState;
    private double mouseX, mouseY;
    private Map<String, double[]> opponentMouse = new HashMap<>();
    private final int rows = 6;
    private final int cols = 7;
    private final double cellSize = 80;
    private int lastMoveRow = -1;
    private int lastMoveCol = -1;
    private Timeline dropAnimation;

    public static WSClient wsClient;

    @FXML
    public void initialize() {
        canvas.setWidth(cols * cellSize);
        canvas.setHeight(rows * cellSize);

        canvas.setOnMouseMoved(this::handleHover);
        canvas.setOnMouseClicked(this::handleClick);

        canvas.widthProperty().addListener((obs, old, newVal) -> redraw());
        canvas.heightProperty().addListener((obs, old, newVal) -> redraw());
        
        // Inicializar WebSocket
        try {
            wsClient = new WSClient(new URI("ws://localhost:3000"), this);
            wsClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void handleHover(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        sendMouse(mouseX, mouseY);
        redraw();
    }

    private void handleClick(MouseEvent e) {
        if (gameState == null || Main.wsClient == null) return;
        if (!clientName.equals(gameState.getString("turn"))) return;

        int col = (int) (e.getX() / cellSize);
        if (col >= 0 && col < cols) {
            sendPlay(col);
        }
    }

    private void sendPlay(int col) {
        if(wsClient == null) return;
        wsClient.safeSend(new org.json.JSONObject()
            .put("type","clientPlay")
            .put("col",col)
            .toString());
    }

    private void sendMouse(double x,double y){
        if(wsClient==null) return;
        wsClient.safeSend(new org.json.JSONObject()
            .put("type","clientMouseMoving")
            .put("x",x)
            .put("y",y)
            .toString());
    }

    private void sendInvite(String dest){
        if(wsClient==null) return;
        wsClient.safeSend(new org.json.JSONObject()
            .put("type","invite")
            .put("dest",dest)
            .toString());
    }

    private void sendAccept(String origin){
        if(wsClient==null) return;
        wsClient.safeSend(new org.json.JSONObject()
            .put("type","acceptInvite")
            .put("origin",origin)
            .toString());
    }


    public void handleMessage(JSONObject msg) {
        String type = msg.getString("type");

        Platform.runLater(() -> {
            switch (type) {
                case "serverData":
                    handleServerData(msg);
                    break;

                case "clientMouseMoving":
                    handleOpponentMouse(msg);
                    break;

                case "countdown":
                    lblTurnIndicator.setText("Comenzando en: " + msg.getInt("seconds"));
                    break;

                case "gameStarted":
                    lblTurnIndicator.setText("Juego iniciado. Oponente: " + msg.optString("opponent", "---"));
                    break;

                case "gameResult":
                    lblTurnIndicator.setText("Resultado: " + msg.getString("result").toUpperCase());
                    break;

                case "opponentDisconnected":
                    lblTurnIndicator.setText("Oponente desconectado: " + msg.getString("name"));
                    break;

                case "invite":
                    System.out.println("Invitación recibida de: " + msg.getString("origin"));
                    break;

                default:
                    System.out.println("Mensaje desconocido: " + msg.toString());
            }
        });
    }

    private void handleServerData(JSONObject msg) {
        gameState = msg.getJSONObject("game");

        // Extraer roles y nombres
        JSONArray clients = msg.getJSONArray("clientsList");
        String opponentName = "---";
        for (int i = 0; i < clients.length(); i++) {
            JSONObject p = clients.getJSONObject(i);
            String name = p.getString("name");
            String pRole = p.getString("role");
            if (name.equals(clientName)) {
                role = pRole;
            } else {
                opponentName = name;
            }
        }

        updatePlayerUI(opponentName);

        // Animar última jugada
        if (gameState.has("lastMove") && !gameState.isNull("lastMove")) {
            JSONObject lm = gameState.getJSONObject("lastMove");
            int r = lm.getInt("row");
            int c = lm.getInt("col");
            if (r != lastMoveRow || c != lastMoveCol) {
                lastMoveRow = r;
                lastMoveCol = c;
                animateDrop(c, r);
                return;
            }
        }

        redraw();
    }

    private void updatePlayerUI(String opponentName) {
        lblPlayerName.setText(clientName);
        lblYourRole.setText("(" + role + ")");
        lblYourRole.setStyle("-fx-text-fill: " + ("R".equals(role) ? "red" : "yellow") + ";");

        String oppRole = "R".equals(role) ? "Y" : "R";
        lblOpponentName.setText(opponentName);
        lblOpponentRole.setText("(" + oppRole + ")");
        lblOpponentRole.setStyle("-fx-text-fill: " + ("R".equals(oppRole) ? "red" : "yellow") + ";");

        // Turno
        boolean myTurn = clientName.equals(gameState.getString("turn"));
        if (myTurn) {
            lblTurnIndicator.setText("Et toca jugar");
            lblTurnIndicator.setStyle("-fx-text-fill: green; -fx-font-weight: bold; -fx-font-style: normal;");
        } else {
            lblTurnIndicator.setText("Esperant...");
            lblTurnIndicator.setStyle("-fx-text-fill: gray; -fx-font-style: italic; -fx-font-weight: normal;");
        }
    }

    private void handleOpponentMouse(JSONObject msg) {
        String player = msg.getString("player");
        if (!player.equals(clientName)) {
            opponentMouse.put(player, new double[]{msg.getDouble("x"), msg.getDouble("y")});
            redraw();
        }
    }


    private void animateDrop(int col, int targetRow) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double startX = col * cellSize + cellSize / 2.0;
        double startY = -cellSize / 2.0;
        double endY = targetRow * cellSize + cellSize / 2.0;
        double[] currentY = {startY};

        Timeline renderLoop = new Timeline(
            new KeyFrame(Duration.millis(16), e -> {
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                redraw();
                String piece = getPieceAt(targetRow, col);
                Color color = "R".equals(piece) ? Color.RED : Color.YELLOW;
                gc.setFill(color);
                gc.fillOval(startX - cellSize/2 + 5, currentY[0] - cellSize/2 + 5, cellSize - 10, cellSize - 10);
                if (currentY[0] < endY) {
                    currentY[0] += 8;
                    if (currentY[0] > endY) currentY[0] = endY;
                }
            })
        );
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

        // Dibujar tablero
        for (int c = 0; c < cols; c++) {
            // Hover local
            if (gameState != null && clientName.equals(gameState.getString("turn"))) {
                int hoverCol = (int) (mouseX / cellSize);
                if (hoverCol == c) {
                    gc.setFill(Color.rgb(255, 255, 0, 0.2));
                    gc.fillRect(c * cellSize, 0, cellSize, canvas.getHeight());
                }
            }

            // Hover remoto
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
            JSONArray board = gameState.getJSONArray("board");
            for (int r = 0; r < rows; r++) {
                JSONArray row = board.getJSONArray(r);
                for (int c = 0; c < cols; c++) {
                    String val = row.getString(c);
                    if ("R".equals(val)) {
                        gc.setFill(Color.RED);
                    } else if ("Y".equals(val)) {
                        gc.setFill(Color.YELLOW);
                    } else continue;

                    if (isWinningCell(r, c)) {
                        gc.setGlobalAlpha(0.8);
                        gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                        gc.setGlobalAlpha(1.0);
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

        // Punter remoto
        for (double[] pos : opponentMouse.values()) {
            gc.setStroke(Color.GRAY);
            gc.setLineWidth(1);
            gc.setLineDashes(5);
            gc.strokeLine(pos[0], 0, pos[0], canvas.getHeight());
            gc.setLineDashes(null);
        }

        // === DIBUJAR FICHAS DISPONIBLES EN LOS PANELES ===
        drawAvailablePieces();
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