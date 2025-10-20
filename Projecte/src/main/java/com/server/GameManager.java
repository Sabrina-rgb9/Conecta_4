package com.server;

import org.java_websocket.WebSocket;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.*;

/**
 * Gestiona creació d'emparellaments, invitacions i sessions actives.
 * També manté un scheduler que fa ticks periòdics (p.ex. per enviar serverData a 30Hz).
 */
public class GameManager {

    private final ClientRegistry registry;
    // map d'invitacions pendents: inviter -> invitedName
    private final Map<String, String> invitations = new ConcurrentHashMap<>();
    // sessions actives per id (id simple "playerA|playerB" ordenat)
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();

    public GameManager(ClientRegistry registry) {
        this.registry = registry;
        // tick sessions ~30 times per second to broadcast state (si cal)
        ticker.scheduleAtFixedRate(this::tickAll, 0, 33, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        ticker.shutdownNow();
    }

    /** Crea una invitació des de origin cap a dest */
    public synchronized JSONObject invite(String origin, String dest) {
        JSONObject resp = new JSONObject();
        if (origin.equals(dest)) {
            resp.put("ok", false).put("reason", "Can't invite yourself");
            return resp;
        }
        WebSocket destSocket = registry.socketByName(dest);
        if (destSocket == null) {
            resp.put("ok", false).put("reason", "Destination not available");
            return resp;
        }
        // Comprovar que el destinatari no està en una partida
        if (findSessionIdByPlayer(dest) != null) {
            resp.put("ok", false).put("reason", "Player already in game");
            return resp;
        }
        // guardar invitació
        invitations.put(origin, dest);
        // enviar missatge al destinatari (private) per notificar
        JSONObject notify = new JSONObject();
        notify.put("type", "invite");
        notify.put("origin", origin);
        try {
            destSocket.send(notify.toString());
        } catch (Exception e) {
            registry.cleanupDisconnected(destSocket);
            resp.put("ok", false).put("reason", "Dest unreachable");
            return resp;
        }
        resp.put("ok", true);
        return resp;
    }

    /** Accepta invitació: crea una sessió i notifica a ambdós jugadors */
    public synchronized JSONObject accept(String acceptor, String origin) {
        JSONObject resp = new JSONObject();
        // comprovar que origin havia convidat a acceptor
        String invited = invitations.get(origin);
        if (invited == null || !invited.equals(acceptor)) {
            resp.put("ok", false).put("reason", "No matching invitation");
            return resp;
        }
        // crear id consistente
        String id = sessionId(origin, acceptor);
        if (sessions.containsKey(id)) {
            resp.put("ok", false).put("reason", "Session already exists");
            return resp;
        }

        // assignem colors: origin -> R, acceptor -> Y (arbitrari)
        GameSession session = new GameSession(origin, acceptor);
        session.startCountdown(3); // 3 segons de countdown
        sessions.put(id, session);

        // notificar ambdós que la sessió s'ha creat
        JSONObject startMsg = new JSONObject();
        startMsg.put("type", "gameStarted");
        startMsg.put("opponent", acceptor);
        WebSocket wr = registry.socketByName(origin);
        if (wr != null) {
            try { wr.send(startMsg.toString()); } catch (Exception e) { registry.cleanupDisconnected(wr); }
        }

        startMsg.put("opponent", origin);
        WebSocket wy = registry.socketByName(acceptor);
        if (wy != null) {
            try { wy.send(startMsg.toString()); } catch (Exception e) { registry.cleanupDisconnected(wy); }
        }

        // eliminar invitació
        invitations.remove(origin);

        resp.put("ok", true).put("sessionId", id);
        return resp;
    }

    /** Processa una jugada clientPlay */
    public JSONObject play(String playerName, int col) {
        String id = findSessionIdByPlayer(playerName);
        if (id == null) return new JSONObject().put("ok", false).put("reason", "No session");
        GameSession s = sessions.get(id);
        if (s == null) return new JSONObject().put("ok", false).put("reason", "Session gone");

        JSONObject result = s.play(playerName, col);

        // després de la jugada, broadcast de l'estat actual
        s.broadcastState(registry);

        return result;
    }

    /** Retorna la sessionId en la que participa player, o null */
    public String findSessionIdByPlayer(String playerName) {
        for (Map.Entry<String, GameSession> e : sessions.entrySet()) {
            if (e.getValue().involves(playerName)) return e.getKey();
        }
        return null;
    }

    private String sessionId(String a, String b) {
        // id determinista (ordre alfabetic) per evitar duplicates
        if (a.compareTo(b) <= 0) return a + "|" + b;
        return b + "|" + a;
    }

    /** Tick periòdic per a cada sessió: countdown ticks i broadcast */
    private void tickAll() {
        try {
            for (Map.Entry<String, GameSession> e : sessions.entrySet()) {
                GameSession s = e.getValue();
                if (s.getStatus() == GameSession.Status.COUNTDOWN) {
                    int left = s.tickCountdown();
                    // Enviar countdown a ambdós
                    JSONObject cd = new JSONObject();
                    cd.put("type", "countdown");
                    cd.put("seconds", left);
                    sendToBoth(s, cd);

                    if (left <= 0) {
                        // Notificar que la partida comença
                        JSONObject startMsg = new JSONObject();
                        startMsg.put("type", "gameStarted");
                        startMsg.put("opponent", s.getPlayerR().equals(getOtherPlayer(s, s.getPlayerR())) ? s.getPlayerY() : s.getPlayerR());
                        sendToBoth(s, startMsg);
                    }
                } else if (s.getStatus() == GameSession.Status.PLAYING) {
                    s.broadcastState(registry);
                } else if (s.getStatus() == GameSession.Status.WIN || s.getStatus() == GameSession.Status.DRAW) {
                    // Enviar resultat final
                    sendGameResult(s);
                    s.finish();
                    // Eliminar la sessió després d'enviar el resultat
                    sessions.remove(e.getKey());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void sendToBoth(GameSession s, JSONObject msg) {
        WebSocket wr = registry.socketByName(s.getPlayerR());
        WebSocket wy = registry.socketByName(s.getPlayerY());
        if (wr != null) {
            try { wr.send(msg.toString()); } catch (Exception ex) { registry.cleanupDisconnected(wr); }
        }
        if (wy != null) {
            try { wy.send(msg.toString()); } catch (Exception ex) { registry.cleanupDisconnected(wy); }
        }
    }

    private String getOtherPlayer(GameSession s, String player) {
        return s.getPlayerR().equals(player) ? s.getPlayerY() : s.getPlayerR();
    }

    private void sendGameResult(GameSession s) {
        String status = s.getStatus() == GameSession.Status.WIN ? "win" : "draw";
        String winner = s.getWinner();

        // Missatge per al guanyador
        JSONObject winMsg = new JSONObject();
        winMsg.put("type", "gameResult");
        winMsg.put("result", "win");
        winMsg.put("winner", winner);
        WebSocket winnerSocket = registry.socketByName(winner);
        if (winnerSocket != null) {
            try { winnerSocket.send(winMsg.toString()); } catch (Exception e) { registry.cleanupDisconnected(winnerSocket); }
        }

        // Missatge per al perdedor (si hi ha)
        if (s.getStatus() == GameSession.Status.WIN) {
            String loser = getOtherPlayer(s, winner);
            JSONObject loseMsg = new JSONObject();
            loseMsg.put("type", "gameResult");
            loseMsg.put("result", "lose");
            loseMsg.put("winner", winner);
            WebSocket loserSocket = registry.socketByName(loser);
            if (loserSocket != null) {
                try { loserSocket.send(loseMsg.toString()); } catch (Exception e) { registry.cleanupDisconnected(loserSocket); }
            }
        } else {
            // Empat: tots reben "draw"
            JSONObject drawMsg = new JSONObject();
            drawMsg.put("type", "gameResult");
            drawMsg.put("result", "draw");
            sendToBoth(s, drawMsg);
        }
    }

    /** Quan un client es desconnecta, caldrà fer cleanup de sessions o invitacions relacionades */
    public void handleDisconnect(String name) {
        // treure invitacions originades per o dirigides a name
        invitations.entrySet().removeIf(e -> e.getKey().equals(name) || e.getValue().equals(name));
        // sessions que impliquin aquest nom: marquem finish i notifiquem l'altre
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, GameSession> e : sessions.entrySet()) {
            GameSession s = e.getValue();
            if (s.involves(name)) {
                // notificar l'altre amb un tipus 'opponentDisconnected'
                String other = s.getPlayerR().equals(name) ? s.getPlayerY() : s.getPlayerR();
                WebSocket otherWs = registry.socketByName(other);
                if (otherWs != null) {
                    JSONObject msg = new JSONObject();
                    msg.put("type", "opponentDisconnected");
                    msg.put("name", name);
                    try { otherWs.send(msg.toString()); } catch (Exception ex) { registry.cleanupDisconnected(otherWs); }
                }
                s.finish();
                toRemove.add(e.getKey());
            }
        }
        // eliminar les sessions acabades immediatament
        for (String id : toRemove) sessions.remove(id);
    }

    // Debug util
    public JSONObject debug() {
        JSONObject res = new JSONObject();
        res.put("invitations", invitations.keySet());
        res.put("sessions", sessions.keySet());
        return res;
    }

    // Afegeix a GameManager.java
    public GameSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

}