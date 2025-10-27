package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.json.JSONObject;

public class CtrlCountdown {

    @FXML private Label lblCountdown;

    public void initialize() {
        if (lblCountdown != null) lblCountdown.setText("");
    }

    public void handleMessage(JSONObject msg) {
        // Tu servidor usa "count" o "seconds". Aceptamos ambos.
        int seconds = msg.optInt("count", msg.optInt("seconds", -1));
        if (seconds >= 0) lblCountdown.setText(String.valueOf(seconds));
    }
}
