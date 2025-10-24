package com.clientFX;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.net.URL;
import java.util.ResourceBundle;

public class CtrlConfig implements Initializable {

    @FXML
    public TextField txtProtocol;

    @FXML
    public TextField txtHost;

    @FXML
    public TextField txtPort;

    @FXML
    public TextField txtPlayerName; // Para que el jugador pueda poner su nombre

    @FXML
    public Label txtMessage;

    @FXML
    private Button btnConnect;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Inicializar valores por defecto
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
    }

    @FXML
    private void connectToServer() {
        // Validar campos
        String player = txtPlayerName.getText().trim();
        String protocol = txtProtocol.getText().trim();
        String host = txtHost.getText().trim();
        String port = txtPort.getText().trim();

        if (player.isEmpty() || protocol.isEmpty() || host.isEmpty() || port.isEmpty()) {
            showMessage("Tots els camps són obligatoris", Color.RED);
            return;
        }

        Main.playerName = player;
        Main.connectToServer();
    }

    @FXML
    private void setConfigLocal() {
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
    }

    @FXML
    private void setConfigProxmox() {
        txtProtocol.setText("wss");
        txtHost.setText("ccarrillo.ieti.site"); // IP o dominio Proxmox
        txtPort.setText("443");
    }

    /**
     * Muestra un mensaje temporal en txtMessage
     */
    private void showMessage(String message, Color color) {
        txtMessage.setTextFill(color);
        txtMessage.setText(message);

        // Desaparece después de 2 segundos
        Main.pauseDuring(2000, () -> txtMessage.setText(""));
    }
}
