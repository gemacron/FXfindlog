module com.gemacron.fxfindlog {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.gemacron.fxfindlog to javafx.fxml;
    exports com.gemacron.fxfindlog;
}
