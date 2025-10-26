// com/clientFX/CtrlGame.java
package com.clientFX;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import com.shared.GameState;
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
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        gc = canvas.getGraphicsContext2D();
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        canvas.setOnMouseMoved(this::handleMouseMove);
        canvas.setOnMouseClicked(this::handleMouseClick);
        canvas.setOnMouseDragged(this::handleMouseDrag);
    }
    
    private void handleMouseMove(MouseEvent event) {
        // Enviar posición del mouse al servidor
        Main.sendMouseMove(event.getX(), event.getY());
    }
    
    private void handleMouseClick(MouseEvent event) {
        if (isMyTurn()) {
            int column = getColumnFromX(event.getX());
            if (isValidColumn(column)) {
                Main.sendPlay(column);
            }
        }
    }
    
    private void handleMouseDrag(MouseEvent event) {
        // Implementar drag & drop para fichas
        if (isMyTurn() && event.isPrimaryButtonDown()) {
            // Lógica de arrastre visual
        }
    }
    
    public void updateGameState(GameState gameState) {
        render(gameState);
    }
    
    private void render(GameState gameState) {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        drawBoard();
        drawPieces(gameState);
        drawHoverEffects(gameState);
        drawWinLine(gameState);
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
    
    private void drawHoverEffects(GameState gameState) {
        // Dibujar hover del oponente
        if (gameState.getClientsList() != null) {
            for (var client : gameState.getClientsList()) {
                if (!client.getName().equals(Main.playerName)) {
                    drawOpponentCursor(client.getMouseX(), client.getMouseY());
                }
            }
        }
        
        // Dibujar hover local si es nuestro turno
        if (isMyTurn()) {
            // Resaltar columna bajo el cursor
        }
    }
    
    private void drawOpponentCursor(double x, double y) {
        gc.setFill(Color.BLACK);
        gc.fillOval(x - 5, y - 5, 10, 10);
        gc.setStroke(Color.WHITE);
        gc.strokeOval(x - 5, y - 5, 10, 10);
    }
    
    private void drawWinLine(GameState gameState) {
        // Implementar línea para mostrar 4 en línea ganador
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