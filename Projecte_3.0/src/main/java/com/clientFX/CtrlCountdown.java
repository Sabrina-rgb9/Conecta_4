// com/clientFX/CtrlCountdown.java
package com.clientFX;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import java.net.URL;
import java.util.ResourceBundle;

public class CtrlCountdown implements Initializable {

    @FXML
    private Label lblCountdown;
    
    private Timeline countdownTimeline;
    private int count = 3;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // El countdown se inicia cuando se muestra esta vista
    }
    
    public void startCountdown(int startCount) {
        count = startCount;
        lblCountdown.setText(String.valueOf(count));
        
        countdownTimeline = new Timeline();
        countdownTimeline.setCycleCount(startCount);
        
        KeyFrame keyFrame = new KeyFrame(Duration.seconds(1), event -> {
            count--;
            if (count > 0) {
                lblCountdown.setText(String.valueOf(count));
            } else {
                lblCountdown.setText("¡GO!");
                // Esperar un momento y forzar cambio a juego
                Main.pauseDuring(1000, () -> {
                    // Esto forzará al servidor a enviar el estado "playing"
                    System.out.println("Countdown terminado - esperando estado playing...");
                });
            }
        });
        
        countdownTimeline.getKeyFrames().add(keyFrame);
        countdownTimeline.play();
    }
    
    public void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
    }
}