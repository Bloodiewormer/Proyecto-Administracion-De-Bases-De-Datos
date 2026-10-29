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
                resumen, List.of(ts), List.of(), List.of(), List.of(), List.of("aviso prueba"), null, List.of());

        when(repository.consultar(any(), anyInt())).thenReturn(raw);
        when(registroCrecimiento.obtenerMediciones(any(), any())).thenReturn(List.of());

        var resultado = service.obtenerAlmacenamiento(FiltroTipoObjeto.TODOS, 10);

        assertNotNull(resultado);
        assertEquals(1, resultado.tablespaces().size());
        assertEquals(new BigDecimal("45.00"), resultado.tablespaces().getFirst().porcentajeUso());

        var res = resultado.resumen();
        assertNotNull(res);
        assertEquals(new BigDecimal("1000"), res.tamanoTotalBytes());
        assertEquals(new BigDecimal("450"), res.usadoBytes());
        assertEquals(new BigDecimal("550"), res.libreBytes());
        assertEquals(new BigDecimal("45.00"), res.porcentajeUso());
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
                null, List.of(ts1, ts2), List.of(), List.of(), List.of(), List.of(), null, List.of());

        when(repository.consultar(any(), anyInt())).thenReturn(raw);
        when(registroCrecimiento.obtenerMediciones(any(), any())).thenReturn(List.of());

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
                null, List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of());
        when(repository.consultar(any(), eq(esperado))).thenReturn(raw);

        var res = service.obtenerAlmacenamiento(FiltroTipoObjeto.TODOS, entrada);

        assertEquals(esperado, res.top());
        verify(repository).consultar(any(), eq(esperado));
    }

    @Test
    void toleraValoresNulosSinFallar() {
        var raw = new AlmacenamientoRepository.DatosAlmacenamientoRaw(
                null, List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of());
        when(repository.consultar(any(), anyInt())).thenReturn(raw);

        var res = service.obtenerAlmacenamiento(null, null);

        assertNotNull(res);
        assertNull(res.resumen());
        assertTrue(res.tablespaces().isEmpty());
        assertEquals(10, res.top());
        assertEquals(FiltroTipoObjeto.TODOS, res.filtroTipo());
    }

    @Test
    void capturaSinDatosNoPuedeTerminarConExito() {
        assertThrows(IllegalStateException.class, service::registrarMedicionActual);
    }

    @Test
    void registroMedicionActualInvocaServicioDeRegistro() {
        var origen = new cr.ac.una.dbmonitor.dto.CrecimientoDto.Origen(
                BigDecimal.ONE, BigDecimal.TEN, 3, "FREE", "FREEPDB1", "FREEPDB1");
        var guardado = new cr.ac.una.dbmonitor.dto.CrecimientoDto.Registro(BigDecimal.ONE,
                java.time.LocalDateTime.of(2026, 10, 28, 12, 0), origen,
                new BigDecimal("900"), new BigDecimal("300"), new BigDecimal("600"));
        when(repository.registrarMedicion()).thenReturn(guardado);
        assertDoesNotThrow(service::registrarMedicionActual);
        verify(repository).registrarMedicion();
        verify(repository, never()).consultar(any(), anyInt());
    }

    @Test
    void falloDePersistenciaSePropagaComoErrorAdministrativo() {
        when(repository.registrarMedicion()).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("sin acceso"));
        assertThrows(IllegalStateException.class, service::registrarMedicionActual);
    }

    @Test
    void ocupacionIncompletaNoSeConvierteEnCero() {
        var ts = new TablespaceDto("DATA", "PERMANENT", "OFFLINE", "LOCAL", "AUTO",
                new BigDecimal("1000"), null, null, new BigDecimal("2000"), null);
        when(repository.consultar(any(), anyInt())).thenReturn(new AlmacenamientoRepository.DatosAlmacenamientoRaw(
                null, List.of(ts), List.of(), List.of(), List.of(), List.of(), null, List.of()));
        var resultado = service.obtenerAlmacenamiento(null, null);
        assertEquals(new BigDecimal("1000"), resultado.resumen().tamanoTotalBytes());
        assertNull(resultado.resumen().usadoBytes());
        assertNull(resultado.resumen().libreBytes());
        assertNull(resultado.resumen().porcentajeUso());
        assertFalse(resultado.avisos().isEmpty());
    }

    @Test
    void erroresPorTablespaceNoPuedenCompensarseEnElResumen() {
        var a = new TablespaceDto("A", "PERMANENT", "ONLINE", "LOCAL", "AUTO",
                new BigDecimal("100"), new BigDecimal("60"), new BigDecimal("30"), null, null);
        var b = new TablespaceDto("B", "PERMANENT", "ONLINE", "LOCAL", "AUTO",
                new BigDecimal("100"), new BigDecimal("60"), new BigDecimal("50"), null, null);
        when(repository.consultar(any(), anyInt())).thenReturn(new AlmacenamientoRepository.DatosAlmacenamientoRaw(
                null, List.of(a, b), List.of(), List.of(), List.of(), List.of(), null, List.of()));
        var resultado = service.obtenerAlmacenamiento(null, null);
        assertNull(resultado.resumen().usadoBytes());
        assertNull(resultado.resumen().porcentajeUso());
    }
}
