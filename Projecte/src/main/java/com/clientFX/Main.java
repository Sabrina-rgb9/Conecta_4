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

public class Main extends Application {

    public static UtilsWS wsClient;

    public static CtrlConfig ctrlConfig;
    public static CtrlOpponentSelection ctrlOpponentSelection;
    public static CtrlWaitingRoom ctrlWaitingRoom;
    public static CtrlCountdown ctrlCountdown;
    public static CtrlGame ctrlGame;
    public static CtrlResult ctrlResult;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) throws Exception {
        final int windowWidth = 850;
        final int windowHeight = 700;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");

        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewOpponentSelection", "/assets/viewOpponentSelection.fxml");
        UtilsViews.addView(getClass(), "ViewWaitingRoom", "/assets/viewWaitingRoom.fxml");
        UtilsViews.addView(getClass(), "ViewCountdown", "/assets/viewCountdown.fxml");
        UtilsViews.addView(getClass(), "ViewGame", "/assets/viewGame.fxml");
        UtilsViews.addView(getClass(), "ViewResult", "/assets/viewResult.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlOpponentSelection = (CtrlOpponentSelection) UtilsViews.getController("ViewOpponentSelection");
        ctrlWaitingRoom = (CtrlWaitingRoom) UtilsViews.getController("ViewWaitingRoom");
        ctrlCountdown = (CtrlCountdown) UtilsViews.getController("ViewCountdown");
        ctrlGame = (CtrlGame) UtilsViews.getController("ViewGame");
        ctrlResult = (CtrlResult) UtilsViews.getController("ViewResult");

        Scene scene = new Scene(UtilsViews.parentContainer, windowWidth, windowHeight);
        stage.setScene(scene);
        stage.setTitle("Connecta 4 - JavaFX");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            try {
                Image icon = new Image(getClass().getResourceAsStream("/assets/icon.png"));
                stage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("No se encontró el icono");
            }
        }
    }

    @Override
    public void stop() {
        if (wsClient != null) wsClient.forceExit();
        Platform.exit();
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    private static String getCurrentPlayerName() {
        if (ctrlOpponentSelection != null) return ctrlOpponentSelection.getPlayerName();
        return "JugadorDesconocido";
    }

    public static void connectToServer() {
        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connectant...");

        pauseDuring(1000, () -> {
            String protocol = ctrlConfig.txtProtocol.getText().trim();
            String host = ctrlConfig.txtHost.getText().trim();
            String port = ctrlConfig.txtPort.getText().trim();

            if (protocol.isEmpty() || host.isEmpty() || port.isEmpty()) {
                System.out.println("Tots els camps són obligatoris");
                return;
            }

            String wsUrl = protocol + "://" + host + ":" + port;
            wsClient = UtilsWS.getSharedInstance(wsUrl);

            wsClient.onMessage(response -> Platform.runLater(() -> handleWebSocketMessage(response)));
            wsClient.onError(response -> Platform.runLater(() -> handleWebSocketMessage(response)));
        });
    }

    private static void handleWebSocketMessage(String response) {
        try {
            JSONObject msg = new JSONObject(response);
            String type = msg.getString("type");

            switch (type) {
                case "clients" -> {
                    ctrlOpponentSelection.handleMessage(msg);
                    ctrlWaitingRoom.handleMessage(msg);
                }
                case "invite", "invitationSent", "inviteAccepted" -> ctrlOpponentSelection.handleMessage(msg);
                case "gameStarted", "countdown", "opponentDisconnected" -> ctrlWaitingRoom.handleMessage(msg);
                case "serverData" -> ctrlGame.handleMessage(msg);
                case "gameResult" -> ctrlResult.handleMessage(msg);
            }
        } catch (Exception e) {
            System.err.println("Error procesando mensaje WS: " + response);
            e.printStackTrace();
        }
    }
}
