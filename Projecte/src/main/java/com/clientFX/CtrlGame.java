package com.clientFX;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.shared.GameState;
import com.shared.GameData;
import com.shared.ClientInfo;
import com.shared.Move;

public class CtrlGame {

    @FXML private Canvas canvas;
    @FXML private Label lblPlayerName;
    @FXML private Label lblYourRole;
    @FXML private Label lblOpponentName;
    @FXML private Label lblOpponentRole;
    @FXML private Label lblTurnIndicator;
    @FXML private VBox paneYourPieces;
    @FXML private VBox paneOpponentPieces;

    private double mouseX;
    private double mouseY;
    private Map<String, double[]> opponentMouse = new HashMap<>();
    private final int rows = 6;
    private final int cols = 7;
    private final double cellSize = 80;

    private String clientName = "Jugador"; // Se setea al conectar
    private String role = "R";
    private GameState gameState;

    public static WSClient wsClient;

    @FXML
    public void initialize() {
        canvas.setWidth(cols * cellSize);
        canvas.setHeight(rows * cellSize);

        canvas.setOnMouseMoved(this::handleHover);
        canvas.setOnMouseClicked(this::handleClick);

        try {
            wsClient = new WSClient(new URI("ws://localhost:3000"), this);
            wsClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleHover(MouseEvent e) {
        mouseX = e.getX();
        sendMouse(mouseX, e.getY());
        redraw();
    }

    private void handleClick(MouseEvent e) {
        if (gameState == null || wsClient == null) return;
        if (!clientName.equals(gameState.getGame().getTurn())) return;

        int col = (int) (e.getX() / cellSize);
        if (col >= 0 && col < cols) sendPlay(col);
    }

    private void sendPlay(int col) {
        if (wsClient == null) return;
        wsClient.safeSend(new JSONObject()
                .put("type","clientPlay")
                .put("column",col)
                .toString());
    }

    private void sendMouse(double x, double y){
        if(wsClient == null) return;
        wsClient.safeSend(new JSONObject()
                .put("type","clientMouseMoving")
                .put("x",x)
                .put("y",y)
                .toString());
    }

    public void handleMessage(JSONObject msg) {
        Platform.runLater(() -> {
            String type = msg.getString("type");
            switch (type) {
                case "serverData":
                    parseServerData(msg);
                    break;
                case "countdown":
                    lblTurnIndicator.setText("Comenzando en: " + msg.getInt("count"));
                    break;
                case "gameStarted":
                    lblTurnIndicator.setText("Juego iniciado");
                    break;
                case "gameResult":
                    lblTurnIndicator.setText("Resultado: " + msg.getString("result"));
                    break;
                case "clientMouseMoving":
                    handleOpponentMouse(msg);
                    break;
                default:
                    System.out.println("Mensaje desconocido: " + msg.toString());
            }
        });
    }

    private void parseServerData(JSONObject msg) {
        // Convertir JSON a GameState simple
        gameState = new GameState(); 
        gameState.setGame(new GameData());
        // Aquí podrías mapear el JSON a GameState real o usar un parser JSON->GameState

        // Actualizar UI de jugadores
        updatePlayerUI();

        // Redibujar tablero
        redraw();
    }

    private void updatePlayerUI() {
        lblPlayerName.setText(clientName);
        lblYourRole.setText("(" + role + ")");
        lblYourRole.setStyle("-fx-text-fill: " + (role.equals("R") ? "red" : "yellow"));

        // Aquí solo un ejemplo
        lblOpponentName.setText("Oponente");
        lblOpponentRole.setText("(" + (role.equals("R") ? "Y" : "R") + ")");
        lblOpponentRole.setStyle("-fx-text-fill: " + (role.equals("R") ? "yellow" : "red"));

        // Turno
        boolean myTurn = gameState != null && clientName.equals(gameState.getGame().getTurn());
        lblTurnIndicator.setText(myTurn ? "Tu turno" : "Esperando...");
    }

    private void handleOpponentMouse(JSONObject msg) {
        String player = msg.getString("player");
        if (!player.equals(clientName)) {
            opponentMouse.put(player, new double[]{msg.getDouble("x"), msg.getDouble("y")});
            redraw();
        }
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0,0,canvas.getWidth(), canvas.getHeight());

        // Fondo
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0,0,canvas.getWidth(), canvas.getHeight());

        // Dibujar tablero
        for(int r=0;r<rows;r++){
            for(int c=0;c<cols;c++){
                gc.setFill(Color.WHITE);
                gc.fillOval(c*cellSize+5,r*cellSize+5,cellSize-10,cellSize-10);
                gc.setStroke(Color.GRAY);
                gc.strokeOval(c*cellSize+5,r*cellSize+5,cellSize-10,cellSize-10);
            }
        }

        // TODO: dibujar fichas del gameState usando gameState.getGame().getBoard()
    }
}
