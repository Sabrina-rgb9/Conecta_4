package com.server;

import org.java_websocket.WebSocket;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import com.shared.GameState;
import com.shared.ClientInfo;
import com.shared.GameObject;
import com.shared.GameData;
import com.shared.Move;

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
    private boolean countdownInProgress = false; // NUEVO: para controlar el countdown
    
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
        for (int i = 0; i < 21; i++) { // 21 fichas por jugador
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
        this.countdownInProgress = true; // NUEVO: empezar countdown
        
        System.out.println("Partida iniciada: " + player1Name + " (R) vs " + player2Name + " (Y)");
        
        // Enviar estado INMEDIATAMENTE con status: "countdown"
        broadcastGameState();
        
        // Luego iniciar countdown
        sendCountdown();
    }
    
    private void sendCountdown() {
        JSONObject countdownMsg = new JSONObject();
        countdownMsg.put("type", "countdown");
        countdownMsg.put("count", 3);
        
        broadcastToPlayers(countdownMsg.toString());
        
        // Programar inicio del juego después del countdown
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                startGame();
            }
        }, 4000); // 3 segundos de countdown + 1 segundo extra
    }
    
    private void startGame() {
        // FINALIZAR COUNTDOWN E INICIAR JUEGO
        this.countdownInProgress = false;
        this.gameStarted = true; // Asegurar que gameStarted sea true
        
        System.out.println("¡Iniciando partida! Turno de: " + currentTurn);
        System.out.println("GameStarted: " + gameStarted + ", CountdownInProgress: " + countdownInProgress);
        
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
        
        // Crear último movimiento para animación
        Move lastMove = new Move();
        lastMove.setCol(column);
        lastMove.setRow(row);
        
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
        
        // Crear lista de clientes
        List<ClientInfo> clients = new ArrayList<>();
        
        // Jugador 1 (Rojo)
        ClientInfo client1 = new ClientInfo();
        client1.setName(player1Name);
        client1.setColor("RED");
        client1.setRole("R");
        client1.setMouseX(0);
        client1.setMouseY(0);
        clients.add(client1);
        
        // Jugador 2 (Amarillo)
        if (player2 != null) {
            ClientInfo client2 = new ClientInfo();
            client2.setName(player2Name);
            client2.setColor("YELLOW");
            client2.setRole("Y");
            client2.setMouseX(0);
            client2.setMouseY(0);
            clients.add(client2);
        }
        
        gameState.setClientsList(clients);
        gameState.setObjectsList(gameObjects);
        
        // Crear datos del juego
        GameData gameData = new GameData();
        
        // CORRECCIÓN: El estado debe mantenerse una vez establecido
        if (gameFinished) {
            if (winner.equals("draw")) {
                gameData.setStatus("draw");
            } else {
                gameData.setStatus("win");
            }
        } else if (countdownInProgress) {
            gameData.setStatus("countdown");
        } else if (gameStarted) {
            // UNA VEZ QUE EL JUEGO HA EMPEZADO, SIEMPRE debe ser "playing" hasta que termine
            gameData.setStatus("playing");
        } else {
            gameData.setStatus("waiting");
        }
        
        gameData.setBoard(board);
        gameData.setTurn(currentTurn);
        gameData.setWinner(winner);
        
        // DEBUG en servidor
        System.out.println("=== SERVIDOR Enviando estado ===");
        System.out.println("Sesión: " + sessionId);
        System.out.println("Jugadores: " + player1Name + " vs " + player2Name);
        System.out.println("GameStarted: " + gameStarted);
        System.out.println("CountdownInProgress: " + countdownInProgress);
        System.out.println("Estado enviado: " + gameData.getStatus());
        
        gameState.setGame(gameData);
        
        return gameState;
    }
    
    private String convertGameStateToJson(GameState gameState) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "serverData");
            json.put("clientName", gameState.getClientName());
            
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
                            rowArray.put(cell);
                        }
                        boardArray.put(rowArray);
                    }
                }
                gameJson.put("board", boardArray);
                
                // Last move
                if (gameData.getLastMove() != null) {
                    JSONObject moveJson = new JSONObject();
                    moveJson.put("col", gameData.getLastMove().getCol());
                    moveJson.put("row", gameData.getLastMove().getRow());
                    gameJson.put("lastMove", moveJson);
                }
                
                json.put("game", gameJson);
            }
            
            return json.toString();
        } catch (Exception e) {
            System.err.println("Error converting GameState to JSON: " + e.getMessage());
            return "{}";
        }
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