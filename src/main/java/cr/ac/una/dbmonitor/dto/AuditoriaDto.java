package cr.ac.una.dbmonitor.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuditoriaDto(FiltrosAuditoria filtros, Contexto contexto, Resumen resumen,
                           UsuariosAuditoria usuarios, RolesAuditoria roles, PrivilegiosAuditoria privilegios,
                           ObjetosAuditoria objetos, List<String> avisos) {
    public AuditoriaDto {
        avisos = List.copyOf(avisos);
    }

    public AuditoriaDto conPrivilegios(PrivilegiosAuditoria nuevos) {
        return new AuditoriaDto(filtros, contexto, resumen, usuarios, roles, nuevos, objetos, avisos);
    }

    public record Contexto(String contenedor, boolean esPdb, String usuarioSesion) {}

    public record Resumen(Integer usuarios, Integer usuariosOrganizacion, Integer cuentasRestringidas,
                          Integer roles, Integer objetosInvalidos) {}

    public record UsuariosAuditoria(Listado<Usuario> usuarios, List<CuentaAdministrativa> cuentasAdministrativas) {}

    public record Usuario(String nombre, String estadoCuenta, LocalDateTime creacion, LocalDateTime bloqueo,
                          LocalDateTime expiracion, String tablespacePorDefecto, String perfil, String autenticacion,
                          boolean comun, boolean mantenidoPorOracle, LocalDateTime ultimoIngreso,
                          int rolesDirectos, int privilegiosSistemaDirectos) {}

    public record CuentaAdministrativa(String nombre, List<String> privilegios, String estadoCuenta,
                                       String autenticacion, boolean comun, LocalDateTime ultimoIngreso) {}

    public record RolesAuditoria(Listado<Rol> roles, Listado<AsignacionRol> asignaciones) {}

    public record Rol(String nombre, String autenticacion, boolean comun, boolean mantenidoPorOracle,
                      int usuariosAsignados, int rolesAsignados) {}

    public record AsignacionRol(String beneficiario, String tipoBeneficiario, String rol,
                                boolean conAdministracion, boolean predeterminado, boolean comun) {}

    public record PrivilegiosAuditoria(List<Beneficiario> beneficiarios, Beneficiario seleccionado,
                                       String nombreSolicitado, List<RolEfectivo> rolesEfectivos,
                                       Listado<PrivilegioSistema> sistema, Integer privilegiosAny,
                                       Listado<PrivilegioObjeto> objetos, List<String> observaciones) {
        public PrivilegiosAuditoria {
            beneficiarios = beneficiarios == null ? null : List.copyOf(beneficiarios);
            rolesEfectivos = rolesEfectivos == null ? null : List.copyOf(rolesEfectivos);
            observaciones = List.copyOf(observaciones);
        }

        public PrivilegiosAuditoria conPresentacion(List<Beneficiario> visibles, List<String> nuevas) {
            return new PrivilegiosAuditoria(visibles, seleccionado, nombreSolicitado, rolesEfectivos, sistema,
                    privilegiosAny, objetos, nuevas);
        }
    }

    public record Beneficiario(String nombre, String tipo, boolean mantenidoPorOracle) {
        public boolean esUsuario() {
            return "USUARIO".equals(tipo);
        }
    }

    public record RolEfectivo(String rol, int nivel, String ruta, boolean conAdministracion, boolean predeterminado) {
        public boolean directo() {
            return nivel == 1;
        }
    }

    public record PrivilegioSistema(String privilegio, String otorgadoA, boolean directo, boolean conAdministracion) {}

    public record PrivilegioObjeto(String privilegio, String propietario, String objeto, String tipoObjeto,
                                   String otorgadoA, String otorgante, boolean directo, boolean otorgable) {}

    public record ObjetosAuditoria(Listado<ObjetoInvalido> invalidos, Listado<ErrorCompilacion> errores,
                                   Listado<DependenciaProblematica> dependencias,
                                   Listado<IndiceInutilizable> indices) {}

    public record ObjetoInvalido(String propietario, String nombre, String tipo, String estado,
                                 LocalDateTime ultimaModificacion, int errores) {}

    public record ErrorCompilacion(String propietario, String nombre, String tipo, int linea, int posicion,
                                   String mensaje, String atributo) {}

    public record DependenciaProblematica(String propietario, String nombre, String tipo, String estado,
                                          String propietarioReferido, String nombreReferido, String tipoReferido) {}

    public record IndiceInutilizable(String propietario, String indice, String particion,
                                     String propietarioTabla, String tabla) {}
}
