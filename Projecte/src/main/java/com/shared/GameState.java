package com.shared;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private String type;
    private String clientName;
    private List<ClientInfo> clientsList = new ArrayList<>();
    private List<GameObject> objectsList = new ArrayList<>();
    private GameData game;
    
    public GameState() {}
    
    // Getters y setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    
    public List<ClientInfo> getClientsList() { return clientsList; }
    public void setClientsList(List<ClientInfo> clientsList) { this.clientsList = clientsList; }
    
    public List<GameObject> getObjectsList() { return objectsList; }
    public void setObjectsList(List<GameObject> objectsList) { this.objectsList = objectsList; }
    
    public GameData getGame() { return game; }
    public void setGame(GameData game) { this.game = game; }
}