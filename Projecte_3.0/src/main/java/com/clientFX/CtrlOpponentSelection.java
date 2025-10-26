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
        lblStatus.setText("Cargando jugadores...");
    }
    
    public void updatePlayersList(GameState gameState) {
        if (gameState != null && gameState.getClientsList() != null) {
            listPlayers.getItems().clear();
            
            int availablePlayers = 0;
            for (ClientInfo client : gameState.getClientsList()) {
                // Mostrar solo jugadores disponibles (no soy yo y no están en partida)
                if (!client.getName().equals(Main.playerName)) {
                    listPlayers.getItems().add(client.getName());
                    availablePlayers++;
                }
            }
            
            if (availablePlayers == 0) {
                lblStatus.setText("No hay jugadores disponibles. Esperando más conexiones...");
            } else {
                lblStatus.setText(availablePlayers + " jugador(es) disponible(s). Haz doble click para invitar.");
            }
            
            System.out.println("Actualizada lista: " + availablePlayers + " jugadores disponibles");
        } else {
            lblStatus.setText("Error cargando lista de jugadores");
            System.err.println("GameState o clientsList es null");
        }
    }
    
    private void sendInvitation(String opponentName) {
        try {
            JSONObject invitation = new JSONObject();
            invitation.put("type", "clientInvite");
            invitation.put("opponent", opponentName);
            
            Main.wsClient.safeSend(invitation.toString());
            lblStatus.setText("Invitación enviada a " + opponentName);
            System.out.println("Invitación enviada a: " + opponentName);
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
                updateStatus("Aceptada invitación de " + fromPlayer);
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