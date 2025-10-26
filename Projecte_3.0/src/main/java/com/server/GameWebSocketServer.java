package com.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameWebSocketServer extends WebSocketServer {
    
    private Map<WebSocket, String> connectedClients = new ConcurrentHashMap<>();
    private Map<String, GameSession> gameSessions = new ConcurrentHashMap<>();
    private Map<WebSocket, String> clientToSession = new ConcurrentHashMap<>();
    
    public GameWebSocketServer(int port) {
        super(new InetSocketAddress(port));
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
    }
    
    private void handleClientConnect(WebSocket conn, JSONObject message) {
        String playerName = message.getString("playerName");
        connectedClients.put(conn, playerName);
        System.out.println("Jugador conectado: " + playerName);
        
        broadcastPlayerList();
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
            
            // Notificar a ambos jugadores
            JSONObject acceptedMsg = new JSONObject();
            acceptedMsg.put("type", "invitation");
            acceptedMsg.put("from", playerName);
            acceptedMsg.put("invitationType", "accepted");
            
            inviterConn.send(acceptedMsg.toString());
            
            System.out.println("Partida creada: " + fromPlayer + " vs " + playerName);
            
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
    
    private void handleClientMouseMoving(WebSocket conn, JSONObject message) {
        // Implementar tracking de mouse para mostrar posición del oponente
        double x = message.getDouble("x");
        double y = message.getDouble("y");
        
        String sessionId = clientToSession.get(conn);
        if (sessionId != null) {
            // Aquí podrías enviar la posición del mouse al oponente
            // para mostrar el cursor en tiempo real
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
        // Crear lista de jugadores disponibles
        List<String> availablePlayers = new ArrayList<>();
        for (String playerName : connectedClients.values()) {
            // Solo incluir jugadores que no estén en una partida
            boolean inGame = false;
            for (String sessionId : clientToSession.values()) {
                GameSession session = gameSessions.get(sessionId);
                if (session != null && (session.getPlayer1Name().equals(playerName) || 
                    (session.getPlayer2Name() != null && session.getPlayer2Name().equals(playerName)))) {
                    inGame = true;
                    break;
                }
            }
            
            if (!inGame) {
                availablePlayers.add(playerName);
            }
        }
        
        // Broadcast la lista a todos los clientes
        for (WebSocket conn : connectedClients.keySet()) {
            JSONObject playerListMsg = new JSONObject();
            playerListMsg.put("type", "playerList");
            playerListMsg.put("players", new JSONArray(availablePlayers));
            conn.send(playerListMsg.toString());
        }
    }
    
    public static void main(String[] args) {
        int port = 3000;
        GameWebSocketServer server = new GameWebSocketServer(port);
        server.start();
        System.out.println("Servidor Conecta 4 iniciado en puerto " + port);
    }
}