package com.clientFX;

import com.shared.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

public class CtrlGame {

    @FXML
    private Label lblPlayerName, lblOpponentName, lblTurnIndicator, lblYourRole, lblOpponentRole;

    @FXML
    private Canvas canvas;

    @FXML
    private VBox paneYourPieces, paneOpponentPieces;

    private GameState currentGameState;

    private static final int ROWS = 6;
    private static final int COLS = 7;
    private static final double CELL_SIZE = 80;

    public CtrlGame() {}

    @FXML
    public void initialize() {
        // Inicialización del canvas
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawEmptyBoard(gc);
    }

    /**
     * Maneja un mensaje JSON del servidor
     */
    public void handleMessage(org.json.JSONObject msg) {
        // Convertir a GameState
        GameState gameState = UtilsWS.convertToGameState(msg);
        this.currentGameState = gameState;

        Platform.runLater(() -> {
            updateUI();
        });
    }

    /**
     * Actualiza todos los elementos de la UI según el GameState
     */
    private void updateUI() {
        if (currentGameState == null) return;

        GameData gameData = currentGameState.getGame();
        if (gameData == null) return;

        // Actualizar clientes
        List<ClientInfo> clients = currentGameState.getClientsList();
        if (clients.size() >= 1) {
            ClientInfo you = clients.get(0);
            lblPlayerName.setText(you.getName());
            lblYourRole.setText("(" + you.getRole() + ")");
        }
        if (clients.size() >= 2) {
            ClientInfo opponent = clients.get(1);
            lblOpponentName.setText(opponent.getName());
            lblOpponentRole.setText("(" + opponent.getRole() + ")");
        }

        // Actualizar turno
        String turn = gameData.getTurn();
        lblTurnIndicator.setText(turn != null ? turn : "---");

        // Actualizar tablero
        drawBoard(gameData.getBoard());

        // Actualizar fichas (solo conteo simple)
        updatePieces(clients);

        // Aquí podrías agregar efectos según estado
        String status = gameData.getStatus();
        switch (status) {
            case "countdown":
                lblTurnIndicator.setText("Cuenta atrás...");
                break;
            case "playing":
                // Nada especial
                break;
            case "win":
                lblTurnIndicator.setText("Ganador: " + gameData.getWinner());
                break;
            case "draw":
                lblTurnIndicator.setText("Empate");
                break;
            case "waiting":
                lblTurnIndicator.setText("Esperando jugadores...");
                break;
        }
    }

    /**
     * Dibuja el tablero vacío
     */
    private void drawEmptyBoard(GraphicsContext gc) {
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, COLS * CELL_SIZE, ROWS * CELL_SIZE);

        gc.setFill(Color.WHITE);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                gc.fillOval(c * CELL_SIZE + 5, r * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
            }
        }
    }

    /**
     * Dibuja el tablero con las fichas colocadas
     */
    private void drawBoard(String[][] board) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawEmptyBoard(gc);

        if (board == null) return;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                String cell = board[r][c];
                if (cell != null && !cell.equals(" ")) {
                    if (cell.equals("R")) {
                        gc.setFill(Color.RED);
                    } else if (cell.equals("Y")) {
                        gc.setFill(Color.YELLOW);
                    } else {
                        gc.setFill(Color.GRAY);
                    }
                    gc.fillOval(c * CELL_SIZE + 5, r * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
                }
            }
        }
    }

    /**
     * Actualiza el panel de fichas disponibles (simple conteo)
     */
    private void updatePieces(List<ClientInfo> clients) {
        paneYourPieces.getChildren().clear();
        paneOpponentPieces.getChildren().clear();

        if (clients.size() >= 1) {
            ClientInfo you = clients.get(0);
            int remaining = countRemainingPieces(you.getRole());
            for (int i = 0; i < remaining; i++) {
                Label piece = new Label(you.getRole());
                piece.setTextFill(you.getRole().equals("R") ? Color.RED : Color.YELLOW);
                paneYourPieces.getChildren().add(piece);
            }
        }

        if (clients.size() >= 2) {
            ClientInfo opponent = clients.get(1);
            int remaining = countRemainingPieces(opponent.getRole());
            for (int i = 0; i < remaining; i++) {
                Label piece = new Label(opponent.getRole());
                piece.setTextFill(opponent.getRole().equals("R") ? Color.RED : Color.YELLOW);
                paneOpponentPieces.getChildren().add(piece);
            }
        }
    }

    /**
     * Calcula fichas restantes según el tablero
     */
    private int countRemainingPieces(String role) {
        if (currentGameState == null || currentGameState.getGame() == null) return 21;
        int used = 0;
        String[][] board = currentGameState.getGame().getBoard();
        for (String[] row : board) {
            for (String cell : row) {
                if (role.equals(cell)) used++;
            }
        }
        return 21 - used;
    }
}
