package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.AuditoriaDto;
import cr.ac.una.dbmonitor.dto.Listado;
import cr.ac.una.dbmonitor.dto.MantenimientoDto;
import cr.ac.una.dbmonitor.dto.TipoCompilacion;
import cr.ac.una.dbmonitor.dto.VistaMantenimiento;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.net.SocketTimeoutException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detección y ejecución de tareas de mantenimiento preventivo en Oracle.
 * Las acciones se limitan a esquemas creados por la organización (ORACLE_MAINTAINED = 'N'):
 * los objetos de Oracle se mantienen con sus propios procedimientos (utlrp.sql, GATHER_DICTIONARY_STATS).
 */
@Repository
public class MantenimientoRepository {
    // Recalcular estadísticas de un esquema grande puede tardar más que una consulta de monitoreo.
    public static final int TIEMPO_MAXIMO_SEGUNDOS = 120;

    static final String VISTA_CONTEXTO = "USERENV";
    static final String VISTA_RESUMEN = "DBA_TAB_STATISTICS/DBA_OBJECTS/DBA_ERRORS";
    static final String VISTA_TABLAS = "DBA_TAB_STATISTICS";
    static final String VISTA_ESQUEMAS = "DBA_USERS";
    static final String VISTA_OBJETOS = "DBA_OBJECTS";
    static final String VISTA_ERRORES = "DBA_ERRORS";

    private static final String ORGANIZACION = "JOIN DBA_USERS U ON U.USERNAME = %s AND U.ORACLE_MAINTAINED = 'N'";
    private static final String TABLAS_ORGANIZACION = """
            FROM DBA_TAB_STATISTICS S
            JOIN DBA_TABLES T ON T.OWNER = S.OWNER AND T.TABLE_NAME = S.TABLE_NAME AND T.DROPPED = 'NO'
            """ + ORGANIZACION.formatted("S.OWNER") + "\nWHERE S.OBJECT_TYPE = 'TABLE'";

    private static final String CONTEXTO = "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') AS CONTENEDOR, "
            + "SYS_CONTEXT('USERENV', 'CON_ID') AS ID, SYS_CONTEXT('USERENV', 'SESSION_USER') AS SESION FROM DUAL";

    private static final String RESUMEN = "SELECT "
            + "(SELECT COUNT(*) " + TABLAS_ORGANIZACION + " AND S.STALE_STATS = 'YES') AS DESACTUALIZADAS, "
            + "(SELECT COUNT(*) " + TABLAS_ORGANIZACION + " AND S.LAST_ANALYZED IS NULL) AS SIN_ESTADISTICAS, "
            + "(SELECT COUNT(*) FROM DBA_OBJECTS O " + ORGANIZACION.formatted("O.OWNER")
            + " WHERE O.STATUS = 'INVALID') AS INVALIDOS, "
            + "(SELECT COUNT(*) FROM DBA_ERRORS E " + ORGANIZACION.formatted("E.OWNER")
            + " WHERE E.ATTRIBUTE = 'ERROR') AS ERRORES "
            + "FROM DUAL";

    private static final String TABLAS = "SELECT S.OWNER, S.TABLE_NAME, S.NUM_ROWS, S.LAST_ANALYZED, S.STALE_STATS, "
            + "S.STATTYPE_LOCKED, COUNT(*) OVER () AS TOTAL " + TABLAS_ORGANIZACION;
    private static final String SOLO_PENDIENTES = " AND (S.STALE_STATS = 'YES' OR S.LAST_ANALYZED IS NULL)";
    private static final String ORDEN_TABLAS = " ORDER BY CASE WHEN S.LAST_ANALYZED IS NULL THEN 0 "
            + "WHEN S.STALE_STATS = 'YES' THEN 1 ELSE 2 END, S.OWNER, S.TABLE_NAME FETCH FIRST ? ROWS ONLY";

    private static final String ESQUEMAS = "SELECT USERNAME FROM DBA_USERS WHERE ORACLE_MAINTAINED = 'N' "
            + "ORDER BY USERNAME";

    private static final String INVALIDOS = """
            SELECT O.OWNER, O.OBJECT_NAME, O.OBJECT_TYPE, O.LAST_DDL_TIME,
                   (SELECT COUNT(*) FROM DBA_ERRORS E
                     WHERE E.OWNER = O.OWNER AND E.NAME = O.OBJECT_NAME AND E.TYPE = O.OBJECT_TYPE
                       AND E.ATTRIBUTE = 'ERROR') AS ERRORES,
                   COUNT(*) OVER () AS TOTAL
            FROM DBA_OBJECTS O
            """ + ORGANIZACION.formatted("O.OWNER") + """

            WHERE O.STATUS = 'INVALID'
            ORDER BY O.OWNER, O.OBJECT_TYPE, O.OBJECT_NAME
            FETCH FIRST ? ROWS ONLY""";

    private static final String ESQUEMAS_CON_INVALIDOS = "SELECT O.OWNER, COUNT(*) AS INVALIDOS FROM DBA_OBJECTS O "
            + ORGANIZACION.formatted("O.OWNER") + " WHERE O.STATUS = 'INVALID' GROUP BY O.OWNER ORDER BY O.OWNER";

    private static final String ERRORES = "SELECT E.OWNER, E.NAME, E.TYPE, E.LINE, E.POSITION, E.TEXT, E.ATTRIBUTE, "
            + "COUNT(*) OVER () AS TOTAL FROM DBA_ERRORS E " + ORGANIZACION.formatted("E.OWNER")
            + " ORDER BY E.OWNER, E.NAME, E.TYPE, E.SEQUENCE FETCH FIRST ? ROWS ONLY";

    private static final String TABLA_DE_ORGANIZACION = "SELECT COUNT(*) FROM DBA_TABLES T "
            + ORGANIZACION.formatted("T.OWNER") + " WHERE T.OWNER = ? AND T.TABLE_NAME = ? AND T.DROPPED = 'NO'";
    private static final String ESQUEMA_DE_ORGANIZACION = "SELECT COUNT(*) FROM DBA_USERS "
            + "WHERE USERNAME = ? AND ORACLE_MAINTAINED = 'N'";
    private static final String ESTADO_OBJETO = """
            SELECT O.STATUS,
                   (SELECT COUNT(*) FROM DBA_ERRORS E
                     WHERE E.OWNER = O.OWNER AND E.NAME = O.OBJECT_NAME AND E.TYPE = O.OBJECT_TYPE
                       AND E.ATTRIBUTE = 'ERROR') AS ERRORES,
                   (SELECT MIN(E.TEXT) KEEP (DENSE_RANK FIRST ORDER BY E.SEQUENCE) FROM DBA_ERRORS E
                     WHERE E.OWNER = O.OWNER AND E.NAME = O.OBJECT_NAME AND E.TYPE = O.OBJECT_TYPE
                       AND E.ATTRIBUTE = 'ERROR') AS PRIMER_ERROR
            FROM DBA_OBJECTS O
            """ + ORGANIZACION.formatted("O.OWNER") + """

            WHERE O.OWNER = ? AND O.OBJECT_NAME = ? AND O.OBJECT_TYPE = ?""";
    private static final String ESTADISTICAS_TABLA = "SELECT NUM_ROWS, LAST_ANALYZED FROM DBA_TAB_STATISTICS "
            + "WHERE OWNER = ? AND TABLE_NAME = ? AND OBJECT_TYPE = 'TABLE'";
    private static final String MOMENTO_ACTUAL = "SELECT SYSDATE AS AHORA FROM DUAL";
    private static final String TABLAS_ANALIZADAS = "SELECT COUNT(*) AS TOTAL, "
            + "COUNT(CASE WHEN S.LAST_ANALYZED >= ? THEN 1 END) AS ANALIZADAS " + TABLAS_ORGANIZACION
            + " AND S.OWNER = ?";
    private static final String INVALIDOS_DEL_ESQUEMA = "SELECT COUNT(*) FROM DBA_OBJECTS "
            + "WHERE OWNER = ? AND STATUS = 'INVALID'";

    // DBMS_ASSERT.ENQUOTE_NAME valida el identificador y lo entrecomilla: los nombres nunca se concatenan desde Java.
    private static final String RECALCULAR_TABLA = "BEGIN DBMS_STATS.GATHER_TABLE_STATS("
            + "ownname => DBMS_ASSERT.ENQUOTE_NAME(?, FALSE), tabname => DBMS_ASSERT.ENQUOTE_NAME(?, FALSE), "
            + "cascade => TRUE); END;";
    private static final String RECALCULAR_ESQUEMA = "BEGIN DBMS_STATS.GATHER_SCHEMA_STATS("
            + "ownname => DBMS_ASSERT.ENQUOTE_NAME(?, FALSE), cascade => TRUE); END;";
    private static final String RECOMPILAR_ESQUEMA = "BEGIN DBMS_UTILITY.COMPILE_SCHEMA("
            + "schema => ?, compile_all => FALSE); END;";

    private static final int ORA_COMPILACION_CON_ERRORES = 24344;

    private final ConexionOracleService conexion;

    public MantenimientoRepository(ConexionOracleService conexion) {
        this.conexion = conexion;
    }

    public record EstadoObjeto(String estado, int errores, String primerError) {}

    public record EstadisticasTabla(Long filas, LocalDateTime ultimoAnalisis) {}

    public record TablasAnalizadas(int total, int analizadas) {}

    public MantenimientoDto consultar(VistaMantenimiento vista, boolean todasLasTablas, int limite) {
        JdbcTemplate jdbc;
        try {
            jdbc = conexion.obtenerJdbcTemplate();
        } catch (IllegalStateException ex) {
            return new MantenimientoDto(vista, todasLasTablas, limite, null, null, null, null, null, null, null,
                    List.of("No hay conexión configurada. Conecte una instancia Oracle."));
        }
        var avisos = new ArrayList<String>();
        var contexto = ConsultasOracle.unica(ConsultasOracle.leerTolerandoVacio(VISTA_CONTEXTO,
                () -> jdbc.query(CONTEXTO, (rs, row) -> new AuditoriaDto.Contexto(rs.getString("CONTENEDOR"),
                        rs.getInt("ID") > 1, rs.getString("SESION"))), avisos), VISTA_CONTEXTO, avisos);
        var resumen = ConsultasOracle.unica(ConsultasOracle.leerTolerandoVacio(VISTA_RESUMEN,
                () -> jdbc.query(RESUMEN, (rs, row) -> new MantenimientoDto.Resumen(entero(rs, "DESACTUALIZADAS"),
                        entero(rs, "SIN_ESTADISTICAS"), entero(rs, "INVALIDOS"), entero(rs, "ERRORES"))), avisos),
                VISTA_RESUMEN, avisos);
        if (vista == VistaMantenimiento.ESTADISTICAS) {
            var sql = TABLAS + (todasLasTablas ? "" : SOLO_PENDIENTES) + ORDEN_TABLAS;
            var tablas = ConsultasOracle.listado(VISTA_TABLAS, jdbc, sql, new Object[]{limite},
                    (rs, row) -> new MantenimientoDto.TablaEstadisticas(rs.getString("OWNER"),
                            rs.getString("TABLE_NAME"), rs.getBigDecimal("NUM_ROWS"), fecha(rs, "LAST_ANALYZED"),
                            "YES".equals(rs.getString("STALE_STATS")), rs.getString("STATTYPE_LOCKED")), avisos);
            var esquemas = ConsultasOracle.leerTolerandoVacio(VISTA_ESQUEMAS,
                    () -> jdbc.query(ESQUEMAS, (rs, row) -> rs.getString("USERNAME")), avisos);
            return new MantenimientoDto(vista, todasLasTablas, limite, contexto, resumen, tablas, esquemas,
                    null, null, null, avisos);
        }
        var invalidos = ConsultasOracle.listado(VISTA_OBJETOS, jdbc, INVALIDOS, new Object[]{limite},
                (rs, row) -> {
                    var tipo = rs.getString("OBJECT_TYPE");
                    return new MantenimientoDto.ObjetoInvalido(rs.getString("OWNER"), rs.getString("OBJECT_NAME"),
                            tipo, fecha(rs, "LAST_DDL_TIME"), rs.getInt("ERRORES"),
                            TipoCompilacion.desde(tipo).isPresent());
                }, avisos);
        var esquemas = ConsultasOracle.leerTolerandoVacio(VISTA_OBJETOS, () -> jdbc.query(ESQUEMAS_CON_INVALIDOS,
                (rs, row) -> new MantenimientoDto.EsquemaConInvalidos(rs.getString("OWNER"),
                        rs.getInt("INVALIDOS"))), avisos);
        var errores = ConsultasOracle.listado(VISTA_ERRORES, jdbc, ERRORES, new Object[]{limite},
                (rs, row) -> new AuditoriaDto.ErrorCompilacion(rs.getString("OWNER"), rs.getString("NAME"),
                        rs.getString("TYPE"), rs.getInt("LINE"), rs.getInt("POSITION"), rs.getString("TEXT"),
                        rs.getString("ATTRIBUTE")), avisos);
        return new MantenimientoDto(vista, todasLasTablas, limite, contexto, resumen, null, null, invalidos,
                esquemas, errores, avisos);
    }

    public boolean esTablaDeOrganizacion(String esquema, String tabla) {
        return contar(TABLA_DE_ORGANIZACION, esquema, tabla) > 0;
    }

    public boolean esEsquemaDeOrganizacion(String esquema) {
        return contar(ESQUEMA_DE_ORGANIZACION, esquema) > 0;
    }

    /** Estado de un objeto de un esquema de la organización; vacío si no existe o pertenece a Oracle. */
    public Optional<EstadoObjeto> estadoObjeto(String esquema, String nombre, String tipo) {
        var filas = conexion.obtenerJdbcTemplate().query(ESTADO_OBJETO, (rs, row) -> new EstadoObjeto(
                rs.getString("STATUS"), rs.getInt("ERRORES"), rs.getString("PRIMER_ERROR")), esquema, nombre, tipo);
        return filas.stream().findFirst();
    }

    public Optional<EstadisticasTabla> estadisticasTabla(String esquema, String tabla) {
        var filas = conexion.obtenerJdbcTemplate().query(ESTADISTICAS_TABLA, (rs, row) -> new EstadisticasTabla(
                rs.getObject("NUM_ROWS", Long.class), fecha(rs, "LAST_ANALYZED")), esquema, tabla);
        return filas.stream().findFirst();
    }

    public LocalDateTime momentoActual() {
        return conexion.obtenerJdbcTemplate().queryForObject(MOMENTO_ACTUAL,
                (rs, row) -> fecha(rs, "AHORA"));
    }

    public TablasAnalizadas tablasAnalizadas(String esquema, LocalDateTime desde) {
        return conexion.obtenerJdbcTemplate().queryForObject(TABLAS_ANALIZADAS, (rs, row) -> new TablasAnalizadas(
                rs.getInt("TOTAL"), rs.getInt("ANALIZADAS")), Timestamp.valueOf(desde), esquema);
    }

    public int invalidosDelEsquema(String esquema) {
        return contar(INVALIDOS_DEL_ESQUEMA, esquema);
    }

    public void recalcularEstadisticasTabla(String esquema, String tabla) {
        ejecutar(RECALCULAR_TABLA, esquema, tabla);
    }

    public void recalcularEstadisticasEsquema(String esquema) {
        ejecutar(RECALCULAR_ESQUEMA, esquema);
    }

    public void recompilarEsquema(String esquema) {
        ejecutar(RECOMPILAR_ESQUEMA, esquema);
    }

    /**
     * Ejecuta ALTER ... COMPILE. Devuelve false si Oracle compiló el objeto con errores (ORA-24344);
     * cualquier otro error se propaga.
     */
    public boolean recompilarObjeto(TipoCompilacion tipo, String esquema, String nombre) {
        try {
            ejecutar(sentenciaRecompilar(tipo), esquema, nombre);
            return true;
        } catch (DataAccessException ex) {
            if (codigoOracle(ex) == ORA_COMPILACION_CON_ERRORES) {
                return false;
            }
            throw ex;
        }
    }

    static String sentenciaRecompilar(TipoCompilacion tipo) {
        return "BEGIN EXECUTE IMMEDIATE '" + tipo.sentencia() + " ' || DBMS_ASSERT.ENQUOTE_NAME(?, FALSE) || '.' "
                + "|| DBMS_ASSERT.ENQUOTE_NAME(?, FALSE) || ' " + tipo.clausula() + "'; END;";
    }

    /** Traduce el error de una operación de mantenimiento a un mensaje comprensible. */
    public static String mensaje(DataAccessException error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof QueryTimeoutException || causa instanceof SQLTimeoutException
                    || causa instanceof SocketTimeoutException) {
                return tiempoAgotado();
            }
            if (causa instanceof SQLException sql) {
                switch (sql.getErrorCode()) {
                    case 1013: return tiempoAgotado();
                    case 1031: return "La cuenta conectada no tiene privilegios para ejecutar esta operación (ORA-01031).";
                    case 20000: return "Oracle no pudo analizar el objeto: no existe o la cuenta no tiene "
                            + "privilegios suficientes, como ANALYZE ANY (ORA-20000).";
                    case 20005: return "Las estadísticas del objeto están bloqueadas (ORA-20005). Deben "
                            + "desbloquearse con DBMS_STATS.UNLOCK_TABLE_STATS antes de recalcularlas.";
                    case 4021: return "El objeto está siendo utilizado por otra sesión y no se pudo bloquear "
                            + "para recompilarlo (ORA-04021). Intente de nuevo más tarde.";
                    case 44003: return "Oracle rechazó el nombre del objeto por no ser un identificador válido "
                            + "(ORA-44003).";
                    default: break;
                }
            }
        }
        var texto = ConsultasOracle.mensaje(error);
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static String tiempoAgotado() {
        return "La operación superó el tiempo máximo de " + TIEMPO_MAXIMO_SEGUNDOS + " segundos.";
    }

    static int codigoOracle(DataAccessException error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getErrorCode() != 0) {
                return sql.getErrorCode();
            }
        }
        return 0;
    }

    /**
     * Ejecuta un bloque PL/SQL con parámetros enlazados. Amplía el tiempo de espera de la conexión solo durante
     * la operación; el pool restablece el valor original al devolver la conexión.
     */
    private void ejecutar(String bloque, Object... parametros) {
        conexion.obtenerJdbcTemplate().execute((ConnectionCallback<Void>) conexionJdbc -> {
            conexionJdbc.setNetworkTimeout(Runnable::run, TIEMPO_MAXIMO_SEGUNDOS * 1000);
            try (var llamada = conexionJdbc.prepareCall(bloque)) {
                llamada.setQueryTimeout(TIEMPO_MAXIMO_SEGUNDOS);
                for (int i = 0; i < parametros.length; i++) {
                    llamada.setObject(i + 1, parametros[i]);
                }
                llamada.execute();
            }
            return null;
        });
    }

    private int contar(String sql, Object... parametros) {
        var total = conexion.obtenerJdbcTemplate().queryForObject(sql, Integer.class, parametros);
        return total == null ? 0 : total;
    }

    private Integer entero(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, Integer.class);
    }

    private LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        var valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
