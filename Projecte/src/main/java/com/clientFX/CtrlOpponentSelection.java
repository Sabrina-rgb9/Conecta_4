package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import org.json.JSONArray;
import org.json.JSONObject;

public class CtrlOpponentSelection {

    @FXML private ListView<String> lstPlayers;
    @FXML private Button btnInvite;

    public void initialize() {
        if (lstPlayers != null) lstPlayers.getItems().clear();
    }

    /**
     * Recibe serverData o mensajes de lista y actualiza la lista de jugadores.
     * Acepta msg tipo serverData con clientsList[] o msg tipo "clients" con list[].
     */
    public void handleMessage(JSONObject msg) {
        try {
            if (msg.has("clientsList")) {
                JSONArray arr = msg.getJSONArray("clientsList");
                lstPlayers.getItems().clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    String name = c.optString("name", "");
                    if (!name.isEmpty() && !name.equals(Main.playerName)) {
                        lstPlayers.getItems().add(name);
                    }
                }
            } else if (msg.has("list")) {
                JSONArray arr = msg.getJSONArray("list");
                lstPlayers.getItems().clear();
                for (int i = 0; i < arr.length(); i++) {
                    String name = arr.getString(i);
                    if (!name.equals(Main.playerName)) lstPlayers.getItems().add(name);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onInvite() {
        String opponent = lstPlayers.getSelectionModel().getSelectedItem();
        if (opponent == null) return;

        JSONObject j = new JSONObject();
        j.put("type", "clientInvite"); // coincide con tu servidor: clientInvite
        j.put("opponent", opponent);
        j.put("from", Main.playerName);

        if (Main.wsClient != null) Main.wsClient.send(j.toString());

        Main.readyToPlay = true;
        // Ir a sala de espera
        UtilsViews.setView("ViewWaitingRoom");
    }
}
