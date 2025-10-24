package com.clientFX;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.json.JSONObject;

public class GameManager {
    private static GameManager instance;
    private GameSession currentSession;

    private GameManager() {}

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
            case "clients" -> Platform.runLater(() -> {
                CtrlOpponentSelection ctrl = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
                if (ctrl != null) ctrl.handleMessage(msg);
            });

            case "invite" -> Platform.runLater(() -> {
                CtrlOpponentSelection ctrl = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
                if (ctrl != null) ctrl.handleMessage(msg);
            });

            case "invitationSent" -> Platform.runLater(() -> {
                CtrlOpponentSelection ctrl = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
                if (ctrl != null) ctrl.handleMessage(msg);
            });

            case "invitationAccepted" -> Platform.runLater(() -> {
                CtrlOpponentSelection ctrl = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
                if (ctrl != null) ctrl.handleMessage(msg);
            });

            case "gameStarted", "countdown", "opponentDisconnected" -> Platform.runLater(() -> {
                CtrlWaitingRoom ctrl = (CtrlWaitingRoom) UtilsViews.getController("ViewWaitingRoom");
                if (ctrl != null) ctrl.handleMessage(msg);
            });

            case "gameResult" -> Platform.runLater(() -> {
                CtrlResult ctrl = (CtrlResult) UtilsViews.getController("ViewResult");
                if (ctrl != null) ctrl.handleMessage(msg);
            });

            case "error" -> Platform.runLater(() -> showError("Error del servidor: " + msg.optString("message", "Desconegut")));

            default -> System.out.println("⚠️ Missatge desconegut del servidor: " + msg);
        }
    }

    public void sendMessage(JSONObject json) {
        if (Main.wsClient != null && Main.wsClient.isOpen()) {
            Main.wsClient.safeSend(json.toString());
        } else {
            System.err.println("⚠️ No hi ha connexió amb el servidor.");
        }
    }

    public void reset() {
        currentSession = null;
    }

    // 🔸 Método local de utilidad para mostrar errores sin depender de UtilsViews
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error de comunicació");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
