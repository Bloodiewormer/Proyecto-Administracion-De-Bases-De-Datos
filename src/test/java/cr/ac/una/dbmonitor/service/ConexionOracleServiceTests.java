package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.ConexionOracleDto;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
        var first = mock(DriverManagerDataSource.class);
        var second = mock(DriverManagerDataSource.class);
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
        service.conectar(form);
        assertSame(second, service.obtenerJdbcTemplate().getDataSource());
        verify(connection, times(2)).close();
        verify(statement, times(2)).close();
        verify(result, times(2)).close();
    }

    @Test
    void consultaSinResultadosNoActivaConfiguracion() throws Exception {
        var service = spy(new ConexionOracleService());
        var form = new ConexionOracleDto();
        var source = mock(DriverManagerDataSource.class);
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
    void configuraUrlYTimeouts() {
        var form = new ConexionOracleDto();
        form.setServiceName("SERVICIO_PRUEBA");
        form.setUsername("usuario_prueba");
        form.setPassword("secreto-de-prueba");
        var source = new ConexionOracleService().crearDataSource(form);
        assertEquals("jdbc:oracle:thin:@//localhost:1521/SERVICIO_PRUEBA", source.getUrl());
        assertEquals("5000", source.getConnectionProperties().getProperty("oracle.net.CONNECT_TIMEOUT"));
        assertEquals("10000", source.getConnectionProperties().getProperty("oracle.jdbc.ReadTimeout"));
    }
}
