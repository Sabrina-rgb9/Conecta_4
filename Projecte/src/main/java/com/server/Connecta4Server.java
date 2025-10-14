package com.server;

import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * Servidor WebSocket para Connecta 4
 */
public class Connecta4Server extends WebSocketServer {

    private final ClientRegistry clients;
    private final Map<String, GameSession> activeGames = new ConcurrentHashMap<>();
    private final Map<String, String> waitingPlayers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public Connecta4Server(InetSocketAddress addr, List<String> names) {
        super(addr);
        this.clients = new ClientRegistry(names);
        // loop para enviar estado ~30 FPS
        scheduler.scheduleAtFixedRate(this::broadcastGameStates, 0, 33, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String name = clients.add(conn);
        System.out.println("Conectado: " + name);
        sendClientsListToAll();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        System.out.println("Desconectado: " + name);
        waitingPlayers.remove(name);
        activeGames.values().removeIf(g -> g.playerRed.equals(name) || g.playerYellow.equals(name));
        sendClientsListToAll();
    }

    @Override
    public void onMessage(WebSocket conn, String msg) {
        String sender = clients.nameBySocket(conn);
        JSONObject obj = new JSONObject(msg);
        String type = obj.getString("type");

        switch (type) {
            case "clientReady" -> handleClientReady(sender);
            case "clientPlay" -> {
                int col = obj.getInt("col");
                GameSession game = findGameOfPlayer(sender);
                if (game != null) game.play(sender, col);
            }
            case "clientMouseMoving" -> {
                GameSession game = findGameOfPlayer(sender);
                if (game != null) {
                    broadcastExcept(sender, new JSONObject()
                            .put("type", "clientMouseMoving")
                            .put("player", sender)
                            .put("x", obj.getDouble("x"))
                            .put("y", obj.getDouble("y"))
                            .toString());
                }
            }
        }
    }

    private void handleClientReady(String player) {
        // añadir a lobby
        waitingPlayers.put(player, player);
        // emparejar si hay otro esperando
        if (waitingPlayers.size() >= 2) {
            Iterator<String> it = waitingPlayers.keySet().iterator();
            String p1 = it.next(); it.remove();
            String p2 = it.next(); it.remove();
            GameSession game = new GameSession(p1, p2);
            activeGames.put(p1, game);
            activeGames.put(p2, game);
        }
    }

    private GameSession findGameOfPlayer(String player) {
        return activeGames.get(player);
    }

    private void broadcastGameStates() {
        for (String player : activeGames.keySet()) {
            GameSession game = activeGames.get(player);
            WebSocket conn = clients.socketByName(player);
            if (conn != null && game != null) {
                JSONObject data = new JSONObject();
                data.put("type", "serverData");
                data.put("clientName", player);
                data.put("role", game.toJSON(player).getString("role"));
                data.put("game", game.toJSON(player));
                sendSafe(conn, data.toString());
            }
        }
    }

    private void sendClientsListToAll() {
        JSONArray list = clients.currentNames();
        for (var entry : clients.snapshot().entrySet()) {
            JSONObject obj = new JSONObject().put("type", "clients");
            obj.put("id", entry.getValue());
            obj.put("list", list);
            sendSafe(entry.getKey(), obj.toString());
        }
    }

    private void sendSafe(WebSocket to, String msg) {
        try { if (to != null) to.send(msg); }
        catch (Exception e) { clients.cleanupDisconnected(to); }
    }

    private void broadcastExcept(String sender, String msg) {
        for (var entry : clients.snapshot().entrySet()) {
            if (!entry.getValue().equals(sender)) sendSafe(entry.getKey(), msg);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) { ex.printStackTrace(); }

    @Override
    public void onStart() { System.out.println("Servidor Connecta 4 en puerto " + getPort()); }

    public static void main(String[] args) {
        Connecta4Server server = new Connecta4Server(
                new InetSocketAddress(3000),
                Arrays.asList("Mario","Luigi","Peach","Toad","Bowser","Wario","Zelda","Link")
        );
        server.start();
    }
}
