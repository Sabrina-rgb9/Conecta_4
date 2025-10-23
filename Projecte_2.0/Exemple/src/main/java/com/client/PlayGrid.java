package com.client;

public class PlayGrid {

    private final double startX;
    private final double startY;
    private final double cellSize;
    private final int cols;
    private final int rows;

    public PlayGrid(double startX, double startY, double cellSize) {
        this.startX = startX;
        this.startY = startY;
        this.cellSize = cellSize;
        this.cols = 7; // 7 columnas
        this.rows = 6; // 6 filas
    }

    public boolean isPositionInsideGrid(double x, double y) {
        return x >= startX && x < startX + cols * cellSize &&
               y >= startY && y < startY + rows * cellSize;
    }

    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getCellSize() { return cellSize; }
    public int getCols() { return cols; }
    public int getRows() { return rows; }

    public int getCol(double x) {
        if (x < startX || x >= startX + cols * cellSize) return -1;
        return (int) ((x - startX) / cellSize);
    }

    public int getRow(double y) {
        if (y < startY || y >= startY + rows * cellSize) return -1;
        return (int) ((y - startY) / cellSize);
    }

    public int getCellX(int col) {
        return (int) (startX + col * cellSize);
    }

    public int getCellY(int row) {
        return (int) (startY + row * cellSize);
    }
}
