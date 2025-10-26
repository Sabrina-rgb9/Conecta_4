package com.shared;

public class ClientInfo {
    private String name;
    private String color;
    private double mouseX;
    private double mouseY;
    private String role;
    
    public ClientInfo() {}
    
    // Getters y setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public double getMouseX() { return mouseX; }
    public void setMouseX(double mouseX) { this.mouseX = mouseX; }
    
    public double getMouseY() { return mouseY; }
    public void setMouseY(double mouseY) { this.mouseY = mouseY; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}