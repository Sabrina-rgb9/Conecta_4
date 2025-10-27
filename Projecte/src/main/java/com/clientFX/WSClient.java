package com.clientFX;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;

public class WSClient extends WebSocketClient {
    private final CtrlGame controller;

    public WSClient(URI serverUri, CtrlGame controller) {
        super(serverUri);
        this.controller = controller;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Conectado al servidor");
    }

    @Override
    public void onMessage(String message) {
        controller.handleMessage(new org.json.JSONObject(message));
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Conexión cerrada: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void safeSend(String msg){
        if(isOpen()) send(msg);
    }
}
