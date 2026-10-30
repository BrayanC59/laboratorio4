import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {

    // Configuración: todo llega por variables de entorno
    static final String DB_URL = "jdbc:postgresql://"
            + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "5432") + "/" + env("DB_NAME", "tareas");
    static final String DB_USER = env("DB_USER", "tareas");
    static final String DB_PASSWORD = env("DB_PASSWORD", "tareas123");

    static String env(String nombre, String porDefecto) {
        return System.getenv().getOrDefault(nombre, porDefecto);
    }

    static Connection conexion() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static void main(String[] args) throws Exception {
        crearTabla();
        int puerto = Integer.parseInt(env("PORT", "8080"));
        HttpServer servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.createContext("/", App::manejar);
        servidor.start();
        System.out.println("API de tareas escuchando en el puerto " + puerto);
    }

    // Crea la tabla al arrancar. Reintenta porque la base puede tardar en estar lista.
    static void crearTabla() throws Exception {
        for (int intento = 1; intento <= 30; intento++) {
            try (Connection c = conexion(); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS tareas (id SERIAL PRIMARY KEY, titulo TEXT NOT NULL)");
                System.out.println("Conectado a " + DB_URL + ". Tabla 'tareas' lista.");
                return;
            } catch (SQLException e) {
                System.out.println("Esperando la base de datos (" + intento + "/30): " + e.getMessage());
                Thread.sleep(2000);
            }
        }
        throw new IllegalStateException("No fue posible conectar con la base de datos");
    }

    static void manejar(HttpExchange ex) throws IOException {
        String metodo = ex.getRequestMethod();
        String ruta = ex.getRequestURI().getPath();
        try {
            if (ruta.equals("/")) {
                responder(ex, 200, "{\"servicio\":\"tareas-api\",\"estado\":\"ok\"}");
            } else if (ruta.equals("/tareas")) {
                if (metodo.equals("GET")) listar(ex);
                else if (metodo.equals("POST")) crear(ex);
                else responder(ex, 405, error("metodo no permitido"));
            } else if (ruta.matches("/tareas/\\d+")) {
                int id = Integer.parseInt(ruta.substring("/tareas/".length()));
                if (metodo.equals("GET")) leer(ex, id);
                else if (metodo.equals("PUT")) actualizar(ex, id);
                else if (metodo.equals("DELETE")) borrar(ex, id);
                else responder(ex, 405, error("metodo no permitido"));
            } else {
                responder(ex, 404, error("ruta no encontrada"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            responder(ex, 500, error("error interno"));
        }
    }

    // CREATE
    static void crear(HttpExchange ex) throws Exception {
        String titulo = extraerTitulo(ex);
        if (titulo == null) { responder(ex, 400, error("falta el campo titulo")); return; }
        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement("INSERT INTO tareas (titulo) VALUES (?) RETURNING id")) {
            ps.setString(1, titulo);
            ResultSet rs = ps.executeQuery();
            rs.next();
            responder(ex, 201, json(rs.getInt(1), titulo));
        }
    }

    // READ (todas)
    static void listar(HttpExchange ex) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement("SELECT id, titulo FROM tareas ORDER BY id")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (sb.length() > 1) sb.append(",");
                sb.append(json(rs.getInt("id"), rs.getString("titulo")));
            }
        }
        responder(ex, 200, sb.append("]").toString());
    }

    // READ (una)
    static void leer(HttpExchange ex, int id) throws Exception {
        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement("SELECT titulo FROM tareas WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) responder(ex, 200, json(id, rs.getString("titulo")));
            else responder(ex, 404, error("tarea no encontrada"));
        }
    }

    // UPDATE
    static void actualizar(HttpExchange ex, int id) throws Exception {
        String titulo = extraerTitulo(ex);
        if (titulo == null) { responder(ex, 400, error("falta el campo titulo")); return; }
        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement("UPDATE tareas SET titulo = ? WHERE id = ?")) {
            ps.setString(1, titulo);
            ps.setInt(2, id);
            if (ps.executeUpdate() == 0) responder(ex, 404, error("tarea no encontrada"));
            else responder(ex, 200, json(id, titulo));
        }
    }

    // DELETE
    static void borrar(HttpExchange ex, int id) throws Exception {
        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement("DELETE FROM tareas WHERE id = ?")) {
            ps.setInt(1, id);
            if (ps.executeUpdate() == 0) responder(ex, 404, error("tarea no encontrada"));
            else responder(ex, 204, "");
        }
    }

    // --- utilidades ---
    static String extraerTitulo(HttpExchange ex) throws IOException {
        String cuerpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"titulo\"\\s*:\\s*\"([^\"]+)\"").matcher(cuerpo);
        return m.find() ? m.group(1) : null;
    }

    static String json(int id, String titulo) {
        String seguro = titulo.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"id\":" + id + ",\"titulo\":\"" + seguro + "\"}";
    }

    static String error(String mensaje) {
        return "{\"error\":\"" + mensaje + "\"}";
    }

    static void responder(HttpExchange ex, int codigo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (codigo == 204) { ex.sendResponseHeaders(204, -1); ex.close(); return; }
        ex.sendResponseHeaders(codigo, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
