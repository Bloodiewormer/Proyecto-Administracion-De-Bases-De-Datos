package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.ConsultasTopDto;
import cr.ac.una.dbmonitor.dto.OrdenConsultas;
import cr.ac.una.dbmonitor.dto.ProcesosDto;
import cr.ac.una.dbmonitor.dto.RendimientoDto;
import cr.ac.una.dbmonitor.dto.SesionesDto;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RendimientoRepository {
    private static final String CONSULTAS_TOP = "SELECT * FROM ("
            + "SELECT SQL_ID, SUBSTR(SQL_TEXT, 1, 300) AS TEXTO, EXECUTIONS, ELAPSED_TIME, CPU_TIME, "
            + "BUFFER_GETS, DISK_READS, ROWS_PROCESSED, LAST_ACTIVE_TIME "
            + "FROM V$SQLSTATS WHERE EXECUTIONS > 0 ORDER BY %s"
            + ") WHERE ROWNUM <= ?";
    private static final String RESUMEN_CONSULTAS = "SELECT SUM(EXECUTIONS) AS EJECUCIONES, "
            + "SUM(ELAPSED_TIME) AS TIEMPO_TOTAL, SUM(CPU_TIME) AS CPU_TOTAL, "
            + "SUM(BUFFER_GETS) AS LECTURAS_MEMORIA, SUM(DISK_READS) AS LECTURAS_DISCO "
            + "FROM V$SQLSTATS WHERE EXECUTIONS > 0";
    private static final String SESIONES = "SELECT SID, SERIAL# AS SERIAL_NUMBER, USERNAME, STATUS, OSUSER, "
            + "MACHINE, PROGRAM, LOGON_TIME, LAST_CALL_ET, SQL_ID, EVENT, STATE, BLOCKING_SESSION "
            + "FROM V$SESSION WHERE TYPE = 'USER' "
            + "ORDER BY DECODE(STATUS, 'ACTIVE', 0, 1), LAST_CALL_ET DESC";
    private static final String BLOQUEOS = "SELECT B.SID AS SID_BLOQUEADA, "
            + "B.SERIAL# AS SERIAL_BLOQUEADA, B.USERNAME AS USUARIO_BLOQUEADO, B.EVENT AS EVENTO, "
            + "B.SECONDS_IN_WAIT AS SEGUNDOS_ESPERA, B.SQL_ID AS SQL_BLOQUEADA, "
            + "T.SID AS SID_BLOQUEADORA, T.USERNAME AS USUARIO_BLOQUEADOR, "
            + "T.PROGRAM AS PROGRAMA_BLOQUEADOR, T.STATUS AS ESTADO_BLOQUEADOR "
            + "FROM V$SESSION B LEFT JOIN V$SESSION T ON T.SID = B.BLOCKING_SESSION "
            + "WHERE B.BLOCKING_SESSION IS NOT NULL ORDER BY B.SECONDS_IN_WAIT DESC";
    private static final String PROCESOS_MEMORIA = "SELECT * FROM ("
            + "SELECT P.PID, P.SPID, P.PNAME, P.PROGRAM, P.PGA_ALLOC_MEM, P.PGA_USED_MEM, P.PGA_MAX_MEM, "
            + "CASE WHEN P.BACKGROUND = '1' THEN 'BACKGROUND' ELSE 'USER' END AS TIPO, "
            + "S.SID, S.SERIAL# AS SERIAL_NUMBER, S.USERNAME, S.STATUS, S.MACHINE "
            + "FROM V$PROCESS P LEFT JOIN V$SESSION S ON S.PADDR = P.ADDR "
            + "WHERE P.PGA_ALLOC_MEM > 0 ORDER BY P.PGA_ALLOC_MEM DESC"
            + ") WHERE ROWNUM <= ?";
    private static final String SESIONES_CPU = "SELECT * FROM ("
            + "SELECT S.SID, S.SERIAL# AS SERIAL_NUMBER, S.USERNAME, S.PROGRAM, S.STATUS, S.TYPE AS TIPO, "
            + "ST.VALUE AS CPU_CENTISEGUNDOS "
            + "FROM V$SESSTAT ST JOIN V$STATNAME SN ON SN.STATISTIC# = ST.STATISTIC# "
            + "JOIN V$SESSION S ON S.SID = ST.SID "
            + "WHERE SN.NAME = 'CPU used by this session' AND ST.VALUE > 0 ORDER BY ST.VALUE DESC"
            + ") WHERE ROWNUM <= ?";
    private static final String VISTA_CONSULTAS = "V$SQLSTATS";
    private static final String VISTA_SESIONES = "V$SESSION";
    private static final String VISTA_PROCESOS = "V$PROCESS";
    private static final String VISTA_CPU = "V$SESSTAT";

    private final ConexionOracleService conexion;

    public RendimientoRepository(ConexionOracleService conexion) {
        this.conexion = conexion;
    }

    public RendimientoDto consultar(OrdenConsultas orden, int top) {
        JdbcTemplate jdbc;
        try {
            jdbc = conexion.obtenerJdbcTemplate();
        } catch (IllegalStateException ex) {
            return new RendimientoDto(null, null, null, orden, top,
                    List.of("No hay conexión configurada. Conecte una instancia Oracle."));
        }
        var avisos = new ArrayList<String>();
        var consultas = ConsultasOracle.leerTolerandoVacio(VISTA_CONSULTAS,
                () -> jdbc.query(CONSULTAS_TOP.formatted(expresionOrden(orden)),
                        (rs, row) -> new ConsultasTopDto.Consulta(rs.getString("SQL_ID"),
                                rs.getString("TEXTO"), rs.getBigDecimal("EXECUTIONS"),
                                rs.getBigDecimal("ELAPSED_TIME"), null, rs.getBigDecimal("CPU_TIME"),
                                rs.getBigDecimal("BUFFER_GETS"), rs.getBigDecimal("DISK_READS"),
                                rs.getBigDecimal("ROWS_PROCESSED"), fecha(rs, "LAST_ACTIVE_TIME")), top),
                avisos);
        var resumen = ConsultasOracle.unica(ConsultasOracle.leerTolerandoVacio(VISTA_CONSULTAS,
                () -> jdbc.query(RESUMEN_CONSULTAS,
                        (rs, row) -> new ConsultasTopDto.Resumen(rs.getBigDecimal("EJECUCIONES"),
                                rs.getBigDecimal("TIEMPO_TOTAL"), null, rs.getBigDecimal("CPU_TOTAL"),
                                rs.getBigDecimal("LECTURAS_MEMORIA"), rs.getBigDecimal("LECTURAS_DISCO"))),
                avisos), VISTA_CONSULTAS, avisos);
        var sesiones = ConsultasOracle.leerTolerandoVacio(VISTA_SESIONES,
                () -> jdbc.query(SESIONES, (rs, row) -> new SesionesDto.Sesion(
                        entero(rs, "SID"), entero(rs, "SERIAL_NUMBER"), rs.getString("USERNAME"),
                        rs.getString("STATUS"), rs.getString("OSUSER"), rs.getString("MACHINE"),
                        rs.getString("PROGRAM"), fecha(rs, "LOGON_TIME"), rs.getBigDecimal("LAST_CALL_ET"),
                        rs.getString("SQL_ID"), rs.getString("EVENT"), rs.getString("STATE"),
                        entero(rs, "BLOCKING_SESSION"))), avisos);
        var bloqueos = ConsultasOracle.leerTolerandoVacio(VISTA_SESIONES,
                () -> jdbc.query(BLOQUEOS, (rs, row) -> new SesionesDto.Bloqueo(
                        entero(rs, "SID_BLOQUEADA"), entero(rs, "SERIAL_BLOQUEADA"),
                        rs.getString("USUARIO_BLOQUEADO"), rs.getString("EVENTO"),
                        rs.getBigDecimal("SEGUNDOS_ESPERA"), rs.getString("SQL_BLOQUEADA"),
                        entero(rs, "SID_BLOQUEADORA"), rs.getString("USUARIO_BLOQUEADOR"),
                        rs.getString("PROGRAMA_BLOQUEADOR"), rs.getString("ESTADO_BLOQUEADOR"))), avisos);
        var procesos = ConsultasOracle.leerTolerandoVacio(VISTA_PROCESOS,
                () -> jdbc.query(PROCESOS_MEMORIA, (rs, row) -> new ProcesosDto.ProcesoMemoria(
                        entero(rs, "PID"), rs.getString("SPID"), rs.getString("PNAME"),
                        rs.getString("PROGRAM"), rs.getString("TIPO"), rs.getBigDecimal("PGA_ALLOC_MEM"),
                        rs.getBigDecimal("PGA_USED_MEM"), rs.getBigDecimal("PGA_MAX_MEM"),
                        entero(rs, "SID"), entero(rs, "SERIAL_NUMBER"), rs.getString("USERNAME"),
                        rs.getString("STATUS"), rs.getString("MACHINE")), top), avisos);
        var cpu = ConsultasOracle.leerTolerandoVacio(VISTA_CPU,
                () -> jdbc.query(SESIONES_CPU, (rs, row) -> new ProcesosDto.SesionCpu(
                        entero(rs, "SID"), entero(rs, "SERIAL_NUMBER"), rs.getString("USERNAME"),
                        rs.getString("PROGRAM"), rs.getString("STATUS"), rs.getString("TIPO"),
                        rs.getBigDecimal("CPU_CENTISEGUNDOS")), top), avisos);
        return new RendimientoDto(new ConsultasTopDto(resumen, consultas),
                new SesionesDto(sesiones, bloqueos, null, null),
                new ProcesosDto(procesos, cpu), orden, top, avisos);
    }

    private String expresionOrden(OrdenConsultas orden) {
        return switch (orden) {
            case TIEMPO_TOTAL -> "ELAPSED_TIME DESC";
            case TIEMPO_PROMEDIO -> "ELAPSED_TIME / EXECUTIONS DESC";
            case CPU -> "CPU_TIME DESC";
            case LECTURAS_DISCO -> "DISK_READS DESC";
            case LECTURAS_MEMORIA -> "BUFFER_GETS DESC";
            case EJECUCIONES -> "EXECUTIONS DESC";
        };
    }

    private Integer entero(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, Integer.class);
    }

    private LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        var valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
