package cr.ac.una.dbmonitor.repository;

import cr.ac.una.dbmonitor.dto.FiltroTipoObjeto;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlmacenamientoRepositoryTests {

    private final ConexionOracleService conexion = mock(ConexionOracleService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AlmacenamientoRepository repository = new AlmacenamientoRepository(conexion);

    private void configurarMocksVacios() {
        when(conexion.obtenerJdbcTemplate()).thenReturn(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
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
}
