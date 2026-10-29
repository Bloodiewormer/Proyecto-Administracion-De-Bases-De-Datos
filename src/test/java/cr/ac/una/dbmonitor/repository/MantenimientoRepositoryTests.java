package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.TipoCompilacion;
import cr.ac.una.dbmonitor.dto.VistaMantenimiento;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MantenimientoRepositoryTests {
    private final ConexionOracleService conexion = mock(ConexionOracleService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MantenimientoRepository repository = new MantenimientoRepository(conexion);
    private final Connection conexionJdbc = mock(Connection.class);
    private final CallableStatement llamada = mock(CallableStatement.class);

    private void sinFilas() {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    }

    private void ejecutaBloques() throws SQLException {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(conexionJdbc.prepareCall(anyString())).thenReturn(llamada);
        doAnswer(inv -> inv.<ConnectionCallback<?>>getArgument(0).doInConnection(conexionJdbc))
                .when(jdbc).execute(any(ConnectionCallback.class));
    }

    private UncategorizedSQLException errorOracle(int codigo) {
        return new UncategorizedSQLException("operación", "BEGIN ... END;", new SQLException("ORA-" + codigo, "72000", codigo));
    }

    @Test
    void sinConexionNoEjecutaConsultas() {
        when(conexion.obtenerJdbcTemplate()).thenThrow(new IllegalStateException("privado"));
        var datos = repository.consultar(VistaMantenimiento.ESTADISTICAS, false, 50);
        assertNull(datos.tablas());
        assertTrue(datos.avisos().getFirst().contains("No hay conexión"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void porDefectoListaSoloTablasQueRequierenIntervencion() {
        sinFilas();
        var datos = repository.consultar(VistaMantenimiento.ESTADISTICAS, false, 50);
        verify(jdbc).query(argThat((String sql) -> sql.contains("FROM DBA_TAB_STATISTICS S")
                && sql.contains("S.STALE_STATS = 'YES' OR S.LAST_ANALYZED IS NULL")
                && sql.contains("U.ORACLE_MAINTAINED = 'N'")), any(RowMapper.class), eq(50));
        assertTrue(datos.tablas().filas().isEmpty());
        assertNull(datos.invalidos());
        assertTrue(datos.avisos().isEmpty());
    }

    @Test
    void puedeListarTodasLasTablas() {
        sinFilas();
        repository.consultar(VistaMantenimiento.ESTADISTICAS, true, 30);
        verify(jdbc).query(argThat((String sql) -> sql.contains("FROM DBA_TAB_STATISTICS S")
                && !sql.contains("OR S.LAST_ANALYZED IS NULL")), any(RowMapper.class), eq(30));
    }

    @Test
    void marcaQueObjetosAdmitenRecompilacionIndividual() throws Exception {
        sinFilas();
        var rs = mock(ResultSet.class);
        when(rs.getString("OWNER")).thenReturn("VENTAS");
        when(rs.getString("OBJECT_NAME")).thenReturn("P_FACTURAR", "CLASE_JAVA");
        when(rs.getString("OBJECT_TYPE")).thenReturn("PROCEDURE", "JAVA CLASS");
        when(rs.getInt("ERRORES")).thenReturn(1);
        doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0), mapper.mapRow(rs, 1));
        }).when(jdbc).query(contains("WHERE O.STATUS = 'INVALID'\nORDER BY"), any(RowMapper.class), any(Object[].class));
        var invalidos = repository.consultar(VistaMantenimiento.RECOMPILACION, false, 50).invalidos().filas();
        assertTrue(invalidos.get(0).recompilable());
        assertFalse(invalidos.get(1).recompilable());
    }

    @ParameterizedTest
    @CsvSource({"PACKAGE_BODY,ALTER PACKAGE,COMPILE BODY", "VIEW,ALTER VIEW,COMPILE", "TYPE_BODY,ALTER TYPE,COMPILE BODY"})
    void laSentenciaDeRecompilacionSoloConcatenaPalabrasClaveFijas(TipoCompilacion tipo, String sentencia,
                                                                   String clausula) {
        var sql = MantenimientoRepository.sentenciaRecompilar(tipo);
        assertEquals("BEGIN EXECUTE IMMEDIATE '" + sentencia + " ' || DBMS_ASSERT.ENQUOTE_NAME(?, FALSE) || '.' "
                + "|| DBMS_ASSERT.ENQUOTE_NAME(?, FALSE) || ' " + clausula + "'; END;", sql);
    }

    @Test
    void recompilaConParametrosEnlazadosYTiempoAmpliado() throws Exception {
        ejecutaBloques();
        assertTrue(repository.recompilarObjeto(TipoCompilacion.PROCEDURE, "VENTAS", "P_FACTURAR"));
        verify(conexionJdbc).setNetworkTimeout(any(Executor.class), eq(MantenimientoRepository.TIEMPO_MAXIMO_SEGUNDOS * 1000));
        verify(conexionJdbc).prepareCall(startsWith("BEGIN EXECUTE IMMEDIATE 'ALTER PROCEDURE '"));
        verify(llamada).setQueryTimeout(MantenimientoRepository.TIEMPO_MAXIMO_SEGUNDOS);
        verify(llamada).setObject(1, "VENTAS");
        verify(llamada).setObject(2, "P_FACTURAR");
        verify(llamada).execute();
        verify(llamada).close();
    }

    @Test
    void compilacionConErroresNoSeTrataComoFallo() throws Exception {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.execute(any(ConnectionCallback.class))).thenThrow(errorOracle(24344));
        assertFalse(repository.recompilarObjeto(TipoCompilacion.VIEW, "VENTAS", "V_RESUMEN"));
    }

    @Test
    void otrosErroresDeRecompilacionSePropagan() {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.execute(any(ConnectionCallback.class))).thenThrow(errorOracle(1031));
        assertThrows(UncategorizedSQLException.class,
                () -> repository.recompilarObjeto(TipoCompilacion.VIEW, "VENTAS", "V_RESUMEN"));
    }

    @Test
    void recalculaEstadisticasConDbmsStatsYNombresValidados() throws Exception {
        ejecutaBloques();
        repository.recalcularEstadisticasTabla("VENTAS", "FACTURAS");
        verify(conexionJdbc).prepareCall(argThat(sql -> sql.contains("DBMS_STATS.GATHER_TABLE_STATS")
                && sql.contains("DBMS_ASSERT.ENQUOTE_NAME(?, FALSE)") && !sql.contains("FACTURAS")));
        verify(llamada).setObject(1, "VENTAS");
        verify(llamada).setObject(2, "FACTURAS");
    }

    @ParameterizedTest
    @CsvSource({"20005,bloqueadas", "1031,privilegios", "20000,ANALYZE ANY", "4021,otra sesión", "1013,tiempo máximo"})
    void traduceLosErroresDeMantenimiento(int codigo, String fragmento) {
        assertTrue(MantenimientoRepository.mensaje(errorOracle(codigo)).contains(fragmento));
    }

    @Test
    void elTiempoAgotadoSeInformaConElLimite() {
        var mensaje = MantenimientoRepository.mensaje(new QueryTimeoutException("lento"));
        assertTrue(mensaje.contains(MantenimientoRepository.TIEMPO_MAXIMO_SEGUNDOS + " segundos"));
    }
}
