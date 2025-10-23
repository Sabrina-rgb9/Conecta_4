package com.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.util.Map;

public class CtrlWait {

    @FXML
    public Label txtTitle;

    @FXML
    public Label txtPlayer0;

    @FXML
    public Label txtPlayer1;

    // Estado de los jugadores conectados
    private String[] players = new String[2];

    /**
     * Se llama cuando se recibe una actualización del servidor.
     * @param clients Map de nombre -> ClientData
     * @param currentTurn nombre del jugador con turno activo
     */
    public void updatePlayers(Map<String, String> clients, String currentTurn) {
        Platform.runLater(() -> {
            int i = 0;
            for (String name : clients.values()) {
                if (i < 2) players[i++] = name;
            }
            txtPlayer0.setText(players[0] != null ? players[0] : "?");
            txtPlayer1.setText(players[1] != null ? players[1] : "?");

            if (currentTurn != null && currentTurn.equals(UtilsWS.getSharedInstance("").clientName)) {
                txtTitle.setText("Es tu turno");
            } else {
                txtTitle.setText("Esperando jugador...");
            }
        });
    }
}
