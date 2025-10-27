package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import org.json.JSONArray;
import org.json.JSONObject;

public class CtrlOpponentSelection {

    @FXML
    private ListView<String> lstPlayers;

    public CtrlOpponentSelection() {}

    @FXML
    public void initialize() {
        lstPlayers.getItems().clear();
    }

    public void handleMessage(JSONObject msg) {
        if (msg.has("list")) {
            JSONArray list = msg.getJSONArray("list");
            lstPlayers.getItems().clear();
            for (int i = 0; i < list.length(); i++) {
                String player = list.getString(i);
                if (!player.equals(Main.playerName)) {
                    lstPlayers.getItems().add(player);
                }
            }
        }
    }

    @FXML
    public void onInviteClicked() {
        String opponent = lstPlayers.getSelectionModel().getSelectedItem();
        if (opponent == null) return;

        JSONObject inviteMsg = new JSONObject();
        inviteMsg.put("type", "invite");
        inviteMsg.put("to", opponent);
        inviteMsg.put("from", Main.playerName);

        Main.wsClient.send(inviteMsg.toString());
        Main.readyToPlay = true;
    }
}
