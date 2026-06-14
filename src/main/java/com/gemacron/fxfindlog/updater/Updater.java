/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gemacron.fxfindlog.updater;

/**
 *
 * @author gemacron
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
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Updater extends Application {

    private static final String APP_NAME = "FXfindlog";
    private static final String GIT_OWNER = "gemacron";
    private static final String GIT_REPO = "FXfindlog";
    private static final String UPDATE_URL = "https://api.github.com/repos/" + GIT_OWNER + "/" + GIT_REPO + "/releases/latest";
    
    // El archivo de versión ahora vive junto al ejecutable en AppData
    private static final String VERSION_FILE = "version.properties";

    private ProgressBar progressBar;
    private Label lblEstado;
    private String downloadUrl;
    private String nuevaVersion;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        inicializarUI(primaryStage);
        new Thread(this::procesarArranque).start();
    }

    private void inicializarUI(Stage stage) {
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0b0f19; -fx-border-color: #1e293b; -fx-border-width: 2px;");

        lblEstado = new Label("Buscando actualizaciones rápidas...");
        lblEstado.setTextFill(Color.web("#00e5ff"));
        lblEstado.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setPrefWidth(360);
        progressBar.setPrefHeight(10);
        progressBar.setStyle("-fx-accent: #00e5ff; -fx-control-inner-background: #161b22; -fx-background-color: #1e293b;");

        root.getChildren().addAll(lblEstado, progressBar);
        Scene scene = new Scene(root, 400, 120);
        
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("Iniciando " + APP_NAME + "...");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    private void procesarArranque() {
        try {
            String versionLocal = obtenerVersionLocal();
            URL url = new URL(UPDATE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Java-Delta-Updater-" + APP_NAME);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) response.append(inputLine);
                in.close();

                parsearGitResponse(response.toString());

                if (esVersionSuperior(nuevaVersion, versionLocal)) {
                    descargarYAplicarDelta();
                    return; 
                }
            }
            // Si no hay actualización, arrancamos en memoria
            lanzarAplicacionPrincipalEnMemoria();

        } catch (Exception e) {
            e.printStackTrace();
            lanzarAplicacionPrincipalEnMemoria();
        }
    }

    private void descargarYAplicarDelta() {
        Platform.runLater(() -> {
            lblEstado.setText("Aplicando parche " + nuevaVersion + "...");
            progressBar.setProgress(0.0);
        });
        
        try {
            // Buscamos cuál es el .jar original que está en la carpeta "app"
            File appDir = new File("app");
            File originalJar = null;
            if (appDir.exists() && appDir.isDirectory()) {
                File[] jars = appDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.equals("update.jar"));
                if (jars != null && jars.length > 0) originalJar = jars[0];
            }

            if (originalJar == null) throw new Exception("No se encontró el JAR original");

            File updateJar = new File("app", "update.jar");

            // Descarga optimizada con Buffer de 128KB
            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Java-Delta-Updater-" + APP_NAME);
            connection.setInstanceFollowRedirects(true);
            connection.connect();

            int status = connection.getResponseCode();
            if (status == 301 || status == 302) {
                String newUrl = connection.getHeaderField("Location");
                connection = (HttpURLConnection) new URL(newUrl).openConnection();
                connection.connect();
            }

            int fileLength = connection.getContentLength();
            InputStream input = connection.getInputStream();
            OutputStream output = new FileOutputStream(updateJar);

            byte[] data = new byte[131072]; // Buffer ultra rápido
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

            actualizarPropiedadesLocales(nuevaVersion);

            // CREACIÓN DEL SCRIPT NINJA (.BAT)
            File batFile = new File("actualizador_ninja.bat");
            try (PrintWriter writer = new PrintWriter(batFile)) {
                writer.println("@echo off");
                writer.println("timeout /t 2 /nobreak > NUL"); // Espera 2 seg a que Java muera
                writer.println("move /Y \"app\\update.jar\" \"app\\" + originalJar.getName() + "\""); // Reemplaza
                writer.println("start " + APP_NAME + ".exe"); // Vuelve a abrir tu app
                writer.println("del \"%~f0\""); // El script se suicida
            }

            // Ejecutamos el script de forma silenciosa y matamos a Java para liberar el archivo
            new ProcessBuilder("cmd", "/c", "start", "/min", batFile.getName()).start();
            Platform.exit();
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
            lanzarAplicacionPrincipalEnMemoria(); 
        }
    }

    private void lanzarAplicacionPrincipalEnMemoria() {
        Platform.runLater(() -> {
            try {
                Stage mainStage = new Stage();
                com.gemacron.fxfindlog.App miAppReal = new com.gemacron.fxfindlog.App();
                miAppReal.start(mainStage);

                Stage updaterStage = (Stage) lblEstado.getScene().getWindow();
                updaterStage.close();
            } catch (Exception e) {
                lblEstado.setText("Error al cargar el módulo principal.");
                e.printStackTrace();
            }
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

        // VOLVEMOS A BUSCAR EL .JAR EN GITHUB PARA LA DESCARGA LIGERA
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