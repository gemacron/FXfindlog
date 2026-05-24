/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.gemacron.service;

/**
 *
 * @author gemac
 */
import com.jcraft.jsch.*;
import javafx.concurrent.Task;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio encargado de conectarse via SSH a los servidores Linux y realizar
 * búsquedas optimizadas para logs de transacciones ATM. Genera resultados
 * estructurados en texto plano formateado y ordenado para un control TextArea
 * estándar con fuentes monoespaciadas. Incorpora tolerancia a advertencias de
 * entorno (Locales/setlocale) del flujo SSH.
 */
public class LogSearchService {

    /**
     * Crea un Task asíncrono para ejecutar la búsqueda de manera no bloqueante.
     *
     * @param ip Dirección del servidor remoto.
     * @param port Puerto SSH (usualmente 22).
     * @param user Usuario del sistema.
     * @param password Contraseña del sistema.
     * @param logPath Directorio de los logs o archivo log de transacciones ATM.
     * @param pattern Patrón transaccional a buscar (Ej. "62252515" o
     * "atm_id=5956").
     * @return Tarea ejecutable de JavaFX con un String de texto formateado
     * listo para el TextArea.
     */
    public Task<String> createSearchTask(String ip, int port, String user, String password, String logPath, String pattern) {
        return new Task<>() {
            @Override
            protected String call() throws Exception {
                updateMessage("Conectando a " + ip + "...");

                JSch jsch = new JSch();
                Session session = null;
                ChannelExec channel = null;

                try {
                    session = jsch.getSession(user, ip, port);
                    session.setPassword(password);

                    // Ignorar la validación interactiva de llaves SSH (StrictHostKeyChecking)
                    java.util.Properties config = new java.util.Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);

                    session.connect(10000); // 10 segundos de timeout de conexión
                    updateMessage("Preparando análisis de tramas JSON en " + ip + "...");

                    // Saneamiento de variables para bloquear inyecciones de comandos en la Shell remota
                    String safePath = logPath.replace("'", "'\\''");
                    String safePattern = pattern.replace("'", "'\\''");

                    /*
                     * SCRIPT DE ALTO RENDIMIENTO RECONSTRUIDO:
                     * 1. Si es directorio, busca archivos de texto plano o comprimidos (.log, .txt o .gz) evitando procesar basura.
                     * 2. Mantiene 'grep -a' para velocidad nativa Boyer-Moore en archivos planos.
                     * 3. Usa 'zgrep' únicamente si el archivo termina de forma explícita en '.gz'.
                     */
                    String command = String.format(
                            "if test -d '%1$s'; then "
                            + "  find '%1$s' -maxdepth 1 -type f \\( -name '*.log*' -o -name '*puercos*' -o -name '*.txt' \\) -exec sh -c '"
                            + "    for f in \"$@\"; do "
                            + "      if echo \"$f\" | grep -q \"\\.gz$\"; then "
                            + "        export LC_ALL=C; zgrep -a -H -n -C 5 -E \"$1\" \"$f\"; "
                            + "      else "
                            + "        export LC_ALL=C; grep -a -H -n -C 5 -E \"$1\" \"$f\"; "
                            + "      fi; "
                            + "    done"
                            + "  ' _ '%2$s' {} + 2>/dev/null; "
                            + "elif test -f '%1$s'; then "
                            + "  if echo '%1$s' | grep -q \"\\.gz$\"; then "
                            + "    export LC_ALL=C; zgrep -a -H -n -C 5 -E '%2$s' '%1$s' 2>/dev/null; "
                            + "  else "
                            + "    export LC_ALL=C; grep -a -H -n -C 5 -E '%2$s' '%1$s' 2>/dev/null; "
                            + "  fi; "
                            + "else "
                            + "  echo 'Error: La ruta proporcionada no existe o no se tienen permisos de lectura.' >&2; "
                            + "  exit 2; "
                            + "fi",
                            safePath, safePattern
                    );

                    channel = (ChannelExec) session.openChannel("exec");
                    channel.setCommand(command);

                    InputStream in = channel.getInputStream();
                    InputStream err = channel.getErrStream();

                    channel.connect(10000);
                    updateMessage("Escaneando logs y optimizando uso de CPU en " + ip + "...");

                    StringBuilder result = new StringBuilder();
                    StringBuilder errorResult = new StringBuilder();
                    byte[] buffer = new byte[1024];

                    // Bucle de lectura asíncrona no bloqueante
                    while (true) {
                        // Consumir flujo de salida estándar (stdout)
                        while (in.available() > 0) {
                            int readBytes = in.read(buffer, 0, 1024);
                            if (readBytes < 0) {
                                break;
                            }
                            result.append(new String(buffer, 0, readBytes, StandardCharsets.UTF_8));
                        }

                        // Consumir flujo de error estándar (stderr)
                        while (err.available() > 0) {
                            int readBytes = err.read(buffer, 0, 1024);
                            if (readBytes < 0) {
                                break;
                            }
                            errorResult.append(new String(buffer, 0, readBytes, StandardCharsets.UTF_8));
                        }

                        if (channel.isClosed()) {
                            // Garantizar la lectura de los últimos bytes remanentes en los descriptores
                            if (in.available() > 0 || err.available() > 0) {
                                continue;
                            }
                            break;
                        }
                        // Evita el uso ineficiente de la CPU (busy-waiting) durmiendo el hilo de ejecución
                        Thread.sleep(100);
                    }

                    int exitStatus = channel.getExitStatus();

                    /*
                     * TRATAMIENTO INTELIGENTE DE ERRORES:
                     * Solo lanzamos una excepción si el canal finalizó con error de ejecución remoto real (código de salida > 1)
                     * o si el canal SSH falló de forma crítica (código -1).
                     * Esto tolera y descarta advertencias inocuas de locale en stderr (ej: setlocale: LC_ALL: cannot change locale).
                     */
                    if ((exitStatus > 1 || exitStatus == -1) && errorResult.length() > 0) {
                        throw new Exception(errorResult.toString().trim());
                    }

                    // Si el buffer de salida estándar está vacío, significa que el patrón no arrojó resultados
                    if (result.length() == 0) {
                        return "[INFO] No se encontraron transacciones con el patrón '" + pattern + "' en la ruta especificada.";
                    }

                    // Procesar, agrupar y formatear los resultados de manera ultra-legible en texto plano
                    return processAndFormatLogsText(result.toString(), pattern);

                } finally {
                    // Garantizar la liberación segura de sockets de red SSH
                    if (channel != null) {
                        channel.disconnect();
                    }
                    if (session != null) {
                        session.disconnect();
                    }
                }
            }
        };
    }

    /**
     * Procesa la salida bruta del comando grep, agrupa los resultados por
     * archivo, remueve los códigos de escape de color ANSI, elimina rutas
     * redundantes de Linux y alinea secuencialmente los números de línea y
     * coincidencias.
     */
    private String processAndFormatLogsText(String rawText, String pattern) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        // 1. Limpieza de códigos de escape de color ANSI de Laravel (ej: \x1B[97m)
        String cleanResult = rawText.replaceAll("\\x1B\\[[0-9;]*[a-zA-Z]", "");

        String[] lines = cleanResult.split("\\r?\\n");
        StringBuilder formatted = new StringBuilder();
        String currentFile = "";

        // Patrón regex para capturar: [Ruta opcional][Nombre de archivo][Separador - o :][Número de línea][Separador - o :][Contenido]
        // Ejemplo: /var/log/laravel.log-5-   "atm_id": "729"
        Pattern logLinePattern = Pattern.compile("^(?:.*/)?([^/\\s]+)([-:])([0-9]+)([-:])(.*)$");

        for (String line : lines) {
            // Manejar y embellecer el separador de bloques de contexto no contiguos de grep ("--")
            if (line.trim().equals("--")) {
                formatted.append("      ...\n");
                continue;
            }

            Matcher matcher = logLinePattern.matcher(line);
            if (matcher.matches()) {
                String fileName = matcher.group(1);
                String separator = matcher.group(2); // : para coincidencia exacta, - para líneas de contexto
                String lineNum = matcher.group(3);
                String content = matcher.group(5);

                // Si cambiamos de archivo en el buffer de salida, imprimir cabecera limpia
                if (!fileName.equals(currentFile)) {
                    currentFile = fileName;
                    formatted.append("\n📁 [ ARCHIVO: ").append(fileName).append(" ]\n");
                    int dividerLength = Math.min(80, 16 + fileName.length());
                    formatted.append(generateDivider(dividerLength)).append("\n");
                }

                // Identificar si es la línea de coincidencia exacta (:) o de contexto (-)
                boolean isMatch = ":".equals(separator);
                String indicator = isMatch ? "▶ " : "  ";

                // Formatear el número de línea alineado a la derecha para un gutter perfecto
                String formattedNum = String.format("%5s", lineNum);

                // --- MEJORA: RESALTAR EL PATRÓN BUSCADO CON CARACTERES DE TEXTO ---
                // Envuelve todas las ocurrencias en marcadores estéticos » « legibles en texto plano
                if (pattern != null && !pattern.isEmpty()) {
                    try {
                        // Compilamos de forma insensible a mayúsculas/minúsculas para marcar todas las coincidencias
                        Pattern highlightPattern = Pattern.compile("(?i)(" + Pattern.quote(pattern) + ")");
                        Matcher highlightMatcher = highlightPattern.matcher(content);
                        content = highlightMatcher.replaceAll("»$1«");
                    } catch (Exception e) {
                        // Caída segura en caso de fallo en regex
                        content = content.replace(pattern, "»" + pattern + "«");
                    }
                }

                formatted.append(indicator)
                        .append(formattedNum)
                        .append(" │ ")
                        .append(content)
                        .append("\n");
            } else {
                // Si la línea no coincide con el formato esperado, se añade con un margen para no romper la estética
                formatted.append("        ").append(line).append("\n");
            }
        }

        return formatted.toString();
    }

    /**
     * Generador de líneas divisorias compatible con cualquier versión de Java
     * (Java 8 a 21+). Evita el uso de String.repeat() para prevenir errores de
     * compilación en entornos antiguos.
     */
    private String generateDivider(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("─");
        }
        return sb.toString();
    }
}
