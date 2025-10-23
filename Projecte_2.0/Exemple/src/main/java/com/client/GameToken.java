package com.client;

import com.shared.GameObject;
import javafx.scene.paint.Color;

public class GameToken {
    public String id;
    public Color color;
    public double x, y;        // Posición actual en canvas
    public int col, row;       // Posición lógica en tablero
    public boolean falling;    // Indica si la ficha está cayendo

    public GameToken(String id, Color color, int col, int row, double x, double y) {
        this.id = id;
        this.color = color;
        this.col = col;
        this.row = row;
        this.x = x;
        this.y = y;
        this.falling = false;
    }

    public GameObject toGameObject() {
        return new GameObject(id, (int)x, (int)y, 1,1);
    }
}
