package com.clientFX;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.AnchorPane;

import java.util.HashMap;
import java.util.Map;

public class UtilsViews {

    public static AnchorPane parentContainer = new AnchorPane();
    private static Map<String, Object> controllers = new HashMap<>();

    public static void addView(Class<?> cls, String viewName, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(cls.getResource(fxmlPath));
            AnchorPane pane = loader.load();
            controllers.put(viewName, loader.getController());
            parentContainer.getChildren().add(pane);
            pane.setVisible(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Object getController(String viewName) {
        return controllers.get(viewName);
    }

    public static void setView(String viewName) {
        parentContainer.getChildren().forEach(n -> n.setVisible(false));
        parentContainer.getChildren().stream()
                .filter(n -> n.isVisible() == false)
                .findFirst()
                .ifPresent(n -> n.setVisible(true));
    }

    public static String getActiveView() {
        for (String key : controllers.keySet()) {
            Object ctrl = controllers.get(key);
            // Asumimos que cada controller tiene un método getView
            if (ctrl != null) return key;
        }
        return "";
    }
}
