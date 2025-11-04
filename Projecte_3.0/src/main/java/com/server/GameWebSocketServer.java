package com.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.shared.GameState;
import com.shared.ClientInfo;
import com.shared.GameObject;
import com.shared.GameData;
import com.shared.Move;

public class GameWebSocketServer extends WebSocketServer {
    
    private Map<WebSocket, String> connectedClients = new ConcurrentHashMap<>();
    private Map<String, GameSession> gameSessions = new ConcurrentHashMap<>();
    private Map<WebSocket, String> clientToSession = new ConcurrentHashMap<>();

    private static final int UPDATE_INTERVAL = 1000;
    
    public GameWebSocketServer(int port) {
        super(new InetSocketAddress(port));
        startPeriodicUpdates(); 
    }
    
    public void startPeriodicUpdates() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
    public void run() {
                // CORRECCIÓN: Usar el método que respeta las sesiones de juego
                for (WebSocket conn : connectedClients.keySet()) {
                    sendCorrectGameStateToClient(conn);
                }
            }
        }, 0, UPDATE_INTERVAL); 
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Nueva conexión: " + conn.getRemoteSocketAddress());
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Conexión cerrada: " + conn.getRemoteSocketAddress());
        
        String playerName = connectedClients.remove(conn);
        String sessionId = clientToSession.get(conn);
        
        if (sessionId != null) {
            GameSession session = gameSessions.get(sessionId);
            if (session != null) {
                session.removePlayer(conn);
                if (!session.hasTwoPlayers()) {
                    gameSessions.remove(sessionId);
                }
            }
            clientToSession.remove(conn);
        }
        
        broadcastPlayerList();
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);
            String type = jsonMessage.getString("type");
            
            switch (type) {
                case "clientConnect":
                    handleClientConnect(conn, jsonMessage);
                    break;
                case "clientInvite":
                    handleClientInvite(conn, jsonMessage);
                    break;
                case "clientAcceptInvite":
                    handleClientAcceptInvite(conn, jsonMessage);
                    break;
                case "clientRejectInvite":
                    handleClientRejectInvite(conn, jsonMessage);
                    break;
                case "clientPlay":
                    handleClientPlay(conn, jsonMessage);
                    break;
                case "clientMouseMoving":
                    handleClientMouseMoving(conn, jsonMessage);
                    break;
                case "clientBackToLobby":
                    handleClientBackToLobby(conn, jsonMessage);
                    break;
                case "clientExit":
                    handleClientExit(conn, jsonMessage);
                    break;
                case "clientDragPiece":
                    handleClientDragPiece(conn, jsonMessage);
                    break;
                default:
                    System.out.println("Tipo de mensaje desconocido: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error procesando mensaje: " + e.getMessage());
        }
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Error en WebSocket: " + ex.getMessage());
    }
    
    @Override
    public void onStart() {
        System.out.println("Servidor WebSocket iniciado en puerto: " + getPort());
        System.out.println("✅ Servidor listo para aceptar conexiones");
    }
    
    private void handleClientConnect(WebSocket conn, JSONObject message) {
        String playerName = message.getString("playerName");
        connectedClients.put(conn, playerName);
        System.out.println("Jugador conectado: " + playerName);
        
        // Enviar estado del juego actual al nuevo cliente
        sendGameStateToClient(conn);
        
        broadcastPlayerList();
    }
    
    private void sendGameStateToClient(WebSocket conn) {
        try {
            // Verificar primero si el cliente está en una sesión
            String sessionId = clientToSession.get(conn);
            if (sessionId != null) {
                GameSession session = gameSessions.get(sessionId);
                if (session != null) {
                    // Enviar estado de la sesión en lugar del estado global
                    session.broadcastGameState();
                    return;
                }
            }
            
            // Solo enviar estado global si no está en una sesión
            GameState gameState = createInitialGameState();
            String gameStateJson = convertGameStateToJson(gameState);
            conn.send(gameStateJson);
        } catch (Exception e) {
            System.err.println("Error enviando estado al cliente: " + e.getMessage());
        }
    }
    
    private GameState createInitialGameState() {
        GameState gameState = new GameState();
        gameState.setType("serverData");
        
        // Crear lista de clientes conectados
        List<ClientInfo> clients = new ArrayList<>();
        for (Map.Entry<WebSocket, String> entry : connectedClients.entrySet()) {
            ClientInfo client = new ClientInfo();
            client.setName(entry.getValue());
            client.setColor("GRAY"); // Color por defecto
            client.setRole(""); // Rol vacío hasta que empiece el juego
            client.setMouseX(0);
            client.setMouseY(0);
            clients.add(client);
        }
        
        gameState.setClientsList(clients);
        gameState.setObjectsList(new ArrayList<>());
        
        // Crear datos del juego vacíos
        GameData gameData = new GameData();
        gameData.setStatus("waiting");
        gameData.setBoard(new String[6][7]); // Tablero vacío
        gameData.setTurn("");
        gameData.setWinner("");
        
        gameState.setGame(gameData);
        
        return gameState;
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
            
            // Objects list (vacío por ahora)
            json.put("objectsList", new JSONArray());
            
            // Game data
            JSONObject gameJson = new JSONObject();
            if (gameState.getGame() != null) {
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
            }
            json.put("game", gameJson);
            
            return json.toString();
        } catch (Exception e) {
            System.err.println("Error converting GameState to JSON: " + e.getMessage());
            return "{\"type\":\"serverData\",\"clientsList\":[],\"game\":{\"status\":\"waiting\"}}";
        }
    }
    
    private void handleClientInvite(WebSocket conn, JSONObject message) {
        String opponentName = message.getString("opponent");
        String playerName = connectedClients.get(conn);
        
        // Buscar la conexión del oponente
        WebSocket opponentConn = findConnectionByName(opponentName);
        if (opponentConn != null) {
            JSONObject invitation = new JSONObject();
            invitation.put("type", "invitation");
            invitation.put("from", playerName);
            invitation.put("invitationType", "received");
            
            opponentConn.send(invitation.toString());
            System.out.println("Invitación enviada de " + playerName + " a " + opponentName);
        } else {
            System.out.println("No se encontró al jugador: " + opponentName);
        }
    }
    
    private void handleClientAcceptInvite(WebSocket conn, JSONObject message) {
        String fromPlayer = message.getString("from");
        String playerName = connectedClients.get(conn);
        
        WebSocket inviterConn = findConnectionByName(fromPlayer);
        if (inviterConn != null) {
            // Crear nueva sesión de juego
            String sessionId = UUID.randomUUID().toString();
            GameSession session = new GameSession(sessionId, inviterConn, fromPlayer);
            session.addPlayer2(conn, playerName);
            
            gameSessions.put(sessionId, session);
            clientToSession.put(inviterConn, sessionId);
            clientToSession.put(conn, sessionId);
            
            // Notificar a ambos jugadores que la invitación fue aceptada
            JSONObject acceptedMsg = new JSONObject();
            acceptedMsg.put("type", "invitation");
            acceptedMsg.put("from", playerName);
            acceptedMsg.put("invitationType", "accepted");
            
            inviterConn.send(acceptedMsg.toString());
            conn.send(acceptedMsg.toString());
            
            System.out.println("Partida creada: " + fromPlayer + " vs " + playerName);
            
            // CORRECCIÓN: Enviar el estado del juego inmediatamente
            session.broadcastGameState();
            
            broadcastPlayerList();
        }
    }
    
    private void handleClientRejectInvite(WebSocket conn, JSONObject message) {
        String fromPlayer = message.getString("from");
        String playerName = connectedClients.get(conn);
        
        WebSocket inviterConn = findConnectionByName(fromPlayer);
        if (inviterConn != null) {
            JSONObject rejectedMsg = new JSONObject();
            rejectedMsg.put("type", "invitation");
            rejectedMsg.put("from", playerName);
            rejectedMsg.put("invitationType", "rejected");
            
            inviterConn.send(rejectedMsg.toString());
            System.out.println("Invitación rechazada: " + fromPlayer + " por " + playerName);
            
            // El que rechaza se queda en OpponentSelection
            // El invitador recibirá el mensaje y volverá a OpponentSelection
        }
    }
    
    private void handleClientPlay(WebSocket conn, JSONObject message) {
        int column = message.getInt("column");
        String sessionId = clientToSession.get(conn);
        
        if (sessionId != null) {
            GameSession session = gameSessions.get(sessionId);
            if (session != null) {
                session.makeMove(conn, column);
            }
        }
    }

    private void handleClientBackToLobby(WebSocket conn, JSONObject message) {
        String playerName = connectedClients.get(conn);
        String sessionId = clientToSession.get(conn);
        
        System.out.println("Jugador " + playerName + " volviendo al lobby");
        
        // Remover de la sesión de juego
        if (sessionId != null) {
            GameSession session = gameSessions.get(sessionId);
            if (session != null) {
                session.removePlayer(conn);
                // Si la sesión queda vacía, eliminarla
                if (!session.hasTwoPlayers()) {
                    gameSessions.remove(sessionId);
                    System.out.println("Sesión " + sessionId + " eliminada");
                }
            }
            clientToSession.remove(conn);
        }
        
        // Enviar estado actualizado (sin sesión de juego)
        sendGameStateToClient(conn);
        broadcastPlayerList();
    }

    private void handleClientExit(WebSocket conn, JSONObject message) {
        String playerName = connectedClients.get(conn);
        System.out.println("Jugador " + playerName + " saliendo");
        
        // Remover de sesión si está en una
        handleClientBackToLobby(conn, message);
        
        // El cierre de conexión se manejará en onClose
    }
    
    private void handleClientMouseMoving(WebSocket conn, JSONObject message) {
        double x = message.getDouble("x");
        double y = message.getDouble("y");
        
        String playerName = connectedClients.get(conn);
        String sessionId = clientToSession.get(conn);

        System.out.println("Mouse move from " + playerName + ": (" + x + ", " + y + ")");
        
        if (sessionId != null) {
            // Actualizar posición del mouse en la sesión de juego
            GameSession session = gameSessions.get(sessionId);
            if (session != null) {
                session.updatePlayerMousePosition(playerName, x, y);
                session.broadcastGameState();
            }
        } else {
            // Actualizar posición del mouse global (fuera de partida)
            updatePlayerMousePosition(playerName, x, y);
        }
    }

    private void handleClientDragPiece(WebSocket conn, JSONObject message) {
        try {
            boolean isDragging = message.getBoolean("isDragging");
            double x = message.getDouble("x");
            double y = message.getDouble("y");
            String pieceColor = message.getString("pieceColor");
            
            String playerName = connectedClients.get(conn);
            String sessionId = clientToSession.get(conn);
            
            System.out.println("Drag from " + playerName + ": " + isDragging + " at (" + x + "," + y + ") color=" + pieceColor);
            
            if (sessionId != null) {
                GameSession session = gameSessions.get(sessionId);
                if (session != null) {
                    // ✅ CORRECCIÓN: Llamar al método que hace broadcast automático
                    session.updatePlayerDragInfo(playerName, isDragging, x, y, pieceColor);
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling drag piece: " + e.getMessage());
        }
    }

    private void updatePlayerMousePosition(String playerName, double x, double y) {
        // Buscar la conexión del jugador y actualizar su posición en todas las sesiones donde esté
        for (Map.Entry<String, GameSession> entry : gameSessions.entrySet()) {
            GameSession session = entry.getValue();
            if (session.hasPlayerWithName(playerName)) {
                session.updatePlayerMousePosition(playerName, x, y);
            }
        }
    }
    
    private WebSocket findConnectionByName(String playerName) {
        for (Map.Entry<WebSocket, String> entry : connectedClients.entrySet()) {
            if (entry.getValue().equals(playerName)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private void broadcastPlayerList() {
        // Enviar estado actualizado a todos los clientes
        for (WebSocket conn : connectedClients.keySet()) {
            sendCorrectGameStateToClient(conn);
        }
    }

    private void sendCorrectGameStateToClient(WebSocket conn) {
        try {
            // Verificar si el cliente está en una sesión de juego
            String sessionId = clientToSession.get(conn);
            if (sessionId != null) {
                // El cliente está en una partida, enviar estado de la sesión
                GameSession session = gameSessions.get(sessionId);
                if (session != null) {
                    session.broadcastGameState();
                    return; // Importante: salir aquí para no enviar estado global
                }
            }
            
            // Si no está en una sesión, enviar estado global
            sendGameStateToClient(conn);
        } catch (Exception e) {
            System.err.println("Error enviando estado al cliente: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        int port = 3000;
        GameWebSocketServer server = new GameWebSocketServer(port);
        server.start();
        System.out.println("🎮 Servidor Conecta 4 iniciado en puerto " + port);
    }
}