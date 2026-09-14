package cr.ac.una.dbmonitor.dto;

public record BaseDatosDto(Base base, Contenedor contenedorActual) {
    public record Base(String nombre, String modoApertura, String cdb) {}
    public record Contenedor(Integer conId, String nombre, String modoApertura) {}
}
