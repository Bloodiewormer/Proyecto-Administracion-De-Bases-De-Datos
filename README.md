# Proyecto de Administración de Bases de Datos

## Descripción

Aplicación web para consultar información de una instancia Oracle y apoyar tareas de monitoreo, administración y auditoría. Incluye paneles para revisar el estado, rendimiento y almacenamiento, así como información de respaldos, auditoría y mantenimiento preventivo.

## Objetivo

Reunir en una interfaz las consultas y tareas de administración implementadas en el proyecto, para facilitar la observación del estado de Oracle, identificar situaciones que requieren atención y ejecutar algunas tareas de mantenimiento sobre la instancia conectada.

## Tecnologías

- Java 21.
- Spring Boot 3.5.16 (Web, Thymeleaf, JDBC y pruebas).
- Maven 3.9.16 mediante Maven Wrapper.
- Oracle Database 21c XE como entorno Oracle documentado para el proyecto.
- Oracle JDBC `ojdbc11`.
- HTML y CSS para las vistas web.

## Requisitos previos

- JDK 21.
- Oracle Database 21c XE iniciado y accesible para las funciones que consultan la base de datos.
- SQL*Plus para instalar el script del historial de crecimiento del Módulo 3.
- Un navegador web.

Maven está incluido mediante `mvnw` y `mvnw.cmd`, por lo que no es necesario instalarlo por separado. No se requiere un IDE específico.

## Configuración de Oracle

La aplicación no contiene una conexión Oracle fija en `application.properties`. Al abrir la opción **Cambiar conexión** en la aplicación, se proporcionan localmente los datos requeridos por el formulario: host, puerto, nombre del servicio, usuario y contraseña. La conexión se comprueba antes de activarse. El puerto inicial del formulario es `1521`; el host, servicio y usuario dependen del entorno utilizado.

La cuenta conectada necesita permisos de lectura sobre las vistas del diccionario y dinámicas que usa cada módulo. Las operaciones de mantenimiento también requieren los permisos correspondientes para ejecutarlas. Consulte la documentación de los módulos para conocer los detalles.

Para habilitar el historial de crecimiento del Módulo 3, ejecute desde la raíz del repositorio, con SQL*Plus y siguiendo los pasos y permisos administrativos descritos en [docs/modulo-3-almacenamiento.md](docs/modulo-3-almacenamiento.md), el script [mecanismo_crecimiento_oracle.sql](src/main/resources/db/mecanismo_crecimiento_oracle.sql). Este script no se ejecuta automáticamente al iniciar la aplicación. Crea o actualiza `HISTORIAL_CRECIMIENTO_BD`, `V_ALMACENAMIENTO_ACTUAL`, `SP_REGISTRAR_CRECIMIENTO` y `V_CRECIMIENTO_ANALITICO`; deja deshabilitado el job opcional de captura diaria. El historial nuevo se registra cuando se solicita una captura desde la aplicación.

## Ejecución

Desde la carpeta raíz del proyecto, inicie la aplicación con Maven Wrapper:

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux o macOS:**

```bash
./mvnw spring-boot:run
```

Cuando Spring Boot indique que inició, abra `http://localhost:8080` en el navegador. Configure la conexión Oracle desde **Cambiar conexión** para consultar los módulos. El puerto web corresponde al valor predeterminado de Spring Boot; el proyecto no define otro en `application.properties`.

## Módulos

1. **Estado general:** muestra información de la instancia, la base de datos, sus contenedores y memoria consultada en Oracle.
2. **Monitoreo de rendimiento:** consulta SQL principales, sesiones, bloqueos, procesos y métricas de CPU desde vistas dinámicas de Oracle.
3. **Gestión del almacenamiento:** presenta tablespaces, archivos, objetos y métricas de ocupación; permite registrar y consultar mediciones de crecimiento.
4. **Respaldo y recuperación:** consulta el historial y el último respaldo RMAN, el modo de registro de la base y mensajes relacionados. El módulo consulta respaldos existentes; no ejecuta copias ni restauraciones.
5. **Auditoría:** consulta usuarios, roles, privilegios, objetos inválidos, errores de compilación, dependencias e índices inutilizables.
6. **Mantenimiento preventivo:** detecta estadísticas ausentes o desactualizadas y objetos inválidos; permite recopilar estadísticas y recompilar objetos o esquemas no mantenidos por Oracle, según los permisos de la cuenta.

## Módulo 3: almacenamiento y crecimiento

Oracle es la fuente de las métricas actuales y del historial de crecimiento. `V_ALMACENAMIENTO_ACTUAL` proporciona la medición del contenedor conectado; `SP_REGISTRAR_CRECIMIENTO` registra una captura en `HISTORIAL_CRECIMIENTO_BD`; y `V_CRECIMIENTO_ANALITICO` ofrece los datos históricos y sus variaciones. La aplicación consulta esos objetos de Oracle para mostrar el historial. El archivo JSON heredado no se utiliza como fuente activa de crecimiento.

## Estructura del proyecto

```text
.
├── docs/                         Documentación de los módulos 3, 5 y 6
├── src/
│   ├── main/
│   │   ├── java/cr/ac/una/dbmonitor/
│   │   │   ├── controller/       Controladores web
│   │   │   ├── dto/              Objetos de transferencia de datos
│   │   │   ├── repository/       Consultas y acceso a Oracle
│   │   │   └── service/          Servicios de la aplicación
│   │   └── resources/
│   │       ├── db/               Script SQL de crecimiento
│   │       ├── static/           CSS e imágenes
│   │       ├── templates/        Vistas Thymeleaf
│   │       └── application.properties
│   └── test/java/cr/ac/una/dbmonitor/
│       ├── controller/
│       ├── repository/
│       └── service/
├── .mvn/                         Configuración de Maven Wrapper
├── mvnw, mvnw.cmd                Lanzadores de Maven Wrapper
└── pom.xml                       Dependencias y configuración de Maven
```

## Pruebas

Hay pruebas automatizadas Java ubicadas en `src/test/java/cr/ac/una/dbmonitor/`, organizadas en pruebas de controladores, repositorios, servicios y la aplicación. Utilizan `spring-boot-starter-test`.

Para ejecutarlas con Maven Wrapper:

```powershell
.\mvnw.cmd test
```

En Linux o macOS se puede ejecutar `./mvnw test`.

## Documentación disponible

- [Módulo 3: almacenamiento](docs/modulo-3-almacenamiento.md)
- [Módulo 5: auditoría](docs/modulo-5-auditoria.md)
- [Módulo 6: mantenimiento preventivo](docs/modulo-6-mantenimiento.md)

## Seguridad y credenciales

No se almacenan credenciales reales, contraseñas ni tokens en el repositorio, en el README ni en archivos. Cada usuario debe proporcionar localmente en el formulario de conexión el host, puerto, servicio, usuario y contraseña autorizados para su entorno Oracle. El archivo application.properties no contiene credenciales de conexión.

## Integrantes

- Alice Fajardo Zaldivar
- Brandon Vinicio Brenes Umaña
- Daniel Alfonso Castro Serrano
- David Alberto González Córdoba
- Felipe Antonio Ugalde Vallejos
