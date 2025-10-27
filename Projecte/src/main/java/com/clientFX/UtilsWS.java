package com.clientFX;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.util.function.Consumer;

public class UtilsWS extends WebSocketClient {

    private Consumer<String> onMessageCallback;
    private Consumer<String> onErrorCallback;

    private static UtilsWS sharedInstance;

    public UtilsWS(URI serverUri) {
        super(serverUri);
    }

    public static UtilsWS getSharedInstance(String url) {
        try {
            if (sharedInstance == null) {
                sharedInstance = new UtilsWS(new URI(url));
                sharedInstance.connectBlocking();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sharedInstance;
    }

    public void onMessage(Consumer<String> callback) { this.onMessageCallback = callback; }
    public void onError(Consumer<String> callback) { this.onErrorCallback = callback; }

    @Override
    public void onOpen(ServerHandshake handshake) {}

    @Override
    public void onMessage(String message) {
        if (onMessageCallback != null) onMessageCallback.accept(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {}

    @Override
    public void onError(Exception ex) {
        if (onErrorCallback != null) onErrorCallback.accept(ex.getMessage());
    }

    public void forceExit() {
        try { closeBlocking(); } catch (Exception e) {}
    }

    public static GameState convertToGameState(JSONObject msg) {
        // Aquí podrías hacer la conversión de JSON a GameState según tus clases compartidas
        // Por simplicidad devolvemos null, se implementa según la estructura de tu servidor
        return null;
    }
}
