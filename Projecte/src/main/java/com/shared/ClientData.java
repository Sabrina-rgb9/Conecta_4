package com.shared;

import org.json.JSONObject;

public class ClientData {
    public String name;
    public String role;  // Cambiado de color a role para coincidir con serverData
    public int mouseX;
    public int mouseY;

    public ClientData(String name) {
        this.name = name;
        this.role = "R";  // Valor por defecto
        this.mouseX = -1;
        this.mouseY = -1;
    }

    public ClientData(String name, String role, int mouseX, int mouseY) {
        this.name = name;
        this.role = role;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }

    // Convierte el objeto a JSON
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("role", role);
        obj.put("mouseX", mouseX);
        obj.put("mouseY", mouseY);
        return obj;
    }

    // Crea un ClientData a partir de JSON
    public static ClientData fromJSON(JSONObject obj) {
        String name = obj.optString("name", null);
        if (name == null) {
            throw new IllegalArgumentException("El JSON debe contener 'name'");
        }

        ClientData cd = new ClientData(name);
        cd.role = obj.optString("role", "R");
        cd.mouseX = obj.optInt("mouseX", -1);
        cd.mouseY = obj.optInt("mouseY", -1);
        return cd;
    }
}