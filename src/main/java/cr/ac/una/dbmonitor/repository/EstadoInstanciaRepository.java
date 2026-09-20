package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.BaseDatosDto;
import cr.ac.una.dbmonitor.dto.EstadoInstanciaDto;
import cr.ac.una.dbmonitor.dto.MemoriaInstanciaDto;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        var instancia = ConsultasOracle.unica(ConsultasOracle.leer("V$INSTANCE",
                () -> consultarInstancia(jdbc, avisos), avisos), "V$INSTANCE", avisos);
        var componentes = ConsultasOracle.leer("V$SGA", () -> jdbc.query(COMPONENTES_SGA,
                (rs, row) -> new MemoriaInstanciaDto.Componente(rs.getString("NAME"),
                        rs.getBigDecimal("VALUE"), rs.getObject("CON_ID", Integer.class))), avisos);
        var uso = ConsultasOracle.unica(ConsultasOracle.leer(VISTA_USO_MEMORIA, () -> jdbc.query(USO_MEMORIA,
                (rs, row) -> new MemoriaInstanciaDto.Uso(rs.getBigDecimal("SGA_LIBRE"), null,
                        rs.getBigDecimal("PGA_ASIGNADA"), rs.getBigDecimal("PGA_UTILIZADA"))), avisos),
                VISTA_USO_MEMORIA, avisos);
        var base = ConsultasOracle.unica(ConsultasOracle.leer("V$DATABASE", () -> jdbc.query(BASE_DATOS,
                (rs, row) -> new BaseDatosDto.Base(rs.getString("NAME"), rs.getString("OPEN_MODE"),
                        rs.getString("CDB"))), avisos), "V$DATABASE", avisos);
        var contenedores = ConsultasOracle.leer("V$CONTAINERS", () -> jdbc.query(CONTENEDORES,
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
            if (!ConsultasOracle.columnaInexistente(ex)) {
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

    private LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        var valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }
}
