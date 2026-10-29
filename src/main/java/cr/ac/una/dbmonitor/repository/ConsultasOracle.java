package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.Listado;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.function.Supplier;

public final class ConsultasOracle {
    private ConsultasOracle() {
    }

    public static <T> List<T> leer(String vista, Supplier<List<T>> consulta, List<String> avisos) {
        var filas = leerTolerandoVacio(vista, consulta, avisos);
        if (filas != null && filas.isEmpty()) {
            avisos.add(vista + ": información no disponible; la consulta no devolvió filas.");
        }
        return filas;
    }

    public static <T> List<T> leerTolerandoVacio(String vista, Supplier<List<T>> consulta, List<String> avisos) {
        try {
            return consulta.get();
        } catch (DataAccessException ex) {
            avisos.add(vista + ": " + mensaje(ex));
            return null;
        }
    }

    /**
     * Ejecuta una consulta limitada con FETCH FIRST cuyo SELECT incluye la columna TOTAL (COUNT(*) OVER ()),
     * calculada antes del límite. Devuelve null y registra un aviso si la consulta falla.
     */
    public static <T> Listado<T> listado(String vista, JdbcTemplate jdbc, String sql, Object[] parametros,
                                         RowMapper<T> mapper, List<String> avisos) {
        var total = new int[1];
        var filas = leerTolerandoVacio(vista, () -> jdbc.query(sql, (rs, row) -> {
            total[0] = rs.getInt("TOTAL");
            return mapper.mapRow(rs, row);
        }, parametros), avisos);
        if (filas == null) {
            return null;
        }
        return new Listado<>(filas, Math.max(total[0], filas.size()));
    }

    public static <T> T unica(List<T> filas, String vista, List<String> avisos) {
        if (filas == null || filas.isEmpty()) {
            return null;
        }
        if (filas.size() != 1) {
            avisos.add(vista + ": resultado ambiguo; se esperaba una sola fila.");
            return null;
        }
        return filas.getFirst();
    }

    public static boolean columnaInexistente(DataAccessException error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getErrorCode() == 904) {
                return true;
            }
        }
        return false;
    }

    public static String mensaje(DataAccessException error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof QueryTimeoutException || causa instanceof SQLTimeoutException
                    || causa instanceof SocketTimeoutException) {
                return "se agotó el tiempo de espera.";
            }
            if (causa instanceof SQLException sql) {
                switch (sql.getErrorCode()) {
                    case 1031: return "permisos insuficientes.";
                    case 942: return "vista no accesible o inexistente.";
                    case 904: return "columnas incompatibles con esta versión de Oracle.";
                    case 1017: return "credenciales inválidas.";
                    case 12170, 12535, 18714: return "se agotó el tiempo de espera.";
                    case 17002, 17008, 12514, 12541, 3113, 3114:
                        return "no fue posible consultar Oracle; revise la conexión y su disponibilidad.";
                    default: break;
                }
            }
        }
        return "no fue posible obtener la información por un error de acceso a Oracle.";
    }
}
