# Bitácora del proyecto

## 1. Información general

- **Proyecto:** Aplicación web de monitoreo y administración de bases de datos Oracle.
- **Curso:** Administración de Bases de Datos.
- **Periodo:** Agosto–octubre de 2026.
- **Integrantes:** Alice Fajardo Zaldivar, Brandon Vinicio Brenes Umaña, Daniel Alfonso Castro Serrano, David Alberto González Córdoba y Felipe Antonio Ugalde Vallejos.

## 2. Objetivo de la bitácora

Registrar la evolución progresiva del sistema a partir de los cambios efectivamente incorporados al repositorio. Las fechas, responsables y actividades se basan en el historial de Git y en los archivos asociados a cada cambio.

## 3. Registro cronológico

| Fecha | Integrante(s) | Actividad realizada | Módulo / componente | Resultado |
|---|---|---|---|---|
| 26/08/2026 | David González Córdoba (Bloodiewormer) | Creación inicial del repositorio y registro de la licencia y README inicial. | Inicio del proyecto | Repositorio con su primer commit. |
| 07/09/2026 | Alice Fajardo Zaldivar | Configuración base con Spring Boot y Java 21; creación de la interfaz inicial y de la conexión configurable a Oracle. | Aplicación y conexión | Aplicación inicial ejecutable y formulario para conexión Oracle. |
| 13/09/2026 | Alice Fajardo Zaldivar | Incorporación de controladores, servicios, repositorio, DTO y vista para consultar el estado general de la instancia. | Módulo 1 | Primera vista de estado general. |
| 19/09/2026 | David González Córdoba | Ampliación y finalización de la consulta del estado de instancia, seguida de la incorporación de consultas y vista de rendimiento. | Módulos 1 y 2 | Estado general ampliado y monitoreo de rendimiento integrado. |
| 20/09/2026 | Brandon Vinicio Brenes Umaña | Incorporación de componentes de almacenamiento, consultas, servicios, vista, navegación y mecanismo de registro de crecimiento. | Módulo 3 | Gestión de almacenamiento disponible en la aplicación. |
| 28/09/2026 | Alice Fajardo Zaldivar | Ajustes de presentación en estilos y vistas de inicio, conexión y estado; actualización visual adicional de la página principal. | Interfaz | Presentación visual refinada. |
| 09/10/2026 | Daniel Alfonso Castro Serrano | Incorporación del DTO y consultas al repositorio para leer respaldos RMAN. | Módulo 4 | Base de consulta de respaldos implementada. |
| 11/10/2026 | Daniel Alfonso Castro Serrano | Incorporación de servicio y controlador de respaldo; ampliación de DTO y repositorio para evaluar el último respaldo exitoso y contemplar el contenedor conectado. | Módulo 4 | Endpoint de respaldo y evaluación del estado disponibles. |
| 14/10/2026 | Daniel Alfonso Castro Serrano | Incorporación de la vista de respaldo, interpretación del estado, último respaldo exitoso e historial, y acceso desde el menú. | Módulo 4 | Flujo de consulta de respaldo integrado a la interfaz. |
| 20/10/2026 | Felipe Antonio Ugalde Vallejos | Incorporación de consultas, servicios, controlador y vista de auditoría sobre usuarios, cuentas administrativas, roles, asignaciones y objetos relacionados. | Módulo 5 | Auditoría integrada al menú principal. |
| 28/10/2026 | Felipe Antonio Ugalde Vallejos | Incorporación de detección y operaciones de mantenimiento preventivo, con componentes de dominio, acceso a datos, servicio, controlador y vista. | Módulo 6 | Mantenimiento preventivo disponible y documentado. |
| 29/10/2026 | Alice Fajardo Zaldivar | Mejora de almacenamiento y crecimiento; actualización del README; incorporación de manuales de usuario y técnico; ajuste visual final. | Módulo 3, documentación e interfaz | Funcionalidad de almacenamiento refinada y documentación principal incorporada. |

## 4. Evolución de los módulos

### Módulo 1 — Estado general de la instancia

La vista inicial se incorporó el 13/09/2026 con componentes para consultar datos de instancia, base de datos y memoria. El 19/09/2026 se completaron y ampliaron sus componentes de consulta y presentación.

### Módulo 2 — Monitoreo de rendimiento

El 19/09/2026 se incorporaron controladores, DTO, repositorio, servicio y vista de rendimiento. El código consulta información de consultas principales, sesiones, procesos y métricas de la instancia.

### Módulo 3 — Gestión del almacenamiento

El módulo se incorporó el 20/09/2026 con consultas de almacenamiento, tablas de datos, servicios, vista y mecanismo SQL para registrar crecimiento. El 29/10/2026 se modificaron sus controladores, DTO, repositorios, servicios, vista y script; además se añadió documentación específica y pruebas asociadas.

### Módulo 4 — Respaldo y recuperación

La implementación comenzó el 09/10/2026 con la consulta de respaldos RMAN. El 11/10 se agregaron servicio y controlador para interpretar el último respaldo y su estado; el 14/10 se incorporaron la pantalla, el historial visible y el acceso desde navegación.

### Módulo 5 — Auditoría

El 20/10/2026 se añadieron consultas, DTO, repositorio, servicio, controlador y vista para información de usuarios, roles, privilegios y objetos con problemas. El módulo también cuenta con documentación y pruebas en el repositorio.

### Módulo 6 — Mantenimiento preventivo

El 28/10/2026 se incorporaron los componentes de detección y mantenimiento, incluyendo operaciones relacionadas con estadísticas y recompilación de objetos, junto con su vista, documentación y pruebas.

## 5. Documentación y cierre

El historial registra README inicial desde el primer commit y su actualización el 29/10/2026. Ese mismo día se añadieron los manuales de usuario y técnico en PDF y se aplicó la mejora visual final. También constan documentos específicos de los módulos 3, 5 y 6, incorporados junto con sus respectivos desarrollos.

## 6. Estado final

Esta bitácora documenta la evolución del proyecto hasta los últimos cambios registrados en el repositorio, con fecha 29/10/2026.
