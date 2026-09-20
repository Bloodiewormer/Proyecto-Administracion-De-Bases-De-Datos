package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.OrdenConsultas;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RendimientoRepositoryTests {
    private final ConexionOracleService conexion = mock(ConexionOracleService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RendimientoRepository repository = new RendimientoRepository(conexion);

    private void sinFilas() {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    }

    @Test
    void sinConexionNoEjecutaConsultas() {
        when(conexion.obtenerJdbcTemplate()).thenThrow(new IllegalStateException("privado"));
        var datos = repository.consultar(OrdenConsultas.CPU, 10);
        assertNull(datos.consultas());
        assertNull(datos.sesiones());
        assertNull(datos.procesos());
        assertEquals(OrdenConsultas.CPU, datos.orden());
        assertEquals(10, datos.top());
        assertTrue(datos.avisos().getFirst().contains("No hay conexión"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void resultadosVaciosNoSeReportanComoError() {
        sinFilas();
        var datos = repository.consultar(OrdenConsultas.TIEMPO_TOTAL, 10);
        assertTrue(datos.consultas().consultas().isEmpty());
        assertNull(datos.consultas().resumen());
        assertTrue(datos.sesiones().sesiones().isEmpty());
        assertTrue(datos.sesiones().bloqueos().isEmpty());
        assertTrue(datos.procesos().memoria().isEmpty());
        assertTrue(datos.procesos().cpu().isEmpty());
        assertTrue(datos.avisos().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"TIEMPO_TOTAL,ELAPSED_TIME DESC", "TIEMPO_PROMEDIO,ELAPSED_TIME / EXECUTIONS DESC",
            "CPU,CPU_TIME DESC", "LECTURAS_DISCO,DISK_READS DESC",
            "LECTURAS_MEMORIA,BUFFER_GETS DESC", "EJECUCIONES,EXECUTIONS DESC"})
    void ordenaSoloConExpresionesPredefinidasYAplicaElLimite(OrdenConsultas orden, String expresion) {
        sinFilas();
        repository.consultar(orden, 15);
        verify(jdbc).query(contains("ORDER BY " + expresion), any(RowMapper.class), eq(15));
        verify(jdbc).query(contains("FROM V$PROCESS"), any(RowMapper.class), eq(15));
        verify(jdbc).query(contains("FROM V$SESSTAT"), any(RowMapper.class), eq(15));
    }

    @Test
    void mapeaConsultasSesionesProcesosYCpu() throws Exception {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        var rs = mock(ResultSet.class);
        var momento = LocalDateTime.of(2026, 3, 4, 5, 6, 7);
        when(rs.getString("SQL_ID")).thenReturn("abc123");
        when(rs.getString("TEXTO")).thenReturn("SELECT 1 FROM DUAL");
        when(rs.getBigDecimal("EXECUTIONS")).thenReturn(new BigDecimal("40"));
        when(rs.getBigDecimal("ELAPSED_TIME")).thenReturn(new BigDecimal("8000000"));
        when(rs.getBigDecimal("CPU_TIME")).thenReturn(new BigDecimal("2000000"));
        when(rs.getBigDecimal("BUFFER_GETS")).thenReturn(new BigDecimal("500"));
        when(rs.getBigDecimal("DISK_READS")).thenReturn(new BigDecimal("30"));
        when(rs.getBigDecimal("ROWS_PROCESSED")).thenReturn(new BigDecimal("40"));
        when(rs.getTimestamp("LAST_ACTIVE_TIME")).thenReturn(Timestamp.valueOf(momento));
        when(rs.getObject("SID", Integer.class)).thenReturn(101);
        when(rs.getObject("SERIAL_NUMBER", Integer.class)).thenReturn(9);
        when(rs.getString("USERNAME")).thenReturn("MONITOR");
        when(rs.getString("STATUS")).thenReturn("ACTIVE");
        when(rs.getString("MACHINE")).thenReturn("equipo-prueba");
        when(rs.getString("PROGRAM")).thenReturn("sqlplus.exe");
        when(rs.getTimestamp("LOGON_TIME")).thenReturn(Timestamp.valueOf(momento));
        when(rs.getBigDecimal("LAST_CALL_ET")).thenReturn(new BigDecimal("90"));
        when(rs.getString("EVENT")).thenReturn("SQL*Net message from client");
        when(rs.getObject("BLOCKING_SESSION", Integer.class)).thenReturn(null);
        when(rs.getObject("SID_BLOQUEADA", Integer.class)).thenReturn(55);
        when(rs.getObject("SID_BLOQUEADORA", Integer.class)).thenReturn(77);
        when(rs.getBigDecimal("SEGUNDOS_ESPERA")).thenReturn(new BigDecimal("120"));
        when(rs.getString("SPID")).thenReturn("4321");
        when(rs.getObject("PID", Integer.class)).thenReturn(28);
        when(rs.getString("PNAME")).thenReturn("MMON");
        when(rs.getString("TIPO")).thenReturn("BACKGROUND");
        when(rs.getBigDecimal("PGA_ALLOC_MEM")).thenReturn(new BigDecimal("2097152"));
        when(rs.getBigDecimal("CPU_CENTISEGUNDOS")).thenReturn(new BigDecimal("350"));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });
        var datos = repository.consultar(OrdenConsultas.TIEMPO_TOTAL, 10);
        var consulta = datos.consultas().consultas().getFirst();
        assertEquals("abc123", consulta.sqlId());
        assertEquals(new BigDecimal("8000000"), consulta.tiempoTotalMicros());
        assertNull(consulta.tiempoPromedioMicros());
        assertEquals(momento, consulta.ultimaActividad());
        var sesion = datos.sesiones().sesiones().getFirst();
        assertEquals(101, sesion.sid());
        assertEquals("MONITOR", sesion.usuario());
        assertEquals(new BigDecimal("90"), sesion.segundosDesdeUltimaLlamada());
        assertNull(sesion.bloqueadaPor());
        var bloqueo = datos.sesiones().bloqueos().getFirst();
        assertEquals(55, bloqueo.sidBloqueada());
        assertEquals(77, bloqueo.sidBloqueadora());
        var proceso = datos.procesos().memoria().getFirst();
        assertEquals("4321", proceso.spid());
        assertEquals(28, proceso.pid());
        assertEquals("MMON", proceso.nombre());
        assertEquals("BACKGROUND", proceso.tipo());
        assertEquals("BACKGROUND", datos.procesos().cpu().getFirst().tipo());
        assertEquals(new BigDecimal("2097152"), proceso.pgaAsignadaBytes());
        assertEquals(new BigDecimal("350"), datos.procesos().cpu().getFirst().cpuCentisegundos());
        assertNull(datos.sesiones().activas());
    }

    @Test
    void falloParcialNoImpideOtrasConsultasNiFiltraDetallesJdbc() {
        sinFilas();
        when(jdbc.query(contains("FROM V$SESSTAT"), any(RowMapper.class), any(Object[].class))).thenThrow(
                new BadSqlGrammarException("privado", "privado", new SQLException("secreto", "42000", 1031)));
        var datos = repository.consultar(OrdenConsultas.TIEMPO_TOTAL, 10);
        assertNull(datos.procesos().cpu());
        assertNotNull(datos.procesos().memoria());
        assertNotNull(datos.sesiones().sesiones());
        assertTrue(datos.avisos().stream().anyMatch(a -> a.contains("permisos insuficientes")));
        assertFalse(datos.avisos().toString().contains("secreto"));
        assertFalse(datos.avisos().toString().contains("privado"));
    }
}
