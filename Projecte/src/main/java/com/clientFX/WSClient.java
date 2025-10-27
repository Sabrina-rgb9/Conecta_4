package com.clientFX;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

public class WSClient extends WebSocketClient {

    private Consumer<JSONObject> onMessageCallback;

    public WSClient(String serverUri) throws URISyntaxException {
        super(new URI(serverUri));
    }

    public void setOnMessageCallback(Consumer<JSONObject> callback) {
        this.onMessageCallback = callback;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("✅ Conectado al servidor");
    }

    @Override
    public void onMessage(String message) {
        if (onMessageCallback != null) {
            onMessageCallback.accept(new JSONObject(message));
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("⚠️ Conexión cerrada: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("❌ Error WS: " + ex.getMessage());
    }

    public void sendJSON(JSONObject json) {
        if (isOpen()) send(json.toString());
    }
}
