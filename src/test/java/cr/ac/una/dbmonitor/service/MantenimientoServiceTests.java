package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.ResultadoMantenimiento.Nivel;
import cr.ac.una.dbmonitor.dto.TipoCompilacion;
import cr.ac.una.dbmonitor.dto.VistaMantenimiento;
import cr.ac.una.dbmonitor.repository.MantenimientoRepository;
import cr.ac.una.dbmonitor.repository.MantenimientoRepository.EstadoObjeto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MantenimientoServiceTests {
    private final MantenimientoRepository repository = mock(MantenimientoRepository.class);
    private final MantenimientoService service = new MantenimientoService(repository);

    private UncategorizedSQLException errorOracle(int codigo) {
        return new UncategorizedSQLException("operación", "BEGIN ... END;", new SQLException("ORA-" + codigo, "72000", codigo));
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,200", "3,10", "80,80", "9000,500"}, nullValues = "NULL")
    void limitaLasFilasPorTabla(Integer solicitado, int esperado) {
        service.obtenerMantenimiento(VistaMantenimiento.ESTADISTICAS, true, solicitado);
        verify(repository).consultar(VistaMantenimiento.ESTADISTICAS, true, esperado);
    }

    @Test
    void recalculaUnaTablaYConfirmaConElNuevoAnalisis() {
        when(repository.esTablaDeOrganizacion("VENTAS", "FACTURAS")).thenReturn(true);
        when(repository.estadisticasTabla("VENTAS", "FACTURAS")).thenReturn(Optional.of(
                new MantenimientoRepository.EstadisticasTabla(1500L, LocalDateTime.of(2026, 10, 28, 9, 15, 0))));
        var resultado = service.recalcularEstadisticasTabla("VENTAS", "FACTURAS");
        assertEquals(Nivel.EXITO, resultado.nivel());
        assertEquals("VENTAS.FACTURAS", resultado.objetivo());
        assertTrue(resultado.detalle().contains("Filas registradas: 1500"));
        assertTrue(resultado.detalle().contains("28/10/2026 09:15:00"));
        verify(repository).recalcularEstadisticasTabla("VENTAS", "FACTURAS");
    }

    @Test
    void noActuaSobreTablasDeOracleNiNombresVacios() {
        var vacia = service.recalcularEstadisticasTabla(" ", "FACTURAS");
        assertEquals(Nivel.ERROR, vacia.nivel());
        when(repository.esTablaDeOrganizacion("SYS", "OBJ$")).thenReturn(false);
        var deOracle = service.recalcularEstadisticasTabla("SYS", "OBJ$");
        assertEquals(Nivel.ERROR, deOracle.nivel());
        assertTrue(deOracle.detalle().contains("no se ejecutó"));
        verify(repository, never()).recalcularEstadisticasTabla(any(), any());
    }

    @Test
    void informaLosErroresDeOracle() {
        when(repository.esTablaDeOrganizacion("VENTAS", "FACTURAS")).thenReturn(true);
        doThrow(errorOracle(20005)).when(repository).recalcularEstadisticasTabla("VENTAS", "FACTURAS");
        var resultado = service.recalcularEstadisticasTabla("VENTAS", "FACTURAS");
        assertEquals(Nivel.ERROR, resultado.nivel());
        assertTrue(resultado.detalle().contains("bloqueadas"));
    }

    @Test
    void sinConexionInformaElMotivo() {
        when(repository.esEsquemaDeOrganizacion("VENTAS"))
                .thenThrow(new IllegalStateException("Primero configure una conexión Oracle."));
        var resultado = service.recompilarEsquema("VENTAS");
        assertEquals(Nivel.ERROR, resultado.nivel());
        assertEquals("Primero configure una conexión Oracle.", resultado.detalle());
    }

    @ParameterizedTest
    @CsvSource({"4,4,EXITO", "4,3,ADVERTENCIA"})
    void elEsquemaInformaCuantasTablasSeAnalizaron(int total, int analizadas, Nivel esperado) {
        var desde = LocalDateTime.of(2026, 10, 28, 9, 0);
        when(repository.esEsquemaDeOrganizacion("VENTAS")).thenReturn(true);
        when(repository.momentoActual()).thenReturn(desde);
        when(repository.tablasAnalizadas("VENTAS", desde))
                .thenReturn(new MantenimientoRepository.TablasAnalizadas(total, analizadas));
        var resultado = service.recalcularEstadisticasEsquema("VENTAS");
        assertEquals(esperado, resultado.nivel());
        assertTrue(resultado.detalle().contains(analizadas + " de " + total + " tablas"));
    }

    @Test
    void tipoSinRecompilacionIndividualNoConsultaOracle() {
        var resultado = service.recompilarObjeto("VENTAS", "CLASE", "JAVA CLASS");
        assertEquals(Nivel.ERROR, resultado.nivel());
        assertTrue(resultado.detalle().contains("esquema completo"));
        verifyNoInteractions(repository);
    }

    @Test
    void confirmaCuandoElObjetoQuedaValido() {
        when(repository.estadoObjeto("VENTAS", "P_FACTURAR", "PROCEDURE")).thenReturn(
                Optional.of(new EstadoObjeto("INVALID", 0, null)), Optional.of(new EstadoObjeto("VALID", 0, null)));
        when(repository.recompilarObjeto(TipoCompilacion.PROCEDURE, "VENTAS", "P_FACTURAR")).thenReturn(true);
        var resultado = service.recompilarObjeto("VENTAS", "P_FACTURAR", "PROCEDURE");
        assertEquals(Nivel.EXITO, resultado.nivel());
        assertTrue(resultado.detalle().contains("VALID"));
    }

    @Test
    void informaLosErroresCuandoElObjetoSigueInvalido() {
        when(repository.estadoObjeto("VENTAS", "P_FACTURAR", "PROCEDURE")).thenReturn(
                Optional.of(new EstadoObjeto("INVALID", 0, null)),
                Optional.of(new EstadoObjeto("INVALID", 2, "PLS-00201: identifier 'X' must be declared ")));
        when(repository.recompilarObjeto(TipoCompilacion.PROCEDURE, "VENTAS", "P_FACTURAR")).thenReturn(false);
        var resultado = service.recompilarObjeto("VENTAS", "P_FACTURAR", "PROCEDURE");
        assertEquals(Nivel.ERROR, resultado.nivel());
        assertTrue(resultado.detalle().contains("2 error(es)"));
        assertTrue(resultado.detalle().endsWith("must be declared"));
    }

    @Test
    void objetoInexistenteNoSeRecompila() {
        when(repository.estadoObjeto(any(), any(), any())).thenReturn(Optional.empty());
        assertEquals(Nivel.ERROR, service.recompilarObjeto("VENTAS", "NO_EXISTE", "VIEW").nivel());
        verify(repository, never()).recompilarObjeto(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"3,0,EXITO", "3,1,ADVERTENCIA", "3,3,ERROR"})
    void laRecompilacionDelEsquemaComparaAntesYDespues(int antes, int despues, Nivel esperado) {
        when(repository.esEsquemaDeOrganizacion("VENTAS")).thenReturn(true);
        when(repository.invalidosDelEsquema("VENTAS")).thenReturn(antes, despues);
        var resultado = service.recompilarEsquema("VENTAS");
        assertEquals(esperado, resultado.nivel());
        verify(repository).recompilarEsquema("VENTAS");
    }

    @Test
    void elDetalleRespetaSingularYPlural() {
        when(repository.esEsquemaDeOrganizacion("VENTAS")).thenReturn(true);
        when(repository.invalidosDelEsquema("VENTAS")).thenReturn(2, 1);
        assertTrue(service.recompilarEsquema("VENTAS").detalle()
                .startsWith("Había 2 objetos inválidos: 1 quedó válido y 1 sigue inválido."));
    }

    @Test
    void esquemaSinInvalidosNoSeRecompila() {
        when(repository.esEsquemaDeOrganizacion("VENTAS")).thenReturn(true);
        when(repository.invalidosDelEsquema("VENTAS")).thenReturn(0);
        assertEquals(Nivel.EXITO, service.recompilarEsquema("VENTAS").nivel());
        verify(repository, never()).recompilarEsquema(any());
    }
}
