package cr.ac.una.dbmonitor.repository;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.dbmonitor.dto.ArchivoDatosDto;
import cr.ac.una.dbmonitor.dto.CrecimientoDto;
import cr.ac.una.dbmonitor.dto.FiltroTipoObjeto;
import cr.ac.una.dbmonitor.dto.ObjetoAlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.ResumenAlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.TablespaceDto;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Repository
public class AlmacenamientoRepository {
    // Diagnóstico temporal de captura: no enviar Throwable sin redactar al logger.
    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoRepository.class);

    private static final String TABLESPACES = """
            SELECT
                T.TABLESPACE_NAME,
                T.CONTENTS,
                T.STATUS,
                T.EXTENT_MANAGEMENT,
                T.SEGMENT_SPACE_MANAGEMENT,
                DF.BYTES_TOTAL,
                DF.MAX_BYTES AS BYTES_MAXIMO,
                CASE
                    WHEN DF.BYTES_TOTAL IS NULL OR T.STATUS = 'OFFLINE' THEN NULL
                    WHEN T.CONTENTS = 'TEMPORARY' THEN NVL(TF.BYTES_USADOS, 0)
                    ELSE NVL(DF.BYTES_TOTAL, 0) - NVL(FS.BYTES_LIBRES, 0)
                END AS BYTES_USADOS,
                CASE
                    WHEN DF.BYTES_TOTAL IS NULL OR T.STATUS = 'OFFLINE' THEN NULL
                    WHEN T.CONTENTS = 'TEMPORARY' THEN NVL(DF.BYTES_TOTAL, 0) - NVL(TF.BYTES_USADOS, 0)
                    ELSE NVL(FS.BYTES_LIBRES, 0)
                END AS BYTES_LIBRES
            FROM DBA_TABLESPACES T
            LEFT JOIN (
                SELECT TABLESPACE_NAME,
                       SUM(BYTES) AS BYTES_TOTAL,
                       SUM(CASE WHEN AUTOEXTENSIBLE = 'YES' THEN GREATEST(BYTES, MAXBYTES) ELSE BYTES END) AS MAX_BYTES
                FROM DBA_DATA_FILES
                GROUP BY TABLESPACE_NAME
                UNION ALL
                SELECT TABLESPACE_NAME,
                       SUM(BYTES) AS BYTES_TOTAL,
                       SUM(CASE WHEN AUTOEXTENSIBLE = 'YES' THEN GREATEST(BYTES, MAXBYTES) ELSE BYTES END) AS MAX_BYTES
                FROM DBA_TEMP_FILES
                GROUP BY TABLESPACE_NAME
            ) DF ON DF.TABLESPACE_NAME = T.TABLESPACE_NAME
            LEFT JOIN (
                SELECT TABLESPACE_NAME, SUM(BYTES) AS BYTES_LIBRES
                FROM DBA_FREE_SPACE
                GROUP BY TABLESPACE_NAME
            ) FS ON FS.TABLESPACE_NAME = T.TABLESPACE_NAME
            LEFT JOIN (
                SELECT TABLESPACE_NAME, SUM(BYTES_USED) AS BYTES_USADOS
                FROM V$TEMP_SPACE_HEADER
                WHERE CON_ID = TO_NUMBER(SYS_CONTEXT('USERENV', 'CON_ID'))
                GROUP BY TABLESPACE_NAME
            ) TF ON TF.TABLESPACE_NAME = T.TABLESPACE_NAME
            ORDER BY T.TABLESPACE_NAME
            """;

    private static final String REDO_INSTANCIA = "SELECT SUM(BYTES * MEMBERS) AS BYTES_REDO FROM V$LOG";
    private static final String ORIGEN = """
            SELECT D.DBID, C.DBID AS CON_DBID, C.CON_ID, I.INSTANCE_NAME,
                   SYS_CONTEXT('USERENV', 'SERVICE_NAME') AS SERVICE_NAME, C.NAME AS CON_NAME
            FROM V$DATABASE D CROSS JOIN V$INSTANCE I CROSS JOIN V$CONTAINERS C
            WHERE C.CON_ID = TO_NUMBER(SYS_CONTEXT('USERENV', 'CON_ID'))
            """;
    private static final String COLUMNAS_HISTORIAL = "H.ID, H.FECHA, H.DBID, H.CON_DBID, H.CON_ID, "
            + "H.INSTANCE_NAME, H.SERVICE_NAME, H.CON_NAME, H.BYTES_TOTAL, H.BYTES_USADOS, H.BYTES_LIBRES";
    private static final String HISTORIAL_BASE = "SELECT " + COLUMNAS_HISTORIAL + """
             FROM V_CRECIMIENTO_ANALITICO H
            WHERE H.DBID = (SELECT DBID FROM V$DATABASE)
              AND H.CON_DBID = (SELECT DBID FROM V$CONTAINERS
                               WHERE CON_ID = TO_NUMBER(SYS_CONTEXT('USERENV', 'CON_ID')))
              AND H.CON_ID = TO_NUMBER(SYS_CONTEXT('USERENV', 'CON_ID'))
              AND H.ALCANCE = 'DATAFILES_TEMPFILES_V1'
            """;
    private static final String HISTORIAL = HISTORIAL_BASE + " ORDER BY H.FECHA DESC, H.ID DESC FETCH FIRST 200 ROWS ONLY";
    private static final String CONFIRMAR_MEDICION = HISTORIAL_BASE + " AND H.ID = ?";

    private static final String ARCHIVOS_DATOS = """
            SELECT
                FILE_ID,
                FILE_NAME,
                TABLESPACE_NAME,
                BYTES,
                MAXBYTES,
                AUTOEXTENSIBLE,
                INCREMENT_BY,
                STATUS,
                'DATA' AS TIPO_ARCHIVO
            FROM DBA_DATA_FILES
            UNION ALL
            SELECT
                FILE_ID,
                FILE_NAME,
                TABLESPACE_NAME,
                BYTES,
                MAXBYTES,
                AUTOEXTENSIBLE,
                INCREMENT_BY,
                STATUS,
                'TEMP' AS TIPO_ARCHIVO
            FROM DBA_TEMP_FILES
            ORDER BY TABLESPACE_NAME, FILE_NAME
            """;

    private static final String OBJETOS_TOP = """
            SELECT * FROM (
                SELECT
                    OWNER,
                    SEGMENT_NAME,
                    PARTITION_NAME,
                    SEGMENT_TYPE,
                    TABLESPACE_NAME,
                    BYTES,
                    BLOCKS,
                    EXTENTS
                FROM DBA_SEGMENTS
                WHERE 1 = 1 %s
                ORDER BY BYTES DESC
            ) WHERE ROWNUM <= ?
            """;

    private static final String HISTORICO_AWR = """
            SELECT * FROM (
                SELECT
                    S.FECHA,
                    T.NAME AS TABLESPACE_NAME,
                    ROUND(H.TABLESPACE_USEDSIZE * TS.BLOCK_SIZE, 0) AS BYTES_USADOS,
                    ROUND(H.TABLESPACE_SIZE * TS.BLOCK_SIZE, 0) AS BYTES_TOTAL
                FROM DBA_HIST_TBSPC_SPACE_USAGE H
                JOIN (SELECT DBID, SNAP_ID, MAX(END_INTERVAL_TIME) AS FECHA
                      FROM DBA_HIST_SNAPSHOT GROUP BY DBID, SNAP_ID) S
                  ON S.SNAP_ID = H.SNAP_ID AND S.DBID = H.DBID
                JOIN V$TABLESPACE T ON T.TS# = H.TABLESPACE_ID AND T.CON_ID = H.CON_ID
                JOIN DBA_TABLESPACES TS ON TS.TABLESPACE_NAME = T.NAME
                JOIN V$CONTAINERS C ON C.CON_ID = H.CON_ID
                WHERE H.DBID = (SELECT DBID FROM V$DATABASE)
                  AND H.CON_ID = TO_NUMBER(SYS_CONTEXT('USERENV', 'CON_ID'))
                  AND (H.CON_DBID = C.DBID OR (C.CON_ID <= 1 AND NVL(H.CON_DBID, 0) = 0))
                ORDER BY S.FECHA DESC, T.NAME
            ) WHERE ROWNUM <= 20
            """;

    private static final String VISTA_TABLESPACES = "DBA_TABLESPACES";
    private static final String VISTA_RESUMEN = "V$LOG";
    private static final String VISTA_ARCHIVOS = "DBA_DATA_FILES/DBA_TEMP_FILES";
    private static final String VISTA_SEGMENTOS = "DBA_SEGMENTS";
    private static final String VISTA_AWR = "DBA_HIST_TBSPC_SPACE_USAGE";

    private final ConexionOracleService conexion;

    public AlmacenamientoRepository(ConexionOracleService conexion) {
        this.conexion = conexion;
    }

    public record DatosAlmacenamientoRaw(
            ResumenAlmacenamientoDto resumen,
            List<TablespaceDto> tablespaces,
            List<ArchivoDatosDto> archivos,
            List<ObjetoAlmacenamientoDto> objetos,
            List<CrecimientoDto.HistoricoAwr> historicoAwr,
            List<String> avisos,
            CrecimientoDto.Origen origen,
            List<CrecimientoDto.Registro> registros
    ) {}

    public DatosAlmacenamientoRaw consultar(FiltroTipoObjeto filtro, int top) {
        JdbcTemplate jdbc;
        try {
            jdbc = conexion.obtenerJdbcTemplate();
        } catch (IllegalStateException ex) {
            return new DatosAlmacenamientoRaw(null, List.of(), List.of(), List.of(), List.of(),
                    List.of("No hay conexión configurada. Conecte una instancia Oracle."), null, List.of());
        }

        var avisos = new ArrayList<String>();
        var origen = ConsultasOracle.unica(ConsultasOracle.leerTolerandoVacio("Identidad Oracle",
                () -> jdbc.query(ORIGEN, (rs, row) -> origen(rs)), avisos), "Identidad Oracle", avisos);

        var tablespaces = ConsultasOracle.leerTolerandoVacio(VISTA_TABLESPACES,
                () -> jdbc.query(TABLESPACES, (rs, row) -> new TablespaceDto(
                        rs.getString("TABLESPACE_NAME"),
                        rs.getString("CONTENTS"),
                        rs.getString("STATUS"),
                        rs.getString("EXTENT_MANAGEMENT"),
                        rs.getString("SEGMENT_SPACE_MANAGEMENT"),
                        rs.getBigDecimal("BYTES_TOTAL"),
                        rs.getBigDecimal("BYTES_USADOS"),
                        rs.getBigDecimal("BYTES_LIBRES"),
                        rs.getBigDecimal("BYTES_MAXIMO"),
                        null)), avisos);

        var resumen = ConsultasOracle.unica(ConsultasOracle.leerTolerandoVacio(VISTA_RESUMEN,
                () -> jdbc.query(REDO_INSTANCIA, (rs, row) -> new ResumenAlmacenamientoDto(
                        null, null, null, null, null, null,
                        rs.getBigDecimal("BYTES_REDO"),
                        null, null)), avisos), VISTA_RESUMEN, avisos);

        var archivos = ConsultasOracle.leerTolerandoVacio(VISTA_ARCHIVOS,
                () -> jdbc.query(ARCHIVOS_DATOS, (rs, row) -> new ArchivoDatosDto(
                        entero(rs, "FILE_ID"),
                        rs.getString("FILE_NAME"),
                        rs.getString("TABLESPACE_NAME"),
                        rs.getBigDecimal("BYTES"),
                        rs.getBigDecimal("MAXBYTES"),
                        rs.getString("AUTOEXTENSIBLE"),
                        rs.getBigDecimal("INCREMENT_BY"),
                        rs.getString("STATUS"),
                        rs.getString("TIPO_ARCHIVO"))), avisos);

        var sqlObjetos = OBJETOS_TOP.formatted(clausulaFiltro(filtro));
        var objetos = ConsultasOracle.leerTolerandoVacio(VISTA_SEGMENTOS,
                () -> jdbc.query(sqlObjetos, (rs, row) -> new ObjetoAlmacenamientoDto(
                        rs.getString("OWNER"),
                        rs.getString("SEGMENT_NAME"),
                        rs.getString("PARTITION_NAME"),
                        rs.getString("SEGMENT_TYPE"),
                        rs.getString("TABLESPACE_NAME"),
                        rs.getBigDecimal("BYTES"),
                        rs.getBigDecimal("BLOCKS"),
                        rs.getBigDecimal("EXTENTS")), top), avisos);

        var historicoAwr = ConsultasOracle.leerTolerandoVacio(VISTA_AWR,
                () -> jdbc.query(HISTORICO_AWR, (rs, row) -> new CrecimientoDto.HistoricoAwr(
                        fecha(rs, "FECHA"),
                        rs.getString("TABLESPACE_NAME"),
                        rs.getBigDecimal("BYTES_USADOS"),
                        rs.getBigDecimal("BYTES_TOTAL"))), avisos);

        List<CrecimientoDto.Registro> registros = List.of();
        if (origen != null && origen.valida()) {
            registros = ConsultasOracle.leerTolerandoVacio("Historial Oracle",
                    () -> jdbc.query(HISTORIAL, (rs, row) -> registro(rs)), avisos);
        } else {
            avisos.add("Historial Oracle: no se pudo identificar la base y el contenedor; no se muestra historial.");
        }
        return new DatosAlmacenamientoRaw(resumen,
                tablespaces == null ? List.of() : tablespaces,
                archivos == null ? List.of() : archivos,
                objetos == null ? List.of() : objetos,
                historicoAwr == null ? List.of() : historicoAwr,
                avisos, origen, registros == null ? List.of() : registros);
    }

    /** Captura, verifica y confirma en la misma conexión. Nunca escribe un archivo local. */
    public CrecimientoDto.Registro registrarMedicion() {
        var fase = new AtomicReference<String>();
        var falloRegistrado = new AtomicBoolean();
        var sensibles = new ArrayList<String>();
        marcarFase(fase, "antes de ejecutar SP_REGISTRAR_CRECIMIENTO: obtener conexión e iniciar transacción");
        try {
            var jdbc = conexion.obtenerJdbcTemplate();
            if (jdbc.getDataSource() instanceof HikariDataSource origen) {
                if (origen.getUsername() != null && !origen.getUsername().isEmpty()) {
                    sensibles.add(origen.getUsername());
                }
                if (origen.getPassword() != null && !origen.getPassword().isEmpty()) {
                    sensibles.add(origen.getPassword());
                }
            }
            return jdbc.execute((ConnectionCallback<CrecimientoDto.Registro>) sesion -> {
                if (!sesion.getAutoCommit()) {
                    throw new SQLException("La captura requiere una conexión sin transacción previa.");
                }
                sesion.setAutoCommit(false);
                boolean transaccionFinalizada = false;
                try {
                    BigDecimal id;
                    marcarFase(fase, "antes de ejecutar SP_REGISTRAR_CRECIMIENTO");
                    try (var llamada = sesion.prepareCall("{call SP_REGISTRAR_CRECIMIENTO(?)}")) {
                        llamada.setQueryTimeout(5);
                        llamada.registerOutParameter(1, Types.NUMERIC);
                        llamada.execute();
                        marcarFase(fase, "después de ejecutar el procedimiento");
                        marcarFase(fase, "al obtener el ID OUT");
                        id = llamada.getBigDecimal(1);
                    }
                    if (id == null || id.signum() <= 0) {
                        throw new SQLException("Oracle no devolvió el identificador de la medición.");
                    }
                    CrecimientoDto.Registro guardado;
                    marcarFase(fase, "al consultar V_CRECIMIENTO_ANALITICO");
                    try (var consulta = sesion.prepareStatement(CONFIRMAR_MEDICION)) {
                        consulta.setQueryTimeout(5);
                        consulta.setBigDecimal(1, id);
                        try (var rs = consulta.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("No se encontró la medición de la base y contenedor actuales.");
                            }
                            marcarFase(fase, "al validar el registro");
                            guardado = registro(rs);
                            if (rs.next()) {
                                throw new SQLException("El identificador de medición no es único.");
                            }
                        }
                    }
                    marcarFase(fase, "al ejecutar COMMIT");
                    sesion.commit();
                    transaccionFinalizada = true;
                    return guardado;
                } catch (SQLException | RuntimeException ex) {
                    registrarDiagnostico(fase.get(), ex, sensibles);
                    falloRegistrado.set(true);
                    try {
                        marcarFase(fase, "al hacer ROLLBACK");
                        sesion.rollback();
                        transaccionFinalizada = true;
                    } catch (SQLException rollback) {
                        registrarDiagnostico(fase.get(), rollback, sensibles);
                        ex.addSuppressed(rollback);
                        try {
                            marcarFase(fase, "al descartar la conexión tras fallo de ROLLBACK");
                            sesion.abort(Runnable::run);
                        } catch (SQLException abortar) {
                            registrarDiagnostico(fase.get(), abortar, sensibles);
                            ex.addSuppressed(abortar);
                        }
                    }
                    throw ex;
                } finally {
                    if (transaccionFinalizada) {
                        marcarFase(fase, "al restaurar autocommit");
                        try {
                            sesion.setAutoCommit(true);
                        } catch (SQLException | RuntimeException ex) {
                            registrarDiagnostico(fase.get(), ex, sensibles);
                            falloRegistrado.set(true);
                            throw ex;
                        }
                    }
                }
            });
        } catch (RuntimeException ex) {
            if (!falloRegistrado.get()) {
                registrarDiagnostico(fase.get(), ex, sensibles);
            }
            throw ex;
        }
    }

    private void marcarFase(AtomicReference<String> fase, String descripcion) {
        fase.set(descripcion);
        log.info("[Diagnóstico captura almacenamiento] fase={}", descripcion);
    }

    /** Conserva stack trace, causas, excepciones suprimidas y errores JDBC encadenados, redactando secretos. */
    private void registrarDiagnostico(String fase, Throwable error, List<String> sensibles) {
        var texto = new StringWriter();
        error.printStackTrace(new PrintWriter(texto));
        var detalle = new StringBuilder(texto.toString());
        var visitadas = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var pendientes = new ArrayDeque<Throwable>();
        pendientes.add(error);
        while (!pendientes.isEmpty()) {
            var actual = pendientes.removeFirst();
            if (!visitadas.add(actual)) {
                continue;
            }
            if (actual instanceof SQLException sql) {
                detalle.append("\nSQLException: tipo=").append(sql.getClass().getName())
                        .append(", errorCode=").append(sql.getErrorCode())
                        .append(", SQLState=").append(sql.getSQLState())
                        .append(", mensaje Oracle=").append(sql.getMessage());
                if (sql.getNextException() != null) {
                    pendientes.add(sql.getNextException());
                }
            }
            if (actual.getCause() != null) {
                pendientes.add(actual.getCause());
            }
            Collections.addAll(pendientes, actual.getSuppressed());
        }
        var raiz = error;
        visitadas.clear();
        while (visitadas.add(raiz) && raiz.getCause() != null && !visitadas.contains(raiz.getCause())) {
            raiz = raiz.getCause();
        }
        detalle.append("\ncausa raíz: tipo=").append(raiz.getClass().getName())
                .append(", mensaje=").append(raiz.getMessage());
        log.error("[Diagnóstico captura almacenamiento] ERROR fase={}\n{}",
                fase, redactarDiagnostico(detalle.toString(), sensibles));
    }

    private String redactarDiagnostico(String texto, List<String> sensibles) {
        var seguro = texto
                .replaceAll("(?i)jdbc:[^\\r\\n]*", "[URL JDBC REDACTADA]")
                .replaceAll("(?i)\\b(password|passwd|pwd|contraseña|username|user|usuario|credentials|credenciales)"
                        + "\\s*(?:=>|[=:])\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;\\)\\]]+)", "$1=[REDACTADO]")
                .replaceAll("(?i)\\bIDENTIFIED\\s+BY\\s+(?:\"[^\"]*\"|'[^']*'|[^\\s;]+)",
                        "IDENTIFIED BY [REDACTADO]");
        for (var sensible : sensibles) {
            seguro = Pattern.compile(Pattern.quote(sensible), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(seguro).replaceAll("[REDACTADO]");
        }
        return seguro;
    }

    private CrecimientoDto.Origen origen(ResultSet rs) throws SQLException {
        var origen = new CrecimientoDto.Origen(rs.getBigDecimal("DBID"), rs.getBigDecimal("CON_DBID"),
                rs.getInt("CON_ID"), rs.getString("INSTANCE_NAME"), rs.getString("SERVICE_NAME"),
                rs.getString("CON_NAME"));
        if (!origen.valida()) {
            throw new SQLException("Identidad Oracle incompleta.");
        }
        return origen;
    }

    private CrecimientoDto.Registro registro(ResultSet rs) throws SQLException {
        var registro = new CrecimientoDto.Registro(rs.getBigDecimal("ID"), fecha(rs, "FECHA"), origen(rs),
                rs.getBigDecimal("BYTES_TOTAL"), rs.getBigDecimal("BYTES_USADOS"), rs.getBigDecimal("BYTES_LIBRES"));
        if (!registro.valido()) {
            throw new SQLException("La medición tiene valores incompletos o inconsistentes.");
        }
        return registro;
    }

    private String clausulaFiltro(FiltroTipoObjeto filtro) {
        if (filtro == null) {
            return "";
        }
        return switch (filtro) {
            case TABLA -> " AND (SEGMENT_TYPE LIKE 'TABLE%' OR SEGMENT_TYPE = 'NESTED TABLE')";
            case INDICE -> " AND SEGMENT_TYPE LIKE 'INDEX%'";
            case LOB -> " AND (SEGMENT_TYPE LIKE 'LOB%' OR SEGMENT_TYPE = 'LOBINDEX')";
            case TODOS -> "";
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
