package com.server;

import org.java_websocket.WebSocket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre de clients connectats.
 * Manté la correspondència entre WebSocket i nom de jugador.
 */
public class ClientRegistry {

    // socket -> nom
    private final Map<WebSocket, String> socketToName = new ConcurrentHashMap<>();
    // nom -> socket
    private final Map<String, WebSocket> nameToSocket = new ConcurrentHashMap<>();
    // Llista inicial de noms disponibles (per assignació automàtica)
    private final Queue<String> availableNames;

    public ClientRegistry(List<String> initialNames) {
        this.availableNames = new LinkedList<>(initialNames);
    }

    /**
     * Afegeix un nou client i li assigna un nom disponible.
     * @return el nom assignat
     */
    public synchronized String add(WebSocket conn) {
        if (availableNames.isEmpty()) {
            // Si no queden noms, genera un genèric
            int id = socketToName.size() + 1;
            String genericName = "Jugador" + id;
            socketToName.put(conn, genericName);
            nameToSocket.put(genericName, conn);
            return genericName;
        } else {
            String name = availableNames.poll(); // agafa el primer nom disponible
            socketToName.put(conn, name);
            nameToSocket.put(name, conn);
            return name;
        }
    }

    /**
     * Elimina un client i torna el seu nom a la llista (opcional).
     * @return el nom del client eliminat, o null si no existia
     */
    public synchronized String remove(WebSocket conn) {
        String name = socketToName.remove(conn);
        if (name != null) {
            nameToSocket.remove(name);
            // Opcional: tornar a afegir el nom a la llista per reutilitzar-lo
            // availableNames.offer(name);
        }
        return name;
    }

    /** Netegen un socket desconnectat (mateix que remove, però sense tornar el nom) */
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