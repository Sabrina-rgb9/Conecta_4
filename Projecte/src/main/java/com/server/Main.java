package com.server;

import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

public class Main extends WebSocketServer {

    private final ClientRegistry clients;
    private final GameManager gameManager;

    public Main(InetSocketAddress address, List<String> playerNames) {
        super(address);
        this.clients = new ClientRegistry(playerNames);
        this.gameManager = new GameManager(clients);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String playerName = clients.add(conn);
        System.out.println("✅ Connectat: " + playerName);
        broadcastClientsList();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String playerName = clients.remove(conn);
        System.out.println("❌ Desconnectat: " + playerName);
        if (playerName != null) {
            gameManager.handleDisconnect(playerName);
        }
        broadcastClientsList();
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String sender = clients.nameBySocket(conn);
        if (sender == null) return;

        try {
            JSONObject msg = new JSONObject(message);
            String type = msg.getString("type");

            switch (type) {
                case "clientReady" -> broadcastClientsList();
                case "invite" -> handleInvite(sender, msg.getString("dest"), conn);
                case "acceptInvite" -> gameManager.accept(sender, msg.getString("origin"));
                case "clientPlay" -> gameManager.play(sender, msg.getInt("col"));
                case "clientMouseMoving" -> handleMouseMoving(sender, msg.getDouble("x"), msg.getDouble("y"));
                case "clientPieceMoving" -> handlePieceMoving(sender, msg.getDouble("x"), msg.getDouble("y"), msg.optString("pieceId", null));
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error amb " + sender + ": " + message);
            e.printStackTrace();
        }
    }

    private void handleInvite(String sender, String dest, WebSocket conn) {
        JSONObject response = gameManager.invite(sender, dest);
        if (response.getBoolean("ok")) {
            conn.send(new JSONObject().put("type", "invitationSent").put("dest", dest).toString());
        } else {
            conn.send(new JSONObject().put("type", "error").put("reason", response.getString("reason")).toString());
        }
    }

    private void handleMouseMoving(String player, double x, double y) {
        String sessionId = gameManager.findSessionIdByPlayer(player);
        if (sessionId == null) return;

        GameSession session = gameManager.getSession(sessionId);
        if (session != null) {
            session.updateMouse(player, x, y);
        }
        // Envía a oponente
        String other = session.getPlayerR().equals(player) ? session.getPlayerY() : session.getPlayerR();
        WebSocket otherConn = clients.socketByName(other);
        if (otherConn != null) {
            JSONObject msg = new JSONObject()
                .put("type", "clientMouseMoving")
                .put("player", player)
                .put("x", x)
                .put("y", y);
            try {
                otherConn.send(msg.toString());
            } catch (Exception e) {
                clients.cleanupDisconnected(otherConn);
            }
        }
    }

    private void handlePieceMoving(String player, double x, double y, String pieceId) {
        // Similar a mouse, pero opcional para arrastre
        handleMouseMoving(player, x, y);  // Reutiliza lógica
    }

    private void broadcastClientsList() {
        List<String> players = clients.currentNames();
        for (WebSocket conn : clients.snapshot().keySet()) {
            String myName = clients.nameBySocket(conn);
            if (myName == null) continue;
            JSONObject msg = new JSONObject()
                .put("type", "clients")
                .put("id", myName)
                .put("list", players);
            try {
                conn.send(msg.toString());
            } catch (Exception e) {
                clients.cleanupDisconnected(conn);
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("💥 Error del servidor: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("🎮 Servidor Connecta 4 actiu al port " + getPort());
    }

    public static void main(String[] args) {
        int port = 3000;
        List<String> names = Arrays.asList(
            "Mario", "Luigi", "Peach", "Toad", "Bowser", "Wario", "Zelda", "Link"
        );
        new Main(new InetSocketAddress(port), names).start();
    }
}