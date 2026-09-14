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
        when(rs.getString("STATUS")).thenReturn("OPEN");
        when(rs.getTimestamp("STARTUP_TIME")).thenReturn(Timestamp.valueOf(inicio));
        when(rs.getString("NAME")).thenReturn("nombre-prueba");
        when(rs.getString("OPEN_MODE")).thenReturn("READ WRITE");
        when(rs.getString("CDB")).thenReturn("YES");
        when(rs.getBigDecimal("VALUE")).thenReturn(new BigDecimal("4294967296"));
        when(rs.getObject("CON_ID", Integer.class)).thenReturn(3);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
        var datos = repository.consultar();
        assertEquals("host-prueba", datos.instancia().host());
        assertEquals("instancia-prueba", datos.instancia().nombre());
        assertEquals("21.0.0.0.0", datos.instancia().version());
        assertEquals(inicio, datos.instancia().inicio());
        assertNull(datos.instancia().fechaConsulta());
        assertEquals(new BigDecimal("4294967296"), datos.memoria().componentes().getFirst().bytes());
        assertEquals(3, datos.memoria().componentes().getFirst().conId());
        assertEquals("YES", datos.baseDatos().base().cdb());
        assertEquals("READ WRITE", datos.baseDatos().contenedorActual().modoApertura());
        verify(conexion, times(1)).obtenerJdbcTemplate();
        verify(jdbc).query(contains("SYS_CONTEXT('USERENV', 'CON_ID')"), any(RowMapper.class));
    }

    @Test
    void resultadosVaciosNoSeConviertenEnCeros() {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        var datos = repository.consultar();
        assertNull(datos.instancia());
        assertTrue(datos.memoria().componentes().isEmpty());
        assertNull(datos.memoria().totalSgaBytes());
        assertNull(datos.baseDatos().base());
        assertNull(datos.baseDatos().contenedorActual());
        assertEquals(4, datos.avisos().size());
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
