package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.json.JSONObject;

public class CtrlWaitingRoom {

    @FXML
    private Label lblStatus;

    public CtrlWaitingRoom() {}

    public void handleMessage(JSONObject msg) {
        if (msg.has("type")) {
            String type = msg.getString("type");
            switch (type) {
                case "invitationAccepted":
                    lblStatus.setText("Oponente aceptó. Esperando inicio...");
                    break;
                case "gameStarted":
                    lblStatus.setText("Partida iniciada!");
                    break;
                default:
                    lblStatus.setText("Esperando jugadores...");
            }
        }
    }
}
