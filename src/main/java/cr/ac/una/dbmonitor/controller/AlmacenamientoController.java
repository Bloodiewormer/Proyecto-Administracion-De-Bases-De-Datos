package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.FiltroTipoObjeto;
import cr.ac.una.dbmonitor.service.AlmacenamientoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
public class AlmacenamientoController {

    private final AlmacenamientoService service;
    private final Formatos formatos;

    public AlmacenamientoController(AlmacenamientoService service, Formatos formatos) {
        this.service = service;
        this.formatos = formatos;
    }

    @GetMapping("/almacenamiento")
    public String almacenamiento(@RequestParam(name = "tipo", required = false) String tipo,
                                 @RequestParam(name = "top", required = false) String top,
                                 Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");

        var filtro = FiltroTipoObjeto.desde(tipo);
        var limite = parseEntero(top);
        var datos = service.obtenerAlmacenamiento(filtro, limite);

        model.addAttribute("almacenamiento", datos);
        model.addAttribute("formatos", formatos);
        model.addAttribute("filtros", FiltroTipoObjeto.values());

        var avisos = new LinkedHashMap<String, List<String>>();
        for (var sec : List.of("general", "tablespaces", "archivos", "objetos", "crecimiento")) {
            avisos.put(sec, new ArrayList<>());
        }
        for (var aviso : datos.avisos()) {
            avisos.get(seccion(aviso)).add(legible(aviso));
        }
        model.addAttribute("avisos", avisos);

        return "almacenamiento";
    }

    @PostMapping("/almacenamiento/snapshot")
    public String registrarInstantanea(RedirectAttributes redirectAttributes) {
        try {
            service.registrarMedicionActual();
            redirectAttributes.addFlashAttribute("snapshotExitoso", true);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorSnapshot",
                    "No fue posible registrar la instantánea: " + ex.getMessage());
        }
        return "redirect:/almacenamiento";
    }

    private Integer parseEntero(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String seccion(String aviso) {
        if (aviso.startsWith("DBA_TABLESPACES")) {
            return "tablespaces";
        }
        if (aviso.startsWith("DBA_DATA_FILES/DBA_TEMP_FILES")) {
            return "archivos";
        }
        if (aviso.startsWith("DBA_SEGMENTS")) {
            return "objetos";
        }
        if (aviso.startsWith("DBA_HIST_TBSPC_SPACE_USAGE") || aviso.startsWith("Historial Oracle")) {
            return "crecimiento";
        }
        return "general";
    }

    private String legible(String aviso) {
        return aviso.replace("DBA_TABLESPACES:", "Tablespaces:")
                .replace("DBA_DATA_FILES/DBA_TEMP_FILES:", "Archivos de datos:")
                .replace("DBA_DATA_FILES/DBA_SEGMENTS/V$LOG:", "Resumen de base de datos:")
                .replace("DBA_SEGMENTS:", "Objetos de almacenamiento:")
                .replace("V$LOG:", "Redo de instancia (excluido de ocupación):")
                .replace("DBA_HIST_TBSPC_SPACE_USAGE:", "Histórico AWR de Oracle:");
    }
}
