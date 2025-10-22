package com.server;

import org.java_websocket.WebSocket;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.*;

public class GameManager {

    private final ClientRegistry registry;
    private final Map<String,String> invitations = new ConcurrentHashMap<>();
    private final Map<String,GameSession> sessions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();

    public GameManager(ClientRegistry registry){
        this.registry=registry;
        ticker.scheduleAtFixedRate(this::tickAll,0,33, TimeUnit.MILLISECONDS);
    }

    public void shutdown(){ ticker.shutdownNow(); }

    public synchronized JSONObject invite(String origin,String dest){
        JSONObject resp = new JSONObject();
        if(origin.equals(dest)){ resp.put("ok",false).put("reason","Can't invite yourself"); return resp; }
        WebSocket destSocket = registry.socketByName(dest);
        if(destSocket==null){ resp.put("ok",false).put("reason","Destination not available"); return resp; }
        if(findSessionIdByPlayer(dest)!=null){ resp.put("ok",false).put("reason","Player already in game"); return resp; }

        invitations.put(origin,dest);

        JSONObject notify = new JSONObject();
        notify.put("type","invite");
        notify.put("origin",origin);
        try{ destSocket.send(notify.toString()); } catch(Exception e){ registry.cleanupDisconnected(destSocket); resp.put("ok",false).put("reason","Dest unreachable"); return resp; }

        resp.put("ok",true);
        return resp;
    }

    public synchronized JSONObject accept(String acceptor,String origin){
        JSONObject resp = new JSONObject();
        String invited = invitations.get(origin);
        if(invited==null || !invited.equals(acceptor)){ resp.put("ok",false).put("reason","No matching invitation"); return resp; }

        String id = sessionId(origin,acceptor);
        if(sessions.containsKey(id)){ resp.put("ok",false).put("reason","Session already exists"); return resp; }

        GameSession session = new GameSession(origin,acceptor);
        session.startCountdown(3);
        sessions.put(id,session);
        invitations.remove(origin);

        resp.put("ok",true).put("sessionId",id);
        return resp;
    }

    public JSONObject play(String playerName,int col){
        String id = findSessionIdByPlayer(playerName);
        if(id==null) return new JSONObject().put("ok",false).put("reason","No session");
        GameSession s = sessions.get(id);
        if(s==null) return new JSONObject().put("ok",false).put("reason","Session gone");

        JSONObject result = s.play(playerName,col);
        s.broadcastState(registry);

        return result;
    }

    public String findSessionIdByPlayer(String playerName){
        for(Map.Entry<String,GameSession> e:sessions.entrySet())
            if(e.getValue().involves(playerName)) return e.getKey();
        return null;
    }

    private String sessionId(String a,String b){ return a.compareTo(b)<=0 ? a+"|"+b : b+"|"+a; }

    private void tickAll(){
        try{
            List<String> removeKeys = new ArrayList<>();
            for(Map.Entry<String,GameSession> e:sessions.entrySet()){
                GameSession s = e.getValue();
                if(s.getStatus()== GameSession.Status.COUNTDOWN){
                    int left = s.tickCountdown();
                    JSONObject cd = new JSONObject();
                    cd.put("type","countdown");
                    cd.put("seconds",left);
                    sendToBoth(s,cd);

                    if(left<=0){
                        JSONObject start = new JSONObject();
                        start.put("type","gameStarted");
                        start.put("opponent",s.getPlayerY());
                        sendToBoth(s,start);
                    }
                } else if(s.getStatus()== GameSession.Status.PLAYING){
                    s.broadcastState(registry);
                } else if(s.getStatus()== GameSession.Status.WIN || s.getStatus()== GameSession.Status.DRAW){
                    sendGameResult(s);
                    s.finish();
                    removeKeys.add(e.getKey());
                }
            }
            removeKeys.forEach(sessions::remove);
        }catch(Exception ex){ ex.printStackTrace(); }
    }

    private void sendToBoth(GameSession s,JSONObject msg){
        WebSocket wr = registry.socketByName(s.getPlayerR());
        WebSocket wy = registry.socketByName(s.getPlayerY());
        if(wr!=null) try{ wr.send(msg.toString()); } catch(Exception e){ registry.cleanupDisconnected(wr); }
        if(wy!=null) try{ wy.send(msg.toString()); } catch(Exception e){ registry.cleanupDisconnected(wy); }
    }

    private void sendGameResult(GameSession s){
        String status = s.getStatus()== GameSession.Status.WIN?"win":"draw";
        String winner = s.getWinner();

        if("win".equals(status)){
            JSONObject winMsg = new JSONObject();
            winMsg.put("type","gameResult");
            winMsg.put("result","win");
            winMsg.put("winner",winner);
            WebSocket wsWinner = registry.socketByName(winner);
            if(wsWinner!=null) try{ wsWinner.send(winMsg.toString()); } catch(Exception e){ registry.cleanupDisconnected(wsWinner); }

            String loser = s.getPlayerR().equals(winner)?s.getPlayerY():s.getPlayerR();
            JSONObject loseMsg = new JSONObject();
            loseMsg.put("type","gameResult");
            loseMsg.put("result","lose");
            loseMsg.put("winner",winner);
            WebSocket wsLoser = registry.socketByName(loser);
            if(wsLoser!=null) try{ wsLoser.send(loseMsg.toString()); } catch(Exception e){ registry.cleanupDisconnected(wsLoser); }
        } else {
            JSONObject drawMsg = new JSONObject();
            drawMsg.put("type","gameResult");
            drawMsg.put("result","draw");
            sendToBoth(s,drawMsg);
        }
    }

    public void handleDisconnect(String name){
        invitations.entrySet().removeIf(e->e.getKey().equals(name)||e.getValue().equals(name));
        List<String> toRemove = new ArrayList<>();
        for(Map.Entry<String,GameSession> e:sessions.entrySet()){
            GameSession s = e.getValue();
            if(s.involves(name)){
                String other = s.getPlayerR().equals(name)?s.getPlayerY():s.getPlayerR();
                WebSocket wsOther = registry.socketByName(other);
                if(wsOther!=null){
                    JSONObject msg = new JSONObject();
                    msg.put("type","opponentDisconnected");
                    msg.put("name",name);
                    try{ wsOther.send(msg.toString()); } catch(Exception ex){ registry.cleanupDisconnected(wsOther); }
                }
                s.finish();
                toRemove.add(e.getKey());
            }
        }
        toRemove.forEach(sessions::remove);
    }

    public JSONObject debug(){
        JSONObject res = new JSONObject();
        res.put("invitations",invitations.keySet());
        res.put("sessions",sessions.keySet());
        return res;
    }

    public GameSession getSession(String sessionId){ return sessions.get(sessionId); }
}
