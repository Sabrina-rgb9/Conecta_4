package com.clientFX;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class CtrlOpponentSelection {

    @FXML private ListView<String> lstAvailablePlayers;
    @FXML private Button btnInvite;
    @FXML private Label lblStatus;
    @FXML private Label lblPlayerName;

    private ObservableList<String> availablePlayers = FXCollections.observableArrayList();
    private Map<String, String> pendingInvitations = new HashMap<>(); // origin -> invited

    @FXML
    public void initialize() {
        lstAvailablePlayers.setItems(availablePlayers);
        btnInvite.setOnAction(e -> sendInvite());
        lstAvailablePlayers.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            btnInvite.setDisable(selected == null || selected.equals(Main.wsClient.getClientName()));
        });
        btnInvite.setDisable(true);
    }

    public void setPlayerName(String name) {
        lblPlayerName.setText(name);
    }

    /** Processa missatges del servidor */
    public void handleMessage(JSONObject msg) {
        String type = msg.getString("type");

        switch (type) {
            case "clients" -> {
                // Actualitza la llista de jugadors disponibles
                JSONArray list = msg.getJSONArray("list");
                availablePlayers.clear();
                for (int i = 0; i < list.length(); i++) {
                    String player = list.getString(i);
                    if (!player.equals(Main.wsClient.getClientName())) {
                        availablePlayers.add(player);
                    }
                }
            }
            case "invite" -> {
                // Rebre invitació
                String origin = msg.getString("origin");
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Invitació");
                alert.setHeaderText("Invitació de " + origin);
                alert.setContentText("Vols acceptar la partida?");
                if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    // Enviar acceptació
                    JSONObject acceptMsg = new JSONObject();
                    acceptMsg.put("type", "acceptInvite");
                    acceptMsg.put("origin", origin);
                    Main.wsClient.safeSend(acceptMsg.toString());
                }
            }
            case "invitationSent" -> {
                lblStatus.setText("Invitació enviada a " + msg.getString("dest"));
            }
            case "invitationAccepted" -> {
                // Canviar a vista de sala d'espera
                UtilsViews.setView("ViewWaitingRoom");
            }
        }
    }

    private void sendInvite() {
        String selected = lstAvailablePlayers.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        JSONObject inviteMsg = new JSONObject();
        inviteMsg.put("type", "invite");
        inviteMsg.put("dest", selected);
        Main.wsClient.safeSend(inviteMsg.toString());
    }
}