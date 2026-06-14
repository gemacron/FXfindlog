/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gemacron.util;

/**
 *
 * @author gemacron
 */

import com.gemacron.model.Servidor;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase de Utilidad para la conexión y persistencia local SQLite.
 * Actualizada para soportar relaciones 1:N entre Credenciales y Servidores.
 */
public class DatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:ssh_logs_config.db";

    /**
     * Inicializa las tablas SQLite requeridas para almacenar IPs, rutas y credenciales cifradas.
     */
public static void inicializarBaseDatos() {
        try (Connection conn = DriverManager.getConnection(DB_URL); 
             Statement stmt = conn.createStatement()) {

            // 1. Tabla maestra de credenciales cifradas
            stmt.execute("CREATE TABLE IF NOT EXISTS credenciales ("
                    + "usuario TEXT PRIMARY KEY,"
                    + "password_cifrada TEXT NOT NULL"
                    + ");");

            // 2. Tabla de servidores
            stmt.execute("CREATE TABLE IF NOT EXISTS servidores ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "alias TEXT NOT NULL,"
                    + "ip_address TEXT NOT NULL UNIQUE,"
                    + "log_path TEXT,"
                    + "usuario_ssh TEXT DEFAULT ''" // Quitamos el NOT NULL estricto para no romper datos viejos
                    + ");");

            // 3. Tabla de configuración global (Retrocompatibilidad)
            stmt.execute("CREATE TABLE IF NOT EXISTS configuracion_global ("
                    + "id INTEGER PRIMARY KEY CHECK (id = 1),"
                    + "log_path TEXT NOT NULL,"
                    + "ssh_user TEXT NOT NULL,"
                    + "ssh_password TEXT NOT NULL"
                    + ");");

            // =================================================================
            // PARCHES DE MIGRACIÓN SEGURA PARA CONSERVAR DATOS EXISTENTES
            // =================================================================
            
            // Parche 1: Migrar columna log_path (de la actualización anterior)
            try {
                stmt.execute("ALTER TABLE servidores ADD COLUMN log_path TEXT");
            } catch (SQLException e) {
                // Se ignora silenciosamente si la columna ya existe
            }

            // Parche 2: Migrar columna usuario_ssh (la actualización actual)
            try {
                // Agrega la columna con un valor en blanco por defecto para que los servidores viejos no rompan el programa
                stmt.execute("ALTER TABLE servidores ADD COLUMN usuario_ssh TEXT DEFAULT ''");
                System.out.println("Migración exitosa: Columna 'usuario_ssh' agregada a los datos existentes.");
            } catch (SQLException e) {
                // Se ignora silenciosamente si la columna ya existe
            }

        } catch (SQLException e) {
            System.err.println("Error al inicializar SQLite: " + e.getMessage());
        }
   }

    // =========================================================================
    // OPERACIONES DE CREDENCIALES
    // =========================================================================

    public static void guardarCredencial(String usuario, String passwordCifrada) throws SQLException {
        // INSERT OR REPLACE actualiza la contraseña si el usuario ya existe
        String sql = "INSERT OR REPLACE INTO credenciales(usuario, password_cifrada) VALUES(?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            pstmt.setString(2, passwordCifrada);
            pstmt.executeUpdate();
        }
    }

    public static String obtenerPasswordCifrada(String usuario) throws SQLException {
        String sql = "SELECT password_cifrada FROM credenciales WHERE usuario = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, usuario);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password_cifrada");
                }
            }
        }
        return null; // Retorna null si no encuentra el usuario
    }

    public static List<String> obtenerListaUsuarios() throws SQLException {
        List<String> usuarios = new ArrayList<>();
        String sql = "SELECT usuario FROM credenciales ORDER BY usuario ASC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                usuarios.add(rs.getString("usuario"));
            }
        }
        return usuarios;
    }

    // =========================================================================
    // OPERACIONES DE SERVIDORES
    // =========================================================================

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
                        rs.getString("ip_address"),
                        rs.getString("log_path"),
                        rs.getString("usuario_ssh") // <-- NUEVO CAMPO RECUPERADO
                ));
            }
        }
        return servidores;
    }

    public static void guardarServidor(String alias, String ip, String logPath, String usuarioSsh) throws SQLException {
        String sql = "INSERT INTO servidores(alias, ip_address, log_path, usuario_ssh) VALUES(?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, alias);
            pstmt.setString(2, ip);
            pstmt.setString(3, logPath);
            pstmt.setString(4, usuarioSsh); // <-- NUEVO CAMPO GUARDADO
            pstmt.executeUpdate();
        }
    }

    public static void actualizarRutaServidor(int id, String nuevaRuta) throws SQLException {
        String sql = "UPDATE servidores SET log_path = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL); 
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nuevaRuta);
            pstmt.setInt(2, id);
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

    // =========================================================================
    // OPERACIONES DE CONFIGURACIÓN GLOBAL (Mantenido por retrocompatibilidad)
    // =========================================================================

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

        private final String user;
        private final String password;

        public ConfigGlobal(String logPath, String user, String password) {
            // Ignoramos logPath intencionalmente según el nuevo diseño de la UI
            this.user = user;
            this.password = password;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }
    }
}