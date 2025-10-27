package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.control.Label;

public class CtrlConfig {

    @FXML
    public TextField txtProtocol, txtHost, txtPort, txtName;

    @FXML
    private Label lblMessage;

    public CtrlConfig() {}

    @FXML
    public void initialize() {
        // Valores por defecto
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("8080");
        txtName.setText("Jugador1");
    }

    @FXML
    public void onConnectClicked() {
        String name = txtName.getText().trim();
        if (name.isEmpty()) {
            showMessage("Introduce un nombre", Color.RED);
            return;
        }
        Main.playerName = name;
        Main.connectedByUser = true;
        Main.connectToServer();
    }

    public void showMessage(String msg, Color color) {
        if (lblMessage != null) {
            lblMessage.setText(msg);
            lblMessage.setTextFill(color);
        }
    }
}
