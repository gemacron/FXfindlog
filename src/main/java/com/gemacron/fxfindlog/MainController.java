package com.gemacron.fxfindlog;

import com.gemacron.model.Servidor;
import com.gemacron.service.LogSearchService;
import com.gemacron.util.DatabaseHelper;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controlador principal de la UI del Buscador de Logs SSH.
 * Gestiona de forma segura los hilos de fondo distribuidos y la persistencia SQLite.
 */
public class MainController implements Initializable {

    // Controles de Configuración Global SSH
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtPassword;
    @FXML private TextField txtRutaLog;

    // Controles de la Tabla de Servidores (SQLite)
    @FXML private TableView<Servidor> tableServidores;
    // Nueva columna inyectada para el CheckBox
    @FXML private TableColumn<Servidor, Boolean> colSeleccionar; 
    @FXML private TableColumn<Servidor, String> colAlias;
    @FXML private TableColumn<Servidor, String> colIp;
    @FXML private TextField txtNuevoAlias;
    @FXML private TextField txtNuevaIp;
    @FXML private Button btnAgregar;
    @FXML private Button btnEliminar;

    // Controles de Consulta y Consola
    @FXML private TextField txtPatron;
    @FXML private Button btnBuscar;
    @FXML private TextArea txtAreaConsola;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label lblEstado;
    

    private ObservableList<Servidor> listaServidores;
    private final LogSearchService searchService = new LogSearchService();
    
    // Rastreador atómico para deshabilitar los controles hasta que la última consulta en paralelo finalice
    private int tareasActivas = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Asignación de la política CONSTRAINED_RESIZE_POLICY
        tableServidores.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 1. CONFIGURACIÓN DE COLUMNA CHECKBOX (Multi-selección nativa)
        colSeleccionar.setCellValueFactory(cellData -> cellData.getValue().seleccionadoProperty());
        colSeleccionar.setCellFactory(CheckBoxTableCell.forTableColumn(colSeleccionar));
        colSeleccionar.setEditable(true);

        // 2. CONFIGURACIÓN DE CELDAS ESTÁNDAR
        colAlias.setCellValueFactory(new PropertyValueFactory<>("alias"));
        colIp.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));

        // Permitir la edición del CheckBox sobre el contenedor TableView
        tableServidores.setEditable(true);

        // Inicializar base de datos y cargar información estática
        DatabaseHelper.inicializarBaseDatos();
        cargarServidores();
        cargarConfiguracionGlobal();
    }

    /**
     * Carga todos los servidores de la base de datos local SQLite a la tabla visual.
     */
    private void cargarServidores() {
        try {
            listaServidores = FXCollections.observableArrayList(DatabaseHelper.obtenerServidores());
            tableServidores.setItems(listaServidores);
        } catch (SQLException e) {
            mostrarAlerta("Error de SQLite", "No se pudieron recuperar los servidores: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Carga las credenciales y rutas compartidas pre-guardadas en la base de datos local.
     */
    private void cargarConfiguracionGlobal() {
        try {
            DatabaseHelper.ConfigGlobal config = DatabaseHelper.obtenerConfiguracion();
            if (config != null) {
                txtUsuario.setText(config.getUser());
                txtPassword.setText(config.getPassword());
                txtRutaLog.setText(config.getLogPath());
            }
        } catch (SQLException e) {
            lblEstado.setText("Error al cargar configuración de SQLite.");
        }
    }

    /**
     * Acción para agregar un nuevo servidor al listado de persistencia de SQLite.
     */
    @FXML
    private void handleAgregarServidor() {
        String alias = txtNuevoAlias.getText().trim();
        String ip = txtNuevaIp.getText().trim();

        if (alias.isEmpty() || ip.isEmpty()) {
            mostrarAlerta("Campos Vacíos", "Por favor completa el Alias y la Dirección IP.", Alert.AlertType.WARNING);
            return;
        }

        try {
            DatabaseHelper.guardarServidor(alias, ip);
            txtNuevoAlias.clear();
            txtNuevaIp.clear();
            cargarServidores(); // Refresca la tabla
            lblEstado.setText("Servidor guardado correctamente.");
        } catch (SQLException e) {
            mostrarAlerta("Error al Guardar", "No se pudo registrar en la base de datos local: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Acción para eliminar el servidor seleccionado en la tabla de la base de datos SQLite.
     */
    @FXML
    private void handleEliminarServidor() {
        Servidor seleccionado = tableServidores.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            mostrarAlerta("Selección Requerida", "Selecciona un servidor de la tabla para eliminarlo.", Alert.AlertType.WARNING);
            return;
        }

        try {
            DatabaseHelper.eliminarServidor(seleccionado.getId());
            cargarServidores(); // Refresca la tabla
            lblEstado.setText("Servidor eliminado correctamente.");
        } catch (SQLException e) {
            mostrarAlerta("Error al Eliminar", "No se pudo borrar el registro: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Ejecuta consultas simultáneas en paralelo sobre todos los servidores marcados con CheckBox.
     */
    @FXML
    private void handleBuscarLog() {
        String usuario = txtUsuario.getText().trim();
        String password = txtPassword.getText();
        String rutaLog = txtRutaLog.getText().trim();
        String patron = txtPatron.getText().trim();

        // 1. Filtrar los servidores marcados vía CheckBox
        List<Servidor> servidoresSeleccionados = new ArrayList<>();
        if (listaServidores != null) {
            for (Servidor s : listaServidores) {
                if (s.isSeleccionado()) {
                    servidoresSeleccionados.add(s);
                }
            }
        }

        // 2. Validaciones previas
        if (servidoresSeleccionados.isEmpty()) {
            mostrarAlerta("Selección Requerida", "Por favor, marque al menos un servidor usando el CheckBox de la tabla.", Alert.AlertType.WARNING);
            return;
        }
        if (usuario.isEmpty() || password.isEmpty() || rutaLog.isEmpty()) {
            mostrarAlerta("Faltan Credenciales", "Es obligatorio ingresar el usuario, contraseña y ruta de log.", Alert.AlertType.WARNING);
            return;
        }

        // 3. Persistir configuración actual en SQLite de fondo
        try {
            DatabaseHelper.guardarConfiguracion(rutaLog, usuario, password);
        } catch (SQLException e) {
            System.err.println("Error no crítico al guardar configuración: " + e.getMessage());
        }

        // Preparar Consola y bloquear controles globales
        txtAreaConsola.clear();
        btnBuscar.setDisable(true);
        progressIndicator.setVisible(true);
        lblEstado.setText("Consultando servidores en paralelo...");
        
        tareasActivas = servidoresSeleccionados.size();

        // 4. Lanzamiento de Hilos de ejecución concurrentes distribuidos
        for (Servidor servidor : servidoresSeleccionados) {
            
            // Instancia la tarea real SSH usando JSch mapeando el puerto estándar (22)
            Task<String> busquedaTask = searchService.createSearchTask(
                servidor.getIpAddress(), 22, usuario, password, rutaLog, patron
            );

            // Monitorear flujo de salida exitoso
            busquedaTask.setOnSucceeded(event -> {
                String resultadoLog = busquedaTask.getValue();
                
                // Agrupar respuestas de forma ordenada en la consola compartida
                txtAreaConsola.appendText(String.format("=========================================\n" +
                                                       " SERVIDOR: %s [%s]\n" +
                                                       "=========================================\n" +
                                                       "%s\n\n", 
                                                       servidor.getAlias(), servidor.getIpAddress(), resultadoLog));
                
                comprobarEstatusDeEjecucion();
            });

            // Monitorear fallos individuales de conexión (No detienen a los demás servidores)
            busquedaTask.setOnFailed(event -> {
                Throwable ex = busquedaTask.getException();
                txtAreaConsola.appendText(String.format("=========================================\n" +
                                                       " ERROR EN: %s [%s]\n" +
                                                       "=========================================\n" +
                                                       "Fallo: %s\n\n", 
                                                       servidor.getAlias(), servidor.getIpAddress(), ex.getMessage()));
                
                comprobarEstatusDeEjecucion();
            });

            // Arrancar el hilo asíncrono
            Thread thread = new Thread(busquedaTask);
            thread.setDaemon(true); 
            thread.start();
        }
    }

    /**
     * Coordina el estado de la UI sincronizando la finalización de los hilos asíncronos.
     */
    private synchronized void comprobarEstatusDeEjecucion() {
        tareasActivas--;
        if (tareasActivas <= 0) {
            // Regresar el control de la UI al hilo de la interfaz principal de JavaFX
            Platform.runLater(() -> {
                btnBuscar.setDisable(false);
                progressIndicator.setVisible(false);
                lblEstado.setText("Búsquedas masivas completadas.");
                txtAreaConsola.selectPositionCaret(txtAreaConsola.getLength()); // Auto-scroll al fondo
            });
        }
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}