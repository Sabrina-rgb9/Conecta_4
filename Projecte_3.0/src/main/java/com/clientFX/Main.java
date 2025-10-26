package com.clientFX;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import org.json.JSONObject;
import org.json.JSONArray;
import com.shared.GameState;
import com.shared.ClientInfo;
import com.shared.GameObject;
import com.shared.GameData;
import com.shared.Move;

public class Main extends Application {

    public static String playerName = "";
    public static boolean connectedByUser = false;
    public static boolean readyToPlay = false;
    public static UtilsWS wsClient = null;
    public static GameState currentGameState = null;
    public static String myRole = "";

    @Override
    public void start(Stage stage) throws Exception {
        
        // Cargar todas las vistas con rutas corregidas
        try {
            UtilsViews.addView(getClass(), "ViewConfig", "assets/viewConfig.fxml");
            UtilsViews.addView(getClass(), "ViewOpponentSelection", "assets/viewOpponentSelection.fxml");
            UtilsViews.addView(getClass(), "ViewWaitingRoom", "assets/viewWaitingRoom.fxml");
            UtilsViews.addView(getClass(), "ViewCountdown", "assets/viewCountdown.fxml");
            UtilsViews.addView(getClass(), "ViewGame", "assets/viewGame.fxml");
            UtilsViews.addView(getClass(), "ViewResult", "assets/viewResult.fxml");
        } catch (Exception e) {
            System.err.println("Error cargando vistas: " + e.getMessage());
            e.printStackTrace();
            throw e; // Relanzar la excepción para ver el error completo
        }
        
        // Configurar escena principal
        Scene scene = new Scene(UtilsViews.parentContainer);
        
        // Cargar CSS de forma segura
        // try {
        //     URL cssUrl = getClass().getResource("/styles.css");
        //     if (cssUrl != null) {
        //         scene.getStylesheets().add(cssUrl.toExternalForm());
        //     } else {
        //         System.out.println("styles.css no encontrado, continuando sin CSS");
        //     }
        // } catch (Exception e) {
        //     System.out.println("Error cargando CSS: " + e.getMessage());
        // }
        
        stage.setScene(scene);
        stage.setTitle("Conecta 4");
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.show();
        
        // Ir a vista de configuración inicial
        UtilsViews.setView("ViewConfig");
    }

    public static void connectToServer() {
        try {
            String protocol = ((CtrlConfig)UtilsViews.getController("ViewConfig")).txtProtocol.getText();
            String host = ((CtrlConfig)UtilsViews.getController("ViewConfig")).txtHost.getText();
            String port = ((CtrlConfig)UtilsViews.getController("ViewConfig")).txtPort.getText();
            
            String wsUrl = protocol + "://" + host + ":" + port;
            System.out.println("Connecting to: " + wsUrl);
            
            wsClient = UtilsWS.getSharedInstance(wsUrl);
            
            wsClient.onOpen((message) -> {
                System.out.println("Connected to server");
                // Enviar mensaje de conexión con nombre de jugador
                JSONObject connectMsg = new JSONObject();
                connectMsg.put("type", "clientConnect");
                connectMsg.put("playerName", playerName);
                wsClient.safeSend(connectMsg.toString());
                UtilsViews.setViewAnimating("ViewOpponentSelection");
            });
            
            wsClient.onMessage((message) -> {
                System.out.println("Received: " + message);
                handleServerMessage(message);
            });
            
            wsClient.onClose((message) -> {
                System.out.println("Disconnected: " + message);
                // Volver a configuración si fue desconexión inesperada
                if (connectedByUser) {
                    Platform.runLater(() -> {
                        ((CtrlConfig)UtilsViews.getController("ViewConfig"))
                            .showMessage("Desconectado del servidor", javafx.scene.paint.Color.RED);
                        UtilsViews.setView("ViewConfig");
                    });
                }
            });
            
            wsClient.onError((message) -> {
                System.err.println("WebSocket error: " + message);
                Platform.runLater(() -> {
                    ((CtrlConfig)UtilsViews.getController("ViewConfig"))
                        .showMessage("Error de conexión: " + message, javafx.scene.paint.Color.RED);
                });
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                ((CtrlConfig)UtilsViews.getController("ViewConfig"))
                    .showMessage("Error de conexión", javafx.scene.paint.Color.RED);
            });
        }
    }
    
    private static void handleServerMessage(String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);
            
            if (jsonMessage.has("type")) {
                String type = jsonMessage.getString("type");
                switch (type) {
                    case "serverData":
                        handleServerData(jsonMessage);
                        break;
                    case "countdown":
                        handleCountdown(jsonMessage);
                        break;
                    case "gameStart":
                        handleGameStart();
                        break;
                    case "invitation":
                        handleInvitation(jsonMessage);
                        break;
                    case "error":
                        handleErrorMessage(jsonMessage);
                        break;
                    default:
                        System.out.println("Unknown message type: " + type);
                }
            } else {
                System.err.println("Message without type: " + message);
            }
        } catch (Exception e) {
            System.err.println("Error parsing message: " + e.getMessage());
            System.err.println("Raw message: " + message);
        }
    }
    
    private static void handleServerData(JSONObject serverData) {
        try {
            GameState gameState = parseGameState(serverData);
            currentGameState = gameState;
            updateGameState(gameState);
        } catch (Exception e) {
            System.err.println("Error parsing serverData: " + e.getMessage());
        }
    }
    
    private static GameState parseGameState(JSONObject json) {
        GameState gameState = new GameState();
        
        if (json.has("type")) gameState.setType(json.getString("type"));
        if (json.has("clientName")) gameState.setClientName(json.getString("clientName"));
        
        // Parse clientsList
        if (json.has("clientsList")) {
            JSONArray clientsArray = json.getJSONArray("clientsList");
            for (int i = 0; i < clientsArray.length(); i++) {
                JSONObject clientJson = clientsArray.getJSONObject(i);
                ClientInfo client = new ClientInfo();
                if (clientJson.has("name")) client.setName(clientJson.getString("name"));
                if (clientJson.has("color")) client.setColor(clientJson.getString("color"));
                if (clientJson.has("mouseX")) client.setMouseX(clientJson.getDouble("mouseX"));
                if (clientJson.has("mouseY")) client.setMouseY(clientJson.getDouble("mouseY"));
                if (clientJson.has("role")) client.setRole(clientJson.getString("role"));
                gameState.getClientsList().add(client);
            }
        }
        
        // Parse objectsList
        if (json.has("objectsList")) {
            JSONArray objectsArray = json.getJSONArray("objectsList");
            for (int i = 0; i < objectsArray.length(); i++) {
                JSONObject objectJson = objectsArray.getJSONObject(i);
                GameObject object = new GameObject();
                if (objectJson.has("id")) object.setId(objectJson.getString("id"));
                if (objectJson.has("x")) object.setX(objectJson.getDouble("x"));
                if (objectJson.has("y")) object.setY(objectJson.getDouble("y"));
                if (objectJson.has("role")) object.setRole(objectJson.getString("role"));
                gameState.getObjectsList().add(object);
            }
        }
        
        // Parse game data
        if (json.has("game")) {
            JSONObject gameJson = json.getJSONObject("game");
            GameData gameData = new GameData();
            
            if (gameJson.has("status")) gameData.setStatus(gameJson.getString("status"));
            if (gameJson.has("turn")) gameData.setTurn(gameJson.getString("turn"));
            if (gameJson.has("winner")) gameData.setWinner(gameJson.getString("winner"));
            
            // Parse board
            if (gameJson.has("board")) {
                JSONArray boardArray = gameJson.getJSONArray("board");
                String[][] board = new String[boardArray.length()][];
                for (int i = 0; i < boardArray.length(); i++) {
                    JSONArray rowArray = boardArray.getJSONArray(i);
                    board[i] = new String[rowArray.length()];
                    for (int j = 0; j < rowArray.length(); j++) {
                        board[i][j] = rowArray.getString(j);
                    }
                }
                gameData.setBoard(board);
            }
            
            // Parse lastMove
            if (gameJson.has("lastMove")) {
                JSONObject moveJson = gameJson.getJSONObject("lastMove");
                Move move = new Move();
                if (moveJson.has("col")) move.setCol(moveJson.getInt("col"));
                if (moveJson.has("row")) move.setRow(moveJson.getInt("row"));
                gameData.setLastMove(move);
            }
            
            gameState.setGame(gameData);
        }
        
        return gameState;
    }
    
    private static void updateGameState(GameState gameState) {
        // Determinar mi rol
        if ((myRole == null || myRole.isEmpty()) && gameState.getClientsList() != null) {
            for (ClientInfo client : gameState.getClientsList()) {
                if (client.getName().equals(playerName)) {
                    myRole = client.getRole();
                    System.out.println("Mi rol asignado: " + myRole);
                    break;
                }
            }
        }
        
        // Actualizar vista según estado del juego
        Platform.runLater(() -> {
            if (gameState.getGame() != null) {
                String status = gameState.getGame().getStatus();
                System.out.println("Estado del juego: " + status);
                
                switch (status) {
                    case "waiting":
                        UtilsViews.setView("ViewWaitingRoom");
                        updateWaitingRoom(gameState);
                        break;
                    case "countdown":
                        UtilsViews.setView("ViewCountdown");
                        startCountdown();
                        break;
                    case "playing":
                        UtilsViews.setView("ViewGame");
                        updateGameView();
                        break;
                    case "win":
                    case "draw":
                        UtilsViews.setView("ViewResult");
                        updateResultView();
                        break;
                    default:
                        // Si no hay partida o estado desconocido, mostrar selección de oponente
                        UtilsViews.setView("ViewOpponentSelection");
                        updateOpponentSelection(gameState);
                }
            } else {
                // Si no hay datos de juego, mostrar selección de oponente
                UtilsViews.setView("ViewOpponentSelection");
                updateOpponentSelection(gameState);
            }
        });
    }
    
    private static void handleInvitation(JSONObject invitation) {
        String fromPlayer = invitation.getString("from");
        String type = invitation.has("invitationType") ? 
                     invitation.getString("invitationType") : "received";
        
        Platform.runLater(() -> {
            if ("received".equals(type)) {
                CtrlOpponentSelection selectionCtrl = (CtrlOpponentSelection) 
                    UtilsViews.getController("ViewOpponentSelection");
                if (selectionCtrl != null) {
                    selectionCtrl.handleIncomingInvitation(fromPlayer);
                }
            } else if ("accepted".equals(type)) {
                // El oponente aceptó nuestra invitación
                UtilsViews.setViewAnimating("ViewWaitingRoom");
                CtrlWaitingRoom waitingCtrl = (CtrlWaitingRoom) 
                    UtilsViews.getController("ViewWaitingRoom");
                if (waitingCtrl != null) {
                    waitingCtrl.updateStatus("Invitación aceptada. Iniciando partida...");
                }
            } else if ("rejected".equals(type)) {
                // El oponente rechazó nuestra invitación
                CtrlOpponentSelection selectionCtrl = (CtrlOpponentSelection) 
                    UtilsViews.getController("ViewOpponentSelection");
                if (selectionCtrl != null) {
                    selectionCtrl.updateStatus("Invitación rechazada por " + fromPlayer);
                }
            }
        });
    }
    
    private static void handleCountdown(JSONObject countdownMsg) {
        int count = countdownMsg.has("count") ? countdownMsg.getInt("count") : 3;
        
        Platform.runLater(() -> {
            UtilsViews.setView("ViewCountdown");
            CtrlCountdown countdownCtrl = (CtrlCountdown) 
                UtilsViews.getController("ViewCountdown");
            if (countdownCtrl != null) {
                countdownCtrl.startCountdown(count);
            }
        });
    }
    
    private static void handleGameStart() {
        Platform.runLater(() -> {
            UtilsViews.setViewAnimating("ViewGame");
            // El estado del juego se actualizará con el primer serverData
        });
    }
    
    private static void handleErrorMessage(JSONObject errorMsg) {
        String error = errorMsg.has("message") ? errorMsg.getString("message") : "Error desconocido";
        
        Platform.runLater(() -> {
            // Mostrar error en la vista actual
            String currentView = UtilsViews.getActiveView();
            switch (currentView) {
                case "ViewConfig":
                    ((CtrlConfig)UtilsViews.getController("ViewConfig"))
                        .showMessage(error, javafx.scene.paint.Color.RED);
                    break;
                case "ViewOpponentSelection":
                    ((CtrlOpponentSelection)UtilsViews.getController("ViewOpponentSelection"))
                        .updateStatus("Error: " + error);
                    break;
                case "ViewGame":
                    // Podrías mostrar un alert en el juego
                    System.err.println("Error durante el juego: " + error);
                    break;
            }
        });
    }
    
    private static void updateOpponentSelection(GameState gameState) {
        CtrlOpponentSelection selectionCtrl = (CtrlOpponentSelection) 
            UtilsViews.getController("ViewOpponentSelection");
        if (selectionCtrl != null) {
            selectionCtrl.updatePlayersList(gameState);
        }
    }
    
    private static void updateWaitingRoom(GameState gameState) {
        CtrlWaitingRoom waitingCtrl = (CtrlWaitingRoom) 
            UtilsViews.getController("ViewWaitingRoom");
        if (waitingCtrl != null) {
            if (gameState != null && gameState.getGame() != null) {
                String status = gameState.getGame().getStatus();
                if ("waiting".equals(status)) {
                    waitingCtrl.updateStatus("Esperando que el oponente acepte la invitación...");
                } else if ("countdown".equals(status)) {
                    waitingCtrl.updateStatus("Partida encontrada. Iniciando...");
                }
            } else {
                waitingCtrl.updateStatus("Conectando con el oponente...");
            }
        }
    }
    
    private static void startCountdown() {
        CtrlCountdown countdownCtrl = (CtrlCountdown) 
            UtilsViews.getController("ViewCountdown");
        if (countdownCtrl != null) {
            countdownCtrl.startCountdown(3); // Countdown de 3 segundos por defecto
        }
    }
    
    private static void updateGameView() {
        CtrlGame gameCtrl = (CtrlGame) UtilsViews.getController("ViewGame");
        if (gameCtrl != null) {
            gameCtrl.updateGameState(currentGameState);
        }
    }
    
    private static void updateResultView() {
        CtrlResult resultCtrl = (CtrlResult) UtilsViews.getController("ViewResult");
        if (resultCtrl != null) {
            resultCtrl.updateResult(currentGameState);
        }
    }
    
    // Métodos para enviar mensajes al servidor
    public static void sendMouseMove(double x, double y) {
        if (wsClient != null && wsClient.isOpen()) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "clientMouseMoving");
                msg.put("x", x);
                msg.put("y", y);
                wsClient.safeSend(msg.toString());
            } catch (Exception e) {
                System.err.println("Error sending mouse move: " + e.getMessage());
            }
        }
    }
    
    public static void sendPlay(int column) {
        if (wsClient != null && wsClient.isOpen()) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "clientPlay");
                msg.put("column", column);
                wsClient.safeSend(msg.toString());
            } catch (Exception e) {
                System.err.println("Error sending play: " + e.getMessage());
            }
        }
    }
    
    public static void sendInvitation(String opponentName) {
        if (wsClient != null && wsClient.isOpen()) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "clientInvite");
                msg.put("opponent", opponentName);
                wsClient.safeSend(msg.toString());
            } catch (Exception e) {
                System.err.println("Error sending invitation: " + e.getMessage());
            }
        }
    }
    
    public static void acceptInvitation(String fromPlayer) {
        if (wsClient != null && wsClient.isOpen()) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "clientAcceptInvite");
                msg.put("from", fromPlayer);
                wsClient.safeSend(msg.toString());
            } catch (Exception e) {
                System.err.println("Error accepting invitation: " + e.getMessage());
            }
        }
    }
    
    public static void rejectInvitation(String fromPlayer) {
        if (wsClient != null && wsClient.isOpen()) {
            try {
                JSONObject msg = new JSONObject();
                msg.put("type", "clientRejectInvite");
                msg.put("from", fromPlayer);
                wsClient.safeSend(msg.toString());
            } catch (Exception e) {
                System.err.println("Error rejecting invitation: " + e.getMessage());
            }
        }
    }
    
    public static void pauseDuring(int millis, Runnable callback) {
        new Thread(() -> {
            try {
                Thread.sleep(millis);
                Platform.runLater(callback);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    public void stop() {
        if (wsClient != null) {
            wsClient.forceExit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}