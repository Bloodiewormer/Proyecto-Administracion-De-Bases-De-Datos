package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.BadSqlGrammarException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EstadoInstanciaRepositoryTests {
    private final ConexionOracleService conexion = mock(ConexionOracleService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EstadoInstanciaRepository repository = new EstadoInstanciaRepository(conexion);

    @Test
    void sinConexionNoEjecutaConsultas() {
        when(conexion.obtenerJdbcTemplate()).thenThrow(new IllegalStateException("privado"));
        var datos = repository.consultar();
        assertNull(datos.instancia());
        assertNull(datos.memoria());
        assertTrue(datos.avisos().getFirst().contains("No hay conexión"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void mapeaColumnasConUnSoloTemplate() throws Exception {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        var rs = mock(ResultSet.class);
        var inicio = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(rs.getString("HOST_NAME")).thenReturn("host-prueba");
        when(rs.getString("INSTANCE_NAME")).thenReturn("instancia-prueba");
        when(rs.getString("VERSION_FULL")).thenReturn("21.0.0.0.0");
        when(rs.getString("EDITION")).thenReturn("XE");
        when(rs.getString("DATABASE_TYPE")).thenReturn("SINGLE");
        when(rs.getString("STATUS")).thenReturn("OPEN");
        when(rs.getTimestamp("STARTUP_TIME")).thenReturn(Timestamp.valueOf(inicio));
        when(rs.getString("NAME")).thenReturn("nombre-prueba");
        when(rs.getString("OPEN_MODE")).thenReturn("READ WRITE");
        when(rs.getString("CDB")).thenReturn("YES");
        when(rs.getBigDecimal("VALUE")).thenReturn(new BigDecimal("4294967296"));
        when(rs.getBigDecimal("SGA_LIBRE")).thenReturn(new BigDecimal("1024"));
        when(rs.getBigDecimal("PGA_ASIGNADA")).thenReturn(new BigDecimal("2048"));
        when(rs.getBigDecimal("PGA_UTILIZADA")).thenReturn(new BigDecimal("512"));
        when(rs.getObject("CON_ID", Integer.class)).thenReturn(3);
        when(rs.getInt("ACTUAL")).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
        var datos = repository.consultar();
        assertEquals("host-prueba", datos.instancia().host());
        assertEquals("instancia-prueba", datos.instancia().nombre());
        assertEquals("21.0.0.0.0", datos.instancia().version());
        assertEquals("XE", datos.instancia().edicion());
        assertEquals("SINGLE", datos.instancia().tipoDespliegue());
        assertEquals(inicio, datos.instancia().inicio());
        assertNull(datos.instancia().fechaConsulta());
        assertEquals(new BigDecimal("4294967296"), datos.memoria().componentes().getFirst().bytes());
        assertEquals(3, datos.memoria().componentes().getFirst().conId());
        assertEquals(new BigDecimal("1024"), datos.memoria().uso().sgaLibreBytes());
        assertEquals(new BigDecimal("2048"), datos.memoria().uso().pgaAsignadaBytes());
        assertNull(datos.memoria().uso().sgaUtilizadaBytes());
        assertEquals("YES", datos.baseDatos().base().cdb());
        assertEquals(1, datos.baseDatos().contenedores().size());
        assertEquals("READ WRITE", datos.baseDatos().contenedorActual().modoApertura());
        verify(conexion, times(1)).obtenerJdbcTemplate();
        verify(jdbc).query(contains("SYS_CONTEXT('USERENV', 'CON_ID')"), any(RowMapper.class));
    }

    @Test
    void listaTodosLosContenedoresYMarcaElActual() throws Exception {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        var raiz = mock(ResultSet.class);
        when(raiz.getObject("CON_ID", Integer.class)).thenReturn(1);
        when(raiz.getString("NAME")).thenReturn("CDB$ROOT");
        when(raiz.getInt("ACTUAL")).thenReturn(0);
        var conectable = mock(ResultSet.class);
        when(conectable.getObject("CON_ID", Integer.class)).thenReturn(3);
        when(conectable.getString("NAME")).thenReturn("XEPDB1");
        when(conectable.getInt("ACTUAL")).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("FROM V$CONTAINERS"), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(raiz, 0), mapper.mapRow(conectable, 1));
        });
        var datos = repository.consultar();
        assertEquals(List.of("CDB$ROOT", "XEPDB1"),
                datos.baseDatos().contenedores().stream().map(c -> c.nombre()).toList());
        assertEquals("XEPDB1", datos.baseDatos().contenedorActual().nombre());
    }

    @Test
    void versionFullAusenteUsaConsultaCompatible() throws Exception {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        var rs = mock(ResultSet.class);
        when(rs.getString("HOST_NAME")).thenReturn("host-antiguo");
        when(rs.getString("VERSION")).thenReturn("11.2.0.4.0");
        when(rs.getString("STATUS")).thenReturn("OPEN");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("VERSION_FULL"), any(RowMapper.class))).thenThrow(
                new BadSqlGrammarException("privado", "privado", new SQLException("secreto", "42000", 904)));
        when(jdbc.query(contains("INSTANCE_NAME, VERSION,"), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
        var datos = repository.consultar();
        assertEquals("host-antiguo", datos.instancia().host());
        assertEquals("11.2.0.4.0", datos.instancia().version());
        assertEquals("OPEN", datos.instancia().estado());
        assertNull(datos.instancia().edicion());
        assertNull(datos.instancia().tipoDespliegue());
        assertTrue(datos.avisos().stream().anyMatch(a -> a.contains("versión básica")));
        assertFalse(datos.avisos().toString().contains("secreto"));
    }

    @Test
    void resultadosVaciosNoSeConviertenEnCeros() {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        var datos = repository.consultar();
        assertNull(datos.instancia());
        assertTrue(datos.memoria().componentes().isEmpty());
        assertNull(datos.memoria().totalSgaBytes());
        assertNull(datos.memoria().uso());
        assertNull(datos.baseDatos().base());
        assertTrue(datos.baseDatos().contenedores().isEmpty());
        assertNull(datos.baseDatos().contenedorActual());
        assertEquals(5, datos.avisos().size());
    }

    @ParameterizedTest
    @CsvSource({"1031,permisos insuficientes", "942,vista no accesible", "904,columnas incompatibles",
            "12170,tiempo de espera", "17002,revise la conexión", "999,error de acceso"})
    void falloParcialNoImpideOtrasConsultas(int codigo, String mensaje) {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("FROM V$SGA"), any(RowMapper.class))).thenThrow(
                new BadSqlGrammarException("privado", "privado", new SQLException("secreto", "42000", codigo)));
        var datos = repository.consultar();
        assertNull(datos.memoria());
        assertTrue(datos.avisos().stream().anyMatch(a -> a.contains(mensaje)));
        assertFalse(datos.avisos().toString().contains("secreto"));
        assertFalse(datos.avisos().toString().contains("privado"));
        verify(jdbc).query(contains("FROM V$DATABASE"), any(RowMapper.class));
        verify(jdbc).query(contains("FROM V$CONTAINERS"), any(RowMapper.class));
    }
}
