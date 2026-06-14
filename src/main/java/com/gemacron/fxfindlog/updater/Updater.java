/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gemacron.fxfindlog.updater;

/**
 *
 * @author gemac
 */

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lanzador Inteligente (Stub) para FXfindlog implementado 100% en JavaFX.
 * Vinculado al repositorio: https://github.com/gemacron/FXfindlog
 * Descarga las actualizaciones en la carpeta de usuario para evadir bloqueos de permisos de Windows (UAC).
 */
public class Updater extends Application {

    private static final String APP_NAME = "FXfindlog";
    
    // --- VINCULACIÓN EXACTA A TU REPOSITORIO DE GITHUB ---
    private static final String GIT_OWNER = "gemacron";
    private static final String GIT_REPO = "FXfindlog";
    private static final String UPDATE_URL = "https://api.github.com/repos/" + GIT_OWNER + "/" + GIT_REPO + "/releases/latest";
    
    // Rutas de persistencia seguras (Directorio de usuario)
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String APP_DIR = USER_HOME + File.separator + ".fxfindlog";
    private static final String VERSION_FILE = APP_DIR + File.separator + "version.properties";
    private static final String LATEST_JAR = APP_DIR + File.separator + "FXfindlog-latest.jar";
    private static final String TEMP_JAR = APP_DIR + File.separator + "download.tmp";

    private ProgressBar progressBar;
    private Label lblEstado;
    private String downloadUrl;
    private String nuevaVersion;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        asegurarDirectorio();
        inicializarUI(primaryStage);
        
        // Disparar proceso asíncrono en un hilo independiente para no congelar la UI de JavaFX
        new Thread(this::procesarArranque).start();
    }

    private void asegurarDirectorio() {
        File dir = new File(APP_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private void inicializarUI(Stage stage) {
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0b0f19; -fx-border-color: #1e293b; -fx-border-width: 2px;");

        lblEstado = new Label("Verificando actualizaciones en GitHub...");
        lblEstado.setTextFill(Color.web("#00e5ff"));
        lblEstado.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setPrefWidth(360);
        progressBar.setPrefHeight(10);
        // Estilos CSS integrados para acoplarse al Dark Theme de tu aplicación
        progressBar.setStyle("-fx-accent: #00e5ff; -fx-control-inner-background: #161b22; -fx-background-color: #1e293b;");

        root.getChildren().addAll(lblEstado, progressBar);

        Scene scene = new Scene(root, 400, 120);
        
        stage.initStyle(StageStyle.UNDECORATED); // Estilo Splash Screen (sin bordes de ventana)
        stage.setTitle("Iniciando " + APP_NAME + "...");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    private void procesarArranque() {
        try {
            String versionLocal = obtenerVersionLocal();
            
            // 1. Consultar API de GitHub
            URL url = new URL(UPDATE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Java-Git-Updater-" + APP_NAME);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) response.append(inputLine);
                in.close();

                parsearGitResponse(response.toString());

                // 2. Si hay nueva versión, o el JAR no existe físicamente, descargarlo
                File jarLocal = new File(LATEST_JAR);
                if (esVersionSuperior(nuevaVersion, versionLocal) || !jarLocal.exists()) {
                    descargarActualizacion();
                    return; // El método de descarga se encarga de lanzar la app al final
                }
            }
            
            // 3. Si no hay actualizaciones, lanzar aplicación inmediatamente
            lanzarAplicacionPrincipal();

        } catch (Exception e) {
            System.err.println("Fallo al verificar red: " + e.getMessage());
            // En caso de estar sin internet, intentar abrir la última versión descargada
            lanzarAplicacionPrincipal();
        }
    }

    private void descargarActualizacion() {
        // En JavaFX, cualquier cambio en la interfaz gráfica DEBE ejecutarse en Platform.runLater
        Platform.runLater(() -> {
            lblEstado.setText("Descargando actualización " + nuevaVersion + "...");
            progressBar.setProgress(0.0);
        });
        
        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Java-Git-Updater-" + APP_NAME);
            connection.setInstanceFollowRedirects(true);
            connection.connect();

            // Seguir redirecciones (GitHub usa servidores CDN de AWS)
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 302) {
                String newUrl = connection.getHeaderField("Location");
                connection = (HttpURLConnection) new URL(newUrl).openConnection();
                connection.connect();
            }

            int fileLength = connection.getContentLength();
            InputStream input = connection.getInputStream();
            OutputStream output = new FileOutputStream(TEMP_JAR);

            byte[] data = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0) {
                    double progreso = (double) total / fileLength;
                    Platform.runLater(() -> progressBar.setProgress(progreso));
                }
                output.write(data, 0, count);
            }

            output.flush(); output.close(); input.close();

            // Reemplazo atómico
            Files.move(Paths.get(TEMP_JAR), Paths.get(LATEST_JAR), StandardCopyOption.REPLACE_EXISTING);
            actualizarPropiedadesLocales(nuevaVersion);
            
            lanzarAplicacionPrincipal();

        } catch (Exception e) {
            Platform.runLater(() -> lblEstado.setText("Error crítico de descarga."));
            lanzarAplicacionPrincipal(); // Intenta abrir la versión vieja si la nueva falla
        }
    }

    private void lanzarAplicacionPrincipal() {
        Platform.runLater(() -> lblEstado.setText("Iniciando entorno gráfico..."));
        try {
            File jarFile = new File(LATEST_JAR);
            if (jarFile.exists()) {
                // Utiliza la misma máquina virtual de Java (JRE) que está corriendo este updater
                String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                new ProcessBuilder(javaBin, "-cp", LATEST_JAR, "com.gemacron.fxfindlog.Launcher").start();
            } else {
                Platform.runLater(() -> {
                    lblEstado.setText("Error Fatal: No se encontró el ejecutable. Verifique la red.");
                    lblEstado.setTextFill(Color.web("#f85149"));
                });
                Thread.sleep(3000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Cerrar el hilo de UI de JavaFX y matar el proceso del Updater
        Platform.runLater(() -> {
            Platform.exit();
            System.exit(0);
        });
    }

    private String obtenerVersionLocal() {
        Properties prop = new Properties();
        try (InputStream input = new FileInputStream(VERSION_FILE)) {
            prop.load(input);
            return prop.getProperty("version", "0.0.0");
        } catch (IOException ex) { return "0.0.0"; }
    }

    private void actualizarPropiedadesLocales(String version) throws IOException {
        Properties prop = new Properties();
        prop.setProperty("version", version);
        try (OutputStream output = new FileOutputStream(VERSION_FILE)) {
            prop.store(output, "Registro de versiones - gemacron/FXfindlog");
        }
    }

    private void parsearGitResponse(String json) throws Exception {
        Matcher vMatcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"").matcher(json);
        if (vMatcher.find()) this.nuevaVersion = vMatcher.group(1);
        else throw new Exception("No tag version");

        Matcher uMatcher = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"(https://[^\"]+\\.jar)\"").matcher(json);
        if (uMatcher.find()) this.downloadUrl = uMatcher.group(1);
        else throw new Exception("No JAR asset found");
    }

    private boolean esVersionSuperior(String versionRemota, String versionLocal) {
        String[] r = versionRemota.split("\\.");
        String[] l = versionLocal.split("\\.");
        int len = Math.max(r.length, l.length);
        for (int i = 0; i < len; i++) {
            int remota = i < r.length ? Integer.parseInt(r[i]) : 0;
            int local = i < l.length ? Integer.parseInt(l[i]) : 0;
            if (remota > local) return true;
            if (remota < local) return false;
        }
        return false;
    }
}