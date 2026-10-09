package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.RespaldoDto;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RespaldoRepository {
    private static final String COLUMNAS = "SESSION_KEY, START_TIME, END_TIME, INPUT_TYPE, STATUS, "
            + "OUTPUT_BYTES, ELAPSED_SECONDS, OUTPUT_DEVICE_TYPE";
    private static final String HISTORIAL = "SELECT * FROM (SELECT " + COLUMNAS
            + " FROM V$RMAN_BACKUP_JOB_DETAILS ORDER BY START_TIME DESC) WHERE ROWNUM <= ?";
    private static final String ULTIMO_EXITOSO = "SELECT * FROM (SELECT " + COLUMNAS
            + " FROM V$RMAN_BACKUP_JOB_DETAILS WHERE STATUS = 'COMPLETED' "
            + "ORDER BY END_TIME DESC) WHERE ROWNUM <= 1";
    private static final String MODO_LOG = "SELECT LOG_MODE FROM V$DATABASE";
    private static final String VISTA_RESPALDOS = "V$RMAN_BACKUP_JOB_DETAILS";
    private static final String VISTA_BASE = "V$DATABASE";

    private final ConexionOracleService conexion;

    public RespaldoRepository(ConexionOracleService conexion) {
        this.conexion = conexion;
    }

    public RespaldoDto consultar(int cantidad) {
        JdbcTemplate jdbc;
        try {
            jdbc = conexion.obtenerJdbcTemplate();
        } catch (IllegalStateException ex) {
            return new RespaldoDto(null, null, null, null, cantidad,
                    List.of("No hay conexión configurada. Conecte una instancia Oracle."));
        }
        var avisos = new ArrayList<String>();
        var historial = ConsultasOracle.leerTolerandoVacio(VISTA_RESPALDOS,
                () -> jdbc.query(HISTORIAL, (rs, row) -> respaldo(rs), cantidad), avisos);
        var ultimoExitoso = ConsultasOracle.unica(ConsultasOracle.leerTolerandoVacio(VISTA_RESPALDOS,
                () -> jdbc.query(ULTIMO_EXITOSO, (rs, row) -> respaldo(rs)), avisos), VISTA_RESPALDOS, avisos);
        var modoLog = ConsultasOracle.unica(ConsultasOracle.leerTolerandoVacio(VISTA_BASE,
                () -> jdbc.query(MODO_LOG, (rs, row) -> rs.getString("LOG_MODE")), avisos), VISTA_BASE, avisos);
        return new RespaldoDto(ultimoExitoso, historial, modoLog, null, cantidad, avisos);
    }

    private RespaldoDto.Respaldo respaldo(ResultSet rs) throws SQLException {
        return new RespaldoDto.Respaldo(rs.getString("SESSION_KEY"), fecha(rs, "START_TIME"),
                fecha(rs, "END_TIME"), rs.getString("INPUT_TYPE"), rs.getString("STATUS"),
                rs.getBigDecimal("OUTPUT_BYTES"), rs.getBigDecimal("ELAPSED_SECONDS"),
                rs.getString("OUTPUT_DEVICE_TYPE"));
    }

    private LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        var valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
