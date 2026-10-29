package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.AuditoriaDto;
import cr.ac.una.dbmonitor.dto.FiltroOrigen;
import cr.ac.una.dbmonitor.dto.FiltrosAuditoria;
import cr.ac.una.dbmonitor.dto.Listado;
import cr.ac.una.dbmonitor.dto.OrigenPrivilegio;
import cr.ac.una.dbmonitor.dto.VistaAuditoria;
import cr.ac.una.dbmonitor.service.AuditoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuditoriaControllerTests {
    private static final AuditoriaDto.Contexto CONTEXTO = new AuditoriaDto.Contexto("FREEPDB1", true, "MONITOR");
    private static final AuditoriaDto.Resumen RESUMEN = new AuditoriaDto.Resumen(33, 2, 29, 109, 0);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuditoriaService service;

    private FiltrosAuditoria filtros(VistaAuditoria vista) {
        return new FiltrosAuditoria(vista, FiltroOrigen.TODOS, null, 200, null, OrigenPrivilegio.TODOS);
    }

    private void responder(AuditoriaDto datos) {
        when(service.obtenerAuditoria(any(), any(), any(), any(), any(), any())).thenReturn(datos);
    }

    @Test
    void muestraUsuariosConEstadoAlcanceYCuentasAdministrativas() throws Exception {
        var momento = LocalDateTime.of(2026, 10, 20, 8, 30, 0);
        var usuario = new AuditoriaDto.Usuario("APP_VENTAS", "EXPIRED & LOCKED", momento, momento, null,
                "USERS", "DEFAULT", "PASSWORD", false, false, null, 2, 1);
        var oracle = new AuditoriaDto.Usuario("SYSTEM", "OPEN", momento, null, null, "SYSTEM", "DEFAULT",
                "PASSWORD", true, true, momento, 2, 1);
        var cuenta = new AuditoriaDto.CuentaAdministrativa("SYS", List.of("SYSDBA", "SYSOPER"), "OPEN",
                "PASSWORD", true, null);
        responder(new AuditoriaDto(filtros(VistaAuditoria.USUARIOS), CONTEXTO, RESUMEN,
                new AuditoriaDto.UsuariosAuditoria(new Listado<>(List.of(usuario, oracle), 5),
                        List.of(cuenta)), null, null, null, List.of()));
        mvc.perform(get("/auditoria")).andExpect(status().isOk()).andExpect(view().name("auditoria"))
                .andExpect(content().string(containsString("APP_VENTAS")))
                .andExpect(content().string(containsString("Contraseña expirada y cuenta bloqueada")))
                .andExpect(content().string(containsString("Local (este contenedor)")))
                .andExpect(content().string(containsString("Común (todos los contenedores)")))
                .andExpect(content().string(containsString("20/10/2026 08:30")))
                .andExpect(content().string(containsString("Se muestran 2 de 5 usuarios")))
                .andExpect(content().string(containsString("SYSDBA, SYSOPER")))
                .andExpect(content().string(containsString("Contenedor consultado: FREEPDB1 (base de datos conectable)")))
                .andExpect(content().string(containsString("beneficiario=APP_VENTAS")))
                .andExpect(content().string(containsString("href=\"/auditoria\"")))
                .andExpect(content().string(containsString("aria-current=\"page\">Auditoría")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void muestraQueUsuarioTieneCadaRol() throws Exception {
        var rol = new AuditoriaDto.Rol("GESTOR_VENTAS", "NONE", false, false, 1, 0);
        var asignacion = new AuditoriaDto.AsignacionRol("APP_VENTAS", "USUARIO", "GESTOR_VENTAS", true, false, false);
        responder(new AuditoriaDto(filtros(VistaAuditoria.ROLES), CONTEXTO, RESUMEN, null,
                new AuditoriaDto.RolesAuditoria(new Listado<>(List.of(rol), 1),
                        new Listado<>(List.of(asignacion), 1)), null, null, List.of()));
        mvc.perform(get("/auditoria").param("vista", "roles")).andExpect(status().isOk())
                .andExpect(content().string(containsString("GESTOR_VENTAS")))
                .andExpect(content().string(containsString("No requiere contraseña")))
                .andExpect(content().string(containsString("Asignación de roles")))
                .andExpect(content().string(containsString(">APP_VENTAS</a>")))
                .andExpect(content().string(not(containsString("id=\"usuarios-title\""))));
    }

    @Test
    void muestraElOrigenDeCadaPrivilegioYLasObservaciones() throws Exception {
        var seleccionado = new AuditoriaDto.Beneficiario("APP_VENTAS", "USUARIO", false);
        var privilegios = new AuditoriaDto.PrivilegiosAuditoria(
                List.of(seleccionado, new AuditoriaDto.Beneficiario("GESTOR_VENTAS", "ROL", false)), seleccionado,
                "APP_VENTAS", List.of(new AuditoriaDto.RolEfectivo("GESTOR_VENTAS", 1, null, false, true),
                        new AuditoriaDto.RolEfectivo("RESOURCE", 2, "GESTOR_VENTAS", false, true)),
                new Listado<>(List.of(
                        new AuditoriaDto.PrivilegioSistema("CREATE SESSION", "APP_VENTAS", true, false),
                        new AuditoriaDto.PrivilegioSistema("CREATE TABLE", "RESOURCE", false, false)), 2), 0,
                new Listado<>(List.of(new AuditoriaDto.PrivilegioObjeto("SELECT", "VENTAS", "FACTURAS",
                        "TABLE", "GESTOR_VENTAS", "VENTAS", false, true)), 1),
                List.of("No se identificaron privilegios de alto impacto."));
        responder(new AuditoriaDto(filtros(VistaAuditoria.PRIVILEGIOS), CONTEXTO, RESUMEN, null, null,
                privilegios, null, List.of()));
        mvc.perform(get("/auditoria").param("vista", "PRIVILEGIOS")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Observaciones de seguridad: APP_VENTAS")))
                .andExpect(content().string(containsString("Mediante GESTOR_VENTAS")))
                .andExpect(content().string(containsString("Mediante el rol RESOURCE")))
                .andExpect(content().string(containsString("VENTAS.FACTURAS")))
                .andExpect(content().string(containsString("No se identificaron privilegios de alto impacto.")))
                .andExpect(content().string(containsString("selected=\"selected\">APP_VENTAS</option>")))
                .andExpect(content().string(containsString("privilegios otorgados a PUBLIC")));
    }

    @Test
    void avisaCuandoElBeneficiarioNoExiste() throws Exception {
        var privilegios = new AuditoriaDto.PrivilegiosAuditoria(List.of(), null, "FANTASMA", null, null, null,
                null, List.of());
        responder(new AuditoriaDto(filtros(VistaAuditoria.PRIVILEGIOS), CONTEXTO, RESUMEN, null, null,
                privilegios, null, List.of()));
        mvc.perform(get("/auditoria").param("vista", "PRIVILEGIOS").param("beneficiario", "FANTASMA"))
                .andExpect(content().string(containsString("No existe un usuario o rol llamado «FANTASMA»")));
    }

    @Test
    void distingueAusenciaDeProblemasDeFaltaDePermisos() throws Exception {
        var vacio = new Listado<AuditoriaDto.ErrorCompilacion>(List.of(), 0);
        var indice = new AuditoriaDto.IndiceInutilizable("SYS", "I_FED_BINDS$", null, "SYS", "FED$BINDS");
        responder(new AuditoriaDto(filtros(VistaAuditoria.OBJETOS), CONTEXTO, RESUMEN, null, null, null,
                new AuditoriaDto.ObjetosAuditoria(null, vacio, new Listado<>(List.of(), 0),
                        new Listado<>(List.of(indice), 1)),
                List.of("DBA_OBJECTS: vista no accesible o inexistente.")));
        mvc.perform(get("/auditoria").param("vista", "OBJETOS")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Objetos inválidos (DBA_OBJECTS): vista no accesible")))
                .andExpect(content().string(containsString("SELECT_CATALOG_ROLE")))
                .andExpect(content().string(containsString("No disponible")))
                .andExpect(content().string(containsString("No hay errores ni advertencias de compilación")))
                .andExpect(content().string(containsString("Ningún objeto depende de objetos inválidos")))
                .andExpect(content().string(containsString("I_FED_BINDS$")))
                .andExpect(content().string(containsString("Índice completo")));
    }

    @Test
    void sinConexionInvitaAConfigurarla() throws Exception {
        responder(new AuditoriaDto(filtros(VistaAuditoria.USUARIOS), null, null, null, null, null, null,
                List.of("No hay conexión configurada. Conecte una instancia Oracle.")));
        mvc.perform(get("/auditoria")).andExpect(status().isOk())
                .andExpect(content().string(containsString("No hay conexión configurada")))
                .andExpect(content().string(containsString("Configurar conexión")))
                .andExpect(content().string(not(containsString("SELECT_CATALOG_ROLE"))));
    }

    @Test
    void parametrosDesconocidosUsanValoresPorDefecto() throws Exception {
        responder(new AuditoriaDto(filtros(VistaAuditoria.USUARIOS), null, null, null, null, null, null, List.of()));
        mvc.perform(get("/auditoria").param("vista", "x").param("origen", "y").param("limite", "abc")
                .param("alcance", "z")).andExpect(status().isOk());
        verify(service).obtenerAuditoria(VistaAuditoria.USUARIOS, FiltroOrigen.TODOS, null, null, null,
                OrigenPrivilegio.TODOS);
    }
}
