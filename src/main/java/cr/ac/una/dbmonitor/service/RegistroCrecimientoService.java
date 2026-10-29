package cr.ac.una.dbmonitor.service;

import cr.ac.una.dbmonitor.dto.CrecimientoDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Presenta exclusivamente registros persistidos en Oracle. No usa el JSON heredado. */
@Service
public class RegistroCrecimientoService {
    public List<CrecimientoDto.Medicion> obtenerMediciones(List<CrecimientoDto.Registro> registros,
                                                         CrecimientoDto.Origen origen) {
        if (origen == null || !origen.valida() || registros == null) {
            return List.of();
        }
        var ordenados = registros.stream()
                .filter(r -> r != null && r.valido() && origen.mismaBase(r.origen()))
                .sorted(Comparator.comparing(CrecimientoDto.Registro::fecha)
                        .thenComparing(CrecimientoDto.Registro::id)).toList();
        var resultado = new ArrayList<CrecimientoDto.Medicion>();
        CrecimientoDto.Registro anterior = null;
        for (var registro : ordenados) {
            BigDecimal variacion = anterior == null ? null : registro.usadoBytes().subtract(anterior.usadoBytes());
            BigDecimal porcentaje = anterior == null || anterior.usadoBytes().signum() == 0 ? null
                    : variacion.multiply(BigDecimal.valueOf(100))
                    .divide(anterior.usadoBytes(), 2, RoundingMode.HALF_UP);
            resultado.add(new CrecimientoDto.Medicion(registro.fecha(), registro.tamanoTotalBytes(),
                    registro.usadoBytes(), registro.libreBytes(), variacion, porcentaje));
            anterior = registro;
        }
        return List.copyOf(resultado.reversed());
    }
}