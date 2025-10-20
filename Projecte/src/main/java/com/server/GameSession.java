package com.server;

import org.java_websocket.WebSocket;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Representa una sessió de joc Connecta4 entre dos clients.
 * Manté l'estat del tauler i fa la validació de jugades.
 */
public class GameSession {

    public enum Status { WAITING, COUNTDOWN, PLAYING, WIN, DRAW, FINISHED }

    private final String playerR;   // nom assignat pel registry -> "R"
    private final String playerY;   // nom assignat pel registry -> "Y"

    // 6 files (0..5) x 7 columnes (0..6) -> board[row][col]
    // empty = ' ', red = 'R', yellow = 'Y'
    private final char[][] board = new char[6][7];

    private volatile String turn; // nom del jugador que té el torn
    private volatile Status status = Status.WAITING;
    private volatile String winner = "";
    private volatile int lastMoveCol = -1;
    private volatile int lastMoveRow = -1;

    // temps de countdown en segons (si cal)
    private final AtomicInteger countdownSeconds = new AtomicInteger(0);

    public GameSession(String playerR, String playerY) {
        this.playerR = playerR;
        this.playerY = playerY;
        clearBoard();
        this.turn = playerR; // per defecte: R comença
        this.status = Status.WAITING;
    }

    private void clearBoard() {
        for (int r = 0; r < 6; r++) for (int c = 0; c < 7; c++) board[r][c] = ' ';
    }

    public String getPlayerR() { return playerR; }
    public String getPlayerY() { return playerY; }

    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }

    public void startCountdown(int seconds) {
        countdownSeconds.set(seconds);
        setStatus(Status.COUNTDOWN);
    }

    public int tickCountdown() {
        int left = countdownSeconds.decrementAndGet();
        if (left <= 0) {
            countdownSeconds.set(0);
            setStatus(Status.PLAYING);
        }
        return left;
    }

    /**
     * Intenta aplicar una jugada enviada per un client.
     * Només s'accepta si:
     *  - estat = PLAYING
     *  - el nom del jugador és el que té el torn
     *  - la columna no està plena
     *
     * Retorna un JSONObject amb el resultat (ok:true/false i raó).
     */
    public synchronized JSONObject play(String playerName, int column) {
        JSONObject resp = new JSONObject();
        resp.put("ok", false);

        if (status != Status.PLAYING) {
            resp.put("reason", "Not playing");
            return resp;
        }
        if (!playerName.equals(turn)) {
            resp.put("reason", "Not your turn");
            return resp;
        }
        if (column < 0 || column > 6) {
            resp.put("reason", "Invalid column");
            return resp;
        }
        int row = dropRow(column);
        if (row == -1) {
            resp.put("reason", "Column full");
            return resp;
        }

        char piece = playerName.equals(playerR) ? 'R' : 'Y';
        board[row][column] = piece;
        lastMoveCol = column;
        lastMoveRow = row;

        // comprovar guanyador
        if (checkWin(row, column, piece)) {
            status = Status.WIN;
            winner = playerName;
        } else if (isBoardFull()) {
            status = Status.DRAW;
            winner = "";
        } else {
            // canvi de torn
            turn = turn.equals(playerR) ? playerY : playerR;
        }

        resp.put("ok", true);
        resp.put("lastMove", new JSONObject().put("col", column).put("row", row));
        resp.put("status", status.name());
        resp.put("winner", winner);
        return resp;
    }

    /** Retorna la fila on cauria la fitxa per la columna, o -1 si està plena */
    private int dropRow(int col) {
        for (int r = 5; r >= 0; r--) {
            if (board[r][col] == ' ') return r;
        }
        return -1;
    }

    private boolean isBoardFull() {
        for (int c = 0; c < 7; c++) if (board[0][c] == ' ') return false;
        return true;
    }

    /**
     * Comprova 4 en línia al voltant de (row,col) per la fitxa 'piece'.
     */
    private boolean checkWin(int row, int col, char piece) {
        // direccions: horitzontal, vertical, diag1, diag2
        int[][] dirs = {{0,1}, {1,0}, {1,1}, {1,-1}};
        for (int[] d : dirs) {
            int count = 1;
            // forward
            count += countDirection(row, col, d[0], d[1], piece);
            // backward
            count += countDirection(row, col, -d[0], -d[1], piece);
            if (count >= 4) return true;
        }
        return false;
    }

    private int countDirection(int r, int c, int dr, int dc, char piece) {
        int rr = r + dr, cc = c + dc, cnt = 0;
        while (rr >= 0 && rr < 6 && cc >= 0 && cc < 7 && board[rr][cc] == piece) {
            cnt++;
            rr += dr; cc += dc;
        }
        return cnt;
    }

    /**
     * Retorna l'estat de la sessió en format JSONObject per enviar com a serverData.
     * Inclou board, players, turn, lastMove, status, winner.
     */
    public synchronized JSONObject toServerData() {
        JSONObject res = new JSONObject();
        res.put("type", "serverData");
        res.put("status", status.name().toLowerCase()); // waiting|countdown|playing|win|draw

        JSONObject game = new JSONObject();
        game.put("status", status.name().toLowerCase());
        // board com 6 files on cada fila és array de 7 strings
        JSONArray boardArr = new JSONArray();
        for (int r = 0; r < 6; r++) {
            JSONArray row = new JSONArray();
            for (int c = 0; c < 7; c++) {
                row.put(Character.toString(board[r][c]));
            }
            boardArr.put(row);
        }
        game.put("board", boardArr);
        game.put("turn", turn);
        if (lastMoveCol >= 0 && lastMoveRow >= 0) {
            game.put("lastMove", new JSONObject().put("col", lastMoveCol).put("row", lastMoveRow));
        } else {
            game.put("lastMove", JSONObject.NULL);
        }
        game.put("winner", winner == null ? "" : winner);

        res.put("game", game);

        // players list minimal info
        JSONArray clientsList = new JSONArray();
        JSONObject r = new JSONObject().put("name", playerR).put("role", "R");
        JSONObject y = new JSONObject().put("name", playerY).put("role", "Y");
        clientsList.put(r).put(y);
        res.put("clientsList", clientsList);

        return res;
    }

    /** Envia l'serverData a un socket concret (via registry) */
    public void sendStateTo(WebSocket ws, ClientRegistry registry) {
        JSONObject msg = toServerData();
        WebSocket target = ws;
        if (target != null) {
            try {
                target.send(msg.toString());
            } catch (Exception e) {
                // si falla, cleanup ho farà el Main
                e.printStackTrace();
            }
        }
    }

    /** Util per broadcast: envia l'estat a tots dos jugadors. */
    public void broadcastState(ClientRegistry registry) {
        JSONObject msg = toServerData();
        WebSocket wr = registry.socketByName(playerR);
        WebSocket wy = registry.socketByName(playerY);
        if (wr != null) {
            try { wr.send(msg.toString()); } catch (Exception e) { registry.cleanupDisconnected(wr); }
        }
        if (wy != null) {
            try { wy.send(msg.toString()); } catch (Exception e) { registry.cleanupDisconnected(wy); }
        }
    }

    /** Marca la sessió com a finalitzada (opcional cleanup posterior). */
    public void finish() {
        setStatus(Status.FINISHED);
    }

    public boolean involves(String name) {
        return playerR.equals(name) || playerY.equals(name);
    }

    public boolean isPlayerTurn(String name) { return name.equals(turn); }

    // Afegeix a GameSession.java
    public String getWinner() {
        return winner;
    }

    // Afegeix aquest mètode (és nou)
    // public GameSession getSession(String sessionId) {
    //     return sessions.get(sessionId);
    // }

    // Getter per debugging
    public JSONObject debugInfo() {
        JSONObject d = new JSONObject();
        d.put("playerR", playerR);
        d.put("playerY", playerY);
        d.put("status", status.name());
        d.put("turn", turn);
        return d;
    }
}