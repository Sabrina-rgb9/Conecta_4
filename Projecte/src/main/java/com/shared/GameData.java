package com.shared;

public class GameData {
    private String status;
    private String[][] board;
    private String turn;
    private Move lastMove;
    private String winner;
    
    public GameData() {}
    
    // Getters y setters
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String[][] getBoard() { return board; }
    public void setBoard(String[][] board) { this.board = board; }
    
    public String getTurn() { return turn; }
    public void setTurn(String turn) { this.turn = turn; }
    
    public Move getLastMove() { return lastMove; }
    public void setLastMove(Move lastMove) { this.lastMove = lastMove; }
    
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
}