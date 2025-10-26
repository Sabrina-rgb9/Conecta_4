package com.clientFX;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.application.Platform;
import java.net.URL;
import java.util.ResourceBundle;
import com.shared.GameState;
import org.json.JSONObject;

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
        System.out.println("CtrlResult inicializado");
        
        // Configurar botones
        btnPlayAgain.setOnAction(event -> {
            System.out.println("Botón Jugar otra vez presionado");
            handlePlayAgain();
        });
        
        btnExit.setOnAction(event -> {
            System.out.println("Botón Salir presionado");
            handleExit();
        });
    }
    
    @FXML
    private void handlePlayAgain() {
        System.out.println("Método handlePlayAgain ejecutado");
        
        try {
            // Limpiar el estado actual del juego
            Main.currentGameState = null;
            Main.myRole = "";
            
            // Enviar mensaje al servidor para volver al lobby
            JSONObject backToLobbyMsg = new JSONObject();
            backToLobbyMsg.put("type", "clientBackToLobby");
            if (Main.wsClient != null) {
                Main.wsClient.safeSend(backToLobbyMsg.toString());
            }
            
            // Cambiar a la vista de selección de oponentes
            UtilsViews.setViewAnimating("ViewOpponentSelection");
            
            System.out.println("Volviendo a la selección de oponentes");
            
        } catch (Exception e) {
            System.err.println("Error en handlePlayAgain: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleExit() {
        System.out.println("Método handleExit ejecutado - Cerrando aplicación");
        
        try {
            // Enviar mensaje de desconexión al servidor
            JSONObject exitMsg = new JSONObject();
            exitMsg.put("type", "clientExit");
            if (Main.wsClient != null) {
                Main.wsClient.safeSend(exitMsg.toString());
            }
            
            // Cerrar la conexión WebSocket
            if (Main.wsClient != null) {
                Main.wsClient.forceExit();
            }
            
            // Cerrar la aplicación completamente
            Platform.exit();
            System.exit(0);
            
        } catch (Exception e) {
            System.err.println("Error en handleExit: " + e.getMessage());
            e.printStackTrace();
            
            // Forzar cierre incluso si hay error
            Platform.exit();
            System.exit(0);
        }
    }
    
    public void updateResult(GameState gameState) {
        if (gameState != null && gameState.getGame() != null) {
            String status = gameState.getGame().getStatus();
            String winner = gameState.getGame().getWinner();
            
            System.out.println("Actualizando resultado - Estado: " + status + ", Ganador: " + winner);
            
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
            } else {
                lblResult.setText("PARTIDA TERMINADA");
                lblDetails.setText("La partida ha finalizado");
            }
        } else {
            lblResult.setText("RESULTADO");
            lblDetails.setText("Información no disponible");
        }
    }
}