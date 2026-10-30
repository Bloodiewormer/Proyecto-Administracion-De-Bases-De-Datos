package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.FiltroTipoObjeto;
import cr.ac.una.dbmonitor.dto.CrecimientoDto;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class AlmacenamientoRepositoryTests {

    private final ConexionOracleService conexion = mock(ConexionOracleService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AlmacenamientoRepository repository = new AlmacenamientoRepository(conexion);

    private void configurarMocksVacios() {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.query(contains("FROM V$DATABASE D CROSS JOIN V$INSTANCE I"), any(RowMapper.class)))
                .thenReturn(List.of(new CrecimientoDto.Origen(BigDecimal.ONE, BigDecimal.TEN,
                        3, "FREE", "PDB1", "PDB1")));
    }

    @Test
    void sinConexionNoEjecutaConsultas() {
        when(conexion.obtenerJdbcTemplate()).thenThrow(new IllegalStateException("Sin conexión"));

        var datos = repository.consultar(FiltroTipoObjeto.TODOS, 10);

        assertNull(datos.resumen());
        assertTrue(datos.tablespaces().isEmpty());
        assertTrue(datos.archivos().isEmpty());
        assertTrue(datos.objetos().isEmpty());
        assertFalse(datos.avisos().isEmpty());
        assertTrue(datos.avisos().getFirst().contains("No hay conexión"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void resultadosVaciosRetornanListasVaciasSinErrores() {
        configurarMocksVacios();

        var datos = repository.consultar(FiltroTipoObjeto.TODOS, 10);

        assertNotNull(datos);
        assertTrue(datos.tablespaces().isEmpty());
        assertTrue(datos.archivos().isEmpty());
        assertTrue(datos.objetos().isEmpty());
        assertTrue(datos.historicoAwr().isEmpty());
        assertTrue(datos.avisos().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(FiltroTipoObjeto.class)
    void filtrosDeObjetoEjecutanConsultas(FiltroTipoObjeto filtro) {
        configurarMocksVacios();

        var datos = repository.consultar(filtro, 20);

        assertNotNull(datos);
        verify(jdbc).query(contains("DBA_SEGMENTS"), any(RowMapper.class), eq(20));
    }

    @Test
    void errorEnConsultaRegistraAvisoSinLanzarExcepcion() {
        configurarMocksVacios();
        when(jdbc.query(contains("FROM DBA_TABLESPACES T"), any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("Error en vista"));

        var datos = repository.consultar(FiltroTipoObjeto.TODOS, 10);

        assertNotNull(datos);
        assertTrue(datos.tablespaces().isEmpty());
        assertFalse(datos.avisos().isEmpty());
        assertTrue(datos.avisos().stream().anyMatch(a -> a.contains("DBA_TABLESPACES")));
    }

    @Test
    void faltaDeAccesoAlHistorialNoInventaUnaFuenteAlternativa() {
        configurarMocksVacios();
        when(jdbc.query(contains("FROM V_CRECIMIENTO_ANALITICO H"), any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("Sin permisos"));
        var datos = repository.consultar(FiltroTipoObjeto.TODOS, 10);
        assertTrue(datos.registros().isEmpty());
        assertTrue(datos.avisos().stream().anyMatch(a -> a.startsWith("Historial Oracle:")));
    }

    @Test
    void awrFallaDeFormaIndependienteSinOcultarElHistorialOracle() {
        configurarMocksVacios();
        when(jdbc.query(contains("FROM DBA_HIST_TBSPC_SPACE_USAGE H"), any(RowMapper.class)))
                .thenThrow(new DataRetrievalFailureException("Sin acceso AWR"));
        var datos = repository.consultar(FiltroTipoObjeto.TODOS, 10);
        assertNotNull(datos.origen());
        assertTrue(datos.avisos().stream().anyMatch(a -> a.startsWith("DBA_HIST_TBSPC_SPACE_USAGE:")));
        verify(jdbc).query(contains("FROM V_CRECIMIENTO_ANALITICO H"), any(RowMapper.class));
    }

    private record Captura(Connection sesion, CallableStatement llamada, PreparedStatement consulta,
                           ResultSet resultado, AlmacenamientoRepository repository) {}

    private Captura prepararCaptura() throws Exception {
        var sesion = mock(Connection.class);
        var llamada = mock(CallableStatement.class);
        var consulta = mock(PreparedStatement.class);
        var rs = mock(ResultSet.class);
        when(sesion.getAutoCommit()).thenReturn(true);
        when(sesion.prepareCall("{call SP_REGISTRAR_CRECIMIENTO(?)}")).thenReturn(llamada);
        when(llamada.getBigDecimal(1)).thenReturn(new BigDecimal("42"));
        when(sesion.prepareStatement(contains("FROM V_CRECIMIENTO_ANALITICO H"))).thenReturn(consulta);
        when(consulta.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getBigDecimal("ID")).thenReturn(new BigDecimal("42"));
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 28, 12, 0)));
        when(rs.getBigDecimal("DBID")).thenReturn(BigDecimal.ONE);
        when(rs.getBigDecimal("CON_DBID")).thenReturn(BigDecimal.TEN);
        when(rs.getInt("CON_ID")).thenReturn(3);
        when(rs.getString("INSTANCE_NAME")).thenReturn("FREE");
        when(rs.getString("SERVICE_NAME")).thenReturn("PDB1");
        when(rs.getString("CON_NAME")).thenReturn("PDB1");
        when(rs.getBigDecimal("BYTES_TOTAL")).thenReturn(new BigDecimal("1000"));
        when(rs.getBigDecimal("BYTES_USADOS")).thenReturn(new BigDecimal("400"));
        when(rs.getBigDecimal("BYTES_LIBRES")).thenReturn(new BigDecimal("600"));
        var conexionPrueba = mock(ConexionOracleService.class);
        when(conexionPrueba.obtenerJdbcTemplate()).thenReturn(
                new JdbcTemplate(new SingleConnectionDataSource(sesion, true)));
        return new Captura(sesion, llamada, consulta, rs, new AlmacenamientoRepository(conexionPrueba));
    }

    @Test
    void capturaConfirmaLaFilaYHaceCommitEnLaMismaConexion() throws Exception {
        var captura = prepararCaptura();
        var guardado = captura.repository().registrarMedicion();
        assertTrue(guardado.valido());
        assertEquals(new BigDecimal("42"), guardado.id());
        assertEquals("PDB1", guardado.origen().contenedor());
        var orden = inOrder(captura.sesion(), captura.llamada(), captura.consulta(), captura.resultado());
        orden.verify(captura.sesion()).setAutoCommit(false);
        orden.verify(captura.llamada()).execute();
        orden.verify(captura.consulta()).setBigDecimal(1, new BigDecimal("42"));
        orden.verify(captura.consulta()).executeQuery();
        orden.verify(captura.sesion()).commit();
        verify(captura.sesion(), never()).rollback();
        verify(captura.sesion()).setAutoCommit(true);
    }

    @Test
    void filaAusenteImpideElCommit() throws Exception {
        var captura = prepararCaptura();
        when(captura.resultado().next()).thenReturn(false);
        assertThrows(org.springframework.dao.DataAccessException.class, captura.repository()::registrarMedicion);
        verify(captura.sesion()).rollback();
        verify(captura.sesion(), never()).commit();
    }

    @Test
    void falloAlPersistirNoRetornaUnaMedicionExitosa() throws Exception {
        var captura = prepararCaptura();
        doThrow(new SQLException("disco no disponible", "08006")).when(captura.sesion()).commit();
        assertThrows(org.springframework.dao.DataAccessException.class, captura.repository()::registrarMedicion);
        verify(captura.sesion()).rollback();
    }

    @Test
    void registroCorruptoImpideElCommit() throws Exception {
        var captura = prepararCaptura();
        when(captura.resultado().getBigDecimal("BYTES_LIBRES")).thenReturn(new BigDecimal("999"));
        assertThrows(org.springframework.dao.DataAccessException.class, captura.repository()::registrarMedicion);
        verify(captura.sesion()).rollback();
        verify(captura.sesion(), never()).commit();
    }

    @Test
    void rollbackFallidoNoPuedeConfirmarseAlRestablecerAutocommit() throws Exception {
        var captura = prepararCaptura();
        when(captura.resultado().next()).thenReturn(false);
        doThrow(new SQLException("Conexión perdida", "08006")).when(captura.sesion()).rollback();
        assertThrows(org.springframework.dao.DataAccessException.class, captura.repository()::registrarMedicion);
        verify(captura.sesion(), never()).setAutoCommit(true);
        verify(captura.sesion()).abort(any(java.util.concurrent.Executor.class));
    }

    @Test
    void diagnosticoIdentificaFaseOutYCausaOracleSinCredenciales(CapturedOutput salida) throws Exception {
        var captura = prepararCaptura();
        var error = new SQLException("ORA-20001 password=clave-prueba username=cuenta-prueba "
                + "jdbc:oracle:thin:cuenta-prueba/clave-prueba@servidor:1521/PDB", "72000", 20001);
        error.setNextException(new SQLException("detalle Oracle encadenado", "99999", 17001));
        when(captura.llamada().getBigDecimal(1)).thenThrow(error);
        assertThrows(org.springframework.dao.DataAccessException.class, captura.repository()::registrarMedicion);
        assertTrue(salida.getAll().contains("al obtener el ID OUT"));
        assertTrue(salida.getAll().contains("java.sql.SQLException"));
        assertTrue(salida.getAll().contains("errorCode=20001"));
        assertTrue(salida.getAll().contains("SQLState=72000"));
        assertTrue(salida.getAll().contains("errorCode=17001"));
        assertTrue(salida.getAll().contains("causa "));
        assertFalse(salida.getAll().contains("clave-prueba"));
        assertFalse(salida.getAll().contains("cuenta-prueba"));
        assertFalse(salida.getAll().contains("jdbc:oracle:thin:"));
        verify(captura.sesion()).rollback();
        verify(captura.sesion(), never()).commit();
    }

    @Test
    void diagnosticoDistingueFalloDeCommitYDeRollback(CapturedOutput salida) throws Exception {
        var captura = prepararCaptura();
        doThrow(new SQLException("fallo commit", "08006", 3113)).when(captura.sesion()).commit();
        doThrow(new SQLException("fallo rollback", "08006", 3114)).when(captura.sesion()).rollback();
        assertThrows(org.springframework.dao.DataAccessException.class, captura.repository()::registrarMedicion);
        assertTrue(salida.getAll().contains("al ejecutar COMMIT"));
        assertTrue(salida.getAll().contains("al hacer ROLLBACK"));
        assertTrue(salida.getAll().contains("errorCode=3113"));
        assertTrue(salida.getAll().contains("errorCode=3114"));
        verify(captura.sesion()).abort(any(java.util.concurrent.Executor.class));
        verify(captura.sesion(), never()).setAutoCommit(true);
    }

    @Test
    void diagnosticoRedactaCredencialesConfiguradasAunqueNoTenganEtiqueta(CapturedOutput salida) throws Exception {
        var captura = prepararCaptura();
        var origen = mock(com.zaxxer.hikari.HikariDataSource.class);
        when(origen.getConnection()).thenReturn(captura.sesion());
        when(origen.getUsername()).thenReturn("cuenta-diagnostico");
        when(origen.getPassword()).thenReturn("secreto libre de diagnostico");
        var conexionPrueba = mock(ConexionOracleService.class);
        when(conexionPrueba.obtenerJdbcTemplate()).thenReturn(new JdbcTemplate(origen));
        var error = new SQLException("ORA-20001 cuenta-diagnostico secreto libre de diagnostico", "72000", 20001);
        when(captura.llamada().getBigDecimal(1)).thenThrow(error);
        assertThrows(org.springframework.dao.DataAccessException.class,
                new AlmacenamientoRepository(conexionPrueba)::registrarMedicion);
        assertTrue(salida.getAll().contains("errorCode=20001"));
        assertTrue(salida.getAll().contains("[REDACTADO]"));
        assertFalse(salida.getAll().contains("cuenta-diagnostico"));
        assertFalse(salida.getAll().contains("secreto libre de diagnostico"));
    }
}
