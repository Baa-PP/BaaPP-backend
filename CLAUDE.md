# CLAUDE.md — Política de ingeniería de BaaPP-backend

Backend **Kotlin 2.1 + Spring Boot 3.4 (Java 21) + JPA + PostgreSQL + Flyway**, con **Arquitectura Onion** (paquete base `com.agrotech.oviapp`). Estas reglas son **obligatorias en cada edición de código**. Solo hay excepciones si el usuario lo dice explícitamente.

Principios no negociables: **Onion**, **SOLID**, **TDD** y **código limpio**. El código existente que los incumple es deuda, no precedente (ver §6).

---

## 0. Antes de escribir código

- **Cada vez que empiezas algo (una tarea, un paso, una petición nueva), lo primero que escribes es "tung tung tung sahur".**

0. **MemPalace siempre (ahorra tokens).** Antes de leer archivos o volver a explorar, consulta MemPalace (`mempalace_search`, wing `baapp_backend`). Lee archivos solo si el palace no responde o un resultado puede estar desactualizado. Tras cada decisión, hallazgo o paso completado, guárdalo (`mempalace_add_drawer`; `mempalace_kg_add`/`kg_supersede` para hechos que cambian). Cierra cada sesión con `mempalace_diary_write`. El palace es una caché; si discrepa con el repo, manda el repo.
1. **Busca primero.** ¿Ya existe? ¿Hay un patrón en el repo? ¿Una dependencia ya instalada lo resuelve? No añadas una dependencia para lo que cabe en pocas líneas.
2. **Propón antes de actuar.** En cualquier cambio no trivial, describe el plan en 2–5 líneas (incluidos los tests que escribirás primero) y espera aprobación. Trivial = un cambio obvio de una línea.
3. **Mantén el README al día** cuando cambien los requisitos o los pasos para compilar/ejecutar.

---

## 1. Arquitectura Onion

```
src/main/kotlin/com/agrotech/oviapp/
  domain/          model/ (entidades, value objects), service/ (reglas de negocio), puertos (interfaces de repositorio)
  application/     casos de uso; orquestan el dominio a través de los puertos
  infrastructure/  adaptadores: JPA, Flyway, Excel (fastexcel), configuración
  presentation/    controllers REST, DTOs, security/ (RanchContextInterceptor)
  <feature>/       un feature = una carpeta propia con sus 4 capas (ej. dashboard/{domain,application,infrastructure,presentation})
src/main/resources/db/migration/   migraciones Flyway
src/test/kotlin/                   tests (incluye ArchUnit)
```

- **Regla de dependencia: siempre hacia adentro.** `presentation` / `infrastructure` → `application` → `domain`. **`domain` no importa nada de Spring, JPA, Jackson ni de otras capas.**
- Los puertos (interfaces) se definen en `domain` o `application`; sus implementaciones viven en `infrastructure`.
- Un controller nunca habla con un repositorio ni expone entidades: usa casos de uso y DTOs propios de `presentation`.
- Las entidades JPA son de `infrastructure`; se mapean al modelo de dominio en el borde.
- **Toda funcionalidad nueva respeta Onion.** La regla se verifica con ArchUnit (`archunit-junit5`): si añades una capa o paquete, actualiza esas reglas.
- **Cada feature nuevo va en su propia carpeta** bajo `com.agrotech.oviapp/<feature>/`, con sus carpetas `domain`, `application`, `infrastructure` y `presentation`. Un feature no importa clases internas de otro; lo compartido va en las capas raíz.
- Esquema de BD solo por migraciones Flyway versionadas; nunca edites una migración ya aplicada.

---

## 2. Cómo crear código nuevo

Antes de crear un archivo, **abre uno equivalente y copia su forma**. No introduzcas un estilo nuevo.

- **Dominio:** Kotlin puro, `data class` inmutables, `val` por defecto, invariantes validadas en el constructor/`init`.
- **Caso de uso:** una clase, una responsabilidad, una operación pública; depende de puertos, no de implementaciones.
- **Controller:** delgado (valida, delega, mapea). DTOs de entrada con Bean Validation (`@Valid`).
- **Contexto de rancho:** el acceso a datos por rancho pasa por `RanchContextInterceptor`; no lo sortees.
- Kotlin idiomático: sin `!!`, nulabilidad explícita, `sealed`/`when` exhaustivo para estados y errores.

---

## 3. SOLID

- **S —** Una clase, una razón de cambio. Si cuesta describirla en una frase, divídela. Sin servicios "dios".
- **O —** Extiende con nuevas implementaciones en vez de añadir `if/else` a código estable, pero solo cuando existe un segundo caso real.
- **L —** Las implementaciones de un puerto respetan su contrato completo.
- **I —** Puertos pequeños y específicos del consumidor.
- **D —** Casos de uso y dominio dependen de abstracciones; Spring inyecta las implementaciones por constructor (nunca `@Autowired` en campos).
- **Pragmatismo:** sin abstracciones especulativas. Tres casos parecidos justifican una abstracción; uno solo no.

---

## 3.1 Consistencia (un patrón por problema)

- **Errores:** excepciones de dominio tipadas, traducidas a respuestas HTTP en **un único** `@RestControllerAdvice`. Nunca devuelvas stack traces ni `e.message` crudo al cliente, ni dejes un `catch` vacío.
- **Logging:** SLF4J, nunca `println`. `debug` diagnóstico, `info` eventos, `warn` recuperables, `error` fallos con la excepción adjunta. No registres y relances el mismo error.
- **Validación:** forma de entrada en DTOs (Bean Validation); reglas de negocio en el dominio. Los datos de archivos (Excel) se validan en el borde.
- **Transacciones:** `@Transactional` en casos de uso (application), no en controllers ni dominio.

---

## 4. TDD

**Rojo → Verde → Refactor** para todo cambio de comportamiento (features, bugs y refactors). No hay código de producción sin un test que falle y lo exija.

1. **Rojo:** escribe el test (JUnit 5) y míralo fallar por la razón correcta.
2. **Verde:** el código mínimo para pasar.
3. **Refactor:** limpia con los tests en verde.

- **Bug:** primero un test que lo reproduzca. **Refactor sin tests:** primero tests de caracterización.
- Dominio y casos de uso: tests unitarios rápidos sin Spring (puertos con dobles). Infraestructura/controllers: tests de integración (`@SpringBootTest` / slices) solo donde aportan valor.
- Prueba lo que el sistema hace, no cómo lo hace. Un test por comportamiento. **Solo tests que aportan valor**: nada de tests triviales ni getters.
- Antes de reportar, ejecuta y reporta resultados reales:
  ```bash
  ./gradlew test    # incluye ArchUnit
  ./gradlew build
  ```

---

## 5. Código limpio

- Nombres que no necesitan comentario. Funciones pequeñas, un nivel de abstracción.
- **Sin código muerto**: sin funciones sin usar, sin código comentado, sin stubs que finjan funcionar.
- Sin literales mágicos; constantes con nombre.
- Comentarios solo para el *porqué* (restricciones no obvias, workarounds, invariantes).
- **Manejo de errores explícito**: nunca tragues excepciones.

---

## 6. Política de refactor

- Si el código que tocas viola Onion, SOLID o TDD, **refactorízalo** como parte de la tarea: primero tests de caracterización, luego el refactor, luego el cambio.
- Si la violación está fuera del área de la tarea o el refactor es grande: **repórtalo y propónlo**, no amplíes el alcance en silencio.
- **Pregunta siempre antes** de cambios que rompan: contratos de la API REST, migraciones Flyway, esquema de BD, configuración de seguridad, o cualquier cosa que afecte a más de un módulo.
- Prefiere pasos reversibles. Los cambios irreversibles necesitan aprobación explícita.

---

## 7. Seguridad

- **Nunca** registres ni devuelvas contraseñas, tokens, claves ni datos personales/sensibles en logs o respuestas.
- Secretos solo desde variables de entorno / perfiles locales no versionados. Nunca en el repo.
- Autenticación y autorización se resuelven en el servidor (Spring Security + `RanchContextInterceptor`); nunca confíes en lo que diga el cliente. Un usuario solo ve datos de su rancho.
- Valida todo lo que cruza la frontera (cuerpos HTTP, parámetros, archivos subidos). Consultas parametrizadas; nunca concatenes SQL. Cuidado con path traversal en archivos.
- Mínimo privilegio: no abras endpoints ni permisos que no necesites.

---

## 8. Git

- **Nunca hagas commit por tu cuenta**: solo cuando el usuario lo pida. Cuando lo pida, commits **atómicos**: un cambio = un propósito = un commit.
- Mensajes **convencionales en inglés** (`fix:`, `refactor:`, `feat:`, `test:`, `docs:`, `build:`, `chore:`).
- **Sin co-author ni "generated by"** en commits o PRs.
- Nunca hagas commit con tests o build rotos. **Nunca hagas push sin el OK del usuario.**
- `main` es la rama principal.

---

## 9. Informe de cambios

Al terminar, informa de forma concisa:
```
[archivo o módulo]: qué cambió y por qué.
```
Más una línea para decisiones de diseño no obvias, los tests escritos y ejecutados (con resultados) y cualquier hallazgo fuera de alcance. No listes lo que no cambió.

---

## Checklist (antes de entregar)

- [ ] Consulté MemPalace y busqué algo equivalente ya existente
- [ ] Plan propuesto y aprobado
- [ ] Tests escritos **primero** y vistos fallar; ahora en verde
- [ ] Onion respetado: `domain` sin Spring/JPA, dependencias hacia adentro
- [ ] Controllers delgados con DTOs; sin entidades expuestas
- [ ] Errores por `@RestControllerAdvice`; logs con SLF4J
- [ ] SOLID: responsabilidad única, inyección por constructor
- [ ] `./gradlew test` y `./gradlew build` pasan
- [ ] Sin código muerto ni comentado; sin secretos en el diff
- [ ] README actualizado si cambió algo de compilación/ejecución
- [ ] Informe: qué cambió y por qué
