// com/clientFX/CtrlResult.java
package com.clientFX;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import java.net.URL;
import java.util.ResourceBundle;
import com.shared.GameState;

public class CtrlResult implements Initializable {

    @FXML
    private Label lblResult;
    
    @FXML
    private Label lblDetails;
    
    @FXML
    private Button btnPlayAgain;
    
    @FXML
    private Button btnExit;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Configurar botones
        btnPlayAgain.setOnAction(event -> {
            // Volver a selección de oponente
            UtilsViews.setViewAnimating("ViewOpponentSelection");
        });
        
        btnExit.setOnAction(event -> {
            // Volver a configuración
            UtilsViews.setViewAnimating("ViewConfig");
        });
    }
    
    public void updateResult(GameState gameState) {
        if (gameState != null && gameState.getGame() != null) {
            String status = gameState.getGame().getStatus();
            String winner = gameState.getGame().getWinner();
            
            if ("win".equals(status)) {
                if (Main.playerName.equals(winner)) {
                    lblResult.setText("¡HAS GANADO!");
                    lblResult.setTextFill(Color.GREEN);
                    lblDetails.setText("Felicidades, has conectado 4 fichas");
                } else {
                    lblResult.setText("HAS PERDIDO");
                    lblResult.setTextFill(Color.RED);
                    lblDetails.setText("El ganador es: " + winner);
                }
            } else if ("draw".equals(status)) {
                lblResult.setText("EMPATE");
                lblResult.setTextFill(Color.ORANGE);
                lblDetails.setText("El tablero se ha llenado sin ganador");
            }
        }
    }
}