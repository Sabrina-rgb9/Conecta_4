package com.server;

import com.shared.ClientData;
import com.shared.GameObject;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

public class Main extends WebSocketServer {

    public static final int DEFAULT_PORT = 3000;
    private static final List<String> PLAYER_NAMES = Arrays.asList("Bulbasaur", "Charizard");
    private static final List<String> PLAYER_COLORS = Arrays.asList("RED", "YELLOW");
    private static final int ROWS = 6;
    private static final int COLS = 7;

    private final ClientRegistry clients;
    private final Map<String, ClientData> clientsData = new HashMap<>();
    private final ColorCell[][] board = new ColorCell[ROWS][COLS];
    private final ScheduledExecutorService ticker;

    private volatile String currentTurn = null;

    private static final String T_CLIENT_MOUSE_MOVING = "clientMouseMoving";
    private static final String T_CLIENT_OBJECT_MOVING = "clientObjectMoving";
    private static final String T_SERVER_DATA = "serverData";
    private static final String T_COUNTDOWN = "countdown";

    public Main(InetSocketAddress address) {
        super(address);
        clients = new ClientRegistry(PLAYER_NAMES);

        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                board[r][c] = new ColorCell();

        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServerTicker");
            t.setDaemon(true);
            return t;
        });
    }

    private static class ColorCell {
        String playerName = null; // nombre del jugador que ocupa la celda
        String color = null;      // "RED" o "YELLOW"
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String name = clients.add(conn);
        String color = PLAYER_COLORS.get(PLAYER_NAMES.indexOf(name));
        clientsData.put(name, new ClientData(name, color));

        if (currentTurn == null)
            currentTurn = name;

        System.out.println("Cliente conectado: " + name);
        startCountdownIfReady();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        clientsData.remove(name);

        if (currentTurn != null && currentTurn.equals(name))
            currentTurn = clients.snapshot().keySet().stream().findFirst().orElse(null);

        System.out.println("Cliente desconectado: " + name);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj = new JSONObject(message);
        String type = obj.optString("type");

        switch (type) {
            case T_CLIENT_MOUSE_MOVING -> {
                String name = clients.nameBySocket(conn);
                ClientData cd = clientsData.get(name);
                if (cd != null) {
                    JSONObject value = obj.getJSONObject("value");
                    cd.mouseX = value.getInt("mouseX");
                    cd.mouseY = value.getInt("mouseY");
                }
            }
            case T_CLIENT_OBJECT_MOVING -> {
                String name = clients.nameBySocket(conn);
                if (!name.equals(currentTurn)) return; // solo turno actual

                JSONObject value = obj.getJSONObject("value");
                int row = value.getInt("row");
                int col = value.getInt("cols");

                String color = clientsData.get(name).color;

                if (board[row][col].playerName == null) {
                    board[row][col].playerName = name;
                    board[row][col].color = color;

                    if (checkWin(row, col, color)) {
                        broadcastMessage(name + " ha ganado!");
                        resetBoard();
                    } else if (isBoardFull()) {
                        broadcastMessage("Empate!");
                        resetBoard();
                    } else {
                        nextTurn();
                    }
                }
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Servidor iniciado en puerto " + getPort());
        startTicker();
    }

    private void startTicker() {
        ticker.scheduleAtFixedRate(this::broadcastStatus, 0, 1000 / 30, TimeUnit.MILLISECONDS);
    }

    private void broadcastStatus() {
        JSONObject rst = new JSONObject();
        rst.put("type", T_SERVER_DATA);

        JSONArray arrClients = new JSONArray();
        clientsData.values().forEach(cd -> arrClients.put(cd.toJSON()));
        rst.put("clientsList", arrClients);

        JSONArray arrObjects = new JSONArray();
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (board[r][c].playerName != null)
                    arrObjects.put(new JSONObject()
                            .put("id", board[r][c].playerName)
                            .put("row", r)
                            .put("cols", c)
                            .put("x", c)
                            .put("y", r));
        rst.put("objectsList", arrObjects);

        for (WebSocket conn : clients.snapshot().keySet()) {
            rst.put("clientName", clients.nameBySocket(conn));
            try { conn.send(rst.toString()); } catch (Exception ignored) {}
        }
    }

    private void broadcastMessage(String msg) {
        JSONObject json = new JSONObject();
        json.put("type", "serverData");
        json.put("message", msg);
        for (WebSocket conn : clients.snapshot().keySet()) {
            try { conn.send(json.toString()); } catch (Exception ignored) {}
        }
    }

    private void startCountdownIfReady() {
        if (clients.snapshot().size() >= 2) {
            new Thread(() -> {
                try {
                    for (int i = 5; i >= 0; i--) {
                        broadcastCountdown(i);
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }

    private void broadcastCountdown(int n) {
        JSONObject json = new JSONObject();
        json.put("type", T_COUNTDOWN);
        json.put("value", n);
        for (WebSocket conn : clients.snapshot().keySet()) {
            try { conn.send(json.toString()); } catch (Exception ignored) {}
        }
    }

    private void nextTurn() {
        List<String> players = new ArrayList<>(clients.snapshot().values());
        int idx = players.indexOf(currentTurn);
        currentTurn = players.get((idx + 1) % players.size());
    }

    private boolean checkWin(int row, int col, String color) {
        return checkDirection(row, col, color, 1, 0) ||
               checkDirection(row, col, color, 0, 1) ||
               checkDirection(row, col, color, 1, 1) ||
               checkDirection(row, col, color, 1, -1);
    }

    private boolean checkDirection(int row, int col, String color, int dr, int dc) {
        int count = 1;
        count += countPieces(row, col, color, dr, dc);
        count += countPieces(row, col, color, -dr, -dc);
        return count >= 4;
    }

    private int countPieces(int row, int col, String color, int dr, int dc) {
        int cnt = 0;
        int r = row + dr;
        int c = col + dc;
        while (r >= 0 && r < ROWS && c >= 0 && c < COLS && board[r][c].color != null && board[r][c].color.equals(color)) {
            cnt++;
            r += dr;
            c += dc;
        }
        return cnt;
    }

    private boolean isBoardFull() {
        for (int c = 0; c < COLS; c++) if (board[0][c].playerName == null) return false;
        return true;
    }

    private void resetBoard() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                board[r][c] = new ColorCell();
    }

    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.stop(); } catch (Exception ignored) {}
        }));
        System.out.println("Servidor corriendo en puerto " + DEFAULT_PORT);
    }
}
