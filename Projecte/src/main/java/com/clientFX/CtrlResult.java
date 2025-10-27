package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.json.JSONObject;

public class CtrlResult {

    @FXML
    private Label lblResult;

    public CtrlResult() {}

    public void handleMessage(JSONObject msg) {
        if (msg.has("winner")) {
            String winner = msg.getString("winner");
            if ("draw".equals(winner)) {
                lblResult.setText("Empate!");
            } else if (winner.equals(Main.playerName)) {
                lblResult.setText("¡Has ganado!");
            } else {
                lblResult.setText("Has perdido. Ganador: " + winner);
            }
        }
    }
}
