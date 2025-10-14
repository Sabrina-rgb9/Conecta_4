package com.clientFX;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class CtrlConnecta4 {

    @FXML private Canvas canvas;
    @FXML private Pane root;

    private WebSocketClient wsClient;
    private String clientName;
    private String role; // "R" o "Y"
    private JSONObject gameState;
    private double mouseX, mouseY;
    private Map<String, double[]> opponentMouse = new HashMap<>();

    private final int rows = 6;
    private final int cols = 7;
    private final double cellSize = 80;

    private Timeline renderLoop;

    // ---------------- Conexión al servidor ----------------
    public void connect(String url) {
        try {
            wsClient = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("Conectado al servidor");
                    sendReady();
                }

                @Override
                public void onMessage(String message) {
                    JSONObject obj = new JSONObject(message);
                    handleServerMessage(obj);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Desconectado: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };
            wsClient.connect();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ---------------- Eventos de cliente ----------------
    private void sendReady() {
        JSONObject obj = new JSONObject();
        obj.put("type", "clientReady");
        wsClient.send(obj.toString());
    }

    private void sendPlay(int col) {
        if (gameState != null && clientName.equals(gameState.getJSONObject("game").getString("turn"))) {
            JSONObject obj = new JSONObject();
            obj.put("type", "clientPlay");
            obj.put("col", col);
            wsClient.send(obj.toString());
        }
    }

    private void sendMouse(double x, double y) {
        JSONObject obj = new JSONObject();
        obj.put("type", "clientMouseMoving");
        obj.put("x", x);
        obj.put("y", y);
        wsClient.send(obj.toString());
    }

    // ---------------- Manejo de mensajes del servidor ----------------
    private void handleServerMessage(JSONObject obj) {
        String type = obj.getString("type");

        switch (type) {
            case "serverData" -> {
                clientName = obj.getString("clientName");
                role = obj.getString("role");
                gameState = obj.getJSONObject("game");
            }
            case "clientMouseMoving" -> {
                String player = obj.getString("player");
                if (!player.equals(clientName)) {
                    opponentMouse.put(player, new double[]{obj.getDouble("x"), obj.getDouble("y")});
                }
            }
        }
    }

    // ---------------- Renderizado ----------------
    public void initialize() {
        canvas.setWidth(cols * cellSize);
        canvas.setHeight(rows * cellSize);

        canvas.setOnMouseMoved(this::handleHover);
        canvas.setOnMouseClicked(this::handleClick);

        renderLoop = new Timeline(new KeyFrame(Duration.millis(33), e -> draw()));
        renderLoop.setCycleCount(Timeline.INDEFINITE);
        renderLoop.play();
    }

    private void handleHover(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        sendMouse(mouseX, mouseY);
    }

    private void handleClick(MouseEvent e) {
        int col = (int)(e.getX() / cellSize);
        sendPlay(col);
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // tablero
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                gc.setFill(Color.WHITE);
                gc.fillOval(c*cellSize+5, r*cellSize+5, cellSize-10, cellSize-10);
            }
        }

        if (gameState != null) {
            JSONArray board = gameState.getJSONArray("board");
            for (int r = 0; r < rows; r++) {
                JSONArray row = board.getJSONArray(r);
                for (int c = 0; c < cols; c++) {
                    String val = row.getString(c);
                    if (val.equals("R")) gc.setFill(Color.RED);
                    else if (val.equals("Y")) gc.setFill(Color.YELLOW);
                    else continue;
                    gc.fillOval(c*cellSize+5, r*cellSize+5, cellSize-10, cellSize-10);
                }
            }
        }

        // hover contrincante
        for (var m : opponentMouse.values()) {
            gc.setStroke(Color.GRAY);
            gc.strokeLine(m[0], 0, m[0], canvas.getHeight());
        }
    }
}
