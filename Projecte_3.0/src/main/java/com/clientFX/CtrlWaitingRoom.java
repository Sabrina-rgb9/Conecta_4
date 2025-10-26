// com/clientFX/CtrlWaitingRoom.java
package com.clientFX;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import java.net.URL;
import java.util.ResourceBundle;

public class CtrlWaitingRoom implements Initializable {

    @FXML
    private Label lblStatus;
    
    @FXML
    private ProgressIndicator progressIndicator;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblStatus.setText("Esperando contrincante...");
    }
    
    public void updateStatus(String status) {
        lblStatus.setText(status);
    }
    
    public void setMatching(boolean matching) {
        if (matching) {
            lblStatus.setText("Emparellant...");
            progressIndicator.setVisible(true);
        } else {
            progressIndicator.setVisible(false);
        }
    }
}