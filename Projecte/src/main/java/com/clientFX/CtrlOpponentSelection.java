package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.paint.Color;
import org.json.JSONArray;
import org.json.JSONObject;

public class CtrlOpponentSelection {

    @FXML
    private ListView<String> lstOpponents;
    @FXML
    private Label lblStatus;
    @FXML
    private Button btnInvite;
    @FXML
    private Button btnBack;

    private String selectedOpponent = "";

    @FXML
    public void initialize() {
        lblStatus.setText("Selecciona un oponent per començar la partida");
        lblStatus.setTextFill(Color.BLACK);

        lstOpponents.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedOpponent = newVal;
            lblStatus.setText("Oponent seleccionat: " + selectedOpponent);
        });

        btnInvite.setOnAction(e -> inviteOpponent());
        btnBack.setOnAction(e -> backToConfig());
    }

    public void handleMessage(JSONObject msg) {
        String type = msg.getString("type");

        switch (type) {
            case "clients" -> updateOpponentList(msg);
            case "invitation" -> {
                String from = msg.getString("from");
                lblStatus.setText("Has rebut una invitació de " + from);
                lblStatus.setTextFill(Color.BLUE);
            }
            case "invitationAccepted" -> {
                lblStatus.setText("Invitació acceptada! Esperant inici de la partida...");
                lblStatus.setTextFill(Color.GREEN);
                Main.connectedByUser = true;
            }
            case "invitationRejected" -> {
                lblStatus.setText("El jugador ha rebutjat la teva invitació.");
                lblStatus.setTextFill(Color.RED);
            }
        }
    }

    private void updateOpponentList(JSONObject msg) {
        lstOpponents.getItems().clear();
        JSONArray list = msg.getJSONArray("list");

        for (int i = 0; i < list.length(); i++) {
            String name = list.getString(i);
            if (!name.equals(Main.playerName)) {
                lstOpponents.getItems().add(name);
            }
        }

        if (lstOpponents.getItems().isEmpty()) {
            lblStatus.setText("No hi ha altres jugadors connectats");
            lblStatus.setTextFill(Color.GRAY);
        }
    }

    private void inviteOpponent() {
        if (selectedOpponent == null || selectedOpponent.isEmpty()) {
            lblStatus.setText("Selecciona un oponent primer!");
            lblStatus.setTextFill(Color.RED);
            return;
        }

        JSONObject invite = new JSONObject();
        invite.put("type", "invitation");
        invite.put("from", Main.playerName);
        invite.put("to", selectedOpponent);
        Main.wsClient.sendMessage(invite.toString());

        lblStatus.setText("Invitació enviada a " + selectedOpponent);
        lblStatus.setTextFill(Color.DARKGREEN);
    }

    private void backToConfig() {
        Main.connectedByUser = false;
        Main.wsClient.close();
        UtilsViews.setView("ViewConfig");
    }
}
