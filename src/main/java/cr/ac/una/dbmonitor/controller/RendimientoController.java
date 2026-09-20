package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.OrdenConsultas;
import cr.ac.una.dbmonitor.service.RendimientoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
public class RendimientoController {
    private final RendimientoService service;
    private final Formatos formatos;

    public RendimientoController(RendimientoService service, Formatos formatos) {
        this.service = service;
        this.formatos = formatos;
    }

    @GetMapping("/rendimiento")
    public String rendimiento(@RequestParam(name = "orden", required = false) String orden,
                              @RequestParam(name = "top", required = false) String top,
                              Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var rendimiento = service.obtenerRendimiento(OrdenConsultas.desde(orden), entero(top));
        model.addAttribute("rendimiento", rendimiento);
        model.addAttribute("formatos", formatos);
        model.addAttribute("ordenes", OrdenConsultas.values());
        var avisos = new LinkedHashMap<String, List<String>>();
        for (var seccion : List.of("general", "consultas", "sesiones", "procesos")) {
            avisos.put(seccion, new ArrayList<>());
        }
        for (var aviso : rendimiento.avisos()) {
            avisos.get(seccion(aviso)).add(legible(aviso));
        }
        model.addAttribute("avisos", avisos);
        return "rendimiento";
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

    private String seccion(String aviso) {
        if (aviso.startsWith("V$SQLSTATS") || aviso.startsWith("Tiempo promedio")) {
            return "consultas";
        }
        if (aviso.startsWith("V$SESSION")) {
            return "sesiones";
        }
        if (aviso.startsWith("V$PROCESS") || aviso.startsWith("V$SESSTAT")) {
            return "procesos";
        }
        return "general";
    }

    private String legible(String aviso) {
        return aviso.replace("V$SQLSTATS:", "Consultas:")
                .replace("V$SESSION:", "Sesiones:")
                .replace("V$PROCESS:", "Procesos:")
                .replace("V$SESSTAT:", "Consumo de CPU:");
    }
}
