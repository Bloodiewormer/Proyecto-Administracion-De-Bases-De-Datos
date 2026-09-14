package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.service.EstadoInstanciaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Controller
public class EstadoInstanciaController {
    private final EstadoInstanciaService service;

    public EstadoInstanciaController(EstadoInstanciaService service) {
        this.service = service;
    }

    @GetMapping("/estado")
    public String estado(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var estado = service.obtenerEstado();
        model.addAttribute("estado", estado);
        var uptime = estado.uptime();
        model.addAttribute("uptimeTexto", uptime == null ? "No disponible"
                : uptime.toDays() + " d " + uptime.toHoursPart() + " h "
                + uptime.toMinutesPart() + " min " + uptime.toSecondsPart() + " s");
        model.addAttribute("tamanosSga", estado.memoria() == null ? List.of()
                : estado.memoria().componentes().stream().map(c -> memoriaLegible(c.bytes())).toList());
        model.addAttribute("totalSgaTexto", memoriaLegible(estado.memoria() == null
                ? null : estado.memoria().totalSgaBytes()));
        var avisos = new LinkedHashMap<String, List<String>>();
        for (var seccion : List.of("general", "instancia", "base", "memoria")) {
            avisos.put(seccion, new ArrayList<>());
        }
        for (var aviso : estado.avisos()) {
            String seccion = aviso.startsWith("V$INSTANCE") || aviso.startsWith("Tiempo de actividad")
                    ? "instancia" : aviso.startsWith("V$SGA") || aviso.startsWith("Total SGA")
                    ? "memoria" : aviso.startsWith("V$DATABASE") || aviso.startsWith("V$CONTAINERS")
                    ? "base" : "general";
            avisos.get(seccion).add(aviso.replace("V$INSTANCE:", "Instancia:")
                    .replace("V$DATABASE:", "Base de datos:").replace("V$CONTAINERS:", "Contenedor:")
                    .replace("V$SGA:", "Memoria SGA:").replace(" (CON_ID = 0)", ""));
        }
        model.addAttribute("avisos", avisos);
        return "estado";
    }

    private String memoriaLegible(BigDecimal bytes) {
        if (bytes == null) {
            return "No disponible";
        }
        var gib = BigDecimal.valueOf(1073741824);
        var mib = BigDecimal.valueOf(1048576);
        if (bytes.compareTo(gib) >= 0) {
            return bytes.divide(gib, 2, RoundingMode.HALF_UP).toPlainString() + " GiB";
        }
        if (bytes.compareTo(mib) >= 0) {
            return bytes.divide(mib, 2, RoundingMode.HALF_UP).toPlainString() + " MiB";
        }
        return bytes.toPlainString() + " bytes";
    }
}
