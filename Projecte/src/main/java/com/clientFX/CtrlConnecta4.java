package com.clientFX;

import java.net.URL;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class CtrlConnecta4 implements Initializable {

    @FXML private Label lblServerStatus;
    @FXML private Label lblPlayerName;
    @FXML private Label lblTurn;
    @FXML private Canvas gameCanvas;
    @FXML private Button btnPlay;
    @FXML private Button btnConnect;

    private boolean connected = false;
    private boolean isMyTurn = false;
    private String myRole = "";
    private double mouseX, mouseY;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        drawBoard();

        // Hover: enviar movimiento de ratón al servidor
        gameCanvas.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            if (connected) {
                mouseX = e.getX();
                mouseY = e.getY();
                JSONObject msg = new JSONObject();
                msg.put("type", "clientMouseMoving");
                msg.put("x", mouseX);
                msg.put("y", mouseY);
                Main.wsClient.safeSend(msg.toString());
            }
        });

        // Clic: jugar una ficha
        gameCanvas.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (connected && isMyTurn) {
                int col = (int) (e.getX() / (gameCanvas.getWidth() / 7));
                JSONObject msg = new JSONObject();
                msg.put("type", "clientPlay");
                msg.put("col", col);
                Main.wsClient.safeSend(msg.toString());
            }
        });
    }

    @FXML
    private void handleConnect() {
        if (!connected) {
            Main.wsClient.connect();
            lblServerStatus.setText("Connecting...");
        }
    }

    @FXML
    private void handlePlay() {
        // Si el jugador está esperando o quiere iniciar partida
        JSONObject msg = new JSONObject();
        msg.put("type", "clientReady");
        Main.wsClient.safeSend(msg.toString());
    }

    // Llamado por Main.wsClient cuando se recibe un mensaje
    public void receiveMessage(JSONObject messageObj) {
        String type = messageObj.getString("type");

        if (type.equals("serverData")) {
            JSONObject game = messageObj.getJSONObject("game");
            String status = game.getString("status");
            String turn = game.getString("turn");
            myRole = messageObj.getString("role");
            isMyTurn = turn.equals(messageObj.getString("clientName"));

            lblTurn.setText("Turno: " + turn);
            lblServerStatus.setText("Estado: " + status);
            drawBoardFromServer(game);
        } 
        else if (type.equals("countdown")) {
            int count = messageObj.getInt("value");
            lblServerStatus.setText("Empieza en: " + count);
        }
    }

    private void drawBoard() {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());
        gc.setFill(Color.WHITE);
        double cellW = gameCanvas.getWidth() / 7;
        double cellH = gameCanvas.getHeight() / 6;
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 7; c++) {
                gc.setFill(Color.WHITE);
                gc.fillOval(c * cellW + 5, r * cellH + 5, cellW - 10, cellH - 10);
            }
        }
    }

    private void drawBoardFromServer(JSONObject game) {
        // Redibuja el tablero a partir del JSON
        drawBoard();
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();
        double cellW = gameCanvas.getWidth() / 7;
        double cellH = gameCanvas.getHeight() / 6;
        var board = game.getJSONArray("board");
        for (int r = 0; r < 6; r++) {
            var row = board.getJSONArray(r);
            for (int c = 0; c < 7; c++) {
                String cell = row.getString(c);
                if (cell.equals("R")) gc.setFill(Color.RED);
                else if (cell.equals("Y")) gc.setFill(Color.YELLOW);
                else continue;
                gc.fillOval(c * cellW + 5, r * cellH + 5, cellW - 10, cellH - 10);
            }
        }
    }

    public void setConnected(boolean value) {
        connected = value;
        lblServerStatus.setText(value ? "Conectado" : "Desconectado");
    }
}
