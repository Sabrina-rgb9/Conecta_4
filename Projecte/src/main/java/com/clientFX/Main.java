package com.clientFX;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.json.JSONObject;

/**
 * Main del cliente JavaFX.
 * - Carga todas las vistas (FXML) en UtilsViews.
 * - Mantiene referencias a los controladores.
 * - Gestiona la conexión WebSocket y delega la lógica al GameManager.
 */
public class Main extends Application {

    public static UtilsWS wsClient;

    public static CtrlConfig ctrlConfig;
    public static CtrlOpponentSelection ctrlOpponentSelection;
    public static CtrlWaitingRoom ctrlWaitingRoom;
    public static CtrlCountdown ctrlCountdown;
    public static CtrlGame ctrlGame;
    public static CtrlResult ctrlResult;

    private static String myName;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        final int width = 950;
        final int height = 700;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");

        // Cargar vistas
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewOpponentSelection", "/assets/viewOpponentSelection.fxml");
        UtilsViews.addView(getClass(), "ViewWaitingRoom", "/assets/viewWaitingRoom.fxml");
        UtilsViews.addView(getClass(), "ViewCountdown", "/assets/viewCountdown.fxml");
        UtilsViews.addView(getClass(), "ViewGame", "/assets/viewGame.fxml");
        UtilsViews.addView(getClass(), "ViewResult", "/assets/viewResult.fxml");

        // Obtener controladores
        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlOpponentSelection = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
        ctrlWaitingRoom = (CtrlWaitingRoom) UtilsViews.getController("ViewWaitingRoom");
        ctrlCountdown = (CtrlCountdown) UtilsViews.getController("ViewCountdown");
        ctrlGame = (CtrlGame) UtilsViews.getController("ViewGame");
        ctrlResult = (CtrlResult) UtilsViews.getController("ViewResult");

        Scene scene = new Scene(UtilsViews.parentContainer, width, height);
        stage.setScene(scene);
        stage.setTitle("Connecta 4 - JavaFX");
        stage.setMinWidth(width);
        stage.setMinHeight(height);

        try {
            Image icon = new Image(getClass().getResourceAsStream("/assets/icon.png"));
            stage.getIcons().add(icon);
        } catch (Exception ignored) {}

        stage.show();
    }

    @Override
    public void stop() {
        if (wsClient != null) wsClient.forceExit();
        Platform.exit();
    }

    // ---------------------------
    // Utilidades de UI
    // ---------------------------
    public static void pauseDuring(long ms, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(ms));
        pause.setOnFinished(e -> Platform.runLater(action));
        pause.play();
    }

    private static void showError(String msg) {
        Platform.runLater(() -> {
            if (ctrlConfig != null && ctrlConfig.txtMessage != null) {
                ctrlConfig.txtMessage.setTextFill(Color.RED);
                ctrlConfig.txtMessage.setText(msg);
                pauseDuring(2000, () -> ctrlConfig.txtMessage.setText(""));
            }
        });
    }

    // ---------------------------
    // Conexión con el servidor
    // ---------------------------
    public static void connectToServer() {
        if (ctrlConfig == null) return;

        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connectant...");

        pauseDuring(800, () -> {
            String protocol = ctrlConfig.txtProtocol.getText().trim();
            String host = ctrlConfig.txtHost.getText().trim();
            String port = ctrlConfig.txtPort.getText().trim();

            if (protocol.isEmpty() || host.isEmpty() || port.isEmpty()) {
                showError("Tots els camps són obligatoris");
                return;
            }

            String wsUrl = protocol + "://" + host + ":" + port;
            try {
                wsClient = UtilsWS.getSharedInstance(wsUrl);
            } catch (Exception ex) {
                showError("No s'ha pogut crear WS: " + ex.getMessage());
                return;
            }

            wsClient.onOpen(msg ->
                Platform.runLater(() -> {
                    ctrlConfig.txtMessage.setTextFill(Color.GREEN);
                    ctrlConfig.txtMessage.setText("Conectat");
                    pauseDuring(1000, () -> ctrlConfig.txtMessage.setText(""));
                })
            );

            wsClient.onError(err -> Platform.runLater(() -> showError("Error WS: " + err)));

            wsClient.onMessage(response -> {
                if (response == null || response.isEmpty()) return;
                try {
                    JSONObject msg = new JSONObject(response);
                    handleWebSocketMessage(msg);
                } catch (Exception e) {
                    System.err.println("JSON inválido: " + response);
                }
            });
        });
    }

    // ---------------------------
    // Procesamiento de mensajes WS
    // ---------------------------
    private static void handleWebSocketMessage(JSONObject msg) {
        if (msg == null || !msg.has("type")) return;

        String type = msg.getString("type");

        // Actualiza nombre local si viene del servidor
        if ("clients".equals(type)) {
            myName = msg.optString("id", myName);
        }

        // ✅ Delegamos la gestión del mensaje al GameManager
        GameManager.getInstance().handleServerMessage(msg);
    }

    // Getter auxiliar
    public static String getMyName() {
        return myName;
    }
}
