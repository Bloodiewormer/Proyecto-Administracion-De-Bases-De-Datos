package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.BaseDatosDto;
import cr.ac.una.dbmonitor.dto.EstadoInstanciaDto;
import cr.ac.una.dbmonitor.dto.MemoriaInstanciaDto;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Repository
public class EstadoInstanciaRepository {
    private final ConexionOracleService conexion;

    public EstadoInstanciaRepository(ConexionOracleService conexion) {
        this.conexion = conexion;
    }

    public EstadoInstanciaDto consultar() {
        JdbcTemplate jdbc;
        try {
            jdbc = conexion.obtenerJdbcTemplate();
        } catch (IllegalStateException ex) {
            return new EstadoInstanciaDto(null, null, null, null,
                    List.of("No hay conexión configurada. Conecte una instancia Oracle."));
        }
        var avisos = new ArrayList<String>();
        var instancia = unica(leer("V$INSTANCE", () -> jdbc.query(
                "SELECT HOST_NAME, INSTANCE_NAME, VERSION_FULL, STATUS, STARTUP_TIME, SYSDATE AS FECHA_CONSULTA FROM V$INSTANCE",
                (rs, row) -> new EstadoInstanciaDto.Instancia(rs.getString("HOST_NAME"),
                        rs.getString("INSTANCE_NAME"), rs.getString("VERSION_FULL"), rs.getString("STATUS"),
                        fecha(rs, "STARTUP_TIME"), fecha(rs, "FECHA_CONSULTA"))), avisos), "V$INSTANCE", avisos);
        var componentes = leer("V$SGA", () -> jdbc.query(
                "SELECT NAME, VALUE, CON_ID FROM V$SGA",
                (rs, row) -> new MemoriaInstanciaDto.Componente(rs.getString("NAME"),
                        rs.getBigDecimal("VALUE"), rs.getObject("CON_ID", Integer.class))), avisos);
        var base = unica(leer("V$DATABASE", () -> jdbc.query(
                "SELECT NAME, OPEN_MODE, CDB FROM V$DATABASE",
                (rs, row) -> new BaseDatosDto.Base(rs.getString("NAME"), rs.getString("OPEN_MODE"),
                        rs.getString("CDB"))), avisos), "V$DATABASE", avisos);
        var contenedor = unica(leer("V$CONTAINERS", () -> jdbc.query(
                "SELECT CON_ID, NAME, OPEN_MODE FROM V$CONTAINERS WHERE CON_ID = TO_NUMBER(SYS_CONTEXT('USERENV', 'CON_ID'))",
                (rs, row) -> new BaseDatosDto.Contenedor(rs.getObject("CON_ID", Integer.class),
                        rs.getString("NAME"), rs.getString("OPEN_MODE"))), avisos), "V$CONTAINERS", avisos);
        return new EstadoInstanciaDto(instancia, null,
                componentes == null ? null : new MemoriaInstanciaDto(componentes, null),
                new BaseDatosDto(base, contenedor), avisos);
    }

    private LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        var valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }

    private <T> List<T> leer(String vista, Supplier<List<T>> consulta, List<String> avisos) {
        try {
            var filas = consulta.get();
            if (filas.isEmpty()) {
                avisos.add(vista + ": información no disponible; la consulta no devolvió filas.");
            }
            return filas;
        } catch (DataAccessException ex) {
            avisos.add(vista + ": " + mensaje(ex));
            return null;
        }
    }

    private <T> T unica(List<T> filas, String vista, List<String> avisos) {
        if (filas == null || filas.isEmpty()) {
            return null;
        }
        if (filas.size() != 1) {
            avisos.add(vista + ": resultado ambiguo; se esperaba una sola fila.");
            return null;
        }
        return filas.getFirst();
    }

    private String mensaje(DataAccessException error) {
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
