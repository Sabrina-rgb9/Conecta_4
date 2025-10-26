package com.shared;

public class GameObject {
    private String id;
    private double x;
    private double y;
    private String role;
    
    public GameObject() {}
    
    // Getters y setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}