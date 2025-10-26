package com.server;

public class ServerMain {
    public static void main(String[] args) {
        int port = 3000;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Puerto inválido, usando puerto por defecto: 3000");
            }
        }
        
        GameWebSocketServer server = new GameWebSocketServer(port);
        server.start();
        System.out.println("🎮 Servidor Conecta 4 iniciado en puerto: " + port);
        
        // Mantener el servidor corriendo
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("Servidor interrumpido");
        }
    }
}