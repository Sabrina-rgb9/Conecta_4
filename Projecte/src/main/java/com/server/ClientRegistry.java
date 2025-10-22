package com.server;

import org.java_websocket.WebSocket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registre de clients connectats.
 * Manté la correspondència entre WebSocket i nom de jugador,
 * amb reutilització automàtica de noms quan es desconnecten.
 */
public class ClientRegistry {

    // socket -> nom
    private final Map<WebSocket, String> socketToName = new ConcurrentHashMap<>();
    // nom -> socket
    private final Map<String, WebSocket> nameToSocket = new ConcurrentHashMap<>();
    // Cua concurrent de noms disponibles per reutilitzar
    private final Queue<String> availableNames = new ConcurrentLinkedQueue<>();
    // Llista original de noms per reiniciar el pool si cal
    private final List<String> seedNames;

    public ClientRegistry(List<String> initialNames) {
        this.seedNames = new ArrayList<>(initialNames);
        this.availableNames.addAll(initialNames);
    }

    /**
     * Afegeix un nou client i li assigna un nom disponible.
     * Si no n'hi ha, reinicia el pool amb els noms inicials.
     * @return el nom assignat
     */
    public String add(WebSocket conn) {
        String name = availableNames.poll();
        if (name == null) {
            // Reinicia el pool amb els noms originals (thread-safe perquè és local)
            synchronized (this) {
                if (availableNames.isEmpty()) {
                    availableNames.addAll(seedNames);
                }
                name = availableNames.poll();
            }
        }
        socketToName.put(conn, name);
        nameToSocket.put(name, conn);
        return name;
    }

    /**
     * Elimina un client i torna el seu nom al pool per reutilitzar-lo.
     * @return el nom del client eliminat, o null si no existia
     */
    public String remove(WebSocket conn) {
        String name = socketToName.remove(conn);
        if (name != null) {
            nameToSocket.remove(name);
            availableNames.offer(name); // 🔁 ¡Reutilización activada!
        }
        return name;
    }

    /** Neteja un socket desconnectat */
    public void cleanupDisconnected(WebSocket conn) {
        remove(conn);
    }

    /** Obté el nom associat a un socket */
    public String nameBySocket(WebSocket conn) {
        return socketToName.get(conn);
    }

    /** Obté el socket associat a un nom */
    public WebSocket socketByName(String name) {
        return nameToSocket.get(name);
    }

    /** Retorna una llista actualitzada de noms de clients connectats */
    public List<String> currentNames() {
        return new ArrayList<>(nameToSocket.keySet());
    }

    /** Retorna una còpia de l'estat actual (socket -> nom) */
    public Map<WebSocket, String> snapshot() {
        return new HashMap<>(socketToName);
    }
}