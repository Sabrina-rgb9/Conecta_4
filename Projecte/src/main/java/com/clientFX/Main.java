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

    // Controladores de las vistas
    public static CtrlConfig ctrlConfig;
    public static CtrlOpponentSelection ctrlOpponentSelection;
    public static CtrlWaitingRoom ctrlWaitingRoom;
    public static CtrlCountdown ctrlCountdown;
    public static CtrlGame ctrlGame;
    public static CtrlResult ctrlResult;

    // Información del jugador y flags de control
    public static String playerName = "";
    public static boolean connectedByUser = false; // Usuario hizo click en Connect
    public static boolean readyToPlay = false;    // Usuario listo para entrar a la partida

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        final int windowWidth = 1200;
        final int windowHeight = 900;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");

        // Cargar todas las vistas
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

        // Icono (excepto macOS)
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
        if (wsClient != null) {
            wsClient.forceExit();
        }
        Platform.exit();
    }

    /**
     * Pausa con delay y ejecuta acción en el hilo de UI
     */
    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    /**
     * Conectar al servidor WebSocket
     */
    public static void connectToServer() {
        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connectant...");

        pauseDuring(500, () -> {
            String protocol = ctrlConfig.txtProtocol.getText().trim();
            String host = ctrlConfig.txtHost.getText().trim();
            String port = ctrlConfig.txtPort.getText().trim();

            if (protocol.isEmpty() || host.isEmpty() || port.isEmpty()) {
                ctrlConfig.showMessage("Tots els camps són obligatoris", Color.RED);
                return;
            }

            String wsUrl = protocol + "://" + host + ":" + port;
            wsClient = UtilsWS.getSharedInstance(wsUrl);

            wsClient.onMessage(response -> Platform.runLater(() -> handleWebSocketMessage(response)));
            wsClient.onError(response -> Platform.runLater(() -> handleWebSocketError(response)));
        });
    }

    /**
     * Manejo de mensajes recibidos del servidor
     */
    private static void handleWebSocketMessage(String response) {
        try {
            JSONObject msg = new JSONObject(response);
            String type = msg.getString("type");

            switch (type) {
                case "clients" -> {
                    JSONArray list = msg.getJSONArray("list");
                    // No sobrescribir playerName si ya fue ingresado
                    if (playerName.isEmpty() && list.length() > 0) {
                        playerName = list.getString(0);
                    }
                    // Solo mostrar selección si el usuario hizo click en Connect
                    if (connectedByUser) {
                        UtilsViews.setView("ViewOpponentSelection");
                        ctrlOpponentSelection.handleMessage(msg);
                    }
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

                    // Solo avanzar a la partida si el usuario hizo click en Connect y está en la partida
                    if (Main.connectedByUser && Main.readyToPlay && "playing".equals(status) && isPlayerInGame(msg)) {
                        UtilsViews.setView("ViewGame");
                        ctrlGame.handleMessage(msg);
                    } else {
                        // Si aún no está listo, solo actualizar la vista actual (por ejemplo WaitingRoom)
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


                case "gameResult" -> {
                    UtilsViews.setView("ViewResult");
                    ctrlResult.handleMessage(msg);
                }

                default -> {
                    // Pasar mensaje a la vista actual
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
            System.err.println("Error procesando mensaje: " + response);
            e.printStackTrace();
        }
    }

    /**
     * Comprueba si el jugador actual está en la partida
     * @param msg JSONObject recibido del servidor (tipo "serverData")
     * @return true si el jugador actual está en la lista de clients de la partida
     */
    private static boolean isPlayerInGame(JSONObject msg) {
        try {
         // Lista de clientes conectados y participando
            JSONArray clients = msg.getJSONArray("clientsList");

            for (int i = 0; i < clients.length(); i++) {
                JSONObject c = clients.getJSONObject(i);
                if (c.getString("name").equals(playerName)) {
                    return true; // El jugador actual está en la partida
                }
            }
        } catch (Exception e) {
            System.err.println("Error verificando si el jugador está en la partida: " + e.getMessage());
            e.printStackTrace();
        }

        return false; // No está en la partida
    }
    /**
     * Manejo de errores de conexión
     */
    private static void handleWebSocketError(String response) {
        String errorMsg = "Error de connexió";
        if (response != null && response.contains("Connection refused")) {
            errorMsg = "No es pot connectar al servidor";
        }
        if (ctrlConfig != null) {
            ctrlConfig.showMessage(errorMsg, Color.RED);
        }
    }
}
