package cr.ac.una.dbmonitor.service;

import com.zaxxer.hikari.HikariDataSource;
import cr.ac.una.dbmonitor.dto.ConexionOracleDto;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.SQLTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConexionOracleServiceTests {
    @Test
    void conservaLaConfiguracionAnteriorAnteFalloYPermiteReemplazarla() throws Exception {
        var service = spy(new ConexionOracleService());
        assertThrows(IllegalStateException.class, service::obtenerJdbcTemplate);
        var form = new ConexionOracleDto();
        var first = mock(HikariDataSource.class);
        var second = mock(HikariDataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        var result = mock(ResultSet.class);
        doReturn(first, second, second).when(service).crearDataSource(form);
        when(first.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1 FROM DUAL")).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        service.conectar(form);
        assertSame(first, service.obtenerJdbcTemplate().getDataSource());
        when(second.getConnection()).thenThrow(new SQLException("detalle privado", "28000", 1017))
                .thenReturn(connection);
        var error = assertThrows(IllegalStateException.class, () -> service.conectar(form));
        assertEquals("Credenciales inválidas.", error.getMessage());
        assertNull(error.getCause());
        assertSame(first, service.obtenerJdbcTemplate().getDataSource());
        verify(first, never()).close();
        service.conectar(form);
        assertSame(second, service.obtenerJdbcTemplate().getDataSource());
        verify(first).close();
        verify(connection, times(2)).close();
        verify(statement, times(2)).close();
        verify(result, times(2)).close();
    }

    @Test
    void liberaElPoolDescartadoCuandoLaValidacionFalla() throws Exception {
        var service = spy(new ConexionOracleService());
        var form = new ConexionOracleDto();
        var descartado = mock(HikariDataSource.class);
        doReturn(descartado).when(service).crearDataSource(form);
        when(descartado.getConnection()).thenThrow(new SQLException("detalle privado", "08000", 12514));
        assertThrows(IllegalStateException.class, () -> service.conectar(form));
        verify(descartado).close();
        assertThrows(IllegalStateException.class, service::obtenerJdbcTemplate);
    }

    @Test
    void consultaSinResultadosNoActivaConfiguracion() throws Exception {
        var service = spy(new ConexionOracleService());
        var form = new ConexionOracleDto();
        var source = mock(HikariDataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        var result = mock(ResultSet.class);
        doReturn(source).when(service).crearDataSource(form);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1 FROM DUAL")).thenReturn(result);
        assertThrows(IllegalStateException.class, () -> service.conectar(form));
        assertThrows(IllegalStateException.class, service::obtenerJdbcTemplate);
        verify(result).close();
        verify(statement).close();
        verify(connection).close();
        verify(source).close();
    }

    @Test
    void traduceErroresSinDetallesJdbc() {
        var service = new ConexionOracleService();
        assertEquals("No fue posible localizar el servicio Oracle especificado.",
                service.mensajeError(new SQLException("privado", "08000", 12514)));
        assertTrue(service.mensajeError(new SQLTimeoutException("privado")).contains("tiempo"));
        assertFalse(service.mensajeError(new SQLException("privado")).contains("privado"));
    }

    @Test
    void configuraPoolUrlYTimeouts() {
        var form = new ConexionOracleDto();
        form.setServiceName("SERVICIO_PRUEBA");
        form.setUsername("usuario_prueba");
        form.setPassword("secreto-de-prueba");
        try (var source = new ConexionOracleService().crearDataSource(form)) {
            assertEquals("jdbc:oracle:thin:@//localhost:1521/SERVICIO_PRUEBA", source.getJdbcUrl());
            assertEquals("5000", source.getDataSourceProperties().getProperty("oracle.net.CONNECT_TIMEOUT"));
            assertEquals("10000", source.getDataSourceProperties().getProperty("oracle.jdbc.ReadTimeout"));
            assertEquals(5000, source.getConnectionTimeout());
            assertEquals(5, source.getMaximumPoolSize());
        }
    }
}
