package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.AlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.FiltroTipoObjeto;
import cr.ac.una.dbmonitor.dto.ResumenAlmacenamientoDto;
import cr.ac.una.dbmonitor.dto.TablespaceDto;
import cr.ac.una.dbmonitor.repository.AlmacenamientoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AlmacenamientoServiceTests {

    private final AlmacenamientoRepository repository = mock(AlmacenamientoRepository.class);
    private final RegistroCrecimientoService registroCrecimiento = mock(RegistroCrecimientoService.class);
    private final AlmacenamientoService service = new AlmacenamientoService(repository, registroCrecimiento);

    @Test
    void calculaPorcentajeDeUsoDeTablespacesYResumen() {
        var ts = new TablespaceDto("USERS", "PERMANENT", "ONLINE", "LOCAL", "AUTO",
                new BigDecimal("1000"), new BigDecimal("450"), new BigDecimal("550"),
                new BigDecimal("2000"), null);

        var resumen = new ResumenAlmacenamientoDto(null, new BigDecimal("450"), null, null,
                new BigDecimal("1000"), new BigDecimal("200"), new BigDecimal("100"), 1, 1);

        var raw = new AlmacenamientoRepository.DatosAlmacenamientoRaw(
                resumen, List.of(ts), List.of(), List.of(), List.of(), List.of("aviso prueba"));

        when(repository.consultar(any(), anyInt())).thenReturn(raw);
        when(registroCrecimiento.obtenerMediciones()).thenReturn(List.of());

        var resultado = service.obtenerAlmacenamiento(FiltroTipoObjeto.TODOS, 10);

        assertNotNull(resultado);
        assertEquals(1, resultado.tablespaces().size());
        assertEquals(new BigDecimal("45.00"), resultado.tablespaces().getFirst().porcentajeUso());

        var res = resultado.resumen();
        assertNotNull(res);
        assertEquals(new BigDecimal("1300"), res.tamanoTotalBytes());
        assertEquals(new BigDecimal("450"), res.usadoBytes());
        assertEquals(new BigDecimal("550"), res.libreBytes());
        assertEquals(new BigDecimal("34.62"), res.porcentajeUso());
        assertTrue(resultado.avisos().contains("aviso prueba"));
    }

    @Test
    void calculaResumenDesdeTablespacesCuandoRawResumenEsNulo() {
        var ts1 = new TablespaceDto("DATA", "PERMANENT", "ONLINE", "LOCAL", "AUTO",
                new BigDecimal("800"), new BigDecimal("300"), new BigDecimal("500"),
                new BigDecimal("1000"), null);
        var ts2 = new TablespaceDto("TEMP", "TEMPORARY", "ONLINE", "LOCAL", "AUTO",
                new BigDecimal("200"), new BigDecimal("50"), new BigDecimal("150"),
                new BigDecimal("500"), null);

        var raw = new AlmacenamientoRepository.DatosAlmacenamientoRaw(
                null, List.of(ts1, ts2), List.of(), List.of(), List.of(), List.of());

        when(repository.consultar(any(), anyInt())).thenReturn(raw);
        when(registroCrecimiento.obtenerMediciones()).thenReturn(List.of());

        var resultado = service.obtenerAlmacenamiento(FiltroTipoObjeto.TODOS, 10);

        assertNotNull(resultado);
        var res = resultado.resumen();
        assertNotNull(res);
        assertEquals(new BigDecimal("1000"), res.tamanoTotalBytes());
        assertEquals(new BigDecimal("350"), res.usadoBytes());
        assertEquals(new BigDecimal("650"), res.libreBytes());
        assertEquals(new BigDecimal("35.00"), res.porcentajeUso());
        assertEquals(2, res.cantidadTablespaces());
    }

    @ParameterizedTest
    @CsvSource({
            "-5, 5",
            "0, 5",
            "4, 5",
            "10, 10",
            "25, 25",
            "50, 50",
            "80, 50"
    })
    void limitaTopEntreCincoYCincuenta(int entrada, int esperado) {
        var raw = new AlmacenamientoRepository.DatosAlmacenamientoRaw(
                null, List.of(), List.of(), List.of(), List.of(), List.of());
        when(repository.consultar(any(), eq(esperado))).thenReturn(raw);

        var res = service.obtenerAlmacenamiento(FiltroTipoObjeto.TODOS, entrada);

        assertEquals(esperado, res.top());
        verify(repository).consultar(any(), eq(esperado));
    }

    @Test
    void toleraValoresNulosSinFallar() {
        var raw = new AlmacenamientoRepository.DatosAlmacenamientoRaw(
                null, List.of(), List.of(), List.of(), List.of(), List.of());
        when(repository.consultar(any(), anyInt())).thenReturn(raw);

        var res = service.obtenerAlmacenamiento(null, null);

        assertNotNull(res);
        assertNull(res.resumen());
        assertTrue(res.tablespaces().isEmpty());
        assertEquals(10, res.top());
        assertEquals(FiltroTipoObjeto.TODOS, res.filtroTipo());
    }

    @Test
    void registroMedicionActualInvocaServicioDeRegistro() {
        var resumen = new ResumenAlmacenamientoDto(null, new BigDecimal("300"), null, null,
                new BigDecimal("800"), new BigDecimal("100"), new BigDecimal("100"), 1, 1);
        var raw = new AlmacenamientoRepository.DatosAlmacenamientoRaw(
                resumen, List.of(), List.of(), List.of(), List.of(), List.of());
        when(repository.consultar(any(), anyInt())).thenReturn(raw);

        service.registrarMedicionActual();

        verify(registroCrecimiento).registrarMedicion(eq(new BigDecimal("1000")),
                eq(new BigDecimal("300")), eq(new BigDecimal("600")));
    }
}
