package com.server;

import org.java_websocket.WebSocket;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.shared.GameState;
import com.shared.ClientInfo;
import com.shared.GameObject;
import com.shared.GameData;
import com.shared.Move;
import com.shared.DragInfo;

public class GameSession {
    private String sessionId;
    private WebSocket player1;
    private WebSocket player2;
    private String player1Name;
    private String player2Name;
    private String currentTurn;
    private String[][] board;
    private boolean gameStarted = false;
    private boolean gameFinished = false;
    private String winner = "";
    private List<GameObject> gameObjects;
    private boolean countdownInProgress = false;

    private Map<String, double[]> playerMousePositions = new ConcurrentHashMap<>();

    // Para tracking del drag & drop
    private Map<String, DragInfo> playerDragInfo = new ConcurrentHashMap<>();
    
    // Constantes del juego
    private static final int ROWS = 6;
    private static final int COLS = 7;
    
    public GameSession(String sessionId, WebSocket player1, String player1Name) {
        this.sessionId = sessionId;
        this.player1 = player1;
        this.player1Name = player1Name;
        this.currentTurn = player1Name;
        initializeBoard();
        initializeGameObjects();
        
        System.out.println("🎮 Nueva sesión: " + player1Name + " será ROJO (R)");
    }
    
    private void initializeBoard() {
        board = new String[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = " ";
            }
        }
    }
    
    private void initializeGameObjects() {
        gameObjects = new ArrayList<>();
        // Crear fichas para los jugadores
        for (int i = 0; i < 21; i++) {
            GameObject redPiece = new GameObject();
            redPiece.setId("R_" + i);
            redPiece.setX(610.0 + (i % 7) * 60);
            redPiece.setY(80.0 + (i / 7) * 60);
            redPiece.setRole("R");
            gameObjects.add(redPiece);
            
            GameObject yellowPiece = new GameObject();
            yellowPiece.setId("Y_" + i);
            yellowPiece.setX(610.0 + (i % 7) * 60);
            yellowPiece.setY(80.0 + (i / 7) * 60);
            yellowPiece.setRole("Y");
            gameObjects.add(yellowPiece);
        }
    }
    
    public void addPlayer2(WebSocket player2, String player2Name) {
        this.player2 = player2;
        this.player2Name = player2Name;
        this.gameStarted = true;
        this.countdownInProgress = true;
        
        System.out.println("🎮 Jugador 2 unido: " + player2Name + " será AMARILLO (Y)");
        System.out.println("🎯 Partida: " + player1Name + " (R) vs " + player2Name + " (Y)");
        
        // Enviar estado INMEDIATAMENTE con roles asignados
        broadcastGameState();
        
        // Luego iniciar countdown
        sendCountdown();
    }
    
    // NUEVO MÉTODO: Actualizar posición del mouse de un jugador
    public void updatePlayerMousePosition(String playerName, double x, double y) {
        if (playerMousePositions.containsKey(playerName)) {
            playerMousePositions.put(playerName, new double[]{x, y});
            System.out.println("Mouse actualizado - " + playerName + ": (" + x + ", " + y + ")");
            // Enviar update a ambos jugadores
            broadcastGameState();
        } else {
            System.out.println("x Jugador no encontrado en sesión: " + playerName);
        }
    }
    
    // NUEVO MÉTODO: Verificar si un jugador está en esta sesión
    public boolean hasPlayerWithName(String playerName) {
        return player1Name.equals(playerName) || (player2Name != null && player2Name.equals(playerName));
    }
    
    private void sendCountdown() {
        System.out.println("📤 [Sesión " + sessionId + "] Enviando countdown a jugadores");
        
        JSONObject countdownMsg = new JSONObject();
        countdownMsg.put("type", "countdown");
        countdownMsg.put("count", 3);
        
        broadcastToPlayers(countdownMsg.toString());
        System.out.println("✅ [Sesión " + sessionId + "] Countdown enviado");
        
        // Programar inicio del juego después del countdown
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("⏰ [Sesión " + sessionId + "] Timer terminado - iniciando juego");
                startGame();
            }
        }, 4000); // 4 segundos total
    }
    
    private void startGame() {
        this.countdownInProgress = false;
        this.gameStarted = true;
        
        System.out.println("¡Iniciando partida! Turno de: " + currentTurn);
        
        // Enviar estado con status: "playing"
        broadcastGameState();
    }
    
    public void makeMove(WebSocket player, int column) {
        if (gameFinished || !gameStarted) return;
        
        String playerName = getPlayerName(player);
        if (!playerName.equals(currentTurn)) return;
        
        // Encontrar la fila disponible en la columna
        int row = -1;
        for (int i = ROWS - 1; i >= 0; i--) {
            if (board[i][column].equals(" ")) {
                row = i;
                break;
            }
        }
        
        if (row == -1) return; // Columna llena
        
        // Hacer el movimiento
        String piece = playerName.equals(player1Name) ? "R" : "Y";
        board[row][column] = piece;
        
        // Verificar victoria
        if (checkWin(row, column, piece)) {
            gameFinished = true;
            winner = playerName;
            broadcastGameState();
            return;
        }
        
        // Verificar empate
        if (checkDraw()) {
            gameFinished = true;
            winner = "draw";
            broadcastGameState();
            return;
        }
        
        // Cambiar turno
        currentTurn = currentTurn.equals(player1Name) ? player2Name : player1Name;
        
        // Broadcast del nuevo estado
        broadcastGameState();
    }
    
    private boolean checkWin(int row, int col, String piece) {
        // Verificar horizontal
        int count = 0;
        for (int c = 0; c < COLS; c++) {
            count = board[row][c].equals(piece) ? count + 1 : 0;
            if (count >= 4) return true;
        }
        
        // Verificar vertical
        count = 0;
        for (int r = 0; r < ROWS; r++) {
            count = board[r][col].equals(piece) ? count + 1 : 0;
            if (count >= 4) return true;
        }
        
        // Verificar diagonal \
        count = 0;
        for (int r = row, c = col; r >= 0 && c >= 0; r--, c--) {
            if (board[r][c].equals(piece)) count++;
            else break;
        }
        for (int r = row + 1, c = col + 1; r < ROWS && c < COLS; r++, c++) {
            if (board[r][c].equals(piece)) count++;
            else break;
        }
        if (count >= 4) return true;
        
        // Verificar diagonal /
        count = 0;
        for (int r = row, c = col; r >= 0 && c < COLS; r--, c++) {
            if (board[r][c].equals(piece)) count++;
            else break;
        }
        for (int r = row + 1, c = col - 1; r < ROWS && c >= 0; r++, c--) {
            if (board[r][c].equals(piece)) count++;
            else break;
        }
        if (count >= 4) return true;
        
        return false;
    }
    
    private boolean checkDraw() {
        for (int c = 0; c < COLS; c++) {
            if (board[0][c].equals(" ")) {
                return false;
            }
        }
        return true;
    }
    
    public void broadcastGameState() {
        GameState gameState = createGameState();
        String gameStateJson = convertGameStateToJson(gameState);
        broadcastToPlayers(gameStateJson);
    }
    
    private GameState createGameState() {
        GameState gameState = new GameState();
        gameState.setType("serverData");
        
        // Usar getClientsList() que ahora incluye la información de drag
        gameState.setClientsList(getClientsList());
        gameState.setObjectsList(gameObjects);
        
        // Crear datos del juego
        GameData gameData = new GameData();
        
        if (gameFinished) {
            if (winner.equals("draw")) {
                gameData.setStatus("draw");
            } else {
                gameData.setStatus("win");
            }
        } else if (countdownInProgress) {
            gameData.setStatus("countdown");
        } else if (gameStarted) {
            gameData.setStatus("playing");
        } else {
            gameData.setStatus("waiting");
        }
        
        gameData.setBoard(board);
        gameData.setTurn(currentTurn);
        gameData.setWinner(winner);
        
        gameState.setGame(gameData);
        
        return gameState;
    }

    private List<ClientInfo> getClientsList() {
        List<ClientInfo> clients = new ArrayList<>();
        
        // Jugador 1
        ClientInfo client1 = new ClientInfo();
        client1.setName(player1Name);
        client1.setColor("RED");
        client1.setRole("R");
        
        // Asignar información de drag del jugador 1
        DragInfo dragInfo1 = playerDragInfo.get(player1Name);
        if (dragInfo1 != null) {
            client1.setDragInfo(dragInfo1);
        }
        
        clients.add(client1);
        
        // Jugador 2
        if (player2 != null) {
            ClientInfo client2 = new ClientInfo();
            client2.setName(player2Name);
            client2.setColor("YELLOW");
            client2.setRole("Y");
            
            // Asignar información de drag del jugador 2
            DragInfo dragInfo2 = playerDragInfo.get(player2Name);
            if (dragInfo2 != null) {
                client2.setDragInfo(dragInfo2);
            }
            
            clients.add(client2);
        }
        
        return clients;
    }
    
    private String convertGameStateToJson(GameState gameState) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "serverData");
            
            // Clients list
            JSONArray clientsArray = new JSONArray();
            for (ClientInfo client : gameState.getClientsList()) {
                JSONObject clientJson = new JSONObject();
                clientJson.put("name", client.getName());
                clientJson.put("color", client.getColor());
                clientJson.put("mouseX", client.getMouseX());
                clientJson.put("mouseY", client.getMouseY());
                clientJson.put("role", client.getRole());
                clientsArray.put(clientJson);
            }
            json.put("clientsList", clientsArray);
            
            // Objects list
            JSONArray objectsArray = new JSONArray();
            for (GameObject obj : gameState.getObjectsList()) {
                JSONObject objJson = new JSONObject();
                objJson.put("id", obj.getId());
                objJson.put("x", obj.getX());
                objJson.put("y", obj.getY());
                objJson.put("role", obj.getRole());
                objectsArray.put(objJson);
            }
            json.put("objectsList", objectsArray);
            
            // Game data
            if (gameState.getGame() != null) {
                JSONObject gameJson = new JSONObject();
                GameData gameData = gameState.getGame();
                
                gameJson.put("status", gameData.getStatus());
                gameJson.put("turn", gameData.getTurn());
                gameJson.put("winner", gameData.getWinner());
                
                // Board
                JSONArray boardArray = new JSONArray();
                String[][] board = gameData.getBoard();
                if (board != null) {
                    for (String[] row : board) {
                        JSONArray rowArray = new JSONArray();
                        for (String cell : row) {
                            rowArray.put(cell != null ? cell : " ");
                        }
                        boardArray.put(rowArray);
                    }
                }
                gameJson.put("board", boardArray);
                
                json.put("game", gameJson);
            }
            
            return json.toString();
        } catch (Exception e) {
            System.err.println("Error converting GameState to JSON: " + e.getMessage());
            return "{\"type\":\"serverData\",\"clientsList\":[],\"game\":{\"status\":\"waiting\"}}";
        }
    }

    public void updatePlayerDragInfo(String playerName, boolean isDragging, double x, double y, String pieceColor) {
        DragInfo dragInfo = playerDragInfo.get(playerName);
        if (dragInfo == null) {
            dragInfo = new DragInfo();
            playerDragInfo.put(playerName, dragInfo);
        }
        
        dragInfo.setDragging(isDragging);
        dragInfo.setDragX(x);
        dragInfo.setDragY(y);
        dragInfo.setPieceColor(pieceColor);
        
        System.out.println("🔄 Drag actualizado: " + playerName + " - " + isDragging + " at (" + x + "," + y + ")");
        
        // BROADCAST INMEDIATO en lugar de esperar al gameState completo
        broadcastDragUpdate(playerName, isDragging, x, y, pieceColor);
    }

    private void broadcastDragUpdate(String playerName, boolean isDragging, double x, double y, String color) {
        JSONObject dragMsg = new JSONObject();
        dragMsg.put("type", "dragUpdate");
        dragMsg.put("player", playerName);
        dragMsg.put("dragging", isDragging);
        dragMsg.put("x", x);
        dragMsg.put("y", y);
        dragMsg.put("color", color);
        
        broadcastToPlayers(dragMsg.toString());
    }
    
    public void broadcastToPlayers(String message) {
        if (player1 != null && player1.isOpen()) {
            player1.send(message);
        }
        if (player2 != null && player2.isOpen()) {
            player2.send(message);
        }
    }
    
    public String getPlayerName(WebSocket player) {
        if (player == player1) return player1Name;
        if (player == player2) return player2Name;
        return null;
    }
    
    public boolean hasPlayer(WebSocket player) {
        return player == player1 || player == player2;
    }
    
    public void removePlayer(WebSocket player) {
        if (player == player1) {
            player1 = null;
        } else if (player == player2) {
            player2 = null;
        }
        
        // Si un jugador se desconecta, terminar la partida
        if (gameStarted && !gameFinished) {
            gameFinished = true;
            winner = getPlayerName(player == player1 ? player2 : player1);
            broadcastGameState();
        }
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public boolean isGameStarted() { return gameStarted; }
    public boolean isGameFinished() { return gameFinished; }
    public boolean hasTwoPlayers() { return player1 != null && player2 != null; }
    public WebSocket getPlayer1() { return player1; }
    public WebSocket getPlayer2() { return player2; }
    public String getPlayer1Name() { return player1Name; }
    public String getPlayer2Name() { return player2Name; }
}