package com.shared;

public class DragInfo {
    private boolean dragging;
    private double dragX;
    private double dragY;
    private String pieceColor;
    
    public DragInfo() {
        this.dragging = false;
        this.dragX = 0;
        this.dragY = 0;
        this.pieceColor = "";
    }
    
    // Getters
    public boolean isDragging() { return dragging; }
    public double getDragX() { return dragX; }
    public double getDragY() { return dragY; }
    public String getPieceColor() { return pieceColor; }
    
    // Setters
    public void setDragging(boolean dragging) { this.dragging = dragging; }
    public void setDragX(double dragX) { this.dragX = dragX; }
    public void setDragY(double dragY) { this.dragY = dragY; }
    public void setPieceColor(String pieceColor) { this.pieceColor = pieceColor; }
}