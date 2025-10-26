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
        System.out.println("CtrlCountdown inicializado");
        // El countdown se inicia cuando se muestra esta vista
    }
    
    public void startCountdown(int startCount) {
        count = startCount;
        System.out.println("Iniciando countdown desde: " + count);
        
        // Mostrar el primer número inmediatamente
        lblCountdown.setText(String.valueOf(count));
        
        countdownTimeline = new Timeline();
        
        // Crear keyframes para cada número del countdown
        for (int i = count; i > 0; i--) {
            final int currentNumber = i;
            KeyFrame keyFrame = new KeyFrame(
                Duration.seconds(count - i), // Tiempo desde el inicio
                event -> {
                    lblCountdown.setText(String.valueOf(currentNumber));
                    System.out.println("Countdown: " + currentNumber);
                }
            );
            countdownTimeline.getKeyFrames().add(keyFrame);
        }
        
        // Añadir el frame final para "¡GO!"
        KeyFrame goFrame = new KeyFrame(
            Duration.seconds(count),
            event -> {
                lblCountdown.setText("¡GO!");
                System.out.println("Countdown: ¡GO!");
                
                // Esperar un momento antes de cambiar a la vista del juego
                Main.pauseDuring(1000, () -> {
                    System.out.println("Countdown terminado, cambiando a juego...");
                    // La transición se manejará automáticamente con el próximo serverData
                });
            }
        );
        countdownTimeline.getKeyFrames().add(goFrame);
        
        countdownTimeline.play();
    }
    
    // Método alternativo más simple
    public void startCountdownSimple(int startCount) {
        count = startCount;
        System.out.println("Iniciando countdown simple desde: " + count);
        
        lblCountdown.setText(String.valueOf(count));
        
        countdownTimeline = new Timeline();
        countdownTimeline.setCycleCount(startCount + 1); // +1 para incluir el "¡GO!"
        
        KeyFrame keyFrame = new KeyFrame(Duration.seconds(1), event -> {
            if (count > 1) {
                count--;
                lblCountdown.setText(String.valueOf(count));
                System.out.println("Countdown: " + count);
            } else if (count == 1) {
                lblCountdown.setText("¡GO!");
                System.out.println("Countdown: ¡GO!");
                count = 0;
            }
        });
        
        countdownTimeline.getKeyFrames().add(keyFrame);
        countdownTimeline.play();
    }
    
    // Método con Timeline separado para mejor control
    public void startCountdownSequential(int startCount) {
        count = startCount;
        System.out.println("Iniciando countdown secuencial desde: " + count);
        
        // Detener cualquier timeline anterior
        stopCountdown();
        
        // Mostrar el primer número
        lblCountdown.setText(String.valueOf(count));
        
        // Crear una nueva timeline
        countdownTimeline = new Timeline();
        
        // Añadir cada paso del countdown
        for (int i = count - 1; i >= 0; i--) {
            final int remaining = i;
            double time = (count - i); // 1, 2, 3 segundos...
            
            KeyFrame frame;
            if (remaining > 0) {
                frame = new KeyFrame(
                    Duration.seconds(time),
                    e -> {
                        lblCountdown.setText(String.valueOf(remaining));
                        System.out.println("Countdown: " + remaining);
                    }
                );
            } else {
                frame = new KeyFrame(
                    Duration.seconds(time),
                    e -> {
                        lblCountdown.setText("¡GO!");
                        System.out.println("Countdown: ¡GO!");
                        
                        // Esperar y luego la transición se hará automáticamente
                        Main.pauseDuring(800, () -> {
                            System.out.println("Transición automática a juego...");
                        });
                    }
                );
            }
            countdownTimeline.getKeyFrames().add(frame);
        }
        
        countdownTimeline.play();
    }
    
    public void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }
}