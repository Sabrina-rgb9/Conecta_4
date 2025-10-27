package com.clientFX;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

public class CtrlConfig {

    @FXML public TextField txtProtocol;
    @FXML public TextField txtHost;
    @FXML public TextField txtPort;
    @FXML public TextField txtName;
    @FXML private Label lblMessage;

    @FXML
    public void initialize() {
        if (txtProtocol != null) txtProtocol.setText("ws");
        if (txtHost != null) txtHost.setText("localhost");
        if (txtPort != null) txtPort.setText("3000");
        if (txtName != null) txtName.setText("Jugador");
    }

    @FXML
    private void onConnect() {
        String protocol = txtProtocol.getText().trim();
        String host = txtHost.getText().trim();
        String port = txtPort.getText().trim();
        String name = txtName.getText().trim();

        if (protocol.isEmpty() || host.isEmpty() || port.isEmpty() || name.isEmpty()) {
            showMessage("Rellena todos los campos", Color.RED);
            return;
        }

        Main.playerName = name;
        String url = protocol + "://" + host + ":" + port;
        showMessage("Conectando a " + url, Color.BLACK);
        Main.connectToServer(url);
    }

    public void showMessage(String msg, Color c) {
        if (lblMessage != null) {
            lblMessage.setText(msg);
            lblMessage.setTextFill(c);
        } else {
            System.out.println(msg);
        }
    }
}
