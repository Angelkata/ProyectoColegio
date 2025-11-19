package proyectocolegio.bd;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Sencilla clase de conexión a SQLite para la base colegio.db
 */
public class ConexionSQLite {

    private static final String URL = "jdbc:sqlite:colegio.db";

    private ConexionSQLite() {
    }

    public static Connection getConexion() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}
