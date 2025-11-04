package com.clientFX;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import java.net.URL;
import java.util.ResourceBundle;
import com.shared.GameState;
import com.shared.ClientInfo;
import org.json.JSONObject;

public class CtrlOpponentSelection implements Initializable {

    @FXML
    private ListView<String> listPlayers;
    
    @FXML
    private Label lblStatus;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Configurar lista de jugadores
        listPlayers.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedPlayer = listPlayers.getSelectionModel().getSelectedItem();
                if (selectedPlayer != null && !selectedPlayer.equals(Main.playerName)) {
                    sendInvitation(selectedPlayer);
                }
            }
        });
        
        // Inicializar estado
        lblStatus.setText("Conectando al servidor...");
    }
    
    public void updatePlayersList(GameState gameState) {
        System.out.println("Actualizando lista de jugadores...");
        
        if (gameState == null) {
            lblStatus.setText("Error: datos del juego no disponibles");
            return;
        }
        
        if (gameState.getClientsList() == null) {
            lblStatus.setText("No hay jugadores conectados");
            listPlayers.getItems().clear();
            return;
        }
        
        // Limpiar la lista actual
        listPlayers.getItems().clear();
        
        int availablePlayers = 0;
        for (ClientInfo client : gameState.getClientsList()) {
            String clientName = client.getName();
            
            // Solo mostrar jugadores que no sean yo mismo
            if (clientName != null && !clientName.equals(Main.playerName)) {
                listPlayers.getItems().add(clientName);
                availablePlayers++;
                System.out.println("Añadido jugador: " + clientName);
            }
        }
        
        // Actualizar el mensaje de estado
        if (availablePlayers == 0) {
            lblStatus.setText("No hay otros jugadores conectados");
        } else {
            lblStatus.setText(availablePlayers + " jugador(es) disponible(s). Haz doble click para invitar.");
        }
        
        System.out.println("Lista actualizada. Jugadores disponibles: " + availablePlayers);
    }
    
    // En CtrlOpponentSelection.java, en sendInvitation:
    private void sendInvitation(String opponentName) {
        try {
            JSONObject invitation = new JSONObject();
            invitation.put("type", "clientInvite");
            invitation.put("opponent", opponentName);
            
            Main.wsClient.safeSend(invitation.toString());
            
            // Marcar invitación pendiente
            Main.invitationPending = true;
            Main.pendingOpponent = opponentName;
            
            // Iniciar timeout
            Main.startInvitationTimeout(opponentName);
            
            System.out.println("Invitación enviada a " + opponentName + ". Cambiando a sala de espera...");
            UtilsViews.setViewAnimating("ViewWaitingRoom");
            
            CtrlWaitingRoom waitingCtrl = (CtrlWaitingRoom) UtilsViews.getController("ViewWaitingRoom");
            if (waitingCtrl != null) {
                waitingCtrl.updateStatus("Invitación enviada a " + opponentName + ". Esperando respuesta...");
            }
            
        } catch (Exception e) {
            System.err.println("Error sending invitation: " + e.getMessage());
            lblStatus.setText("Error al enviar invitación");
        }
    }
    
    public void handleIncomingInvitation(String fromPlayer) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Invitación de partida");
        alert.setHeaderText("¡Invitación recibida!");
        alert.setContentText("¿Aceptas jugar contra " + fromPlayer + "?");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                Main.acceptInvitation(fromPlayer);
                // El que acepta también va a sala de espera
                UtilsViews.setViewAnimating("ViewWaitingRoom");
                CtrlWaitingRoom waitingCtrl = (CtrlWaitingRoom) UtilsViews.getController("ViewWaitingRoom");
                if (waitingCtrl != null) {
                    waitingCtrl.updateStatus("Aceptada invitación de " + fromPlayer + ". Iniciando partida...");
                }
            } else {
                Main.rejectInvitation(fromPlayer);
                updateStatus("Rechazada invitación de " + fromPlayer);
            }
        });
    }
    
    public void updateStatus(String message) {
        lblStatus.setText(message);
    }
}