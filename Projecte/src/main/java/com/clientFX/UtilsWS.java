package com.clientFX;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.function.Consumer;

public class UtilsWS extends WebSocketClient {

    private static UtilsWS sharedInstance = null;

    private Consumer<String> onMsg;
    private Consumer<String> onErr;
    private Runnable onOpen;

    public UtilsWS(URI serverUri) {
        super(serverUri);
    }

    public static synchronized UtilsWS getSharedInstance(String url) throws Exception {
        if (sharedInstance == null || sharedInstance.isClosed()) {
            sharedInstance = new UtilsWS(new URI(url));
            sharedInstance.connect(); // conexión asíncrona
        }
        return sharedInstance;
    }

    public void setOnMessage(Consumer<String> c) { this.onMsg = c; }
    public void setOnError(Consumer<String> c) { this.onErr = c; }
    public void setOnOpen(Runnable r) { this.onOpen = r; }
    public void setOnOpenInline(Runnable r) { this.onOpen = r; } // alias

    public void setOnOpenCallback(Runnable r) { this.onOpen = r; }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        if (onOpen != null) onOpen.run();
    }

    @Override
    public void onMessage(String message) {
        if (onMsg != null) onMsg.accept(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // Puedes notificar a la UI si quieres
        System.out.println("WS cerrado: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        if (onErr != null) onErr.accept(ex.getMessage());
    }

    public void forceExit() {
        try { if (!isClosed()) closeBlocking(); } catch (Exception ignored) {}
    }

    // helpers para set callbacks con nombres simples
    public void setOnMessageSimple(Consumer<String> c) { setOnMessage(c); }
    public void setOnErrorSimple(Consumer<String> c) { setOnError(c); }
    public void setOnOpenSimple(Runnable r) { setOnOpen(r); }

    // setters compatibilidad con Main (nombres usados allí)
    public void setOnMessage(java.util.function.Consumer<String> c, boolean dummy) { setOnMessage(c); }
    public void setOnOpen(Runnable r, boolean dummy) { setOnOpen(r); }

    // convenience
    public void setOnMessageForMain(Consumer<String> c) { setOnMessage(c); }
    public void setOnErrorForMain(Consumer<String> c) { setOnError(c); }
}
