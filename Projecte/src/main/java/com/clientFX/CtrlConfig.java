package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

public class CtrlConfig {

    @FXML
    public TextField txtProtocol;
    @FXML
    public TextField txtHost;
    @FXML
    public TextField txtPort;
    @FXML
    public TextField txtPlayerName;
    @FXML
    public Label lblMessage;
    @FXML
    public Button btnConnect;

    @FXML
    private void initialize() {
        lblMessage.setText("");
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
    }

    @FXML
    private void onConnectClick() {
        String protocol = txtProtocol.getText().trim();
        String host = txtHost.getText().trim();
        String port = txtPort.getText().trim();
        String playerName = txtPlayerName.getText().trim();

        if (protocol.isEmpty() || host.isEmpty() || port.isEmpty() || playerName.isEmpty()) {
            showMessage("Tots els camps són obligatoris", Color.RED);
            return;
        }

        Main.playerName = playerName;
        Main.connectedByUser = true;
        Main.connectToServer();
    }

    public void showMessage(String msg, Color color) {
        lblMessage.setTextFill(color);
        lblMessage.setText(msg);
    }
}
