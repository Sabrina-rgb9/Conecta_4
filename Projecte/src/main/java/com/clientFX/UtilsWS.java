package com.clientFX;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

public class UtilsWS {

    private static UtilsWS sharedInstance;
    private WebSocketClient client;

    private Consumer<String> onMessage;
    private Consumer<String> onError;
    private Consumer<String> onClose;
    private boolean connected = false;

    private UtilsWS(String serverUri) {
        try {
            client = new WebSocketClient(new URI(serverUri)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    System.out.println("✅ Conectado al servidor WebSocket: " + serverUri);
                    sendHello();
                }

                @Override
                public void onMessage(String message) {
                    if (onMessage != null) {
                        onMessage.accept(message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    System.out.println("⚠️ Conexión cerrada: " + reason);
                    if (onClose != null) {
                        onClose.accept(reason);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    connected = false;
                    System.err.println("❌ Error en WebSocket: " + ex.getMessage());
                    if (onError != null) {
                        onError.accept(ex.getMessage());
                    }
                }
            };
            client.connect();
        } catch (URISyntaxException e) {
            System.err.println("❌ URI del servidor inválida: " + serverUri);
            if (onError != null) onError.accept("URI inválida");
        }
    }

    // -------------------------------------------------------
    // Singleton: obtener una instancia compartida
    // -------------------------------------------------------
    public static UtilsWS getSharedInstance(String serverUri) {
        if (sharedInstance == null) {
            sharedInstance = new UtilsWS(serverUri);
        } else {
            if (!sharedInstance.isConnected()) {
                sharedInstance.reconnect(serverUri);
            }
        }
        return sharedInstance;
    }

    private void reconnect(String serverUri) {
        try {
            System.out.println("🔄 Reintentando conexión con el servidor...");
            if (client != null && client.isOpen()) {
                client.close();
            }
            client = new WebSocketClient(new URI(serverUri)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    System.out.println("✅ Reconectado al servidor WebSocket: " + serverUri);
                    sendHello();
                }

                @Override
                public void onMessage(String message) {
                    if (onMessage != null) onMessage.accept(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    System.out.println("⚠️ Conexión cerrada: " + reason);
                    if (onClose != null) onClose.accept(reason);
                }

                @Override
                public void onError(Exception ex) {
                    connected = false;
                    System.err.println("❌ Error en WebSocket (reconnect): " + ex.getMessage());
                    if (onError != null) onError.accept(ex.getMessage());
                }
            };
            client.connect();
        } catch (URISyntaxException e) {
            System.err.println("❌ Error al reconectar: URI inválida");
        }
    }

    // -------------------------------------------------------
    // Métodos públicos
    // -------------------------------------------------------

    public boolean isConnected() {
        return connected && client != null && client.isOpen();
    }

    public void sendMessage(String message) {
        if (client != null && client.isOpen()) {
            client.send(message);
            System.out.println("📤 Enviado → " + message);
        } else {
            System.err.println("⚠️ No se pudo enviar el mensaje: conexión no abierta");
        }
    }

    public void sendHello() {
        if (Main.playerName != null && !Main.playerName.isEmpty()) {
            String helloMsg = String.format("{\"type\": \"hello\", \"name\": \"%s\"}", Main.playerName);
            sendMessage(helloMsg);
        }
    }

    public void close() {
        if (client != null && client.isOpen()) {
            System.out.println("🔒 Cerrando conexión WebSocket...");
            client.close();
        }
    }

    public void forceExit() {
        try {
            close();
        } catch (Exception ignored) {
        }
    }

    // -------------------------------------------------------
    // Event Listeners
    // -------------------------------------------------------

    public void onMessage(Consumer<String> callback) {
        this.onMessage = callback;
    }

    public void onError(Consumer<String> callback) {
        this.onError = callback;
    }

    public void onClose(Consumer<String> callback) {
        this.onClose = callback;
    }
}
