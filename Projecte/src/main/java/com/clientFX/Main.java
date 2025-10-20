package com.clientFX;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import org.json.JSONArray;
import org.json.JSONObject;

public class Main extends Application {

    public static UtilsWS wsClient;

    // Controladors de les vistes
    public static CtrlConfig ctrlConfig;
    public static CtrlOpponentSelection ctrlOpponentSelection;
    public static CtrlWaitingRoom ctrlWaitingRoom;
    public static CtrlCountdown ctrlCountdown;
    public static CtrlGame ctrlGame;
    public static CtrlResult ctrlResult;
    
    public static String playerName = "";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        final int windowWidth = 800;
        final int windowHeight = 600;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");

        // Carregar totes les vistes (ordre determina direcció d'animació)
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml");
        UtilsViews.addView(getClass(), "ViewOpponentSelection", "/assets/viewOpponentSelection.fxml");
        UtilsViews.addView(getClass(), "ViewWaitingRoom", "/assets/viewWaitingRoom.fxml");
        UtilsViews.addView(getClass(), "ViewCountdown", "/assets/viewCountdown.fxml");
        UtilsViews.addView(getClass(), "ViewGame", "/assets/viewGame.fxml");
        UtilsViews.addView(getClass(), "ViewResult", "/assets/viewResult.fxml");

        // Obtenir controladors
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

        // Icona (excepte macOS)
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            try {
                Image icon = new Image(getClass().getResourceAsStream("/assets/icon.png"));
                stage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("No s'ha trobat l'icona");
            }
        }
    }

    @Override
    public void stop() {
        if (wsClient != null) {
            wsClient.forceExit();
        }
        Platform.exit(); // Millor que System.exit() en JavaFX
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    public static void connectToServer() {
        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connectant...");

        pauseDuring(1000, () -> {
            String protocol = ctrlConfig.txtProtocol.getText().trim();
            String host = ctrlConfig.txtHost.getText().trim();
            String port = ctrlConfig.txtPort.getText().trim();

            if (protocol.isEmpty() || host.isEmpty() || port.isEmpty()) {
                showError("Tots els camps són obligatoris");
                return;
            }

            String wsUrl = protocol + "://" + host + ":" + port;
            wsClient = UtilsWS.getSharedInstance(wsUrl);

            wsClient.onMessage(response -> Platform.runLater(() -> handleWebSocketMessage(response)));
            wsClient.onError(response -> Platform.runLater(() -> handleWebSocketError(response)));
        });
    }

    private static void handleWebSocketMessage(String response) {
        try {
            JSONObject msg = new JSONObject(response);
            String type = msg.getString("type");

            switch (type) {
                case "clients" -> {
                    String myName = msg.getString("id");
                    Platform.runLater(() -> {
                        ctrlOpponentSelection.setPlayerName(myName);
                        ctrlWaitingRoom.setPlayerName(myName);
                        ctrlResult.setPlayerName(myName);
                    });
                    UtilsViews.setView("ViewOpponentSelection");
                    ctrlOpponentSelection.handleMessage(msg);
                }
                case "invitationAccepted", "gameStarted" -> {
                    UtilsViews.setView("ViewWaitingRoom");
                    ctrlWaitingRoom.handleMessage(msg);
                }
                case "countdown" -> {
                    UtilsViews.setView("ViewCountdown");
                    ctrlCountdown.handleMessage(msg);
                }
                case "serverData" -> {
                    String status = msg.getJSONObject("game").getString("status");
                    if ("playing".equals(status) || "win".equals(status) || "draw".equals(status)) {
                        UtilsViews.setView("ViewGame");
                        ctrlGame.handleMessage(msg);
                    }
                }
                case "gameResult" -> {
                    UtilsViews.setView("ViewResult");
                    ctrlResult.handleMessage(msg);
                }
                default -> {
                    String currentView = UtilsViews.getActiveView();
                    switch (currentView) {
                        case "ViewOpponentSelection" -> ctrlOpponentSelection.handleMessage(msg);
                        case "ViewWaitingRoom" -> ctrlWaitingRoom.handleMessage(msg);
                        case "ViewCountdown" -> ctrlCountdown.handleMessage(msg);
                        case "ViewGame" -> ctrlGame.handleMessage(msg);
                        case "ViewResult" -> ctrlResult.handleMessage(msg);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error processant: " + response);
            e.printStackTrace();
        }
    }

    private static void handleWebSocketError(String response) {
        String errorMsg = "Error de connexió";
        if (response != null && response.contains("Connection refused")) {
            errorMsg = "No es pot connectar al servidor";
        }
        showError(errorMsg);
    }

    private static void showError(String message) {
        Platform.runLater(() -> {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(message);
            pauseDuring(2000, () -> ctrlConfig.txtMessage.setText(""));
        });
    }
}