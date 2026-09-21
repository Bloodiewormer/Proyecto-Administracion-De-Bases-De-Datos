package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.ArchivoDatosDto;
import cr.ac.una.dbmonitor.dto.CrecimientoDto;
import cr.ac.una.dbmonitor.dto.FiltroTipoObjeto;
import cr.ac.una.dbmonitor.dto.ObjetoAlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.ResumenAlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.TablespaceDto;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AlmacenamientoRepository {

    private static final String TABLESPACES = """
            SELECT
                T.TABLESPACE_NAME,
                T.CONTENTS,
                T.STATUS,
                T.EXTENT_MANAGEMENT,
                T.SEGMENT_SPACE_MANAGEMENT,
                NVL(DF.BYTES_TOTAL, 0) AS BYTES_TOTAL,
                NVL(DF.MAX_BYTES, 0) AS BYTES_MAXIMO,
                CASE
                    WHEN T.CONTENTS = 'TEMPORARY' THEN NVL(TF.BYTES_USADOS, 0)
                    ELSE NVL(DF.BYTES_TOTAL, 0) - NVL(FS.BYTES_LIBRES, 0)
                END AS BYTES_USADOS,
                CASE
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
                GROUP BY TABLESPACE_NAME
            ) TF ON TF.TABLESPACE_NAME = T.TABLESPACE_NAME
            ORDER BY T.TABLESPACE_NAME
            """;

    private static final String RESUMEN_BD = """
            SELECT
                (SELECT NVL(SUM(BYTES), 0) FROM DBA_DATA_FILES) AS BYTES_DATAFILES,
                (SELECT NVL(SUM(BYTES), 0) FROM DBA_TEMP_FILES) AS BYTES_TEMPFILES,
                (SELECT NVL(SUM(BYTES * MEMBERS), NVL(SUM(BYTES), 0)) FROM V$LOG) AS BYTES_REDO,
                (SELECT NVL(SUM(BYTES), 0) FROM DBA_SEGMENTS) AS BYTES_USADOS,
                (SELECT COUNT(*) FROM DBA_TABLESPACES) AS CANTIDAD_TABLESPACES,
                (SELECT (SELECT COUNT(*) FROM DBA_DATA_FILES) + (SELECT COUNT(*) FROM DBA_TEMP_FILES) FROM DUAL) AS CANTIDAD_ARCHIVOS
            FROM DUAL
            """;

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
                    S.BEGIN_INTERVAL_TIME AS FECHA,
                    T.NAME AS TABLESPACE_NAME,
                    ROUND(H.TABLESPACE_USEDSIZE * TS.BLOCK_SIZE, 0) AS BYTES_USADOS,
                    ROUND(H.TABLESPACE_SIZE * TS.BLOCK_SIZE, 0) AS BYTES_TOTAL
                FROM DBA_HIST_TBSPC_SPACE_USAGE H
                JOIN DBA_HIST_SNAPSHOT S ON S.SNAP_ID = H.SNAP_ID AND S.DBID = H.DBID AND S.INSTANCE_NUMBER = H.INSTANCE_NUMBER
                JOIN V$TABLESPACE T ON T.TS# = H.TABLESPACE_ID
                JOIN DBA_TABLESPACES TS ON TS.TABLESPACE_NAME = T.NAME
                ORDER BY S.BEGIN_INTERVAL_TIME DESC
            ) WHERE ROWNUM <= 20
            """;

    private static final String VISTA_TABLESPACES = "DBA_TABLESPACES";
    private static final String VISTA_RESUMEN = "DBA_DATA_FILES/DBA_SEGMENTS/V$LOG";
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
            List<String> avisos
    ) {}

    public DatosAlmacenamientoRaw consultar(FiltroTipoObjeto filtro, int top) {
        JdbcTemplate jdbc;
        try {
            jdbc = conexion.obtenerJdbcTemplate();
        } catch (IllegalStateException ex) {
            return new DatosAlmacenamientoRaw(null, List.of(), List.of(), List.of(), List.of(),
                    List.of("No hay conexión configurada. Conecte una instancia Oracle."));
        }

        var avisos = new ArrayList<String>();

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
                () -> jdbc.query(RESUMEN_BD, (rs, row) -> new ResumenAlmacenamientoDto(
                        null,
                        rs.getBigDecimal("BYTES_USADOS"),
                        null,
                        null,
                        rs.getBigDecimal("BYTES_DATAFILES"),
                        rs.getBigDecimal("BYTES_TEMPFILES"),
                        rs.getBigDecimal("BYTES_REDO"),
                        entero(rs, "CANTIDAD_TABLESPACES"),
                        entero(rs, "CANTIDAD_ARCHIVOS"))), avisos), VISTA_RESUMEN, avisos);

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

        return new DatosAlmacenamientoRaw(resumen,
                tablespaces == null ? List.of() : tablespaces,
                archivos == null ? List.of() : archivos,
                objetos == null ? List.of() : objetos,
                historicoAwr == null ? List.of() : historicoAwr,
                avisos);
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
