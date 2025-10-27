package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.json.JSONObject;

public class CtrlResult {

    @FXML private Label lblResult;

    public void handleMessage(JSONObject msg) {
        String type = msg.optString("type", "");
        if ("gameResult".equals(type)) {
            String result = msg.optString("result", "");
            if ("win".equals(result)) {
                String winner = msg.optString("winner", "");
                if (winner.equals(Main.playerName)) lblResult.setText("¡Has ganado!");
                else lblResult.setText("Has perdido. Ganador: " + winner);
            } else if ("draw".equals(result)) {
                lblResult.setText("Empate");
            } else {
                lblResult.setText("Resultado: " + result);
            }
        } else if (msg.has("winner")) {
            String winner = msg.optString("winner", "");
            if ("draw".equalsIgnoreCase(winner)) lblResult.setText("Empate");
            else if (winner.equals(Main.playerName)) lblResult.setText("¡Has ganado!");
            else lblResult.setText("Ha ganado: " + winner);
        } else {
            lblResult.setText("Resultado desconocido");
        }
    }
}
