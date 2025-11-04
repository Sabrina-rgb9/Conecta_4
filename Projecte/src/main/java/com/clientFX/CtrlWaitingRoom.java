package com.clientFX;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import java.net.URL;
import java.util.ResourceBundle;

public class CtrlWaitingRoom implements Initializable {

    @FXML
    private Label lblStatus;
    
    @FXML
    private ProgressIndicator progressIndicator;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblStatus.setText("Conectando...");
        progressIndicator.setVisible(true);
    }
    
    public void updateStatus(String status) {
        lblStatus.setText(status);
    }
    
    public void setMatching(boolean matching) {
        if (matching) {
            lblStatus.setText("Emparellant...");
            progressIndicator.setVisible(true);
        } else {
            progressIndicator.setVisible(false);
        }
    }

    public void handleOpponentDisconnected(String opponentName) {
        System.out.println("🔌 Oponente se desconectó durante la sala de espera: " + opponentName);
        
        Platform.runLater(() -> {
            // Mostrar mensaje y volver a selección de oponente
            updateStatus(opponentName + " se desconectó. Volviendo al menú...");
            
            // Pequeño delay antes de volver
            Main.pauseDuring(2000, () -> {
                UtilsViews.setView("ViewOpponentSelection");
            });
        });
    }
}