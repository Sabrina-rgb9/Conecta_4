package com.clientFX;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.util.Duration;
import org.json.JSONObject;

public class CtrlWaitingRoom {

    @FXML private Label lblStatus;
    @FXML private Label lblOpponent;
    @FXML private Label lblPlayerName;

    private String playerName = "";
    private String opponentName = "";

    @FXML
    public void initialize() {
        lblStatus.setText("Esperant contrincant...");
        lblOpponent.setText("---");
    }

    public void setPlayerName(String name) {
        this.playerName = name;
        Platform.runLater(() -> lblPlayerName.setText(name));
    }

    public void setOpponentName(String name) {
        this.opponentName = name;
        Platform.runLater(() -> lblOpponent.setText(name));
    }

    public void handleMessage(JSONObject msg) {
        String type = msg.getString("type");

        switch (type) {
            case "gameStarted" -> {
                // El servidor confirma que la partida ha començat
                setOpponentName(msg.getString("opponent"));
                lblStatus.setText("Partida iniciada! Preparant compte enrere...");
                // Canviar automàticament al compte enrere després d'1 segon
                pauseThenSwitchToCountdown();
            }
            case "countdown" -> {
                // Si rebem directament un countdown, anem-hi
                UtilsViews.setView("ViewCountdown");
                Main.ctrlCountdown.handleMessage(msg);
            }
            case "opponentDisconnected" -> {
                String opponent = msg.getString("name");
                lblStatus.setText("El contrincant " + opponent + " s'ha desconnectat.");
                lblOpponent.setText("(desconnectat)");
                // Tornar a selecció després de 3 segons
                PauseTransition pause = new PauseTransition(Duration.seconds(3));
                pause.setOnFinished(e -> Platform.runLater(() -> UtilsViews.setView("ViewOpponentSelection")));
                pause.play();
            }
        }
    }

    private void pauseThenSwitchToCountdown() {
        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(e -> Platform.runLater(() -> {
            UtilsViews.setView("ViewCountdown");
            // Enviar senyal per iniciar el countdown (si cal)
            JSONObject readyMsg = new JSONObject();
            readyMsg.put("type", "clientReady");
            Main.wsClient.safeSend(readyMsg.toString());
        }));
        pause.play();
    }
}