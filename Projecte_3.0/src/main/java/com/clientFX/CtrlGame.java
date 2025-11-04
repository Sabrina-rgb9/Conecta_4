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
import javafx.application.Platform;
import javafx.util.Duration;

import org.json.JSONObject;
import com.shared.GameState;
import com.shared.ClientInfo;
import com.shared.DragInfo;
import com.shared.Move;

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
    
    // ===== Zona de drop solo en la parte superior =====
    private static final int DROP_ZONE_HEIGHT = 40;
    private static final int DROP_ZONE_Y = BOARD_OFFSET_Y - DROP_ZONE_HEIGHT;

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

    private class GamePiece {
        String color;
        double originalX;
        double originalY;
        double currentX;
        double currentY;
        boolean isAvailable;
    }

    /**
     * ANIMACIÓN CON FÍSICA MÁS LENTA Y VISIBLE
     */
    private class FallingAnimation {
        String pieceColor;
        int targetColumn;
        int targetRow;
        double currentX, currentY;
        double progress = 0;
        boolean isActive = true;
        Timeline timeline;
        
        double finalX, finalY;
        double startY;
        
        public FallingAnimation(String color, int column, int targetRow) {
            this.pieceColor = color;
            this.targetColumn = column;
            this.targetRow = targetRow;
            
            // Calcular posiciones
            this.finalX = column * CELL_SIZE + BOARD_OFFSET_X + CELL_SIZE / 2;
            this.finalY = targetRow * CELL_SIZE + BOARD_OFFSET_Y + CELL_SIZE / 2;
            this.startY = BOARD_OFFSET_Y - CELL_SIZE * 3;
            
            this.currentX = finalX;
            this.currentY = startY;
            
            System.out.println("🎬 ANIMACIÓN INICIADA: " + color + " desde Y=" + startY + " hasta Y=" + finalY);
            startAnimation();
        }
        
        private void startAnimation() {
            timeline = new Timeline();
            KeyFrame keyFrame = new KeyFrame(
                Duration.millis(16),
                e -> updateAnimation()
            );
            timeline.getKeyFrames().add(keyFrame);
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();
        }
        
        private void updateAnimation() {
            if (!isActive) return;
            
            progress += 0.015; // Un poco más lento para mejor visualización
            
            if (progress >= 1.0) {
                progress = 1.0;
                completeAnimation();
                return; // ✅ IMPORTANTE: Salir aquí para no actualizar más
            }
            
            // Interpolación con ease-out para efecto más natural
            double easedProgress = 1 - Math.pow(1 - progress, 1.5);
            currentY = startY + (finalY - startY) * easedProgress;
            
            // Forzar redibujado
            Platform.runLater(() -> {
                if (Main.currentGameState != null) {
                    render(Main.currentGameState);
                }
            });
        }
        
        private void completeAnimation() {
            System.out.println("✅ ANIMACIÓN COMPLETADA para (" + targetColumn + "," + targetRow + ")");
            isActive = false;
            
            if (timeline != null) {
                timeline.stop();
                timeline = null;
            }
            
            // ✅ LIMPIAR AUTOMÁTICAMENTE después de un breve delay
            Platform.runLater(() -> {
                // Forzar un último render para asegurar que se vea la ficha final
                if (Main.currentGameState != null) {
                    render(Main.currentGameState);
                }
                
                // Programar limpieza después de 500ms
                Main.pauseDuring(500, () -> {
                    cleanupCompletedAnimations();
                });
            });
        }
        
        public void draw(GraphicsContext gc) {
            if (!isActive) return;
            
            double radius = (CELL_SIZE - 4) / 2;
            Color color = "R".equals(pieceColor) ? Color.RED : Color.YELLOW;
            
            // Efecto de sombra durante la caída
            double shadowOpacity = 0.2 * (1 - progress);
            gc.setFill(Color.rgb(0, 0, 0, shadowOpacity));
            gc.fillOval(currentX - radius + 2, finalY + 2, radius * 2, radius / 3);
            
            // Ficha principal
            gc.setFill(color.darker());
            gc.fillOval(currentX - radius + 2, currentY - radius + 2, radius * 2, radius * 2);
            
            gc.setFill(color);
            gc.fillOval(currentX - radius, currentY - radius, radius * 2, radius * 2);
            
            // Highlight
            gc.setFill(color.brighter());
            gc.fillOval(currentX - radius/2, currentY - radius/2, radius, radius);
            
            // Borde
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(2);
            gc.strokeOval(currentX - radius, currentY - radius, radius * 2, radius * 2);
        }
    }

    private void cleanupCompletedAnimations() {
        int before = activeAnimations.size();
        
        // ✅ LIMPIAR SOLO ANIMACIONES COMPLETADAS
        activeAnimations.removeIf(anim -> {
            boolean shouldRemove = !anim.isActive;
            if (shouldRemove) {
                System.out.println("🧹 Eliminando animación completada: (" + anim.targetColumn + "," + anim.targetRow + ")");
            }
            return shouldRemove;
        });
        
        int after = activeAnimations.size();
        int removed = before - after;
        
        if (removed > 0) {
            System.out.println("✅ Limpiadas " + removed + " animaciones completadas");
            
            // Forzar redibujado para limpiar visualmente
            if (Main.currentGameState != null) {
                render(Main.currentGameState);
            }
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
                Main.wsClient.safeSend(msg.toString());
            } catch (Exception e) {
                System.err.println("Error sending drag info: " + e.getMessage());
            }
        }
    }

    private void handleDropOnBoard(double x, double y) {
        // ✅ Solo permitir drop en la zona superior del tablero
        if (isDragging && isMyTurn() && isPointInDropZone(x, y)) {
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

    // ===== ANIMACIONES CON FÍSICA MÁS LENTA =====
    private void startFallingAnimation(String pieceColor, int column, int row) {
        System.out.println("🎬 SOLICITANDO ANIMACIÓN para " + pieceColor + " en (" + column + "," + row + ")");
        
        // ✅ VERIFICAR MÁS ESTRICTAMENTE para evitar duplicados
        boolean alreadyAnimating = activeAnimations.stream()
            .anyMatch(anim -> anim.isActive && 
                            anim.targetColumn == column && 
                            anim.targetRow == row);
        
        if (!alreadyAnimating) {
            System.out.println("🚀 CREANDO NUEVA ANIMACIÓN");
            FallingAnimation animation = new FallingAnimation(pieceColor, column, row);
            activeAnimations.add(animation);
        } else {
            System.out.println("⏸️  Ya hay una animación activa para esta posición, ignorando...");
        }
    }

    /**
     * Detecta movimientos nuevos usando lastMove del servidor
     */
    private void detectAndAnimateMoves(GameState gameState) {
        System.out.println("🎯 DETECTANDO MOVIMIENTOS PARA ANIMAR");
        
        if (gameState == null) {
            System.out.println("❌ GameState es null");
            return;
        }
        
        if (gameState.getGame() == null) {
            System.out.println("❌ GameData es null");
            return;
        }
        
        // ✅ USAR lastMove DEL SERVIDOR
        Move lastMove = gameState.getGame().getLastMove();
        
        System.out.println("📊 LastMove recibido: " + (lastMove != null ? 
            "Columna=" + lastMove.getCol() + ", Fila=" + lastMove.getRow() : "NULL"));
        
        if (lastMove != null) {
            int column = lastMove.getCol();
            int row = lastMove.getRow();
            
            System.out.println("📍 Posición lastMove: (" + column + ", " + row + ")");
            
            // Verificar que la posición es válida
            if (column >= 0 && column < COLUMNS && row >= 0 && row < ROWS) {
                String[][] board = gameState.getGame().getBoard();
                if (board != null && row < board.length && column < board[row].length) {
                    String pieceColor = board[row][column];
                    
                    System.out.println("🎨 Color en posición: '" + pieceColor + "'");
                    
                    if (pieceColor != null && !pieceColor.trim().isEmpty()) {
                        System.out.println("🚀 INICIANDO ANIMACIÓN para " + pieceColor + " en (" + column + ", " + row + ")");
                        startFallingAnimation(pieceColor, column, row);
                    } else {
                        System.out.println("⚠️ Celda vacía en lastMove position");
                    }
                } else {
                    System.out.println("❌ Board inválido o posición fuera de rango");
                }
            } else {
                System.out.println("❌ Posición lastMove inválida: (" + column + ", " + row + ")");
            }
        } else {
            System.out.println("🔍 No hay último movimiento para animar");
            
            // ✅ DEBUG: Mostrar el estado completo del juego
            System.out.println("📋 Estado del juego:");
            System.out.println("  - Status: " + gameState.getGame().getStatus());
            System.out.println("  - Turn: " + gameState.getGame().getTurn());
            System.out.println("  - Winner: " + gameState.getGame().getWinner());
            
            // Mostrar el tablero actual
            String[][] board = gameState.getGame().getBoard();
            if (board != null) {
                System.out.println("  - Board: " + Arrays.deepToString(board));
            }
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
        
        // Limpiar animaciones completadas antes de procesar nuevo estado
        cleanupCompletedAnimations();
        
        // Guardar el estado anterior ANTES de actualizar
        String[][] previousBoard = getPreviousBoard();
        
        // Actualizar información del oponente
        updateOpponentInfo(gameState);
        updatePlayerInfo();
        
        // ⭐ ACTUALIZAR EL ESTADO ACTUAL PRIMERO
        Main.currentGameState = gameState;
        
        // ⭐ LUEGO detectar animaciones (con el estado anterior guardado)
        detectAndAnimateMoves(gameState);
        
        // Reinicializar fichas si es necesario
        if (shouldResetPieces(gameState)) {
            System.out.println("🔄 Reinicializando fichas...");
            initializePieces();
        }
        
        // Renderizar
        render(gameState);
        
        System.out.println("✅ Estado del juego actualizado. Animaciones activas: " + 
                        activeAnimations.stream().filter(anim -> anim.isActive).count());
    }

    public void updateOpponentDragInfo(boolean dragging, double x, double y, String color) {
        System.out.println("🎯 ACTUALIZANDO DRAG OPONENTE: " + 
                          dragging + " at (" + x + "," + y + ") color=" + color);
        
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
        
        // ✅ Dibujar zona de drop
        drawDropZone();
        
        drawBoard();
        
        // ✅ DIBUJAR FICHAS DEL TABLERO (EXCLUYENDO LAS QUE ESTÁN ANIMÁNDOSE)
        drawPieces(gameState);
        
        drawMouseCursors();
        drawHoverEffects();
        drawDraggedPieces();
        
        // ✅ DIBUJAR ANIMACIONES ACTIVAS (ENCIMA de las fichas estáticas)
        drawActiveAnimations();
        
        drawWinLine(gameState);
    }

    // ✅ Dibujar zona de drop en la parte superior
    private void drawDropZone() {
        // Fondo semitransparente de la zona de drop
        gc.setFill(Color.rgb(0, 255, 0, 0.1));
        gc.fillRect(BOARD_OFFSET_X, DROP_ZONE_Y, COLUMNS * CELL_SIZE, DROP_ZONE_HEIGHT);
        
        // Borde de la zona de drop
        gc.setStroke(Color.rgb(0, 200, 0, 0.5));
        gc.setLineWidth(2);
        gc.strokeRect(BOARD_OFFSET_X, DROP_ZONE_Y, COLUMNS * CELL_SIZE, DROP_ZONE_HEIGHT);
        
        // Texto indicativo
        gc.setFill(Color.GREEN);
        gc.fillText("Zona de Soltar", BOARD_OFFSET_X + 10, DROP_ZONE_Y + 20);
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
        
        // ✅ OBTENER POSICIONES QUE ESTÁN SIENDO ANIMADAS
        Set<String> animatingPositions = new HashSet<>();
        for (FallingAnimation anim : activeAnimations) {
            if (anim.isActive) {
                String key = anim.targetColumn + "," + anim.targetRow;
                animatingPositions.add(key);
            }
        }
        
        for (int col = 0; col < COLUMNS; col++) {
            for (int row = 0; row < ROWS; row++) {
                // ✅ NO DIBUJAR SI ESTÁ SIENDO ANIMADA
                String positionKey = col + "," + row;
                if (animatingPositions.contains(positionKey)) {
                    continue; // Saltar esta posición - se dibuja en la animación
                }
                
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
        
        // ✅ Solo mostrar highlight en la zona de drop
        if (isPointInDropZone(x, y)) {
            int hoverColumn = getColumnFromX(x);
            if (isValidColumn(hoverColumn)) {
                Color highlightColor = isLocal ? 
                    Color.rgb(0, 255, 0, 0.3) : Color.rgb(255, 0, 0, 0.2);
                gc.setFill(highlightColor);
                gc.fillRect(hoverColumn * CELL_SIZE + BOARD_OFFSET_X, DROP_ZONE_Y, CELL_SIZE, DROP_ZONE_HEIGHT);
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
        // ✅ Solo mostrar hover effects en la zona de drop
        if (isMyTurn() && !isDragging && isPointInDropZone(myMouseX, myMouseY)) {
            int hoverColumn = getColumnFromX(myMouseX);
            if (isValidColumn(hoverColumn)) {
                gc.setFill(Color.rgb(0, 255, 0, 0.2));
                gc.fillRect(hoverColumn * CELL_SIZE + BOARD_OFFSET_X, DROP_ZONE_Y, CELL_SIZE, DROP_ZONE_HEIGHT);
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

    // ✅ Verificar si el punto está en la zona de drop
    private boolean isPointInDropZone(double x, double y) {
        return x >= BOARD_OFFSET_X && 
               x <= BOARD_OFFSET_X + COLUMNS * CELL_SIZE &&
               y >= DROP_ZONE_Y && 
               y <= DROP_ZONE_Y + DROP_ZONE_HEIGHT;
    }

    private boolean isPointOverBoard(double x, double y) {
        return x >= BOARD_OFFSET_X && x <= BOARD_OFFSET_X + COLUMNS * CELL_SIZE &&
               y >= BOARD_OFFSET_Y && y <= BOARD_OFFSET_Y + ROWS * CELL_SIZE;
    }

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