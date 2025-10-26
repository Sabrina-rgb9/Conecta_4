package com.clientFX;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import com.shared.GameState;
import com.shared.ClientInfo;
import java.net.URL;
import java.util.ResourceBundle;

public class CtrlGame implements Initializable {

    @FXML
    private Canvas canvas;
    
    private GraphicsContext gc;
    private static final int COLUMNS = 7;
    private static final int ROWS = 6;
    private static final int CELL_SIZE = 60;
    private static final int BOARD_OFFSET_X = 50;
    private static final int BOARD_OFFSET_Y = 50;
    
    // Variables para tracking del mouse
    private double myMouseX = 0;
    private double myMouseY = 0;
    private double opponentMouseX = 0;
    private double opponentMouseY = 0;
    private String opponentName = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        gc = canvas.getGraphicsContext2D();
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        // Trackear movimiento del mouse local
        canvas.setOnMouseMoved(this::handleMouseMove);
        canvas.setOnMouseDragged(this::handleMouseMove);
        
        // Click para jugar (mantener funcionalidad existente)
        canvas.setOnMouseClicked(this::handleMouseClick);
    }
    
    private void handleMouseMove(MouseEvent event) {
        myMouseX = event.getX();
        myMouseY = event.getY();
        
        // Enviar posición al servidor
        Main.sendMouseMove(myMouseX, myMouseY);
        
        // Redibujar para actualizar cursores
        if (Main.currentGameState != null) {
            render(Main.currentGameState);
        }
    }
    
    private void handleMouseClick(MouseEvent event) {
        if (isMyTurn()) {
            int column = getColumnFromX(event.getX());
            if (isValidColumn(column)) {
                Main.sendPlay(column);
            }
        }
    }
    
    public void updateGameState(GameState gameState) {
        // Actualizar posición del oponente
        updateOpponentMousePosition(gameState);
        
        // Renderizar el juego
        render(gameState);
    }
    
    private void updateOpponentMousePosition(GameState gameState) {
        if (gameState.getClientsList() != null) {
            for (ClientInfo client : gameState.getClientsList()) {
                if (!client.getName().equals(Main.playerName)) {
                    opponentMouseX = client.getMouseX();
                    opponentMouseY = client.getMouseY();
                    opponentName = client.getName();
                    break;
                }
            }
        }
    }
    
    private void render(GameState gameState) {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        drawBoard();
        drawPieces(gameState);
        drawMouseCursors();
        drawHoverEffects();
        drawWinLine(gameState);
    }
    
    private void drawMouseCursors() {
        // Dibujar cursor local (verde)
        drawCursor(myMouseX, myMouseY, Color.LIMEGREEN, "Tú");
        
        // Dibujar cursor del oponente (rojo) si está dentro del canvas
        if (opponentMouseX > 0 && opponentMouseY > 0 && 
            opponentMouseX < canvas.getWidth() && opponentMouseY < canvas.getHeight()) {
            drawCursor(opponentMouseX, opponentMouseY, Color.RED, opponentName);
        }
    }
    
    private void drawCursor(double x, double y, Color color, String label) {
        // Círculo del cursor
        gc.setFill(color);
        gc.fillOval(x - 5, y - 5, 10, 10);
        
        // Borde del cursor
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeOval(x - 5, y - 5, 10, 10);
        
        // Línea de referencia (opcional)
        gc.setStroke(color.deriveColor(0, 1, 1, 0.5));
        gc.setLineWidth(1);
        gc.strokeLine(x, 0, x, canvas.getHeight());
        gc.strokeLine(0, y, canvas.getWidth(), y);
        
        // Etiqueta con nombre
        gc.setFill(color);
        gc.fillText(label, x + 8, y - 8);
    }
    
    private void drawBoard() {
        gc.setFill(Color.BLUE);
        gc.fillRect(BOARD_OFFSET_X - 5, BOARD_OFFSET_Y - 5, 
                   COLUMNS * CELL_SIZE + 10, ROWS * CELL_SIZE + 10);
        
        for (int col = 0; col < COLUMNS; col++) {
            for (int row = 0; row < ROWS; row++) {
                double x = col * CELL_SIZE + BOARD_OFFSET_X;
                double y = row * CELL_SIZE + BOARD_OFFSET_Y;
                
                gc.setFill(Color.WHITE);
                gc.fillOval(x + 2, y + 2, CELL_SIZE - 4, CELL_SIZE - 4);
            }
        }
    }
    
    private void drawPieces(GameState gameState) {
        if (gameState.getGame() == null || gameState.getGame().getBoard() == null) return;
        
        String[][] board = gameState.getGame().getBoard();
        for (int col = 0; col < COLUMNS; col++) {
            for (int row = 0; row < ROWS; row++) {
                String cell = board[row][col];
                if (cell != null && !cell.trim().isEmpty()) {
                    drawPiece(col, row, cell);
                }
            }
        }
    }
    
    private void drawPiece(int col, int row, String piece) {
        double x = col * CELL_SIZE + BOARD_OFFSET_X + 2;
        double y = row * CELL_SIZE + BOARD_OFFSET_Y + 2;
        double radius = (CELL_SIZE - 4) / 2;
        
        Color color = "R".equals(piece) ? Color.RED : Color.YELLOW;
        
        // Sombra
        gc.setFill(color.darker());
        gc.fillOval(x + 2, y + 2, radius * 2, radius * 2);
        
        // Pieza principal
        gc.setFill(color);
        gc.fillOval(x, y, radius * 2, radius * 2);
        
        // Highlight
        gc.setFill(color.brighter());
        gc.fillOval(x + radius/2, y + radius/2, radius, radius);
    }
    
    private void drawHoverEffects() {
        // Efecto de hover para columna (solo si es tu turno)
        if (isMyTurn() && myMouseX >= BOARD_OFFSET_X && 
            myMouseX <= BOARD_OFFSET_X + COLUMNS * CELL_SIZE) {
            
            int hoverColumn = getColumnFromX(myMouseX);
            if (isValidColumn(hoverColumn)) {
                gc.setFill(Color.rgb(0, 255, 0, 0.3));
                gc.fillRect(hoverColumn * CELL_SIZE + BOARD_OFFSET_X, BOARD_OFFSET_Y, 
                           CELL_SIZE, ROWS * CELL_SIZE);
            }
        }
    }
    
    private void drawWinLine(GameState gameState) {
        // Implementar línea de victoria si es necesario
    }
    
    private int getColumnFromX(double x) {
        return (int) ((x - BOARD_OFFSET_X) / CELL_SIZE);
    }
    
    private boolean isValidColumn(int column) {
        return column >= 0 && column < COLUMNS;
    }
    
    private boolean isMyTurn() {
        return Main.currentGameState != null && 
               Main.currentGameState.getGame() != null &&
               Main.playerName.equals(Main.currentGameState.getGame().getTurn());
    }
}