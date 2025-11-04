package com.clientFX;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform; // ⭐ IMPORTANTE: Añadir este import
import javafx.util.Duration;

import org.json.JSONObject;
import com.shared.GameState;
import com.shared.ClientInfo;
import com.shared.DragInfo;

import java.net.URL;
import java.util.*;

public class CtrlGame implements Initializable {

    // ===== CONSTANTES =====
    private static final int COLUMNS = 7;
    private static final int ROWS = 6;
    private static final int CELL_SIZE = 60;
    private static final int BOARD_OFFSET_X = 50;
    private static final int BOARD_OFFSET_Y = 50;
    private static final int PIECE_RADIUS = 20;
    private static final int PIECE_SPACING = 25;
    private static final int PIECES_PER_ROW = 4;

    // ===== COMPONENTES FXML =====
    @FXML private Canvas canvas;
    @FXML private Label lblPlayerName;
    @FXML private Label lblYourRole;
    @FXML private Label lblOpponentName;
    @FXML private Label lblOpponentRole;
    @FXML private Label lblTurnIndicator;
    @FXML private Pane paneYourPieces;
    @FXML private Pane paneOpponentPieces;

    // ===== VARIABLES DE RENDERIZADO =====
    private GraphicsContext gc;

    // ===== ESTADO DEL JUEGO =====
    private List<GamePiece> availablePieces = new ArrayList<>();
    private List<FallingAnimation> activeAnimations = new ArrayList<>();

    // ===== ESTADO LOCAL DEL JUGADOR =====
    private double myMouseX = 0;
    private double myMouseY = 0;
    private boolean isDragging = false;
    private String draggedPieceColor = "";

    // ===== ESTADO DEL OPONENTE =====
    private String opponentName = "";
    private double opponentMouseX = 0;
    private double opponentMouseY = 0;
    private boolean opponentIsDragging = false;
    private double opponentDragX = 0;
    private double opponentDragY = 0;
    private String opponentDragColor = "";

    // ===== CLASES INTERNAS =====

    /**
     * Representa una ficha del juego
     */
    private class GamePiece {
        String color;
        double originalX;
        double originalY;
        double currentX;
        double currentY;
        boolean isAvailable;
    }

    /**
     * Maneja la animación de caída de fichas
     */
    private class FallingAnimation {
        String pieceColor;
        int targetColumn;
        int targetRow;
        double currentY;
        double startY;
        double endY;
        boolean isActive;
        Timeline timeline;
        long startTime;
        final long DURATION = 1000; // 1 segundo completo para la caída
        
        public FallingAnimation(String color, int column, int targetRow, double startY, double endY) {
            this.pieceColor = color;
            this.targetColumn = column;
            this.targetRow = targetRow;
            this.startY = startY;
            this.currentY = startY;
            this.endY = endY;
            this.isActive = true;
            this.startTime = System.currentTimeMillis();
            
            System.out.println("🎬 CREANDO ANIMACIÓN: " + color + " en columna " + column + 
                              " desde Y=" + startY + " hasta Y=" + endY);
            
            startAnimation();
        }
        
        private void startAnimation() {
            timeline = new Timeline();
            KeyFrame keyFrame = new KeyFrame(
                Duration.millis(16), // ~60 FPS
                e -> updateAnimation()
            );
            timeline.getKeyFrames().add(keyFrame);
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
        }
        
        private void updateAnimation() {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - startTime;
            double progress = Math.min(1.0, (double) elapsed / DURATION);
            
            // Interpolación suave (ease-out)
            double easedProgress = 1 - Math.pow(1 - progress, 3);
            currentY = startY + (endY - startY) * easedProgress;
            
            if (progress >= 1.0) {
                currentY = endY;
                isActive = false;
                timeline.stop();
                System.out.println("✅ ANIMACIÓN COMPLETADA: " + pieceColor + " en columna " + targetColumn);
            }
            
            // Forzar redibujado en el hilo de JavaFX
            Platform.runLater(() -> {
                if (Main.currentGameState != null) {
                    render(Main.currentGameState);
                }
            });
        }
        
        public void draw(GraphicsContext gc) {
            if (!isActive) return;
            
            double x = targetColumn * CELL_SIZE + BOARD_OFFSET_X + CELL_SIZE / 2;
            double radius = (CELL_SIZE - 4) / 2;
            Color color = "R".equals(pieceColor) ? Color.RED : Color.YELLOW;
            
            // Dibujar ficha animada con efecto de sombra
            gc.setFill(color.darker());
            gc.fillOval(x - radius + 2, currentY - radius + 2, radius * 2, radius * 2);
            
            gc.setFill(color);
            gc.fillOval(x - radius, currentY - radius, radius * 2, radius * 2);
            
            gc.setFill(color.brighter());
            gc.fillOval(x - radius/2, currentY - radius/2, radius, radius);
            
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(2);
            gc.strokeOval(x - radius, currentY - radius, radius * 2, radius * 2);
            
            // Dibujar trazo de la caída (efecto visual)
            gc.setStroke(Color.rgb(0, 0, 0, 0.3));
            gc.setLineWidth(1);
            gc.strokeLine(x, startY, x, currentY);
        }
    }

    // ===== MÉTODOS DE INICIALIZACIÓN =====

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        gc = canvas.getGraphicsContext2D();
        initializePieces();
        setupEventHandlers();
        updatePlayerInfo();
    }

    private void initializePieces() {
        availablePieces.clear();
        paneYourPieces.getChildren().clear();
        paneOpponentPieces.getChildren().clear();
        
        initializePlayerPieces(Main.myRole, paneYourPieces, true);
        initializePlayerPieces(getOpponentRole(), paneOpponentPieces, false);
    }

    private void initializePlayerPieces(String color, Pane pane, boolean isLocal) {
        for (int i = 0; i < 21; i++) {
            GamePiece piece = new GamePiece();
            piece.color = color;
            
            int row = i / PIECES_PER_ROW;
            int col = i % PIECES_PER_ROW;
            piece.originalX = 15 + col * PIECE_SPACING;
            piece.originalY = 15 + row * PIECE_SPACING;
            piece.currentX = piece.originalX;
            piece.currentY = piece.originalY;
            piece.isAvailable = true;
            
            if (isLocal) availablePieces.add(piece);
            
            pane.getChildren().add(createPieceCircle(piece, isLocal));
        }
    }

    private Circle createPieceCircle(GamePiece piece, boolean isLocal) {
        Circle circle = new Circle();
        circle.setCenterX(piece.originalX);
        circle.setCenterY(piece.originalY);
        circle.setRadius(PIECE_RADIUS);
        
        Color pieceColor = "R".equals(piece.color) ? Color.RED : Color.YELLOW;
        circle.setFill(pieceColor);
        circle.setStroke(Color.BLACK);
        circle.setStrokeWidth(1);
        
        if (!isLocal) {
            circle.setOpacity(0.6);
        } else {
            circle.setOnMousePressed(event -> handlePieceMousePressed(event, piece));
            circle.setOnMouseDragged(event -> handlePieceMouseDragged(event, piece));
            circle.setOnMouseReleased(event -> handlePieceMouseReleased(event, piece));
            circle.setCursor(Cursor.OPEN_HAND);
        }
        
        return circle;
    }

    private void setupEventHandlers() {
        canvas.setOnMouseMoved(this::handleCanvasMouseMove);
        canvas.setOnMouseDragged(this::handleCanvasMouseDrag);
        canvas.setOnMouseReleased(this::handleCanvasMouseReleased);
    }

    // ===== MANEJO DE EVENTOS =====

    private void handleCanvasMouseMove(MouseEvent event) {
        myMouseX = event.getX();
        myMouseY = event.getY();
        Main.sendMouseMove(myMouseX, myMouseY);
        if (Main.currentGameState != null) render(Main.currentGameState);
    }

    private void handleCanvasMouseDrag(MouseEvent event) {
        if (isDragging) {
            myMouseX = event.getX();
            myMouseY = event.getY();
            sendDragInfo(true, myMouseX, myMouseY, draggedPieceColor);
            if (Main.currentGameState != null) render(Main.currentGameState);
        } else {
            myMouseX = event.getX();
            myMouseY = event.getY();
            Main.sendMouseMove(myMouseX, myMouseY);
        }
    }

    private void handleCanvasMouseReleased(MouseEvent event) {
        if (isDragging) handleDropOnBoard(event.getX(), event.getY());
    }

    private void handlePieceMousePressed(MouseEvent event, GamePiece piece) {
        if (!isMyTurn() || isDragging) return;
        
        isDragging = true;
        draggedPieceColor = piece.color;
        
        javafx.geometry.Point2D sceneCoords = paneYourPieces.localToScene(event.getX(), event.getY());
        javafx.geometry.Point2D canvasCoords = canvas.sceneToLocal(sceneCoords);
        
        myMouseX = canvasCoords.getX();
        myMouseY = canvasCoords.getY();
        sendDragInfo(true, myMouseX, myMouseY, draggedPieceColor);
        
        event.consume();
    }

    private void handlePieceMouseDragged(MouseEvent event, GamePiece piece) {
        if (isDragging) {
            javafx.geometry.Point2D sceneCoords = paneYourPieces.localToScene(event.getX(), event.getY());
            javafx.geometry.Point2D canvasCoords = canvas.sceneToLocal(sceneCoords);
            
            myMouseX = canvasCoords.getX();
            myMouseY = canvasCoords.getY();
            sendDragInfo(true, myMouseX, myMouseY, draggedPieceColor);
            
            if (Main.currentGameState != null) render(Main.currentGameState);
        }
        event.consume();
    }

    private void handlePieceMouseReleased(MouseEvent event, GamePiece piece) {
        if (isDragging) {
            javafx.geometry.Point2D sceneCoords = paneYourPieces.localToScene(event.getX(), event.getY());
            javafx.geometry.Point2D canvasCoords = canvas.sceneToLocal(sceneCoords);
            handleDropOnBoard(canvasCoords.getX(), canvasCoords.getY());
        }
        event.consume();
    }

    // ===== LÓGICA DE DRAG & DROP =====

    private void sendDragInfo(boolean dragging, double x, double y, String color) {
        if (Main.wsClient != null && Main.wsClient.isOpen()) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "clientDragPiece");
                msg.put("isDragging", dragging);
                msg.put("x", x);
                msg.put("y", y);
                msg.put("pieceColor", color);
                msg.put("player", Main.playerName);
                Main.wsClient.safeSend(msg.toString());
                System.out.println("Enviando drag: " + msg.toString());
            } catch (Exception e) {
                System.err.println("Error sending drag info: " + e.getMessage());
            }
        }
    }

    private void handleDropOnBoard(double x, double y) {
        if (isDragging && isMyTurn() && isPointOverBoard(x, y)) {
            int column = getColumnFromX(x);
            if (isValidColumn(column)) {
                Main.sendPlay(column);
                markPieceAsUsed();
            }
        }
        
        isDragging = false;
        sendDragInfo(false, 0, 0, "");
        if (Main.currentGameState != null) render(Main.currentGameState);

    }

    private void markPieceAsUsed() {
        for (GamePiece piece : availablePieces) {
            if (piece.isAvailable && piece.color.equals(draggedPieceColor)) {
                piece.isAvailable = false;
                updatePieceVisual(piece, paneYourPieces);
                break;
            }
        }
    }

    private void updatePieceVisual(GamePiece piece, Pane pane) {
        for (javafx.scene.Node node : pane.getChildren()) {
            if (node instanceof Circle) {
                Circle circle = (Circle) node;
                if (Math.abs(circle.getCenterX() - piece.originalX) < 1 && 
                    Math.abs(circle.getCenterY() - piece.originalY) < 1) {
                    circle.setOpacity(0.3);
                    circle.setCursor(Cursor.DEFAULT);
                    break;
                }
            }
        }
    }

    // ===== ANIMACIONES =====

    private void startFallingAnimation(String pieceColor, int column, int row) {
        System.out.println("🎬 INICIANDO ANIMACIÓN para " + pieceColor + " en columna " + column + ", fila " + row);
        
        // Calcular coordenadas - empezar más arriba para mejor efecto visual
        double startY = BOARD_OFFSET_Y - CELL_SIZE * 2; // Empieza 2 celdas arriba del tablero
        double endY = row * CELL_SIZE + BOARD_OFFSET_Y + CELL_SIZE / 2;
        
        System.out.println("📏 Coordenadas animación: desde Y=" + startY + " hasta Y=" + endY);
        
        // Verificar que no haya ya una animación para esta posición
        boolean alreadyAnimating = activeAnimations.stream()
            .anyMatch(anim -> anim.targetColumn == column && anim.targetRow == row);
        
        if (!alreadyAnimating) {
            // Crear y añadir animación
            FallingAnimation animation = new FallingAnimation(pieceColor, column, row, startY, endY);
            activeAnimations.add(animation);
            
            // Limpiar animaciones completadas después de un tiempo
            Main.pauseDuring(2000, () -> {
                int before = activeAnimations.size();
                activeAnimations.removeIf(anim -> !anim.isActive);
                int after = activeAnimations.size();
                if (before != after) {
                    System.out.println("🧹 Limpiadas " + (before - after) + " animaciones completadas");
                }
            });
        } else {
            System.out.println("⚠️ Ya hay una animación en curso para esta posición");
        }
    }

    /**
     * Detecta movimientos nuevos y inicia animaciones
     */
    private void detectAndAnimateMoves(GameState gameState, String[][] previousBoard) {
        if (gameState.getGame() == null || gameState.getGame().getBoard() == null) {
            System.out.println("❌ No hay datos del tablero para animar");
            return;
        }
        
        String[][] currentBoard = gameState.getGame().getBoard();
        int newPiecesFound = 0;
        
        System.out.println("🔍 Buscando movimientos para animar...");

        for (int col = 0; col < COLUMNS; col++) {
            for (int row = 0; row < ROWS; row++) {
                String currentCell = currentBoard[row][col];
                String previousCell = (previousBoard != null && row < previousBoard.length && col < previousBoard[row].length) 
                                    ? previousBoard[row][col] : null;

                if (isNewPiece(currentCell, previousCell)) {
                    System.out.println("🆕 Ficha nueva detectada: " + currentCell + " en (" + col + "," + row + ")");
                    startFallingAnimation(currentCell, col, row);
                    newPiecesFound++;
                }
            }
        }
        
        if (newPiecesFound > 0) {
            System.out.println("🎯 Total de fichas nuevas para animar: " + newPiecesFound);
        }
    }


    /**
     * Obtiene el tablero anterior del estado del juego
     */
    private String[][] getPreviousBoard() {
        if (Main.currentGameState == null || Main.currentGameState.getGame() == null) {
            return null;
        }
        return Main.currentGameState.getGame().getBoard();
    }

    /**
     * Verifica si una celda contiene una ficha nueva
     */
    private boolean isNewPiece(String currentCell, String previousCell) {
        // La celda actual debe tener una ficha
        if (currentCell == null || currentCell.trim().isEmpty()) {
            return false;
        }
        
        // La celda anterior debe estar vacía o ser diferente
        if (previousCell == null || previousCell.trim().isEmpty()) {
            return true;
        }
        
        return !currentCell.equals(previousCell);
    }

    // ===== ACTUALIZACIÓN DE ESTADO =====

    public void updateGameState(GameState gameState) {
        System.out.println("🔄 ACTUALIZANDO ESTADO DEL JUEGO");
        
        // Guardar el estado anterior ANTES de actualizar
        // String[][] previousBoard = getPreviousBoard();
        
        // Actualizar información del oponente
        updateOpponentInfo(gameState);
        updatePlayerInfo();

        // Detectar si el oponente se desconectó
        checkOpponentDisconnected(gameState);
        
        // ⭐ ACTUALIZAR EL ESTADO ACTUAL PRIMERO
        // Main.currentGameState = gameState;
        
        // ⭐ LUEGO detectar animaciones (con el estado anterior guardado)
        // detectAndAnimateMoves(gameState);

        // Guardar copia del tablero anterior
        String[][] previousBoard = null;
        if (Main.currentGameState != null && Main.currentGameState.getGame() != null) {
            String[][] board = Main.currentGameState.getGame().getBoard();
            if (board != null) {
                previousBoard = new String[ROWS][COLUMNS];
                for (int r = 0; r < ROWS; r++) {
                    previousBoard[r] = Arrays.copyOf(board[r], COLUMNS);
                }
            }
        }

        // Actualizar el estado actual
        Main.currentGameState = gameState;

        // Detectar animaciones usando el tablero anterior
        detectAndAnimateMoves(gameState, previousBoard);

        
        // Reinicializar fichas si es necesario
        if (shouldResetPieces(gameState)) {
            System.out.println("🔄 Reinicializando fichas...");
            initializePieces();
        }
        
        // Renderizar
        render(gameState);
        
        System.out.println("✅ Estado del juego actualizado");
    }

    private void checkOpponentDisconnected(GameState gameState) {
        boolean opponentStillConnected = false;
        
        if (gameState.getClientsList() != null) {
            for (ClientInfo client : gameState.getClientsList()) {
                if (!client.getName().equals(Main.playerName)) {
                    opponentStillConnected = true;
                    break;
                }
            }
        }
        
        if (!opponentStillConnected) {
            Platform.runLater(() -> showOpponentDisconnectedDialog());
        }
    }

    private void showOpponentDisconnectedDialog() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Jugador desconectado");
        alert.setHeaderText(null);
        alert.setContentText("El otro jugador se ha desconectado.");
        
        javafx.scene.control.ButtonType btnMenu = new javafx.scene.control.ButtonType("Volver al menú principal");
        alert.getButtonTypes().setAll(btnMenu);
        
        alert.showAndWait().ifPresent(response -> {
            if (response == btnMenu) {
                // Volver a la vista de configuración
                UtilsViews.setView("ViewConfig");
            }
        });
    }

    public void updateOpponentDragInfo(boolean dragging, double x, double y, String color) {
        opponentIsDragging = dragging;
        opponentDragX = x;
        opponentDragY = y;
        opponentDragColor = color;
        
        if (Main.currentGameState != null) render(Main.currentGameState);
    }

    public void resetGameState() {
        isDragging = false;
        draggedPieceColor = "";
        myMouseX = 0;
        myMouseY = 0;
        opponentIsDragging = false;
        opponentDragX = 0;
        opponentDragY = 0;
        opponentDragColor = "";
        
        activeAnimations.clear();
        initializePieces();
        
        if (gc != null) gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        updatePlayerInfo();
    }

    // ===== RENDERIZADO =====

    public void render(GameState gameState) {
        // Asegurarse de que estamos en el hilo de JavaFX
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> render(gameState));
            return;
        }
        
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        drawBoard();
        drawPieces(gameState);
        drawMouseCursors();
        drawHoverEffects();
        drawDraggedPieces();
        
        // ⭐ DIBUJAR ANIMACIONES ANTES de las fichas normales para que se vean encima
        drawActiveAnimations();
        
        drawWinLine(gameState);

        for (FallingAnimation anim : activeAnimations) {
            if (anim.isActive) anim.draw(gc);
        }
        activeAnimations.removeIf(a -> !a.isActive);

        // Dibujar puntero del oponente
        if (opponentMouseX > 0 && opponentMouseY > 0) {
            gc.setStroke(Color.web("#00BFFF"));
            gc.setLineWidth(2);
            gc.strokeOval(opponentMouseX - 8, opponentMouseY - 8, 16, 16);
            gc.strokeLine(opponentMouseX, opponentMouseY - 10, opponentMouseX, opponentMouseY + 10);
            gc.strokeLine(opponentMouseX - 10, opponentMouseY, opponentMouseX + 10, opponentMouseY);
        }
        
        // Debug: mostrar info de animaciones activas
        if (!activeAnimations.isEmpty()) {
            gc.setFill(Color.BLACK);
            gc.fillText("Animaciones activas: " + activeAnimations.size(), 10, 20);
        }
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
                    drawPieceOnBoard(col, row, cell);
                }
            }
        }
    }

    private void drawPieceOnBoard(int col, int row, String piece) {
        double x = col * CELL_SIZE + BOARD_OFFSET_X + 2;
        double y = row * CELL_SIZE + BOARD_OFFSET_Y + 2;
        double radius = (CELL_SIZE - 4) / 2;
        Color color = "R".equals(piece) ? Color.RED : Color.YELLOW;
        
        gc.setFill(color.darker());
        gc.fillOval(x + 2, y + 2, radius * 2, radius * 2);
        gc.setFill(color);
        gc.fillOval(x, y, radius * 2, radius * 2);
        gc.setFill(color.brighter());
        gc.fillOval(x + radius/2, y + radius/2, radius, radius);
    }

    private void drawDraggedPieces() {
        if (isDragging && isMyTurn()) {
            drawDraggedPiece(myMouseX, myMouseY, draggedPieceColor, true);
        }
        if (opponentIsDragging && !isMyTurn()) {
            drawDraggedPiece(opponentDragX, opponentDragY, opponentDragColor, false);
        }
    }

    private void drawDraggedPiece(double x, double y, String color, boolean isLocal) {
        double radius = (CELL_SIZE - 4) / 2;
        Color pieceColor = "R".equals(color) ? Color.RED : Color.YELLOW;
        
        if (!isLocal) {
            pieceColor = Color.color(pieceColor.getRed(), pieceColor.getGreen(), pieceColor.getBlue(), 0.7);
        }
        
        gc.setFill(pieceColor.darker());
        gc.fillOval(x - radius + 2, y - radius + 2, radius * 2, radius * 2);
        gc.setFill(pieceColor);
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        gc.setFill(pieceColor.brighter());
        gc.fillOval(x - radius/2, y - radius/2, radius, radius);
        gc.setStroke(Color.BLACK);
        gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);
        
        if (isPointOverBoard(x, y)) {
            int hoverColumn = getColumnFromX(x);
            if (isValidColumn(hoverColumn)) {
                Color highlightColor = isLocal ? 
                    Color.rgb(0, 255, 0, 0.3) : Color.rgb(255, 0, 0, 0.2);
                gc.setFill(highlightColor);
                gc.fillRect(hoverColumn * CELL_SIZE + BOARD_OFFSET_X, BOARD_OFFSET_Y, CELL_SIZE, ROWS * CELL_SIZE);
            }
        }
    }

    private void drawMouseCursors() {
        drawCursor(myMouseX, myMouseY, Color.LIMEGREEN, "Tú");
        
        if (opponentMouseX > 0 && opponentMouseY > 0 && 
            opponentMouseX < canvas.getWidth() && opponentMouseY < canvas.getHeight()) {
            drawCursor(opponentMouseX, opponentMouseY, Color.RED, opponentName);
        }
    }

    private void drawCursor(double x, double y, Color color, String label) {
        gc.setFill(color);
        gc.fillOval(x - 5, y - 5, 10, 10);
        gc.setStroke(Color.WHITE);
        gc.strokeOval(x - 5, y - 5, 10, 10);
        gc.setFill(color);
        gc.fillText(label, x + 8, y - 8);
    }

    private void drawHoverEffects() {
        if (isMyTurn() && !isDragging && myMouseX >= BOARD_OFFSET_X && 
            myMouseX <= BOARD_OFFSET_X + COLUMNS * CELL_SIZE) {
            
            int hoverColumn = getColumnFromX(myMouseX);
            if (isValidColumn(hoverColumn)) {
                gc.setFill(Color.rgb(0, 255, 0, 0.2));
                gc.fillRect(hoverColumn * CELL_SIZE + BOARD_OFFSET_X, BOARD_OFFSET_Y, CELL_SIZE, ROWS * CELL_SIZE);
            }
        }
    }

    private void drawActiveAnimations() {
        for (FallingAnimation animation : activeAnimations) {
            animation.draw(gc);
        }
    }

    private void drawWinLine(GameState gameState) {
        // Para implementación futura
    }

    // ===== MÉTODOS AUXILIARES =====

    private void updateOpponentInfo(GameState gameState) {
        if (gameState.getClientsList() != null) {
            for (ClientInfo client : gameState.getClientsList()) {
                if (!client.getName().equals(Main.playerName)) {
                    opponentName = client.getName();
                    opponentMouseX = client.getMouseX();
                    opponentMouseY = client.getMouseY();
                    
                    DragInfo dragInfo = client.getDragInfo();
                    if (dragInfo != null) {
                        opponentIsDragging = dragInfo.isDragging();
                        opponentDragX = dragInfo.getDragX();
                        opponentDragY = dragInfo.getDragY();
                        opponentDragColor = dragInfo.getPieceColor();
                    }
                    break;
                }
            }
        }
    }

    private void updatePlayerInfo() {
        lblPlayerName.setText(Main.playerName);
        lblYourRole.setText("R".equals(Main.myRole) ? "(Rojo)" : "(Amarillo)");
        lblYourRole.setStyle("-fx-text-fill: " + ("R".equals(Main.myRole) ? "red" : "yellow") + ";");
        
        if (Main.currentGameState != null && Main.currentGameState.getClientsList() != null) {
            for (ClientInfo client : Main.currentGameState.getClientsList()) {
                if (!client.getName().equals(Main.playerName)) {
                    lblOpponentName.setText(client.getName());
                    lblOpponentRole.setText("R".equals(client.getRole()) ? "(Rojo)" : "(Amarillo)");
                    lblOpponentRole.setStyle("-fx-text-fill: " + ("R".equals(client.getRole()) ? "red" : "yellow") + ";");
                    break;
                }
            }
        }
        updateTurnIndicator();
    }

    private void updateTurnIndicator() {
        if (Main.currentGameState != null && Main.currentGameState.getGame() != null) {
            String turn = Main.currentGameState.getGame().getTurn();
            if (Main.playerName.equals(turn)) {
                lblTurnIndicator.setText("¡TE TOCA!");
                lblTurnIndicator.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            } else {
                lblTurnIndicator.setText("Torn del rival");
                lblTurnIndicator.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            }
        }
    }

    private String getOpponentRole() {
        return "R".equals(Main.myRole) ? "Y" : "R";
    }

    private boolean shouldResetPieces(GameState gameState) {
        return gameState.getGame() != null && 
               "playing".equals(gameState.getGame().getStatus()) &&
               (availablePieces.isEmpty() || !availablePieces.get(0).color.equals(Main.myRole));
    }

    private boolean isPointOverBoard(double x, double y) {
        return x >= BOARD_OFFSET_X && x <= BOARD_OFFSET_X + COLUMNS * CELL_SIZE &&
               y >= BOARD_OFFSET_Y && y <= BOARD_OFFSET_Y + ROWS * CELL_SIZE;
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

    // ===== MÉTODOS DE VISIBILIDAD DE VISTA =====
    
    public void onViewShown() {
        System.out.println("👀 Vista Game mostrada");
    }

    public void onViewHidden() {
        System.out.println("👻 Vista Game oculta");
    }
}