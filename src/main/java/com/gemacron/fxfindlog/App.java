package com.gemacron.fxfindlog;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.image.Image;

import java.io.IOException;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadFXML("mainView"), 640, 480);
        stage.setTitle("Buscador de Logs SSH");
        try {
            Image icon = new Image(App.class.getResourceAsStream("/com/gemacron/ico/icono_fx.png"));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Advertencia visual: No se pudo cargar el icono personalizado: " + e.getMessage());
        }     
        stage.setMaximized(true);
        stage.setScene(scene);
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }

}