package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.ResultadoMantenimiento;
import cr.ac.una.dbmonitor.dto.VistaMantenimiento;
import cr.ac.una.dbmonitor.repository.MantenimientoRepository;
import cr.ac.una.dbmonitor.service.MantenimientoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Las operaciones usan POST y redirigen al GET (patrón Post/Redirect/Get) para que recargar la página
 * no vuelva a ejecutarlas; el resultado viaja como atributo flash.
 */
@Controller
public class MantenimientoController {
    private final MantenimientoService service;
    private final Formatos formatos;

    public MantenimientoController(MantenimientoService service, Formatos formatos) {
        this.service = service;
        this.formatos = formatos;
    }

    @GetMapping("/mantenimiento")
    public String mantenimiento(@RequestParam(name = "vista", required = false) String vista,
                                @RequestParam(name = "todas", required = false) String todas,
                                @RequestParam(name = "limite", required = false) String limite,
                                Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var mantenimiento = service.obtenerMantenimiento(VistaMantenimiento.desde(vista), "true".equals(todas),
                entero(limite));
        model.addAttribute("mantenimiento", mantenimiento);
        model.addAttribute("formatos", formatos);
        model.addAttribute("vistas", VistaMantenimiento.values());
        model.addAttribute("avisos", mantenimiento.avisos().stream().map(this::legible).toList());
        model.addAttribute("faltanPermisos", mantenimiento.avisos().stream().anyMatch(a ->
                a.endsWith("permisos insuficientes.") || a.endsWith("vista no accesible o inexistente.")));
        model.addAttribute("tiempoMaximo", MantenimientoRepository.TIEMPO_MAXIMO_SEGUNDOS);
        return "mantenimiento";
    }

    @PostMapping("/mantenimiento/estadisticas/tabla")
    public String recalcularTabla(@RequestParam(name = "esquema", required = false) String esquema,
                                  @RequestParam(name = "tabla", required = false) String tabla,
                                  @RequestParam(name = "todas", required = false) String todas,
                                  RedirectAttributes redirect) {
        return volver(VistaMantenimiento.ESTADISTICAS, todas,
                service.recalcularEstadisticasTabla(esquema, tabla), redirect);
    }

    @PostMapping("/mantenimiento/estadisticas/esquema")
    public String recalcularEsquema(@RequestParam(name = "esquema", required = false) String esquema,
                                    @RequestParam(name = "todas", required = false) String todas,
                                    RedirectAttributes redirect) {
        return volver(VistaMantenimiento.ESTADISTICAS, todas, service.recalcularEstadisticasEsquema(esquema),
                redirect);
    }

    @PostMapping("/mantenimiento/recompilar/objeto")
    public String recompilarObjeto(@RequestParam(name = "esquema", required = false) String esquema,
                                   @RequestParam(name = "nombre", required = false) String nombre,
                                   @RequestParam(name = "tipo", required = false) String tipo,
                                   RedirectAttributes redirect) {
        return volver(VistaMantenimiento.RECOMPILACION, null, service.recompilarObjeto(esquema, nombre, tipo),
                redirect);
    }

    @PostMapping("/mantenimiento/recompilar/esquema")
    public String recompilarEsquema(@RequestParam(name = "esquema", required = false) String esquema,
                                    RedirectAttributes redirect) {
        return volver(VistaMantenimiento.RECOMPILACION, null, service.recompilarEsquema(esquema), redirect);
    }

    private String volver(VistaMantenimiento vista, String todas, ResultadoMantenimiento resultado,
                          RedirectAttributes redirect) {
        redirect.addAttribute("vista", vista.name());
        if ("true".equals(todas)) {
            redirect.addAttribute("todas", "true");
        }
        redirect.addFlashAttribute("resultado", resultado);
        return "redirect:/mantenimiento";
    }

    private Integer entero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String legible(String aviso) {
        return aviso.replace("DBA_TAB_STATISTICS/DBA_OBJECTS/DBA_ERRORS:", "Resumen de mantenimiento:")
                .replace("DBA_TAB_STATISTICS:", "Estadísticas de tablas (DBA_TAB_STATISTICS):")
                .replace("DBA_USERS:", "Esquemas (DBA_USERS):")
                .replace("DBA_OBJECTS:", "Objetos inválidos (DBA_OBJECTS):")
                .replace("DBA_ERRORS:", "Errores de compilación (DBA_ERRORS):")
                .replace("USERENV:", "Contexto de la sesión:");
    }
}
