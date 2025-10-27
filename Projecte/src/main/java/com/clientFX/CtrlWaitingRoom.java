package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.json.JSONObject;

public class CtrlWaitingRoom {

    @FXML private Label lblStatus;

    public void initialize() {
        if (lblStatus != null) lblStatus.setText("En espera...");
    }

    public void handleMessage(JSONObject msg) {
        String type = msg.optString("type", "");
        if ("invitation".equals(type) || "invite".equals(type)) {
            String invitationType = msg.optString("invitationType", msg.optString("invitationType", ""));
            if ("received".equals(invitationType)) {
                String from = msg.optString("from", msg.optString("origin", "??"));
                lblStatus.setText("Invitación recibida de: " + from);
            } else if ("accepted".equals(invitationType)) {
                lblStatus.setText("Invitación aceptada. Preparando partida...");
            } else if ("rejected".equals(invitationType)) {
                lblStatus.setText("Invitación rechazada.");
            } else {
                // mensajes genéricos de inicio
                lblStatus.setText("Esperando...");
            }
        } else if ("gameStarted".equals(type)) {
            lblStatus.setText("Partida iniciada!");
        } else if (msg.has("game") && msg.getJSONObject("game").optString("status", "").equalsIgnoreCase("playing")) {
            lblStatus.setText("Partida en curso");
        } else if ("opponentDisconnected".equals(type)) {
            lblStatus.setText("Oponente desconectado: " + msg.optString("name", ""));
        }
    }
}
