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
    private static final String INSTANCIA_CON_EDICION = "SELECT HOST_NAME, INSTANCE_NAME, VERSION_FULL, EDITION, "
            + "DATABASE_TYPE, STATUS, STARTUP_TIME, SYSDATE AS FECHA_CONSULTA FROM V$INSTANCE";
    private static final String INSTANCIA_COMPATIBLE = "SELECT HOST_NAME, INSTANCE_NAME, VERSION, STATUS, "
            + "STARTUP_TIME, SYSDATE AS FECHA_CONSULTA FROM V$INSTANCE";
    private static final String COMPONENTES_SGA = "SELECT NAME, VALUE, CON_ID FROM V$SGA";
    private static final String USO_MEMORIA = "SELECT "
            + "(SELECT SUM(BYTES) FROM V$SGASTAT WHERE NAME = 'free memory') AS SGA_LIBRE, "
            + "(SELECT MAX(VALUE) FROM V$PGASTAT WHERE NAME = 'total PGA allocated') AS PGA_ASIGNADA, "
            + "(SELECT MAX(VALUE) FROM V$PGASTAT WHERE NAME = 'total PGA inuse') AS PGA_UTILIZADA FROM DUAL";
    private static final String BASE_DATOS = "SELECT NAME, OPEN_MODE, CDB FROM V$DATABASE";
    private static final String CONTENEDORES = "SELECT CON_ID, NAME, OPEN_MODE, "
            + "CASE WHEN CON_ID = TO_NUMBER(SYS_CONTEXT('USERENV', 'CON_ID')) THEN 1 ELSE 0 END AS ACTUAL "
            + "FROM V$CONTAINERS ORDER BY CON_ID";
    private static final String VISTA_USO_MEMORIA = "V$SGASTAT/V$PGASTAT";

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
        var instancia = unica(leer("V$INSTANCE", () -> consultarInstancia(jdbc, avisos), avisos),
                "V$INSTANCE", avisos);
        var componentes = leer("V$SGA", () -> jdbc.query(COMPONENTES_SGA,
                (rs, row) -> new MemoriaInstanciaDto.Componente(rs.getString("NAME"),
                        rs.getBigDecimal("VALUE"), rs.getObject("CON_ID", Integer.class))), avisos);
        var uso = unica(leer(VISTA_USO_MEMORIA, () -> jdbc.query(USO_MEMORIA,
                (rs, row) -> new MemoriaInstanciaDto.Uso(rs.getBigDecimal("SGA_LIBRE"), null,
                        rs.getBigDecimal("PGA_ASIGNADA"), rs.getBigDecimal("PGA_UTILIZADA"))), avisos),
                VISTA_USO_MEMORIA, avisos);
        var base = unica(leer("V$DATABASE", () -> jdbc.query(BASE_DATOS,
                (rs, row) -> new BaseDatosDto.Base(rs.getString("NAME"), rs.getString("OPEN_MODE"),
                        rs.getString("CDB"))), avisos), "V$DATABASE", avisos);
        var contenedores = leer("V$CONTAINERS", () -> jdbc.query(CONTENEDORES,
                (rs, row) -> new BaseDatosDto.Contenedor(rs.getObject("CON_ID", Integer.class),
                        rs.getString("NAME"), rs.getString("OPEN_MODE"), rs.getInt("ACTUAL") == 1)), avisos);
        return new EstadoInstanciaDto(instancia, null, memoria(componentes, uso),
                new BaseDatosDto(base, contenedores), avisos);
    }

    private MemoriaInstanciaDto memoria(List<MemoriaInstanciaDto.Componente> componentes,
                                        MemoriaInstanciaDto.Uso uso) {
        if (componentes == null && uso == null) {
            return null;
        }
        return new MemoriaInstanciaDto(componentes == null ? List.of() : componentes, null, uso);
    }

    private List<EstadoInstanciaDto.Instancia> consultarInstancia(JdbcTemplate jdbc, List<String> avisos) {
        try {
            return jdbc.query(INSTANCIA_CON_EDICION, (rs, row) -> new EstadoInstanciaDto.Instancia(
                    rs.getString("HOST_NAME"), rs.getString("INSTANCE_NAME"), rs.getString("VERSION_FULL"),
                    rs.getString("EDITION"), rs.getString("DATABASE_TYPE"), rs.getString("STATUS"),
                    fecha(rs, "STARTUP_TIME"), fecha(rs, "FECHA_CONSULTA")));
        } catch (DataAccessException ex) {
            if (!columnaInexistente(ex)) {
                throw ex;
            }
            avisos.add("V$INSTANCE: esta versión de Oracle no expone la edición ni la versión completa; "
                    + "se muestra la versión básica.");
            return jdbc.query(INSTANCIA_COMPATIBLE, (rs, row) -> new EstadoInstanciaDto.Instancia(
                    rs.getString("HOST_NAME"), rs.getString("INSTANCE_NAME"), rs.getString("VERSION"),
                    null, null, rs.getString("STATUS"),
                    fecha(rs, "STARTUP_TIME"), fecha(rs, "FECHA_CONSULTA")));
        }
    }

    private boolean columnaInexistente(DataAccessException error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getErrorCode() == 904) {
                return true;
            }
        }
        return false;
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
