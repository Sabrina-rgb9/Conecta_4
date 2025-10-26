package com.clientFX;

import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class UtilsWS {

    private static UtilsWS sharedInstance = null;
    private WebSocketClient client;
    private final String location;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final AtomicBoolean exitRequested = new AtomicBoolean(false);

    private Consumer<String> onOpenCallBack;
    private Consumer<String> onMessageCallBack;
    private Consumer<String> onCloseCallBack;
    private Consumer<String> onErrorCallBack;

    private UtilsWS(String location) {
        this.location = location;
        createNewWebSocketClient();
    }

    private void createNewWebSocketClient() {
        try {
            this.client = new WebSocketClient(new URI(location), new Draft_6455()) {

                @Override
                public void onOpen(ServerHandshake handshake) {
                    String message = "WS connected to: " + getURI();
                    System.out.println(message);
                    runLaterIfSet(onOpenCallBack, message);
                }

                @Override
                public void onMessage(String message) {
                    runLaterIfSet(onMessageCallBack, message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    String message = "WS closed: " + reason + " (remote=" + remote + ")";
                    System.out.println(message);
                    runLaterIfSet(onCloseCallBack, message);

                    if (!exitRequested.get() && remote) {
                        scheduleReconnect();
                    }
                }

                @Override
                public void onError(Exception e) {
                    String message = "WS error: " + e.getMessage();
                    System.out.println(message);
                    runLaterIfSet(onErrorCallBack, message);

                    if (!exitRequested.get() &&
                        (message.contains("Connection refused") || message.contains("Connection reset"))) {
                        scheduleReconnect();
                    }
                }
            };

            this.client.connect();

        } catch (URISyntaxException e) {
            System.err.println("WS Error: invalid URI -> " + location);
        }
    }

    private void runLaterIfSet(Consumer<String> callback, String msg) {
        if (callback != null) {
            Platform.runLater(() -> callback.accept(msg));
        }
    }

    private void scheduleReconnect() {
        if (!exitRequested.get()) {
            System.out.println("WS scheduling reconnect in 5 seconds...");
            scheduler.schedule(this::reconnect, 5, TimeUnit.SECONDS);
        }
    }

    private void reconnect() {
        if (exitRequested.get()) return;

        System.out.println("WS reconnecting to: " + this.location);

        try {
            if (client != null && client.isOpen()) {
                client.closeBlocking();
            }
        } catch (Exception ignored) {}

        createNewWebSocketClient();
    }

    // ---- Public methods ----

    public static UtilsWS getSharedInstance(String location) {
        if (sharedInstance == null || !sharedInstance.location.equals(location)) {
            if (sharedInstance != null) {
                sharedInstance.forceExit();
            }
            sharedInstance = new UtilsWS(location);
        }
        return sharedInstance;
    }

    public void onOpen(Consumer<String> callback) { this.onOpenCallBack = callback; }

    public void onMessage(Consumer<String> callback) { this.onMessageCallBack = callback; }

    public void onClose(Consumer<String> callback) { this.onCloseCallBack = callback; }

    public void onError(Consumer<String> callback) { this.onErrorCallBack = callback; }

    public void safeSend(String text) {
        try {
            if (client != null && client.isOpen()) {
                client.send(text);
            } else {
                System.out.println("WS Warning: not connected, scheduling reconnect...");
                scheduleReconnect();
            }
        } catch (Exception e) {
            System.err.println("WS Error sending message: " + e.getMessage());
        }
    }

    public void forceExit() {
        System.out.println("WS Closing...");
        exitRequested.set(true);
        try {
            if (client != null && !client.isClosed()) {
                client.closeBlocking();
            }
        } catch (Exception e) {
            System.out.println("WS Interrupted while closing: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            scheduler.shutdownNow();
        }
    }

    public boolean isOpen() {
        return client != null && client.isOpen();
    }

    public static void clearSharedInstance() {
        if (sharedInstance != null) {
            sharedInstance.forceExit();
            sharedInstance = null;
            exitRequested.set(false);
        }
    }
}
