# Evidencias de pruebas

## Objetivo

Las pruebas automatizadas permiten verificar componentes principales de la aplicación y comprobar su comportamiento en los escenarios cubiertos por la suite.

## Pruebas existentes

Actualmente existen 20 clases de prueba:

- **7 de controladores:** verifican respuestas y flujos de la capa web para los componentes cubiertos.
- **7 de servicios:** comprueban reglas y coordinación de operaciones de la aplicación.
- **5 de repositorios:** evalúan consultas y acceso a datos, incluyendo interacciones simuladas con JDBC.
- **1 de la aplicación:** verifica la carga del contexto de Spring Boot.

## Ejecución de pruebas

Las pruebas se ejecutaron mediante Maven Wrapper:

```powershell
.\mvnw.cmd test
```

| Elemento | Resultado |
|---|---:|
| Pruebas ejecutadas | 185 |
| Exitosas | 185 |
| Fallidas | 0 |
| Errores | 0 |
| Omitidas | 0 |
| Resultado | BUILD SUCCESS |

## Resultado

La suite terminó exitosamente: las 185 pruebas ejecutadas fueron superadas.

## Alcance y observaciones

- Las pruebas automatizadas cubren principalmente controladores, servicios y repositorios.
- Existe una prueba de contexto de la aplicación.
- La cobertura automatizada incluye principalmente Estado, Rendimiento, Almacenamiento, Auditoría y Mantenimiento Preventivo.
- No existen pruebas automatizadas dedicadas específicamente al módulo de Respaldo y Recuperación.
- Las pruebas automatizadas no sustituyen la validación manual de la aplicación contra una instancia Oracle real.
