package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.service.RespaldoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RespaldoController {
    private final RespaldoService service;
    private final Formatos formatos;

    public RespaldoController(RespaldoService service, Formatos formatos) {
        this.service = service;
        this.formatos = formatos;
    }

    @GetMapping("/respaldo")
    public String respaldo(@RequestParam(name = "cantidad", required = false) String cantidad,
                           Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var respaldo = service.obtenerRespaldos(entero(cantidad));
        model.addAttribute("respaldo", respaldo);
        model.addAttribute("formatos", formatos);
        model.addAttribute("avisos", respaldo.avisos().stream().map(this::legible).toList());
        return "respaldo";
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
        return aviso.replace("V$RMAN_BACKUP_JOB_DETAILS:", "Respaldos:")
                .replace("V$DATABASE:", "Base de datos:");
    }
}
