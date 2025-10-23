package com.clientFX;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Main del cliente JavaFX.
 * - Carga todas las vistas (FXML) en UtilsViews.
 * - Mantiene referencias a los controladores.
 * - Proporciona connectToServer() (método público estático).
 * - Procesa mensajes entrantes del servidor y actualiza controladores/vistas.
 */
public class Main extends Application {

    public static UtilsWS wsClient;

    public static CtrlConfig ctrlConfig;
    public static CtrlOpponentSelection ctrlOpponentSelection;
    public static CtrlWaitingRoom ctrlWaitingRoom;
    public static CtrlCountdown ctrlCountdown;
    public static CtrlGame ctrlGame;
    public static CtrlResult ctrlResult;

    // Nombre del cliente asignado por el servidor (id)
    private static String myName = null;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        final int windowWidth = 950;
        final int windowHeight = 700;

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

        // Vincular UtilsWS de CtrlOpponentSelection (si lo usan directo)
        CtrlOpponentSelection.wsClient = wsClient;

        Scene scene = new Scene(UtilsViews.parentContainer, windowWidth, windowHeight);
        stage.setScene(scene);
        stage.setTitle("Connecta 4 - JavaFX");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Icono (opcional)
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            try {
                Image icon = new Image(getClass().getResourceAsStream("/assets/icon.png"));
                stage.getIcons().add(icon);
            } catch (Exception e) {
                // no pasa nada si no existe
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

    // ---------------------------
    // Helper UI utilities
    // ---------------------------
    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    private static void showError(String message) {
        Platform.runLater(() -> {
            if (ctrlConfig != null && ctrlConfig.txtMessage != null) {
                ctrlConfig.txtMessage.setTextFill(Color.RED);
                ctrlConfig.txtMessage.setText(message);
                pauseDuring(2000, () -> ctrlConfig.txtMessage.setText(""));
            }
        });
    }

    // ---------------------------
    // Conexión WebSocket (método público solicitado)
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

            // Registrar callbacks
            wsClient.onMessage(response -> {
                // Ejecutar en el hilo de JavaFX
                Platform.runLater(() -> handleWebSocketMessage(response));
            });

            wsClient.onError(err -> {
                Platform.runLater(() -> handleWebSocketError(err));
            });

            wsClient.onOpen(msg -> {
                // Opcional: mostrar mensaje
                Platform.runLater(() -> {
                    if (ctrlConfig != null && ctrlConfig.txtMessage != null) {
                        ctrlConfig.txtMessage.setTextFill(Color.GREEN);
                        ctrlConfig.txtMessage.setText("Conectat");
                        pauseDuring(1200, () -> ctrlConfig.txtMessage.setText(""));
                    }
                });
            });
        });
    }

    // ---------------------------
    // Procesamiento de mensajes del servidor
    // ---------------------------
    private static void handleWebSocketError(String response) {
        String errorMsg = "Error de connexió";
        if (response != null && response.contains("Connection refused")) {
            errorMsg = "No es pot connectar al servidor";
        }
        showError(errorMsg);
    }

    /**
     * Procesa mensajes JSON que vienen del servidor y actualiza controladores/vistas.
     */
    private static void handleWebSocketMessage(String response) {
        if (response == null || response.isEmpty()) return;

        JSONObject msg;
        try {
            msg = new JSONObject(response);
        } catch (Exception e) {
            System.err.println("JSON invalido: " + response);
            return;
        }

        String type = msg.optString("type", "");
        switch (type) {

            // Lista de clientes conectados (mensaje broadcast inicial y actualizaciones)
            case "clients": {
                // Estructura esperada: { type: "clients", id: "<myName>", list: [ ... ] }
                String id = msg.optString("id", null);
                myName = id != null && !id.isEmpty() ? id : myName;

                JSONArray arr = msg.optJSONArray("list");
                List<String> players = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) players.add(arr.optString(i));
                }

                final String finalMyName = myName;
                Platform.runLater(() -> {
                    // Actualizar label del controlador de selección
                    if (ctrlOpponentSelection != null) {
                        // eliminarme de la lista
                        List<String> filtered = new ArrayList<>();
                        for (String p : players) if (!p.equals(finalMyName)) filtered.add(p);
                        ctrlOpponentSelection.updatePlayersList(filtered.toArray(new String[0]));
                        if (finalMyName != null) ctrlOpponentSelection.lblPlayerName.setText(finalMyName);
                    }
                });
                break;
            }

            // Invitación privada (llegada al destinatario)
            case "invite": {
                // { type: "invite", origin: "<player>" }
                String origin = msg.optString("origin", "");
                if (origin.isEmpty()) break;

                Platform.runLater(() -> {
                    // Mostrar confirm dialog para aceptar
                    boolean accept = javafx.scene.control.Alert.AlertType.CONFIRMATION == null ? false
                            : Dialog.showConfirm("Invitació", "Invitació de " + origin + ". Acceptar?");
                    // above line uses legacy Dialogs; instead show an Alert
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Invitació");
                    alert.setHeaderText("Invitació de " + origin);
                    alert.setContentText("Vols acceptar la partida?");
                    if (alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK) {
                        // enviar acceptInvite
                        if (wsClient != null) {
                            JSONObject accept = new JSONObject();
                            accept.put("type", "acceptInvite");
                            accept.put("origin", origin);
                            wsClient.safeSend(accept.toString());
                        }
                    }
                });
                break;
            }

            // Confirmación que la invitación se envió
            case "invitationSent": {
                String dest = msg.optString("dest", "");
                Platform.runLater(() -> {
                    if (ctrlOpponentSelection != null) ctrlOpponentSelection.lblStatus.setText("Invitació enviada a " + dest);
                });
                break;
            }

            // El servidor dice que la partida se va a iniciar (preparando countdown / sala espera)
            case "gameStarted": {
                // { type: "gameStarted", opponent: "<name>" }
                String opponent = msg.optString("opponent", "");
                // Mostrar sala de espera y actualizar labels
                Platform.runLater(() -> {
                    UtilsViews.setView("ViewWaitingRoom");
                    if (ctrlWaitingRoom != null) ctrlWaitingRoom.setPlayers(myName != null ? myName : "---", opponent);
                });
                break;
            }

            // Countdown (en tick): { type: "countdown", seconds: n }
            case "countdown": {
                int seconds = msg.optInt("seconds", -1);
                if (seconds >= 0) {
                    Platform.runLater(() -> {
                        UtilsViews.setView("ViewCountdown");
                        if (ctrlCountdown != null) {
                            // Si tu CtrlCountdown tiene startCountdown(seconds) lo usamos:
                            try {
                                ctrlCountdown.startCountdown(seconds);
                            } catch (Throwable t) {
                                // fallback: si no existe startCountdown con segundos, intentar otra cosa
                                System.err.println("CtrlCountdown.startCountdown(seconds) no disponible: " + t.getMessage());
                            }
                        }
                    });
                }
                break;
            }

            // Estado completo del servidor para la partida (serverData)
            case "serverData": {
                // Estructura esperada (según GameSession.toServerData):
                // { type: "serverData", clientsList: [{name,role},...], game: { status, board, turn, lastMove, playerR, playerY, ... } }
                JSONObject game = msg.optJSONObject("game");
                if (game == null) break;

                // Determinar roles y nombres
                String playerR = game.optString("playerR", "");
                String playerY = game.optString("playerY", "");

                // Calcular mi rol
                String myRole = null;
                if (myName != null) {
                    if (myName.equals(playerR)) myRole = "R";
                    else if (myName.equals(playerY)) myRole = "Y";
                }

                // Determinar opponent name
                String opponent = null;
                if (playerR != null && playerY != null && myName != null) {
                    if (myName.equals(playerR)) opponent = playerY;
                    else if (myName.equals(playerY)) opponent = playerR;
                }

                // Pasar datos al controlador del juego
                final String fMyName = myName != null ? myName : "Jugador";
                final String fMyRole = myRole != null ? myRole : "";
                final String fOpponent = opponent != null ? opponent : "---";
                final String fOpponentRole = ("R".equals(fMyRole) ? "Y" : "R");

                Platform.runLater(() -> {
                    UtilsViews.setView("ViewGame");
                    try {
                        if (ctrlGame != null) {
                            ctrlGame.setPlayers(fMyName, fMyRole, fOpponent, fOpponentRole);
                            ctrlGame.updateServerData(msg); // le pasamos el objeto completo; el controlador lo procesa
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                break;
            }

            // Resultado final de la partida
            case "gameResult": {
                // { type: "gameResult", result: "win"/"lose"/"draw", winner: "<name>" }
                String result = msg.optString("result", "");
                String winner = msg.optString("winner", "");
                Platform.runLater(() -> {
                    UtilsViews.setView("ViewResult");
                    if (ctrlResult != null) {
                        String text;
                        switch (result) {
                            case "win": text = "Has guanyat!"; break;
                            case "lose": text = "Has perdut :("; break;
                            case "draw": text = "Empat!"; break;
                            default: text = "Partida finalitzada";
                        }
                        ctrlResult.setResult(myName != null ? myName : "Jugador", text);
                    }
                });
                break;
            }

            // Oponente desconectado
            case "opponentDisconnected": {
                String name = msg.optString("name", "");
                Platform.runLater(() -> {
                    UtilsViews.setView("ViewOpponentSelection");
                    if (ctrlWaitingRoom != null) {
                        ctrlWaitingRoom.setStatus("El contrincant " + name + " s'ha desconnectat.");
                        ctrlWaitingRoom.setPlayers(myName != null ? myName : "---", "(desconnectat)");
                    }
                });
                break;
            }

            // Mensajes que no se mapearon explícitamente: intentar pasar al controlador activo
            default: {
                // Intentamos reenviar al controlador activo si tiene un método handleMessage(JSONObject)
                String active = UtilsViews.getActiveView();
                Platform.runLater(() -> {
                    try {
                        switch (active) {
                            case "ViewOpponentSelection" -> {
                                // Si tiene updatePlayersList o similar, intentar
                                if ("clients".equals(type) && ctrlOpponentSelection != null) {
                                    JSONArray arr = msg.optJSONArray("list");
                                    if (arr != null) {
                                        List<String> players = new ArrayList<>();
                                        for (int i = 0; i < arr.length(); i++) players.add(arr.optString(i));
                                        ctrlOpponentSelection.updatePlayersList(players.toArray(new String[0]));
                                    }
                                }
                            }
                            case "ViewWaitingRoom" -> {
                                if (ctrlWaitingRoom != null) ctrlWaitingRoom.setStatus(msg.optString("status", ""));
                            }
                            case "ViewGame" -> {
                                if (ctrlGame != null && "clientMouseMoving".equals(type)) {
                                    // dejarlo al controlador si implementa la recepción
                                }
                            }
                        }
                    } catch (Exception ex) {
                        // ignore
                    }
                });
                break;
            }
        }
    }
}
