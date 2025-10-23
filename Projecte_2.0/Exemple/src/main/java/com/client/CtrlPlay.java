package com.client;

import com.shared.ClientData;
import com.shared.GameObject;
import javafx.animation.AnimationTimer;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class CtrlPlay {

    @FXML private Canvas canvas;

    private final int COLS = 7;
    private final int ROWS = 6;
    private double cellWidth, cellHeight;

    private String myName;
    private String myColor;
    private String currentTurn;

    private String[][] board; // [row][col] -> "RED", "YELLOW", null
    private Map<String, double[]> opponentMouse = new HashMap<>();

    private AnimationTimer timer;

    @FXML
    public void initialize() {
        canvas.setWidth(700);
        canvas.setHeight(600);
        cellWidth = canvas.getWidth() / COLS;
        cellHeight = canvas.getHeight() / ROWS;

        board = new String[ROWS][COLS];

        canvas.setOnMouseMoved(this::handleMouseMove);
        canvas.setOnMouseReleased(this::handleMouseRelease);

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                draw();
            }
        };
        timer.start();
    }

    private boolean myTurn() {
        return myName != null && myName.equals(currentTurn);
    }

    private void handleMouseMove(MouseEvent e) {
        if (!myTurn()) return;

        double mouseX = e.getX();
        double mouseY = e.getY();

        JSONObject msg = new JSONObject();
        msg.put("type", "clientMouseMoving");
        JSONObject value = new JSONObject();
        value.put("mouseX", mouseX);
        value.put("mouseY", mouseY);
        msg.put("value", value);

        UtilsWS.getSharedInstance(null).safeSend(msg.toString());
    }

    private void handleMouseRelease(MouseEvent e) {
        if (!myTurn()) return;

        int col = (int) (e.getX() / cellWidth);
        if (col < 0 || col >= COLS) return;

        int row = findAvailableRow(col);
        if (row < 0) return;

        board[row][col] = myColor;
        animateDrop(row, col, myColor);

        JSONObject msg = new JSONObject();
        msg.put("type", "clientObjectMoving");
        JSONObject value = new JSONObject();
        value.put("col", col);
        value.put("row", row);
        value.put("color", myColor);
        msg.put("value", value);

        UtilsWS.getSharedInstance(null).safeSend(msg.toString());
    }

    private int findAvailableRow(int col) {
        for (int r = ROWS - 1; r >= 0; r--) {
            if (board[r][col] == null) return r;
        }
        return -1;
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Dibujar tablero
        gc.setFill(Color.WHITE);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                double x = c * cellWidth;
                double y = r * cellHeight;
                gc.fillOval(x + 5, y + 5, cellWidth - 10, cellHeight - 10);

                if (board[r][c] != null) {
                    gc.setFill(board[r][c].equals("RED") ? Color.RED : Color.YELLOW);
                    gc.fillOval(x + 5, y + 5, cellWidth - 10, cellHeight - 10);
                    gc.setFill(Color.WHITE);
                }
            }
        }

        // Dibujar punteros rivales
        for (double[] pos : opponentMouse.values()) {
            gc.setFill(Color.BLACK);
            gc.fillOval(pos[0] - 5, pos[1] - 5, 10, 10);
        }

        // Texto de turno
        gc.setFill(Color.BLACK);
        gc.fillText(myTurn() ? "Et toca jugar" : "Esperant torn adversari", 10, 15);
    }

    private void animateDrop(int row, int col, String color) {
        double startY = 0;
        double endY = row * cellHeight;
        double x = col * cellWidth;

        TranslateTransition tt = new TranslateTransition(Duration.millis(300), canvas);
        tt.setFromY(startY);
        tt.setToY(endY);
        tt.setOnFinished(e -> draw());
        tt.play();
    }

    // Actualizar desde servidor
    public void updateFromServer(JSONObject data) {
        JSONArray clientsArray = data.getJSONArray("clientsList");
        JSONArray objectsArray = data.getJSONArray("objectsList");

        currentTurn = null;

        for (int i = 0; i < clientsArray.length(); i++) {
            JSONObject c = clientsArray.getJSONObject(i);
            String name = c.getString("name");
            String color = c.getString("color");
            if (myName == null) myName = name;
            if (myColor == null) myColor = color;

            int mx = c.optInt("mouseX", -1);
            int my = c.optInt("mouseY", -1);
            if (!name.equals(myName) && mx >= 0 && my >= 0) {
                opponentMouse.put(name, new double[]{mx, my});
            }

            boolean turn = c.optBoolean("turn", false);
            if (turn) currentTurn = name;
        }

        for (int i = 0; i < objectsArray.length(); i++) {
            JSONObject obj = objectsArray.getJSONObject(i);
            int r = obj.getInt("row");
            int c = obj.getInt("col");
            String color = obj.getString("color");
            board[r][c] = color;
        }

        draw();
    }
}
