package com.clientFX;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;

public class CtrlConfig implements Initializable {

    @FXML
    public TextField txtProtocol;

    @FXML
    public TextField txtHost;

    @FXML
    public TextField txtPort;

    @FXML
    public TextField txtPlayerName; // Campo para ingresar el nombre del jugador

    @FXML
    public Label txtMessage;

    @FXML 
    private Button btnConnect;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Configuración inicial por defecto
        setConfigLocal();
    }

    /**
     * Acción del botón "Connect"
     */
    @FXML
    private void connectToServer() {
        String player = txtPlayerName.getText().trim();
        String protocol = txtProtocol.getText().trim();
        String host = txtHost.getText().trim();
        String port = txtPort.getText().trim();

        if (player.isEmpty() || protocol.isEmpty() || host.isEmpty() || port.isEmpty()) {
            showMessage("Tots els camps són obligatoris", Color.RED);
            return;
        }

        // Guardar datos en Main
        Main.playerName = player;
        Main.connectedByUser = true;
        Main.readyToPlay = true;

        // Mostrar mensaje de conexión
        showMessage("Connectant...", Color.BLACK);

        // Llamar al método de Main para iniciar la conexión
        Main.connectToServer();
    }

    /**
     * Configuración local predefinida
     */
    @FXML
    private void setConfigLocal() {
        txtProtocol.setText("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
    }

    /**
     * Configuración Proxmox predefinida
     */
    @FXML
    private void setConfigProxmox() {
        txtProtocol.setText("wss");
        txtHost.setText("ccarrillocrespo.ieti.site");
        txtPort.setText("443");
    }

    /**
     * Muestra un mensaje temporal en txtMessage
     * @param message Texto a mostrar
     * @param color Color del texto
     */
    public void showMessage(String message, Color color) {
        txtMessage.setTextFill(color);
        txtMessage.setText(message);

        // Desaparece después de 2 segundos usando la función de Main
        Main.pauseDuring(2000, () -> txtMessage.setText(""));
    }
}
