package com.shared;

import org.json.JSONObject;

public class GameObject {
    public String id;
    public double x;
    public double y;
    public double radius;
    public int col;
    public int row;
    public String role;

    public GameObject(String id, double x, double y, double radius, int col, int row) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.col = col;
        this.row = row;
        this.role = null;
    }

    public GameObject(String id, double x, double y, double radius, int col, int row, String role) {
        this(id, x, y, radius, col, row);
        this.role = role;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }

    // Convierte el objeto a JSON
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("x", x);
        obj.put("y", y);
        obj.put("radius", radius);
        obj.put("col", col);
        obj.put("row", row);
        obj.put("role", role != null ? role : "R");
        return obj;
    }

    // Crea un GameObject a partir de JSON
    public static GameObject fromJSON(JSONObject obj) {
        String id = obj.optString("id", null);
        if (id == null) {
            throw new IllegalArgumentException("El JSON debe contener 'id'");
        }

        GameObject go = new GameObject(
                id,
                obj.optDouble("x", 0.0),
                obj.optDouble("y", 0.0),
                obj.optDouble("radius", 20.0),
                obj.optInt("col", -1),
                obj.optInt("row", -1));
        go.role = obj.optString("role", "R");
        return go;
    }
}