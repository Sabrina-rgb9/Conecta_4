package com.clientFX;

import javafx.application.Platform;
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
    private Map<String, String> pendingInvitations = new HashMap<>();
    private String playerName = "";

    @FXML
    public void initialize() {
        lstAvailablePlayers.setItems(availablePlayers);

        btnInvite.setOnAction(e -> sendInvite());

        lstAvailablePlayers.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            btnInvite.setDisable(selected == null || selected.equals(playerName));
        });

        btnInvite.setDisable(true);
    }

    public void setPlayerName(String name) {
        this.playerName = name;
        Platform.runLater(() -> lblPlayerName.setText(name));
    }

    public String getPlayerName() {
        return playerName;
    }

    public void handleMessage(JSONObject msg) {
        String type = msg.getString("type");

        switch (type) {
            case "clients" -> {
                JSONArray list = msg.getJSONArray("list");
                availablePlayers.clear();
                for (int i = 0; i < list.length(); i++) {
                    String player = list.getString(i);
                    if (!player.equals(playerName)) {
                        availablePlayers.add(player);
                    }
                }
            }
            case "invite" -> {
                String origin = msg.getString("origin");
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Invitación");
                    alert.setHeaderText("Invitación de " + origin);
                    alert.setContentText("¿Deseas aceptar la partida?");
                    if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        JSONObject acceptMsg = new JSONObject();
                        acceptMsg.put("type", "acceptInvite");
                        acceptMsg.put("origin", origin);
                        Main.wsClient.safeSend(acceptMsg.toString());
                    }
                });
            }
            case "invitationSent" -> lblStatus.setText("Invitación enviada a " + msg.getString("dest"));
            case "invitationAccepted" -> UtilsViews.setView("ViewWaitingRoom");
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
