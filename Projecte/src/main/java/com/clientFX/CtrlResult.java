package com.clientFX;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.json.JSONObject;

public class CtrlResult {

    @FXML private Label lblResult;
    @FXML private Label lblPlayerName;
    @FXML private Button btnBack;
    @FXML private Button btnClose;

    private String playerName = "";

    @FXML
    public void initialize() {
        btnBack.setOnAction(e -> goBackToSelection());
        btnClose.setOnAction(e -> Platform.exit());
    }

    public void setPlayerName(String name) {
        this.playerName = name;
        Platform.runLater(() -> lblPlayerName.setText(name));
    }

    public void handleMessage(JSONObject msg) {
        if ("gameResult".equals(msg.getString("type"))) {
            String result = msg.getString("result"); // "win", "lose", "draw"
            String winner = msg.optString("winner", "");

            Platform.runLater(() -> {
                if ("win".equals(result)) {
                    lblResult.setText("Has guanyat!");
                } else if ("lose".equals(result)) {
                    lblResult.setText("Has perdut :(");
                } else if ("draw".equals(result)) {
                    lblResult.setText("Empat!");
                } else {
                    lblResult.setText("Partida finalitzada");
                }
            });
        }
    }

    @FXML
    private void goBackToSelection() {
        UtilsViews.setView("ViewOpponentSelection");
    }
}