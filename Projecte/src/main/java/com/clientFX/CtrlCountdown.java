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
    private int currentCount = 3;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        System.out.println("🔧 CtrlCountdown inicializado");
    }
    
    public void startCountdown() {
        System.out.println("🚀 INICIANDO COUNTDOWN");
        
        // Detener y limpiar timeline anterior completamente
        stopCountdown();
        
        currentCount = 3;
        lblCountdown.setText(String.valueOf(currentCount));
        
        // Crear timeline con KeyFrames EXPLÍCITOS para cada número
        countdownTimeline = new Timeline();
        
        // KeyFrame para 3 → 2
        KeyFrame frame3to2 = new KeyFrame(
            Duration.seconds(1),
            e -> {
                currentCount = 2;
                lblCountdown.setText("2");
                System.out.println("🔢 Countdown: 2");
            }
        );
        
        // KeyFrame para 2 → 1  
        KeyFrame frame2to1 = new KeyFrame(
            Duration.seconds(2),
            e -> {
                currentCount = 1;
                lblCountdown.setText("1");
                System.out.println("🔢 Countdown: 1");
            }
        );
        
        // KeyFrame para 1 → GO
        KeyFrame frame1toGO = new KeyFrame(
            Duration.seconds(3),
            e -> {
                lblCountdown.setText("¡GO!");
                System.out.println("🎯 Countdown: ¡GO!");
                
                // Pequeña pausa antes de la transición automática
                Main.pauseDuring(500, () -> {
                    System.out.println("✅ Countdown COMPLETADO");
                });
            }
        );
        
        countdownTimeline.getKeyFrames().addAll(frame3to2, frame2to1, frame1toGO);
        countdownTimeline.setCycleCount(1); // Solo una ejecución
        
        countdownTimeline.play();
    }
    
    public void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
        currentCount = 3;
        lblCountdown.setText("3"); // Reset visual
    }

    
}
