package com.server;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Representa una partida de Connecta 4 entre dos jugadores.
 */
public class GameSession {
    public final String playerRed;
    public final String playerYellow;
    public String[][] board = new String[6][7]; // 6 filas x 7 columnas
    public String currentTurn; // nombre del jugador
    public String winner = "";
    public AtomicBoolean finished = new AtomicBoolean(false);
    public JSONObject lastMove = null;

    public GameSession(String red, String yellow) {
        this.playerRed = red;
        this.playerYellow = yellow;
        this.currentTurn = red;
        // inicializar tablero vacío
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 7; c++)
                board[r][c] = " ";
    }

    /**
     * Intenta jugar en la columna. Retorna true si el movimiento es válido.
     */
    public synchronized boolean play(String player, int col) {
        if (finished.get() || !player.equals(currentTurn)) return false;
        for (int r = 5; r >= 0; r--) {
            if (board[r][col].equals(" ")) {
                board[r][col] = (player.equals(playerRed)) ? "R" : "Y";
                lastMove = new JSONObject().put("col", col).put("row", r);
                // comprobar victoria
                if (checkWin(r, col)) finished.set(true);
                // comprobar empate
                else if (isBoardFull()) finished.set(true);
                // cambiar turno
                else currentTurn = (currentTurn.equals(playerRed)) ? playerYellow : playerRed;
                return true;
            }
        }
        return false; // columna llena
    }

    private boolean isBoardFull() {
        for (int c = 0; c < 7; c++)
            if (board[0][c].equals(" ")) return false;
        return true;
    }

    private boolean checkWin(int row, int col) {
        String role = board[row][col];
        int[][] dirs = {{1,0},{0,1},{1,1},{1,-1}}; // vertical, horizontal, diagonales
        for (int[] d : dirs) {
            int count = 1;
            for (int sign = -1; sign <= 1; sign += 2) {
                int r = row + d[0]*sign, c = col + d[1]*sign;
                while (r >= 0 && r < 6 && c >= 0 && c < 7 && board[r][c].equals(role)) {
                    count++;
                    r += d[0]*sign;
                    c += d[1]*sign;
                }
            }
            if (count >= 4) {
                winner = (role.equals("R")) ? playerRed : playerYellow;
                return true;
            }
        }
        return false;
    }

    /**
     * Devuelve el estado completo de la partida en JSON
     */
    public JSONObject toJSON(String requestingPlayer) {
        JSONObject obj = new JSONObject();
        obj.put("status", finished.get() ? "finished" : "playing");
        obj.put("turn", currentTurn);
        obj.put("winner", winner);
        obj.put("board", new JSONArray(board));
        obj.put("lastMove", lastMove == null ? JSONObject.NULL : lastMove);
        obj.put("role", requestingPlayer.equals(playerRed) ? "R" : "Y");
        return obj;
    }
}
