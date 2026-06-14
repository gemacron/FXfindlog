/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gemacron.fxfindlog;

/**
 *
 * @author gemac
 */

/**
 * Clase lanzadora auxiliar (Bootstrap) requerida para empaquetar aplicaciones JavaFX en un FAT JAR.
 * Al no extender de javafx.application.Application, la JVM carga esta clase de manera convencional
 * en el Classpath clásico, evitando la validación restrictiva de módulos nativos de JavaFX en el arranque.
 */
public class Launcher {
    
    /**
     * Punto de entrada alternativo para la JVM.
     * @param args Argumentos de consola heredados.
     */
    public static void main(String[] args) {
        // Redirige la ejecución de manera segura al inicio real de la aplicación JavaFX
        App.main(args);
    }
}