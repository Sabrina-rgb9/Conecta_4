package com.clientFX;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
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

import java.util.HashMap;
import java.util.Map;

public class CtrlGame {

    @FXML private Canvas canvas;
    @FXML private Label lblStatus;
    @FXML private Label lblTurn;
    @FXML private Pane paneFichas;
    @FXML private Pane paneRedPieces;
    @FXML private Pane paneYellowPieces;

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
    private String lastWinner = "";

    // Variables para arrastrar fichas
    private boolean dragging = false;
    private double dragStartX, dragStartY;
    private Color dragPieceColor;
    private int dragColumn = -1;
    private Timeline dropAnimation;

    @FXML
    public void initialize() {
        // Configurar tamaño del canvas
        canvas.setWidth(cols * cellSize);
        canvas.setHeight(rows * cellSize);

        // Eventos del canvas
        canvas.setOnMouseMoved(this::handleHover);
        canvas.setOnMouseClicked(this::handleClick);
        canvas.setOnMousePressed(this::handleDragStart);
        canvas.setOnMouseReleased(this::handleDragEnd);

        // Inicializar paneles de fichas
        initPiecePanels();

        // Redibujar cada vez que cambie el tamaño
        canvas.widthProperty().addListener((obs, old, newVal) -> redraw());
        canvas.heightProperty().addListener((obs, old, newVal) -> redraw());
    }

    private void initPiecePanels() {
        // Crear 5 fichas rojas y 5 amarillas (aleatorias)
        for (int i = 0; i < 5; i++) {
            createDraggablePiece(paneRedPieces, Color.RED);
            createDraggablePiece(paneYellowPieces, Color.YELLOW);
        }
    }

    private void createDraggablePiece(Pane parent, Color color) {
        double pieceSize = 30;
        javafx.scene.shape.Circle piece = new javafx.scene.shape.Circle(pieceSize / 2);
        piece.setFill(color);
        piece.setStroke(Color.BLACK);
        piece.setStrokeWidth(1);

        // Hacerla arrastrable
        piece.setOnMousePressed(e -> {
            if (clientName.equals(gameState.getString("turn"))) {
                dragging = true;
                dragStartX = e.getSceneX();
                dragStartY = e.getSceneY();
                dragPieceColor = color;
                // Mover la pieza al canvas (temporalmente)
                parent.getChildren().remove(piece);
                canvas.getParent().getChildrenUnmodifiable().add(piece);
                piece.setTranslateX(dragStartX - canvas.getLayoutX() - pieceSize / 2);
                piece.setTranslateY(dragStartY - canvas.getLayoutY() - pieceSize / 2);
            }
        });

        piece.setOnMouseDragged(e -> {
            if (dragging && dragPieceColor == color) {
                double dx = e.getSceneX() - dragStartX;
                double dy = e.getSceneY() - dragStartY;
                piece.setTranslateX(piece.getTranslateX() + dx);
                piece.setTranslateY(piece.getTranslateY() + dy);
                dragStartX = e.getSceneX();
                dragStartY = e.getSceneY();

                // Calcular columna bajo el mouse
                double cx = piece.getTranslateX() + pieceSize / 2;
                int col = (int) (cx / cellSize);
                if (col >= 0 && col < cols) {
                    dragColumn = col;
                } else {
                    dragColumn = -1;
                }
                redraw(); // Para resaltar la columna
            }
        });

        parent.getChildren().add(piece);
    }

    private void handleDragStart(MouseEvent e) {
        // No hacer nada aquí, ya se maneja en las fichas
    }

    private void handleDragEnd(MouseEvent e) {
        if (dragging) {
            dragging = false;
            // Si estamos sobre una columna válida, enviar jugada
            if (dragColumn >= 0 && dragColumn < cols) {
                sendPlay(dragColumn);
            }
            // Devolver la ficha al panel
            javafx.scene.shape.Circle piece = (javafx.scene.shape.Circle) canvas.getParent().getChildrenUnmodifiable().get(canvas.getParent().getChildrenUnmodifiable().size() - 1);
            canvas.getParent().getChildrenUnmodifiable().remove(piece);
            if (dragPieceColor == Color.RED) {
                paneRedPieces.getChildren().add(piece);
            } else {
                paneYellowPieces.getChildren().add(piece);
            }
            dragColumn = -1;
            redraw();
        }
    }

    private void handleHover(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        sendMouse(mouseX, mouseY);

        // Redibujar para resaltar la columna bajo el mouse
        redraw();
    }

    private void handleClick(MouseEvent e) {
        // Solo si es nuestro turno y no estamos arrastrando
        if (!dragging && gameState != null && clientName.equals(gameState.getString("turn"))) {
            int col = (int) (e.getX() / cellSize);
            if (col >= 0 && col < cols) {
                sendPlay(col);
            }
        }
    }

    private void sendPlay(int col) {
        if (Main.wsClient == null) return;
        JSONObject obj = new JSONObject();
        obj.put("type", "clientPlay");
        obj.put("col", col);
        Main.wsClient.safeSend(obj.toString());
    }

    private void sendMouse(double x, double y) {
        if (Main.wsClient == null) return;
        JSONObject obj = new JSONObject();
        obj.put("type", "clientMouseMoving");
        obj.put("x", x);
        obj.put("y", y);
        Main.wsClient.safeSend(obj.toString());
    }

    public void handleMessage(JSONObject msg) {
        if ("serverData".equals(msg.getString("type"))) {
            Platform.runLater(() -> {
                clientName = msg.getString("clientName");
                role = msg.getString("role");
                gameState = msg.getJSONObject("game");

                // Actualizar etiquetas
                String turn = gameState.getString("turn");
                lblTurn.setText(turn);
                String status = gameState.getString("status");
                if ("win".equals(status)) {
                    String winner = gameState.getString("winner");
                    lblStatus.setText(winner.equals(clientName) ? "Has guanyat!" : "Has perdut :(");
                    lastWinner = winner;
                } else if ("draw".equals(status)) {
                    lblStatus.setText("Empat!");
                } else {
                    lblStatus.setText("En joc...");
                }

                // Comprobar si hay una nueva jugada para animar
                if (gameState.has("lastMove") && !gameState.isNull("lastMove")) {
                    JSONObject lastMove = gameState.getJSONObject("lastMove");
                    int newRow = lastMove.getInt("row");
                    int newCol = lastMove.getInt("col");
                    if (newRow != lastMoveRow || newCol != lastMoveCol) {
                        lastMoveRow = newRow;
                        lastMoveCol = newCol;
                        animateDrop(newCol, newRow);
                    }
                }

                // Desactivar interacción si no es tu turno
                boolean myTurn = clientName.equals(turn);
                canvas.setDisable(!myTurn);
                if (myTurn) {
                    lblTurn.setText("Et toca jugar");
                    lblTurn.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                } else {
                    lblTurn.setText("Esperant...");
                    lblTurn.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                }

                // Actualizar el panel de fichas según el rol
                updatePiecePanels();

                redraw();
            });
        } else if ("clientMouseMoving".equals(msg.getString("type"))) {
            String player = msg.getString("player");
            if (!player.equals(clientName)) {
                Platform.runLater(() -> {
                    opponentMouse.put(player, new double[]{msg.getDouble("x"), msg.getDouble("y")});
                    redraw();
                });
            }
        }
    }

    private void updatePiecePanels() {
        // Limpiar paneles
        paneRedPieces.getChildren().clear();
        paneYellowPieces.getChildren().clear();

        // Re-crear fichas según el rol
        for (int i = 0; i < 5; i++) {
            if ("R".equals(role)) {
                createDraggablePiece(paneRedPieces, Color.RED);
                createDraggablePiece(paneYellowPieces, Color.YELLOW);
            } else {
                createDraggablePiece(paneRedPieces, Color.RED);
                createDraggablePiece(paneYellowPieces, Color.YELLOW);
            }
        }
    }

    private void animateDrop(int col, int targetRow) {
        if (dropAnimation != null) {
            dropAnimation.stop();
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        double startX = col * cellSize + cellSize / 2.0;
        double startY = -cellSize / 2.0; // Comienza desde arriba
        double endY = targetRow * cellSize + cellSize / 2.0;

        // Variable para la animación
        double[] currentY = {startY};

        dropAnimation = new Timeline(
            new KeyFrame(Duration.millis(500), e -> {
                currentY[0] = endY;
                redraw(); // Redibuja el estado final
            })
        );

        // Render loop para efecto fluido (opcional)
        Timeline renderLoop = new Timeline(
            new KeyFrame(Duration.millis(16), e -> {
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                redraw(); // Dibuja tablero y fichas
                // Dibuja ficha animada
                String piece = getPieceAt(targetRow, col);
                Color color = "R".equals(piece) ? Color.RED : Color.YELLOW;
                gc.setFill(color);
                double y = currentY[0];
                gc.fillOval(startX - cellSize/2 + 5, y - cellSize/2 + 5, cellSize - 10, cellSize - 10);
            })
        );
        renderLoop.setCycleCount(1);
        dropAnimation.play();
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Fondo
        gc.setFill(Color.LIGHTBLUE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Dibujar tablero (cel·las vacías)
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                // Resaltar columna bajo el mouse (local)
                if (c == dragColumn && dragging) {
                    gc.setFill(Color.rgb(255, 255, 0, 0.3)); // Amarillo suave
                    gc.fillRect(c * cellSize, 0, cellSize, canvas.getHeight());
                }

                // Resaltar columna bajo el mouse del oponente
                for (double[] pos : opponentMouse.values()) {
                    int colUnderOpponent = (int) (pos[0] / cellSize);
                    if (c == colUnderOpponent) {
                        gc.setStroke(Color.GRAY);
                        gc.setLineWidth(2);
                        gc.strokeRect(c * cellSize, 0, cellSize, canvas.getHeight());
                    }
                }

                // Cel·la vacía
                gc.setStroke(Color.GRAY);
                gc.setFill(Color.WHITE);
                gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                gc.strokeOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
            }
        }

        // Dibujar fichas del tablero
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
                    } else {
                        continue;
                    }

                    // Verificar si es parte de la victoria
                    if (isWinningCell(r, c)) {
                        gc.setGlobalAlpha(0.7);
                        gc.fillOval(c * cellSize + 5, r * cellSize + 5, cellSize - 10, cellSize - 10);
                        gc.setGlobalAlpha(1.0);
                        // Efecto de iluminación
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

        // Punter del contrincant (línea vertical)
        for (double[] pos : opponentMouse.values()) {
            gc.setStroke(Color.GRAY);
            gc.setLineWidth(2);
            gc.strokeLine(pos[0], 0, pos[0], canvas.getHeight());
            gc.setLineWidth(1);
        }
    }

    private String getPieceAt(int row, int col) {
        if (gameState == null) return " ";
        JSONArray board = gameState.getJSONArray("board");
        return board.getJSONArray(row).getString(col);
    }

    private boolean isWinningCell(int row, int col) {
        if (!"win".equals(gameState.getString("status"))) return false;
        // En un entorno real, el servidor debería enviar las coordenadas ganadoras
        // Por simplicidad, asumimos que cualquier celda con pieza en estado "win" es ganadora
        return true;
    }
}