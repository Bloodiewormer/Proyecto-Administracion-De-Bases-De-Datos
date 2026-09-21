package cr.ac.una.dbmonitor.dto;

import java.util.List;

public record AlmacenamientoDto(ResumenAlmacenamientoDto resumen,
                                List<TablespaceDto> tablespaces,
                                List<ArchivoDatosDto> archivos,
                                List<ObjetoAlmacenamientoDto> objetos,
                                CrecimientoDto crecimiento,
                                FiltroTipoObjeto filtroTipo,
                                int top,
                                List<String> avisos) {
    public AlmacenamientoDto {
        tablespaces = tablespaces == null ? List.of() : List.copyOf(tablespaces);
        archivos = archivos == null ? List.of() : List.copyOf(archivos);
        objetos = objetos == null ? List.of() : List.copyOf(objetos);
        avisos = avisos == null ? List.of() : List.copyOf(avisos);
    }
}
