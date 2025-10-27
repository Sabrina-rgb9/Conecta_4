package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.json.JSONObject;

public class CtrlCountdown {

    @FXML
    private Label lblCountdown;

    public CtrlCountdown() {}

    public void handleMessage(JSONObject msg) {
        if (msg.has("count")) {
            int count = msg.getInt("count");
            lblCountdown.setText(String.valueOf(count));
        }
    }
}
