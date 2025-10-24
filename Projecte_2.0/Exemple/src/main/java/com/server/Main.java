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

/**
 * Servidor WebSocket per a Conecta4.
 * - Tauler 7x6 (COLS x ROWS)
 * - Control de torns
 * - Validació de jugades (només jugador del torn)
 * - Detecció de victoria / empat
 * - Broadcast de l'estat a 30 FPS
 */
public class Main extends WebSocketServer {

    public static final int DEFAULT_PORT = 3000;

    // Noms / colors disponibles (ClientRegistry administra pool de noms)
    private static final List<String> PLAYER_NAMES = Arrays.asList("Bulbasaur", "Charizard", "Blaziken", "Umbreon");
    private static final List<String> PLAYER_COLORS = Arrays.asList("RED", "YELLOW");

    private static final int ROWS = 6;
    private static final int COLS = 7;

    private static final int REQUIRED_CLIENTS_TO_START = 2;
    private static final int SEND_FPS = 30;

    // message types
    private static final String T_CLIENT_MOUSE_MOVING = "clientMouseMoving";
    private static final String T_CLIENT_OBJECT_MOVING = "clientObjectMoving";
    private static final String T_SERVER_DATA = "serverData";
    private static final String T_COUNTDOWN = "countdown";

    private final ClientRegistry clients;
    private final Map<String, ClientData> clientsData = new ConcurrentHashMap<>();

    // board[row][col] -> playerName or null
    private final String[][] board = new String[ROWS][COLS];

    // game state
    private volatile String currentTurn = null;   // name of player whose turn it is
    private volatile String gameStatus = "waiting"; // waiting | playing | win | draw
    private volatile String winner = null;
    private final ScheduledExecutorService scheduler;
    private final ScheduledExecutorService ticker;

    // last move for animation purposes
    private volatile JSONObject lastMove = null;

    public Main(InetSocketAddress address) {
        super(address);
        this.clients = new ClientRegistry(PLAYER_NAMES);

        // init board
        clearBoard();

        // scheduler for delayed tasks (reset after win, etc.)
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServerScheduler");
            t.setDaemon(true);
            return t;
        });

        // ticker for broadcasting state
        this.ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServerTicker");
            t.setDaemon(true);
            return t;
        });
    }

    // ----------------- Lifecycle -----------------

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        // start broadcast ticker
        startTicker();
        setConnectionLostTimeout(60);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String name = clients.add(conn);
        String color = getColorForName(name);
        ClientData cd = new ClientData(name, color);
        clientsData.put(name, cd);

        System.out.println("Client connected: " + name + " (" + color + ")");

        // if we have enough clients, start countdown to game start
        startCountdownIfReady();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String name = clients.remove(conn);
        clientsData.remove(name);
        System.out.println("Client disconnected: " + name);

        // if the disconnected client had the turn, pick next available
        synchronized (this) {
            if (name != null && name.equals(currentTurn)) {
                pickNextTurnAfterDisconnect();
            }
            // if too few players, go back to waiting state
            if (clients.snapshot().size() < REQUIRED_CLIENTS_TO_START) {
                gameStatus = "waiting";
                currentTurn = null;
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    // ----------------- Message handling -----------------

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj;
        try {
            obj = new JSONObject(message);
        } catch (Exception ex) {
            return; // invalid JSON
        }

        String type = obj.optString("type", "");
        switch (type) {
            case T_CLIENT_MOUSE_MOVING -> handleClientMouseMoving(conn, obj);
            case T_CLIENT_OBJECT_MOVING -> handleClientObjectMoving(conn, obj);
            default -> {
                // ignore unknown types
            }
        }
    }

    private void handleClientMouseMoving(WebSocket conn, JSONObject obj) {
        String name = clients.nameBySocket(conn);
        if (name == null) return;
        ClientData cd = clientsData.get(name);
        if (cd == null) return;

        JSONObject value = obj.optJSONObject("value");
        if (value == null) return;
        // optional ints or doubles
        cd.mouseX = value.optInt("mouseX", cd.mouseX);
        cd.mouseY = value.optInt("mouseY", cd.mouseY);
    }

    private void handleClientObjectMoving(WebSocket conn, JSONObject obj) {
        String name = clients.nameBySocket(conn);
        if (name == null) return;
        JSONObject value = obj.optJSONObject("value");
        if (value == null) return;

        // Expect at least "col" (clients may also send row/color etc.)
        int col = value.has("col") ? value.optInt("col", -1) : value.optInt("cols", -1);
        if (col < 0 || col >= COLS) return;

        synchronized (this) {
            // Validate game status and turn
            if (!"playing".equals(gameStatus)) return;
            if (!name.equals(currentTurn)) return;

            int row = getAvailableRow(col);
            if (row < 0) {
                // column full -> ignore
                return;
            }

            // apply move
            board[row][col] = name;
            lastMove = new JSONObject().put("row", row).put("col", col).put("player", name);

            // check win/draw
            String color = clientsData.get(name).color;
            if (checkWinner(row, col, name)) {
                gameStatus = "win";
                winner = name;
                System.out.println("Winner: " + winner);
                // broadcastStatus will include winner
                // schedule reset after 5 seconds
                scheduler.schedule(this::resetAfterRound, 5, TimeUnit.SECONDS);
            } else if (isBoardFull()) {
                gameStatus = "draw";
                winner = null;
                System.out.println("Game draw");
                scheduler.schedule(this::resetAfterRound, 5, TimeUnit.SECONDS);
            } else {
                // next player's turn
                pickNextTurn();
            }
        }
    }

    // ----------------- Game helpers -----------------

    private void clearBoard() {
        for (int r = 0; r < ROWS; r++) Arrays.fill(board[r], null);
        lastMove = null;
        winner = null;
    }

    private int getAvailableRow(int col) {
        for (int r = ROWS - 1; r >= 0; r--) {
            if (board[r][col] == null) return r;
        }
        return -1;
    }

    private void pickNextTurn() {
        // pick next connected player in registry order (rotate)
        List<String> players = new ArrayList<>(clients.snapshot().values());
        if (players.isEmpty()) {
            currentTurn = null;
            gameStatus = "waiting";
            return;
        }
        if (currentTurn == null) {
            currentTurn = players.get(0);
            return;
        }
        int idx = players.indexOf(currentTurn);
        if (idx < 0) idx = 0;
        currentTurn = players.get((idx + 1) % players.size());
    }

    private void pickNextTurnAfterDisconnect() {
        List<String> players = new ArrayList<>(clients.snapshot().values());
        if (players.isEmpty()) {
            currentTurn = null;
            gameStatus = "waiting";
            return;
        }
        // set currentTurn to first available
        currentTurn = players.get(0);
    }

    private boolean checkWinner(int row, int col, String playerName) {
        // check 4 in a row in all directions using playerName
        return checkDirection(row, col, playerName, 1, 0)   // horizontal
                || checkDirection(row, col, playerName, 0, 1)   // vertical
                || checkDirection(row, col, playerName, 1, 1)   // diag \
                || checkDirection(row, col, playerName, 1, -1); // diag /
    }

    private boolean checkDirection(int row, int col, String playerName, int dc, int dr) {
        int count = 1;
        // positive direction
        int r = row + dr, c = col + dc;
        while (r >= 0 && r < ROWS && c >= 0 && c < COLS && playerAt(r, c, playerName)) {
            count++; r += dr; c += dc;
        }
        // negative direction
        r = row - dr; c = col - dc;
        while (r >= 0 && r < ROWS && c >= 0 && c < COLS && playerAt(r, c, playerName)) {
            count++; r -= dr; c -= dc;
        }
        return count >= 4;
    }

    private boolean playerAt(int row, int col, String playerName) {
        String p = board[row][col];
        return p != null && p.equals(playerName);
    }

    private boolean isBoardFull() {
        for (int c = 0; c < COLS; c++) if (board[0][c] == null) return false;
        return true;
    }

    private void resetAfterRound() {
        synchronized (this) {
            clearBoard();
            // if enough players, start new round and set currentTurn to first connected
            if (clients.snapshot().size() >= REQUIRED_CLIENTS_TO_START) {
                gameStatus = "playing";
                List<String> players = new ArrayList<>(clients.snapshot().values());
                currentTurn = players.isEmpty() ? null : players.get(0);
            } else {
                gameStatus = "waiting";
                currentTurn = null;
            }
        }
    }

    // ----------------- Broadcast -----------------

    private void startTicker() {
        long periodMs = Math.max(1, 1000 / SEND_FPS);
        ticker.scheduleAtFixedRate(() -> {
            try {
                if (!clients.snapshot().isEmpty()) broadcastStatus();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Builds and sends the authoritative serverData to each client.
     * For each client it sets "clientName" (that client's own name) and includes:
     * - clientsList: array of ClientData JSONs (we add "turn": true for currentTurn)
     * - objectsList: array of placed pieces { id, row, col, color }
     * - game: { status, currentTurn, winner, lastMove }
     */
    private void broadcastStatus() {
        JSONObject base = new JSONObject();
        base.put("type", T_SERVER_DATA);

        // clients list with turn flag
        JSONArray arrClients = new JSONArray();
        for (ClientData cd : clientsData.values()) {
            JSONObject j = cd.toJSON();
            j.put("turn", cd.name != null && cd.name.equals(currentTurn));
            arrClients.put(j);
        }
        base.put("clientsList", arrClients);

        // objects list (placed pieces)
        JSONArray arrObjects = new JSONArray();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                String p = board[r][c];
                if (p != null) {
                    String color = clientsData.containsKey(p) ? clientsData.get(p).color : "RED";
                    JSONObject o = new JSONObject();
                    o.put("id", p);
                    o.put("row", r);
                    o.put("col", c);
                    o.put("color", color);
                    arrObjects.put(o);
                }
            }
        }
        base.put("objectsList", arrObjects);

        // game object
        JSONObject game = new JSONObject();
        game.put("status", gameStatus);
        game.put("currentTurn", currentTurn == null ? JSONObject.NULL : currentTurn);
        game.put("winner", winner == null ? JSONObject.NULL : winner);
        game.put("lastMove", lastMove == null ? JSONObject.NULL : lastMove);
        base.put("game", game);

        // send to each client, but include clientName field specific to recipient
        Map<WebSocket, String> snapshot = clients.snapshot();
        for (Map.Entry<WebSocket, String> e : snapshot.entrySet()) {
            WebSocket conn = e.getKey();
            String clientName = e.getValue();
            try {
                base.put("clientName", clientName);
                sendSafe(conn, base.toString());
            } catch (Exception ignored) {
            }
        }
    }

    private void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (Exception ex) {
            // if connection lost, cleanup
            String name = clients.cleanupDisconnected(to);
            clientsData.remove(name);
            System.out.println("Client disconnected during send: " + name);
        }
    }

    // ----------------- Countdown -----------------

    private void startCountdownIfReady() {
        if (clients.snapshot().size() >= REQUIRED_CLIENTS_TO_START && !"playing".equals(gameStatus)) {
            // start countdown only if not already playing
            scheduler.execute(() -> {
                synchronized (this) {
                    gameStatus = "countdown";
                }
                try {
                    for (int i = 5; i >= 0; i--) {
                        broadcastCountdown(i);
                        Thread.sleep(750);
                    }
                } catch (InterruptedException ignored) {
                }
                synchronized (this) {
                    if (clients.snapshot().size() >= REQUIRED_CLIENTS_TO_START) {
                        gameStatus = "playing";
                        // set first connected as currentTurn
                        List<String> players = new ArrayList<>(clients.snapshot().values());
                        currentTurn = players.isEmpty() ? null : players.get(0);
                    } else {
                        gameStatus = "waiting";
                        currentTurn = null;
                    }
                }
            });
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

    // ----------------- Utilities -----------------

    private String getColorForName(String name) {
        int idx = PLAYER_NAMES.indexOf(name);
        if (idx < 0) idx = 0;
        return PLAYER_COLORS.get(idx % PLAYER_COLORS.size());
    }

    // ----------------- Shutdown -----------------

    @Override
    public void stop() throws Exception {
        super.stop();
        try {
            ticker.shutdownNow();
            scheduler.shutdownNow();
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ignored) {}
        }));
        System.out.println("Server running on port " + DEFAULT_PORT);
    }
}
