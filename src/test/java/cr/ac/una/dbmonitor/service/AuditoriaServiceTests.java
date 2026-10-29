package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.AuditoriaDto;
import cr.ac.una.dbmonitor.dto.FiltroOrigen;
import cr.ac.una.dbmonitor.dto.FiltrosAuditoria;
import cr.ac.una.dbmonitor.dto.Listado;
import cr.ac.una.dbmonitor.dto.OrigenPrivilegio;
import cr.ac.una.dbmonitor.dto.VistaAuditoria;
import cr.ac.una.dbmonitor.repository.AuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditoriaServiceTests {
    private final AuditoriaRepository repository = mock(AuditoriaRepository.class);
    private final AuditoriaService service = new AuditoriaService(repository);

    private FiltrosAuditoria consultar(Integer limite, String busqueda, String beneficiario) {
        when(repository.consultar(any())).thenAnswer(inv -> new AuditoriaDto(inv.getArgument(0), null, null,
                null, null, null, null, List.of()));
        service.obtenerAuditoria(VistaAuditoria.USUARIOS, FiltroOrigen.TODOS, busqueda, limite, beneficiario,
                OrigenPrivilegio.TODOS);
        var captor = ArgumentCaptor.forClass(FiltrosAuditoria.class);
        verify(repository).consultar(captor.capture());
        return captor.getValue();
    }

    private AuditoriaDto.PrivilegiosAuditoria privilegios(List<AuditoriaDto.RolEfectivo> roles,
                                                          List<AuditoriaDto.PrivilegioSistema> sistema,
                                                          Integer privilegiosAny) {
        var seleccionado = new AuditoriaDto.Beneficiario("APP", "USUARIO", false);
        return new AuditoriaDto.PrivilegiosAuditoria(List.of(seleccionado), seleccionado, "APP", roles,
                sistema == null ? null : new Listado<>(sistema, sistema.size()), privilegiosAny,
                null, List.of());
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,200", "1,10", "75,75", "9999,500"}, nullValues = "NULL")
    void limitaLasFilasPorTabla(Integer solicitado, int esperado) {
        assertEquals(esperado, consultar(solicitado, null, null).limite());
    }

    @Test
    void normalizaLosTextosRecibidos() {
        var filtros = consultar(null, "  app  ", "   ");
        assertEquals("app", filtros.busqueda());
        assertNull(filtros.beneficiario());
        reset(repository);
        assertEquals(128, consultar(null, "x".repeat(300), null).busqueda().length());
    }

    @Test
    void reconoceRolDbaHeredadoPrivilegiosAnyYOpcionDeAdministracion() {
        var roles = List.of(new AuditoriaDto.RolEfectivo("GESTOR", 1, null, true, true),
                new AuditoriaDto.RolEfectivo("DBA", 2, "GESTOR", false, true));
        var sistema = List.of(new AuditoriaDto.PrivilegioSistema("CREATE SESSION", "APP", true, true));
        var observaciones = service.observaciones(privilegios(roles, sistema, 3));
        assertTrue(observaciones.stream().anyMatch(o -> o.contains("Hereda el rol DBA mediante GESTOR")));
        assertTrue(observaciones.stream().anyMatch(o -> o.contains("3 privilegio(s) de sistema «ANY»")));
        assertTrue(observaciones.stream().anyMatch(o -> o.contains("2 privilegio(s) o rol(es)")));
    }

    @Test
    void informaRolesNoPredeterminadosYAusenciaDeRiesgos() {
        var roles = List.of(new AuditoriaDto.RolEfectivo("CONNECT", 1, null, false, false));
        var observaciones = service.observaciones(privilegios(roles, List.of(), 0));
        assertTrue(observaciones.getFirst().contains("no son predeterminados"));
        var sinRiesgos = service.observaciones(privilegios(List.of(), List.of(), 0));
        assertTrue(sinRiesgos.getFirst().startsWith("No se identificaron privilegios de alto impacto"));
    }

    @Test
    void sinInformacionNoAfirmaAusenciaDeRiesgos() {
        assertTrue(service.observaciones(privilegios(null, null, null)).isEmpty());
    }

    @Test
    void filtraLaListaDeBeneficiariosSinPerderElSeleccionado() {
        var seleccionado = new AuditoriaDto.Beneficiario("SYSTEM", "USUARIO", true);
        var propio = new AuditoriaDto.Beneficiario("APP", "USUARIO", false);
        var oracle = new AuditoriaDto.Beneficiario("DBA", "ROL", true);
        var datos = new AuditoriaDto.PrivilegiosAuditoria(List.of(seleccionado, propio, oracle), seleccionado,
                "SYSTEM", List.of(), new Listado<>(List.of(), 0), 0, null, List.of());
        when(repository.consultar(any())).thenReturn(new AuditoriaDto(null, null, null, null, null, datos, null,
                List.of("aviso previo")));
        var resultado = service.obtenerAuditoria(VistaAuditoria.PRIVILEGIOS, FiltroOrigen.ORGANIZACION, null, null,
                "SYSTEM", OrigenPrivilegio.TODOS);
        assertEquals(List.of(seleccionado, propio), resultado.privilegios().beneficiarios());
        assertFalse(resultado.privilegios().observaciones().isEmpty());
        assertEquals(List.of("aviso previo"), resultado.avisos());
    }
}
