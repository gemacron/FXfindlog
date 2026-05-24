/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gemacron.util;

/**
 *
 * @author gemac
 */

import com.gemacron.model.Servidor;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase de Utilidad para la conexión y persistencia local SQLite.
 */
public class DatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:ssh_logs_config.db";

    /**
     * Inicializa las tablas SQLite requeridas para almacenar IPs y variables estáticas.
     */
    public static void inicializarBaseDatos() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // Tabla de servidores
            stmt.execute("CREATE TABLE IF NOT EXISTS servidores (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "alias TEXT NOT NULL," +
                    "ip_address TEXT NOT NULL UNIQUE" +
                    ");");

            // Tabla para guardar la configuración global estática (una única fila)
            stmt.execute("CREATE TABLE IF NOT EXISTS configuracion_global (" +
                    "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                    "log_path TEXT NOT NULL," +
                    "ssh_user TEXT NOT NULL," +
                    "ssh_password TEXT NOT NULL" +
                    ");");

        } catch (SQLException e) {
            System.err.println("Error al inicializar SQLite: " + e.getMessage());
        }
    }

    // --- OPERACIONES DE SERVIDORES ---

    public static List<Servidor> obtenerServidores() throws SQLException {
        List<Servidor> servidores = new ArrayList<>();
        String sql = "SELECT * FROM servidores ORDER BY alias ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                servidores.add(new Servidor(
                        rs.getInt("id"),
                        rs.getString("alias"),
                        rs.getString("ip_address")
                ));
            }
        }
        return servidores;
    }

    public static void guardarServidor(String alias, String ip) throws SQLException {
        String sql = "INSERT INTO servidores(alias, ip_address) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, alias);
            pstmt.setString(2, ip);
            pstmt.executeUpdate();
        }
    }

    public static void eliminarServidor(int id) throws SQLException {
        String sql = "DELETE FROM servidores WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    // --- OPERACIONES DE CONFIGURACIÓN GLOBAL ---

    public static void guardarConfiguracion(String path, String user, String pass) throws SQLException {
        String sql = "INSERT OR REPLACE INTO configuracion_global(id, log_path, ssh_user, ssh_password) VALUES(1, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.setString(2, user);
            pstmt.setString(3, pass);
            pstmt.executeUpdate();
        }
    }

    public static ConfigGlobal obtenerConfiguracion() throws SQLException {
        String sql = "SELECT * FROM configuracion_global WHERE id = 1";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return new ConfigGlobal(
                        rs.getString("log_path"),
                        rs.getString("ssh_user"),
                        rs.getString("ssh_password")
                );
            }
        }
        return null;
    }

    // Clase auxiliar estática para transportar la configuración
    public static class ConfigGlobal {
        private final String logPath;
        private final String user;
        private final String password;

        public ConfigGlobal(String logPath, String user, String password) {
            this.logPath = logPath;
            this.user = user;
            this.password = password;
        }

        public String getLogPath() { return logPath; }
        public String getUser() { return user; }
        public String getPassword() { return password; }
    }
}
