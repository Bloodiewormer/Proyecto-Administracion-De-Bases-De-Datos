package cr.ac.una.dbmonitor.dto;

import java.util.List;

public record BaseDatosDto(Base base, List<Contenedor> contenedores) {
    public BaseDatosDto {
        contenedores = contenedores == null ? null : List.copyOf(contenedores);
    }

    public Contenedor contenedorActual() {
        return contenedores == null ? null
                : contenedores.stream().filter(Contenedor::actual).findFirst().orElse(null);
    }

    public record Base(String nombre, String modoApertura, String cdb) {}
    public record Contenedor(Integer conId, String nombre, String modoApertura, boolean actual) {}
}
