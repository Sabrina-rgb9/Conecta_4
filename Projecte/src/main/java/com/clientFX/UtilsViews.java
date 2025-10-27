package com.clientFX;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;

import java.util.HashMap;
import java.util.Map;

public class UtilsViews {

    public static AnchorPane parentContainer = new AnchorPane();
    private static final Map<String, Object> controllers = new HashMap<>();
    private static final Map<String, Node> views = new HashMap<>();
    private static String activeView = "";

    public static void addView(Class<?> cls, String viewName, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(cls.getResource(fxmlPath));
            Node pane = loader.load();
            controllers.put(viewName, loader.getController());
            views.put(viewName, pane);
            // Poner el pane en el contenedor (oculto por defecto)
            pane.setVisible(false);
            parentContainer.getChildren().add(pane);
            AnchorPane.setTopAnchor(pane, 0.0);
            AnchorPane.setLeftAnchor(pane, 0.0);
            AnchorPane.setRightAnchor(pane, 0.0);
            AnchorPane.setBottomAnchor(pane, 0.0);
        } catch (Exception e) {
            System.err.println("Error cargando FXML " + fxmlPath);
            e.printStackTrace();
        }
    }

    public static Object getController(String viewName) { return controllers.get(viewName); }

    public static void setView(String viewName) {
        views.forEach((k, node) -> node.setVisible(false));
        Node v = views.get(viewName);
        if (v != null) {
            v.setVisible(true);
            v.toFront();
            activeView = viewName;
        }
    }

    public static String getActiveView() { return activeView; }
}
