package com.clientFX;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.util.Duration;
import org.json.JSONObject;

public class CtrlCountdown {

    @FXML private Label lblCountdown;
    @FXML private Label lblStatus;

    private int currentCount = 3;

    @FXML
    public void initialize() {
        lblCountdown.setText(String.valueOf(currentCount));
        lblStatus.setText("Comença la partida!");
        startCountdown();
    }

    public void handleMessage(JSONObject msg) {
        if ("countdown".equals(msg.getString("type"))) {
            int seconds = msg.getInt("seconds");
            if (seconds > 0) {
                Platform.runLater(() -> {
                    lblCountdown.setText(String.valueOf(seconds));
                    currentCount = seconds;
                });
            } else {
                // El servidor indica que el compte enrere ha acabat
                goToGameView();
            }
        }
    }

    private void startCountdown() {
        if (currentCount <= 0) {
            goToGameView();
            return;
        }

        PauseTransition pause = new PauseTransition(Duration.seconds(1));
        pause.setOnFinished(e -> {
            currentCount--;
            Platform.runLater(() -> {
                if (currentCount > 0) {
                    lblCountdown.setText(String.valueOf(currentCount));
                    startCountdown(); // Recursiu
                } else {
                    lblCountdown.setText("¡Jugant!");
                    PauseTransition finalPause = new PauseTransition(Duration.seconds(0.5));
                    finalPause.setOnFinished(ev -> goToGameView());
                    finalPause.play();
                }
            });
        });
        pause.play();
    }

    private void goToGameView() {
        Platform.runLater(() -> UtilsViews.setView("ViewGame"));
    }
}