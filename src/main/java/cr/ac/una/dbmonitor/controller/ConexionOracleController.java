package cr.ac.una.dbmonitor.controller;

import cr.ac.una.dbmonitor.dto.ConexionOracleDto;
import cr.ac.una.dbmonitor.service.ConexionOracleService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/conexion")
public class ConexionOracleController {
    private final ConexionOracleService service;

    public ConexionOracleController(ConexionOracleService service) {
        this.service = service;
    }

    @InitBinder("conexion")
    public void configurarBinding(WebDataBinder binder) {
        binder.setAllowedFields("host", "port", "serviceName", "username", "password");
    }

    @GetMapping
    public String formulario(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("conexion", new ConexionOracleDto());
        return "conexion";
    }

    @PostMapping
    public String conectar(@ModelAttribute("conexion") ConexionOracleDto form,
                           BindingResult errors, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        validar(form, errors);
        try {
            if (!errors.hasErrors()) {
                service.conectar(form);
                model.addAttribute("success", "Conexión establecida correctamente.");
            }
        } catch (IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
        } finally {
            form.setPassword(null);
        }
        return "conexion";
    }

    private void validar(ConexionOracleDto form, BindingResult errors) {
        if (form.getHost() == null || !form.getHost().trim().matches("[a-zA-Z0-9]+(?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?")) {
            errors.rejectValue("host", "invalid", "Ingrese un host o dirección IPv4 válido.");
        }
        if (errors.hasFieldErrors("port") || form.getPort() == null || form.getPort() < 1 || form.getPort() > 65535) {
            if (!errors.hasFieldErrors("port")) {
                errors.rejectValue("port", "invalid", "Ingrese un puerto entre 1 y 65535.");
            }
        }
        if (form.getServiceName() == null || !form.getServiceName().trim().matches("[a-zA-Z0-9_][a-zA-Z0-9_.$#-]*")) {
            errors.rejectValue("serviceName", "invalid", "Ingrese un Service Name válido.");
        }
        if (form.getUsername() == null || form.getUsername().isBlank()) {
            errors.rejectValue("username", "required", "Ingrese el usuario.");
        }
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            errors.rejectValue("password", "required", "Ingrese la contraseña.");
        }
    }
}
