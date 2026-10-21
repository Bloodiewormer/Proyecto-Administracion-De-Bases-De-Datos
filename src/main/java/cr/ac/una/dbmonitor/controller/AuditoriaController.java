package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.FiltroOrigen;
import cr.ac.una.dbmonitor.dto.OrigenPrivilegio;
import cr.ac.una.dbmonitor.dto.VistaAuditoria;
import cr.ac.una.dbmonitor.service.AuditoriaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class AuditoriaController {
    // Más específicos primero: una vista compuesta comienza con el nombre de una vista simple.
    private static final Map<String, String> NOMBRES_VISTAS = new LinkedHashMap<>();

    static {
        NOMBRES_VISTAS.put("DBA_USERS/DBA_ROLES/DBA_OBJECTS", "Resumen de auditoría");
        NOMBRES_VISTAS.put("DBA_USERS/DBA_ROLES", "Lista de usuarios y roles");
        NOMBRES_VISTAS.put("DBA_USERS", "Usuarios");
        NOMBRES_VISTAS.put("V$PWFILE_USERS", "Cuentas administrativas");
        NOMBRES_VISTAS.put("DBA_ROLES", "Roles");
        NOMBRES_VISTAS.put("DBA_ROLE_PRIVS", "Asignación de roles");
        NOMBRES_VISTAS.put("DBA_SYS_PRIVS", "Privilegios de sistema");
        NOMBRES_VISTAS.put("DBA_TAB_PRIVS", "Privilegios sobre objetos");
        NOMBRES_VISTAS.put("DBA_OBJECTS", "Objetos inválidos");
        NOMBRES_VISTAS.put("DBA_ERRORS", "Errores de compilación");
        NOMBRES_VISTAS.put("DBA_DEPENDENCIES", "Dependencias");
        NOMBRES_VISTAS.put("DBA_INDEXES", "Índices inutilizables");
        NOMBRES_VISTAS.put("USERENV", "Contexto de la sesión");
    }

    private final AuditoriaService service;
    private final Formatos formatos;

    public AuditoriaController(AuditoriaService service, Formatos formatos) {
        this.service = service;
        this.formatos = formatos;
    }

    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(name = "vista", required = false) String vista,
                            @RequestParam(name = "origen", required = false) String origen,
                            @RequestParam(name = "buscar", required = false) String buscar,
                            @RequestParam(name = "limite", required = false) String limite,
                            @RequestParam(name = "beneficiario", required = false) String beneficiario,
                            @RequestParam(name = "alcance", required = false) String alcance,
                            Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var auditoria = service.obtenerAuditoria(VistaAuditoria.desde(vista), FiltroOrigen.desde(origen), buscar,
                entero(limite), beneficiario, OrigenPrivilegio.desde(alcance));
        model.addAttribute("auditoria", auditoria);
        model.addAttribute("formatos", formatos);
        model.addAttribute("vistas", VistaAuditoria.values());
        model.addAttribute("origenes", FiltroOrigen.values());
        model.addAttribute("alcances", OrigenPrivilegio.values());
        model.addAttribute("avisos", auditoria.avisos().stream().map(this::legible).toList());
        model.addAttribute("faltanPermisos", auditoria.avisos().stream().anyMatch(this::esFaltaDePermisos));
        return "auditoria";
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

    private boolean esFaltaDePermisos(String aviso) {
        return aviso.endsWith("permisos insuficientes.") || aviso.endsWith("vista no accesible o inexistente.");
    }

    private String legible(String aviso) {
        for (var vista : NOMBRES_VISTAS.entrySet()) {
            var prefijo = vista.getKey() + ":";
            if (aviso.startsWith(prefijo)) {
                return vista.getValue() + " (" + vista.getKey() + "):" + aviso.substring(prefijo.length());
            }
        }
        return aviso;
    }
}
