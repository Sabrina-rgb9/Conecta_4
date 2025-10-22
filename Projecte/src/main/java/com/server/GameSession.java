package com.server;

import org.java_websocket.WebSocket;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

public class GameSession {

    public enum Status { WAITING, COUNTDOWN, PLAYING, WIN, DRAW, FINISHED }

    private final String playerR;
    private final String playerY;

    private final char[][] board = new char[6][7];

    private volatile String turn;
    private volatile Status status = Status.WAITING;
    private volatile String winner = "";
    private volatile int lastMoveCol = -1;
    private volatile int lastMoveRow = -1;

    private final AtomicInteger countdownSeconds = new AtomicInteger(0);

    public GameSession(String playerR, String playerY) {
        this.playerR = playerR;
        this.playerY = playerY;
        clearBoard();
        this.turn = playerR;
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

    public synchronized JSONObject play(String playerName, int column) {
        JSONObject resp = new JSONObject();
        resp.put("ok", false);

        if (status != Status.PLAYING) { resp.put("reason", "Not playing"); return resp; }
        if (!playerName.equals(turn)) { resp.put("reason", "Not your turn"); return resp; }
        if (column < 0 || column > 6) { resp.put("reason", "Invalid column"); return resp; }

        int row = dropRow(column);
        if (row == -1) { resp.put("reason", "Column full"); return resp; }

        char piece = playerName.equals(playerR) ? 'R' : 'Y';
        board[row][column] = piece;
        lastMoveCol = column;
        lastMoveRow = row;

        if (checkWin(row, column, piece)) {
            status = Status.WIN;
            winner = playerName;
        } else if (isBoardFull()) {
            status = Status.DRAW;
            winner = "";
        } else {
            turn = turn.equals(playerR) ? playerY : playerR;
        }

        resp.put("ok", true);
        resp.put("lastMove", new JSONObject().put("col", column).put("row", row));
        resp.put("status", status.name());
        resp.put("winner", winner);
        return resp;
    }

    private int dropRow(int col) {
        for (int r = 5; r >= 0; r--) if (board[r][col] == ' ') return r;
        return -1;
    }

    private boolean isBoardFull() {
        for (int c = 0; c < 7; c++) if (board[0][c] == ' ') return false;
        return true;
    }

    private boolean checkWin(int row, int col, char piece) {
        int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
        for (int[] d : dirs) {
            int count = 1 + countDirection(row,col,d[0],d[1],piece) + countDirection(row,col,-d[0],-d[1],piece);
            if (count >= 4) return true;
        }
        return false;
    }

    private int countDirection(int r,int c,int dr,int dc,char piece) {
        int rr=r+dr,cc=c+dc,cnt=0;
        while(rr>=0 && rr<6 && cc>=0 && cc<7 && board[rr][cc]==piece){cnt++; rr+=dr; cc+=dc;}
        return cnt;
    }

    public synchronized JSONObject toServerData() {
        JSONObject res = new JSONObject();
        res.put("type","serverData");
        res.put("status",status.name().toLowerCase());

        JSONArray clientsList = new JSONArray();
        clientsList.put(new JSONObject().put("name",playerR).put("role","R"));
        clientsList.put(new JSONObject().put("name",playerY).put("role","Y"));
        res.put("clientsList",clientsList);

        JSONObject game = new JSONObject();
        game.put("status",status.name().toLowerCase());
        game.put("playerR",playerR);
        game.put("playerY",playerY);

        JSONArray boardArr = new JSONArray();
        for(int r=0;r<6;r++){
            JSONArray row = new JSONArray();
            for(int c=0;c<7;c++) row.put(Character.toString(board[r][c]));
            boardArr.put(row);
        }
        game.put("board",boardArr);
        game.put("turn",turn);
        if(lastMoveCol>=0 && lastMoveRow>=0) game.put("lastMove", new JSONObject().put("col",lastMoveCol).put("row",lastMoveRow));
        else game.put("lastMove", JSONObject.NULL);

        game.put("winner",winner==null?"":winner);

        res.put("game",game);
        return res;
    }

    public void sendStateTo(WebSocket ws, ClientRegistry registry){
        JSONObject msg = toServerData();
        if(ws!=null) try{ ws.send(msg.toString()); } catch(Exception e){ e.printStackTrace(); }
    }

    public void broadcastState(ClientRegistry registry){
        JSONObject msg = toServerData();
        WebSocket wr = registry.socketByName(playerR);
        WebSocket wy = registry.socketByName(playerY);
        if(wr!=null) try{ wr.send(msg.toString()); } catch(Exception e){ registry.cleanupDisconnected(wr); }
        if(wy!=null) try{ wy.send(msg.toString()); } catch(Exception e){ registry.cleanupDisconnected(wy); }
    }

    public void finish(){ setStatus(Status.FINISHED); }

    public boolean involves(String name){ return playerR.equals(name)||playerY.equals(name); }

    public boolean isPlayerTurn(String name){ return name.equals(turn); }

    public String getWinner(){ return winner; }
}
