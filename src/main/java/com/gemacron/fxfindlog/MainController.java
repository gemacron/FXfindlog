package com.gemacron.fxfindlog;

import com.gemacron.model.Servidor;
import com.gemacron.service.LogSearchService;
import com.gemacron.util.CryptoUtil;
import com.gemacron.util.DatabaseHelper;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;

/**
 * Controlador principal de la UI del Buscador de Logs SSH. Gestiona de forma
 * segura los hilos de fondo distribuidos y la persistencia SQLite cifrada.
 */
public class MainController implements Initializable {

    // Controles de Configuración y Credenciales
    @FXML
    private ComboBox<String> cmbUsuario;
    @FXML
    private PasswordField txtPassword;

    // Controles de la Tabla de Servidores (SQLite)
    @FXML
    private TableView<Servidor> tableServidores;
    @FXML
    private TableColumn<Servidor, Boolean> colSeleccionar;
    @FXML
    private TableColumn<Servidor, String> colAlias;
    @FXML
    private TableColumn<Servidor, String> colIp;
    @FXML
    private TableColumn<Servidor, String> colRutaLog;

    // Controles de Formulario para Agregar Servidor
    @FXML
    private TextField txtNuevoAlias;
    @FXML
    private TextField txtNuevaIp;
    @FXML
    private TextField txtNuevaRuta;
    @FXML
    private Button btnLimpiar;
    @FXML
    private Button btnAgregar;
    @FXML
    private Button btnEliminar;

    // Controles de Consulta y Consola
    @FXML
    private TextField txtPatron;
    @FXML
    private Button btnBuscar;
    @FXML
    private Button btnCancelar;
    @FXML
    private TextArea txtAreaConsola;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private Label lblEstado;
    @FXML
    private Label lblActiveServer; // Inyectado para mostrar el servidor activo sin errores

    private ObservableList<Servidor> listaServidores;
    private final LogSearchService searchService = new LogSearchService();

    private final List<Task<String>> tareasEnEjecucion = new ArrayList<>();

    // Rastreador atómico para deshabilitar los controles hasta que la última consulta en paralelo finalice
    private int tareasActivas = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        // 1. CONFIGURACIÓN DEL COMBOBOX (Autocompletado de usuarios)
        cmbUsuario.setEditable(true);
        cargarUsuariosGuardados();

        // Se activa cuando el usuario SELECCIONA una opción con el mouse
        cmbUsuario.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty()) {
                estirarPassword(newVal.trim());
            }
        });

        // Se activa si el usuario ESCRIBE a mano el nombre y presiona ENTER
        cmbUsuario.getEditor().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String userEscrito = cmbUsuario.getEditor().getText().trim();
                estirarPassword(userEscrito);
                txtPatron.requestFocus(); // Mueve el foco al patrón de búsqueda
            }
        });

        // Lógica para ACTUALIZAR la contraseña al escribir una nueva y presionar ENTER
        txtPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String user = cmbUsuario.getEditor().getText().trim();
                String nuevaPassword = txtPassword.getText();

                if (user.isEmpty() || nuevaPassword.isEmpty()) {
                    mostrarAlerta("Campos Requeridos", "Debe especificar el usuario y la nueva contraseña.", Alert.AlertType.WARNING);
                    return;
                }

                try {
                    String passCifrada = CryptoUtil.encrypt(nuevaPassword);
                    DatabaseHelper.guardarCredencial(user, passCifrada);
                    lblEstado.setText("✓ Contraseña actualizada globalmente para: " + user);
                } catch (Exception e) {
                    mostrarAlerta("Error", "No se pudo actualizar la contraseña: " + e.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });
        // Lógica para ACTUALIZAR la contraseña del usuario al presionar ENTER en el PasswordField
        txtPassword.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String user = cmbUsuario.getEditor().getText().trim();
                String nuevaPassword = txtPassword.getText();

                if (user.isEmpty()) {
                    mostrarAlerta("Usuario Requerido", "Debe especificar el usuario para poder actualizar su contraseña.", Alert.AlertType.WARNING);
                    return;
                }
                if (nuevaPassword.isEmpty()) {
                    mostrarAlerta("Contraseña Vacía", "Por favor, ingrese la nueva contraseña.", Alert.AlertType.WARNING);
                    return;
                }

                try {
                    // Cifrar y sobreescribir en la tabla 'credenciales' (gracias al INSERT OR REPLACE)
                    String passCifrada = CryptoUtil.encrypt(nuevaPassword);
                    DatabaseHelper.guardarCredencial(user, passCifrada);

                    // 2. Notificar al usuario del éxito de la operación
                    lblEstado.setText("✓ Contraseña actualizada globalmente para el usuario: " + user);

                    // Opcional: limpiar el campo de contraseña por seguridad después de guardar
                    txtPassword.clear();

                } catch (Exception e) {
                    mostrarAlerta("Error de Seguridad", "No se pudo cifrar o guardar la nueva contraseña: " + e.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });

        // 2. CONFIGURACIÓN DE LA TABLA
        tableServidores.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableServidores.setEditable(true);

        colSeleccionar.setCellValueFactory(cellData -> cellData.getValue().seleccionadoProperty());
        colSeleccionar.setCellFactory(CheckBoxTableCell.forTableColumn(colSeleccionar));
        colSeleccionar.setEditable(true);

        colAlias.setCellValueFactory(new PropertyValueFactory<>("alias"));
        colIp.setCellValueFactory(new PropertyValueFactory<>("ipAddress"));

        // Celda editable para la ruta individual
        colRutaLog.setCellValueFactory(new PropertyValueFactory<>("logPath"));
        colRutaLog.setCellFactory(TextFieldTableCell.forTableColumn());
        colRutaLog.setOnEditCommit(event -> {
            Servidor servidorModificado = event.getRowValue();
            String nuevaRuta = event.getNewValue() != null ? event.getNewValue().trim() : "";

            servidorModificado.setLogPath(nuevaRuta);

            try {
                DatabaseHelper.actualizarRutaServidor(servidorModificado.getId(), nuevaRuta);
                lblEstado.setText("Ruta de '" + servidorModificado.getAlias() + "' actualizada correctamente.");
            } catch (SQLException e) {
                mostrarAlerta("Error de SQLite", "No se pudo actualizar la ruta: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        });

        // Forzar ajuste de línea en la consola para una lectura cómoda
        txtAreaConsola.setWrapText(true);

        // Escuchar clics en la tabla para cargar datos en el formulario inferior
        tableServidores.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String usuarioAsignado = newSelection.getUsuarioSsh();

                // 1. Cargar campos de texto estándar
                txtNuevoAlias.setText(newSelection.getAlias());
                txtNuevaIp.setText(newSelection.getIpAddress());
                txtNuevaRuta.setText(newSelection.getLogPath());

                // 2. Sincronizar de forma mandatoria el ComboBox y estirar el password
                if (usuarioAsignado != null && !usuarioAsignado.trim().isEmpty()) {
                    cmbUsuario.setValue(usuarioAsignado.trim());
                    cmbUsuario.getEditor().setText(usuarioAsignado.trim());
                    estirarPassword(usuarioAsignado.trim());
                } else {
                    cmbUsuario.setValue("");
                    cmbUsuario.getEditor().setText("");
                    txtPassword.clear();
                    lblEstado.setText("Este servidor no tiene un usuario asignado en SQLite.");
                }

                // 3. ACTUALIZAR ESTADO DE BOTONES
                btnAgregar.setText("Actualizar Servidor");
                if (btnLimpiar != null) {
                    btnLimpiar.setDisable(false); // ACTIVAR el botón "+"
                }
                
            } else {
                // Si la selección es nula (ej. al llamar a clearSelection)
                btnAgregar.setText("Guardar Servidor");
                if (btnLimpiar != null) {
                    btnLimpiar.setDisable(true); // DESACTIVAR el botón "+"
                }
            }
        });

        // INICIALIZACIÓN DE DATOS
        DatabaseHelper.inicializarBaseDatos();
        cargarServidores();
        cargarConfiguracionGlobal();
        if (btnLimpiar != null) btnLimpiar.setDisable(true);
    }

    private void cargarServidores() {
        try {
            listaServidores = FXCollections.observableArrayList(DatabaseHelper.obtenerServidores());
            tableServidores.setItems(listaServidores);
        } catch (SQLException e) {
            mostrarAlerta("Error de SQLite", "No se pudieron recuperar los servidores: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void cargarUsuariosGuardados() {
        try {
            cmbUsuario.getItems().clear();
            cmbUsuario.getItems().addAll(DatabaseHelper.obtenerListaUsuarios());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void cargarConfiguracionGlobal() {
        try {
            DatabaseHelper.ConfigGlobal config = DatabaseHelper.obtenerConfiguracion();
            if (config != null) {
                if (config.getUser() != null && !config.getUser().isEmpty()) {
                    cmbUsuario.getEditor().setText(config.getUser());
                }
                if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                    txtPassword.setText(config.getPassword());
                }
            }
        } catch (SQLException e) {
            lblEstado.setText("Error al cargar configuración de SQLite.");
        }
    }

    @FXML
    private void handleAgregarServidor() {
        String alias = txtNuevoAlias.getText().trim();
        String ip = txtNuevaIp.getText().trim();
        String ruta = txtNuevaRuta.getText().trim();
        String usuario = cmbUsuario.getEditor().getText().trim();
        String password = txtPassword.getText();

        if (alias.isEmpty() || ip.isEmpty() || usuario.isEmpty() || password.isEmpty()) {
            mostrarAlerta("Campos Requeridos", "Alias, IP, Usuario y Contraseña son obligatorios.", Alert.AlertType.WARNING);
            return;
        }

        try {
            // Guardamos o actualizamos la credencial cifrada en la bóveda
            DatabaseHelper.guardarCredencial(usuario, CryptoUtil.encrypt(password));

            // Guardamos el servidor vinculándolo a la credencial
            DatabaseHelper.guardarServidor(alias, ip, ruta, usuario);

            // Limpiamos los campos del servidor (mantenemos credenciales por si quiere agregar otro rápido)
            txtNuevoAlias.clear();
            txtNuevaIp.clear();
            txtNuevaRuta.clear();

            cargarServidores();
            cargarUsuariosGuardados(); // Refrescar lista de usuarios en el ComboBox
            lblEstado.setText("Servidor y credenciales guardados con éxito.");
        } catch (Exception e) {
            mostrarAlerta("Error", "Fallo al guardar: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

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

    @FXML
    private void handleBuscarLog() {
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

        // 2. Validaciones estrictas de la tabla
        if (servidoresSeleccionados.isEmpty()) {
            mostrarAlerta("Selección Requerida", "Por favor, marque al menos un servidor usando el CheckBox de la tabla.", Alert.AlertType.WARNING);
            return;
        }

        // Asegurar que todos los seleccionados tengan su ruta y su usuario configurado en SQLite
        for (Servidor s : servidoresSeleccionados) {
            if (s.getLogPath() == null || s.getLogPath().trim().isEmpty()) {
                mostrarAlerta("Ruta Faltante", "El servidor '" + s.getAlias() + "' no tiene una ruta configurada en la tabla.", Alert.AlertType.ERROR);
                return;
            }
            if (s.getUsuarioSsh() == null || s.getUsuarioSsh().trim().isEmpty()) {
                mostrarAlerta("Usuario Faltante", "El servidor '" + s.getAlias() + "' no tiene un usuario SSH asignado en la base de datos.", Alert.AlertType.ERROR);
                return;
            }
        }

        // 3. Preparar Consola y bloquear controles globales
        txtAreaConsola.clear();
        btnBuscar.setDisable(true);
        btnCancelar.setDisable(false);
        progressIndicator.setVisible(true);
        lblEstado.setText("Consultando servidores en paralelo...");

        tareasActivas = servidoresSeleccionados.size();
        tareasEnEjecucion.clear();

        // 4. Lanzamiento de Hilos de ejecución concurrentes distribuidos
        for (Servidor servidor : servidoresSeleccionados) {

            String rutaIndividual = servidor.getLogPath().trim();
            String usuarioAsignado = servidor.getUsuarioSsh().trim();
            String passwordDescifrada = "";

            // --- EXTRACCIÓN DINÁMICA DE CREDENCIALES ---
            try {
                String passCifrada = DatabaseHelper.obtenerPasswordCifrada(usuarioAsignado);

                if (passCifrada != null && !passCifrada.isEmpty()) {
                    passwordDescifrada = CryptoUtil.decrypt(passCifrada);
                } else {
                    throw new Exception("No hay contraseña guardada en la bóveda local para el usuario: " + usuarioAsignado);
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    txtAreaConsola.appendText(String.format("=========================================\n"
                            + " ERROR DE CREDENCIALES EN: %s [%s]\n"
                            + "=========================================\n"
                            + "Fallo: %s\n\n",
                            servidor.getAlias(), servidor.getIpAddress(), e.getMessage()));
                });
                comprobarEstatusDeEjecucion();
                continue; // Pasa al siguiente servidor del bucle
            }

            // --- INSTANCIACIÓN DE LA TAREA SSH ---
            Task<String> busquedaTask = searchService.createSearchTask(
                    servidor.getIpAddress(), 22, usuarioAsignado, passwordDescifrada, rutaIndividual, patron
            );

            tareasEnEjecucion.add(busquedaTask);

            // Monitorear flujo de salida exitoso
            busquedaTask.setOnSucceeded(event -> {
                String resultadoLog = busquedaTask.getValue();

                Platform.runLater(() -> {
                    txtAreaConsola.appendText(String.format("=========================================\n"
                            + " SERVIDOR: %s [%s] (User: %s)\n"
                            + "=========================================\n"
                            + "%s\n\n",
                            servidor.getAlias(), servidor.getIpAddress(), usuarioAsignado, resultadoLog));

                    if (lblActiveServer != null) {
                        lblActiveServer.setText(servidor.getAlias());
                    }
                });

                comprobarEstatusDeEjecucion();
            });

            // Monitorear fallos individuales de conexión
            busquedaTask.setOnFailed(event -> {
                Throwable ex = busquedaTask.getException();

                Platform.runLater(() -> {
                    txtAreaConsola.appendText(String.format("=========================================\n"
                            + " ERROR SSH EN: %s [%s]\n"
                            + "=========================================\n"
                            + "Fallo: %s\n\n",
                            servidor.getAlias(), servidor.getIpAddress(), ex.getMessage()));
                });

                comprobarEstatusDeEjecucion();
            });

            // NUEVO: Monitorear si la tarea es cancelada por el usuario
            busquedaTask.setOnCancelled(event -> {
                Platform.runLater(() -> {
                    txtAreaConsola.appendText(String.format("=========================================\n"
                            + " ⛔ BÚSQUEDA CANCELADA: %s [%s]\n"
                            + "=========================================\n\n",
                            servidor.getAlias(), servidor.getIpAddress()));
                });
                comprobarEstatusDeEjecucion();
            });

            // Arrancar el hilo asíncrono
            Thread thread = new Thread(busquedaTask);
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Coordina el estado de la UI sincronizando la finalización de los hilos
     * asíncronos.
     */
    private synchronized void comprobarEstatusDeEjecucion() {
        tareasActivas--;
        if (tareasActivas <= 0) {
            Platform.runLater(() -> {
                btnBuscar.setDisable(false);
                btnCancelar.setDisable(true); // Apagar botón de cancelar
                progressIndicator.setVisible(false);
                lblEstado.setText("Operación finalizada.");
                txtAreaConsola.selectPositionCaret(txtAreaConsola.getLength());
            });
        }
    }

    private void estirarPassword(String usuario) {
        if (usuario == null || usuario.trim().isEmpty()) {
            return;
        }
        try {
            // Consultar la contraseña cifrada en la bóveda SQLite
            String passCifrada = DatabaseHelper.obtenerPasswordCifrada(usuario.trim());

            if (passCifrada != null && !passCifrada.isEmpty()) {
                // Descifrar e inyectar en el PasswordField
                txtPassword.setText(CryptoUtil.decrypt(passCifrada));
                lblEstado.setText("✓ Credencial estirada para el usuario: " + usuario);
            } else {
                txtPassword.clear();
            }
        } catch (Exception e) {
            System.err.println("Error al estirar contraseña de la base de datos: " + e.getMessage());
        }
    }

    /**
     * Interrumpe todos los hilos de búsqueda SSH que se estén ejecutando.
     */
    @FXML
    private void handleCancelarBusqueda() {
        lblEstado.setText("Cancelando conexiones SSH...");
        btnCancelar.setDisable(true); // Prevenir múltiples clics

        for (Task<String> task : tareasEnEjecucion) {
            if (task != null && task.isRunning()) {
                task.cancel(true); // true = interrumpe el hilo subyacente (rompe la conexión JSch)
            }
        }
    }
    
    @FXML
    private void handleLimpiarFormulario() {
        // 1. Limpiar todos los campos de texto
        txtNuevoAlias.clear();
        txtNuevaIp.clear();
        txtNuevaRuta.clear();
        
        // 2. Limpiar el ComboBox y el Password
        cmbUsuario.setValue("");
        cmbUsuario.getEditor().clear();
        txtPassword.clear();

        // 3. Quitar la selección visual de la tabla
        tableServidores.getSelectionModel().clearSelection();

        // 4. Restaurar el estado de los botones
        btnAgregar.setText("Guardar Servidor");
        btnLimpiar.setDisable(true); // Se desactiva a sí mismo por defecto
        
        lblEstado.setText("Formulario limpiado. Listo para un nuevo registro.");
        txtNuevoAlias.requestFocus(); // Mueve el cursor al primer campo
    }

    private void mostrarAlerta(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}
