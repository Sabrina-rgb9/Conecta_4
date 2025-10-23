package com.clientFX;

import org.json.JSONObject;

public class GameSession {
    private String playerName;
    private String opponentName;
    private String playerRole;
    private String opponentRole;
    private boolean isMyTurn;

    public GameSession(String playerName) {
        this.playerName = playerName;
        this.opponentName = "";
        this.playerRole = "";
        this.opponentRole = "";
        this.isMyTurn = false;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public String getPlayerRole() {
        return playerRole;
    }

    public String getOpponentRole() {
        return opponentRole;
    }

    public boolean isMyTurn() {
        return isMyTurn;
    }

    public void setOpponentName(String opponentName) {
        this.opponentName = opponentName;
    }

    public void setPlayerRole(String playerRole) {
        this.playerRole = playerRole;
    }

    public void setOpponentRole(String opponentRole) {
        this.opponentRole = opponentRole;
    }

    public void setMyTurn(boolean myTurn) {
        isMyTurn = myTurn;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("playerName", playerName);
        json.put("opponentName", opponentName);
        json.put("playerRole", playerRole);
        json.put("opponentRole", opponentRole);
        json.put("isMyTurn", isMyTurn);
        return json;
    }

    public static GameSession fromJSON(JSONObject json) {
        GameSession session = new GameSession(json.optString("playerName", ""));
        session.setOpponentName(json.optString("opponentName", ""));
        session.setPlayerRole(json.optString("playerRole", ""));
        session.setOpponentRole(json.optString("opponentRole", ""));
        session.setMyTurn(json.optBoolean("isMyTurn", false));
        return session;
    }
}
