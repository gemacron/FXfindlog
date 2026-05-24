module com.gemacron.fxfindlog {
    requires javafx.controls;
    requires javafx.fxml;

    // Requiere el módulo de SQL (necesario para el DatabaseHelper y SQLite)
    requires java.sql;
    // Requiere la librería SSH JSch (Fork modernizado de mwiede)
    requires com.jcraft.jsch;
    
    // Permite que JavaFX FXML acceda de forma reflexiva a los controladores de la interfaz
    opens com.gemacron.fxfindlog to javafx.fxml;
    
    // Permite que las columnas de TableView lean las propiedades del modelo mediante reflexión
    opens com.gemacron.model to javafx.base;
    
    // Exporta el paquete principal para que el motor de JavaFX inicie la clase App
    exports com.gemacron.fxfindlog;
}
