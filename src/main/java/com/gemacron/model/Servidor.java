/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gemacron.model;

/**
 *
 * @author gemac
 */
/**
 * Modelo de Datos que representa un servidor guardado en la base de datos local
 * SQLite.
 */
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Modelo de datos que representa a un Servidor. Incluye el identificador único
 * de la base de datos SQLite y soporte para bindings de JavaFX.
 */
public class Servidor {

    private int id;
    private final StringProperty alias = new SimpleStringProperty();
    private final StringProperty ipAddress = new SimpleStringProperty();
    private final StringProperty logPath = new SimpleStringProperty();
    private final StringProperty usuarioSsh = new SimpleStringProperty(); // <-- CRÍTICO
    private final BooleanProperty seleccionado = new SimpleBooleanProperty(false);

    /**
     * Constructor completo utilizado al recuperar servidores existentes de la
     * base de datos.
     *
     * * @param id Identificador único del registro en SQLite.
     * @param alias Nombre descriptivo del servidor.
     * @param ipAddress Dirección IP o dominio del servidor.
     * @param logPath
     * @param usuarioSsh
     */
// Constructor completo usado por DatabaseHelper.obtenerServidores()
    public Servidor(int id, String alias, String ipAddress, String logPath, String usuarioSsh) {
        this.id = id;
        setAlias(alias);
        setIpAddress(ipAddress);
        setLogPath(logPath);
        setUsuarioSsh(usuarioSsh); // <-- ASIGNAR AQUÍ
    }

    /**
     * Constructor alternativo utilizado para crear instancias temporales antes
     * de persistirlas.
     *
     * * @param alias Nombre descriptivo del servidor.
     * @param ipAddress Dirección IP o dominio del servidor.
     * @param logPath path o url
     */
    public Servidor(String alias, String ipAddress, String logPath, String usuarioSsh) {
        this(-1, alias, ipAddress, logPath, usuarioSsh);
    }

    // --- Métodos de acceso para el ID ---
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // --- Propiedades y métodos de acceso JavaFX (Bindings) ---
    public BooleanProperty seleccionadoProperty() {
        return seleccionado;
    }

    public boolean isSeleccionado() {
        return seleccionado.get();
    }

    public void setSeleccionado(boolean seleccionado) {
        this.seleccionado.set(seleccionado);
    }

    public StringProperty aliasProperty() {
        return alias;
    }

    public String getAlias() {
        return alias.get();
    }

    public void setAlias(String alias) {
        this.alias.set(alias);
    }

    public StringProperty ipAddressProperty() {
        return ipAddress;
    }

    public String getIpAddress() {
        return ipAddress.get();
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress.set(ipAddress);
    }

    public StringProperty logPathProperty() {
        return logPath;
    }

    public String getLogPath() {
        return logPath.get();
    }

    public void setLogPath(String logPath) {
        this.logPath.set(logPath);
    }
    // Getters y Setters de usuarioSsh

    public StringProperty usuarioSshProperty() {
        return usuarioSsh;
    }

    public String getUsuarioSsh() {
        return usuarioSsh.get();
    }

    public void setUsuarioSsh(String usuarioSsh) {
        this.usuarioSsh.set(usuarioSsh);
    }
}
