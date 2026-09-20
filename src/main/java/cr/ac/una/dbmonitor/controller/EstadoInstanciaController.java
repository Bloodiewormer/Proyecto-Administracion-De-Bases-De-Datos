package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.service.EstadoInstanciaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
public class EstadoInstanciaController {
    private final EstadoInstanciaService service;
    private final Formatos formatos;

    public EstadoInstanciaController(EstadoInstanciaService service, Formatos formatos) {
        this.service = service;
        this.formatos = formatos;
    }

    @GetMapping("/estado")
    public String estado(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var estado = service.obtenerEstado();
        model.addAttribute("estado", estado);
        model.addAttribute("formatos", formatos);
        var avisos = new LinkedHashMap<String, List<String>>();
        for (var seccion : List.of("general", "instancia", "base", "memoria")) {
            avisos.put(seccion, new ArrayList<>());
        }
        for (var aviso : estado.avisos()) {
            avisos.get(seccion(aviso)).add(legible(aviso));
        }
        model.addAttribute("avisos", avisos);
        return "estado";
    }

    private String seccion(String aviso) {
        if (aviso.startsWith("V$INSTANCE") || aviso.startsWith("Tiempo de actividad")) {
            return "instancia";
        }
        if (aviso.startsWith("V$SGA") || aviso.startsWith("Total SGA") || aviso.startsWith("Memoria SGA")) {
            return "memoria";
        }
        if (aviso.startsWith("V$DATABASE") || aviso.startsWith("V$CONTAINERS")) {
            return "base";
        }
        return "general";
    }

    private String legible(String aviso) {
        return aviso.replace("V$INSTANCE:", "Instancia:")
                .replace("V$DATABASE:", "Base de datos:")
                .replace("V$CONTAINERS:", "Bases administradas:")
                .replace("V$SGASTAT/V$PGASTAT:", "Uso de memoria:")
                .replace("V$SGA:", "Memoria SGA:")
                .replace(" (CON_ID = 0)", "");
    }
}
