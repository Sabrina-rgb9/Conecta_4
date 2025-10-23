package com.clientFX;

import javafx.application.Platform;
import org.json.JSONObject;

public class GameManager {
    private static GameManager instance;
    private GameSession currentSession;

    private GameManager() {
    }

    public static synchronized GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public void createSession(String playerName) {
        currentSession = new GameSession(playerName);
    }

    public GameSession getSession() {
        return currentSession;
    }

    public void handleServerMessage(JSONObject msg) {
        if (msg == null || !msg.has("type")) return;

        String type = msg.getString("type");

        switch (type) {
            case "updateClients" -> Platform.runLater(() -> {
                CtrlOpponentSelection ctrl = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
                if (ctrl != null) ctrl.updateAvailablePlayers(msg);
            });

            case "inviteReceived" -> Platform.runLater(() -> {
                CtrlWaitingRoom ctrl = (CtrlWaitingRoom) UtilsViews.getController("ViewWaitingRoom");
                if (ctrl != null) ctrl.handleInvite(msg);
            });

            case "gameStart" -> Platform.runLater(() -> {
                CtrlGame ctrl = (CtrlGame) UtilsViews.getController("ViewGame");
                if (ctrl != null) ctrl.startGame(msg);
            });

            case "move" -> Platform.runLater(() -> {
                CtrlGame ctrl = (CtrlGame) UtilsViews.getController("ViewGame");
                if (ctrl != null) ctrl.updateBoard(msg);
            });

            case "gameResult" -> Platform.runLater(() -> {
                CtrlResult ctrl = (CtrlResult) UtilsViews.getController("ViewResult");
                if (ctrl != null) ctrl.handleMessage(msg);
            });

            case "error" -> Platform.runLater(() ->
                UtilsViews.showError("Error del servidor: " + msg.optString("message", "Desconegut"))
            );

            default -> System.out.println("⚠️ Missatge desconegut del servidor: " + msg);
        }
    }

    public void sendMessage(JSONObject json) {
        if (Main.wsClient != null && Main.wsClient.isOpen()) {
            Main.wsClient.send(json.toString());
        } else {
            System.err.println("⚠️ No hi ha connexió amb el servidor.");
        }
    }

    public void reset() {
        currentSession = null;
    }
}
