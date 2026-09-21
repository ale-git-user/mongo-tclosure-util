# mongo-tclosure-util

Utilidad de línea de comandos que transforma un release **SNOMED CT RF2** (o la salida de un
clasificador) en **índices precalculados para MongoDB**: clausura transitiva (ancestros y
descendientes), definiciones de concepto y pertenencia a refsets.

El resultado se puede generar de dos maneras:

1. **Volcado a archivos `.bson` + `.metadata.json`** listos para `mongorestore` (modos `*_INDEX`).
   Es el camino habitual: no necesita conexión a Mongo durante la generación.
2. **Inserción directa en una base MongoDB** (modos `*2MONGO`).

Artefacto ejecutable: `target/TClosure_Definition_Refset_Indexer.jar`
(uber-jar con dependencias, clase principal `com.termmed.runner.Runner`).

---

## Índice

- [Requisitos](#requisitos)
- [Compatibilidad con versiones de Java](#compatibilidad-con-versiones-de-java)
- [Compilación](#compilación)
- [Uso](#uso)
  - [Argumentos](#argumentos)
  - [Modos de ejecución](#modos-de-ejecución)
- [Archivos de entrada](#archivos-de-entrada)
- [Salida generada](#salida-generada)
  - [Estructura de carpetas](#estructura-de-carpetas)
  - [Colecciones y esquema de documentos](#colecciones-y-esquema-de-documentos)
  - [Restauración en MongoDB](#restauración-en-mongodb)
- [Término preferido y fallback de idioma](#término-preferido-y-fallback-de-idioma)
- [Documentación adicional](#documentación-adicional)
- [Problemas conocidos](#problemas-conocidos)

---

## Requisitos

| Elemento | Versión |
|---|---|
| JDK | **17**. Ver [Compatibilidad con versiones de Java](#compatibilidad-con-versiones-de-java): por debajo de 17 los modos que procesan OWL no arrancan, y en 17 necesitan un flag adicional (`--add-opens`). |
| Maven | 3.x |
| MongoDB | Sólo para los modos `*2MONGO` y para restaurar los dumps (driver `mongodb-driver-sync` 4.0.3) |

Dependencias declaradas en [pom.xml](pom.xml):

- `com.termmed:sct2-utilities:1.8.0-SNAPSHOT` — detección de archivos RF2 por cabecera (`FileHelper`).
  Es un artefacto interno (SNAPSHOT): debe estar instalado en el repositorio Maven local.
- `org.snomed.otf:snomed-owl-toolkit:5.4.0` — conversión de axiomas OWL a relaciones.
  Compilado para Java 17: es la dependencia que fija el JRE mínimo del proyecto.
- `org.mongodb:mongodb-driver-sync:4.0.3` — cliente Mongo y codificación BSON.

## Compatibilidad con versiones de Java

Resumen verificado ejecutando el pipeline completo (`-ALL_INDEX_WITH_PREF`) sobre un release RF2
de prueba:

| JDK | Compila | Modos sin OWL (`-INFERRED_INDEX`, `-REFSET_INDEX`, `*2MONGO`) | Modos con OWL (`-ALL_INDEX`, `-STATED_INFERRED_INDEX`) |
|---|---|---|---|
| 8 | **no** — el build exige `release 17` | — | — |
| 11 | **no** — el build exige `release 17` | sí (con un jar ya construido) | **no** — `UnsupportedClassVersionError` |
| 17 | sí | sí | **sólo con `--add-opens`** |

### El piso real es Java 17

El proyecto **no se puede ejecutar con un JRE 8 ni con uno 11**: `snomed-owl-toolkit` 5.4.0 viene
compilado para Java 17 (*class file version* 61.0). En un JRE anterior, al llegar al paso de
axiomas OWL el proceso aborta con:

```
java.lang.UnsupportedClassVersionError: org/snomed/otf/owltoolkit/conversion/ConversionException
has been compiled by a more recent version of the Java Runtime (class file version 61.0),
this version of the Java Runtime only recognizes class file versions up to 55.0
```

Por eso el `maven-compiler-plugin` está configurado con `<release>17</release>`: así el build
declara el mismo piso que imponen las dependencias, y una incompatibilidad salta en tiempo de
compilación en lugar de en producción, a mitad de un release ya procesado.

Histórico del piso de ejecución, por si hay que volver atrás:

| `snomed-owl-toolkit` | *class file* | JRE mínimo |
|---|---|---|
| 3.0.4 | 52.0 | 8 |
| 3.0.10 | 55.0 | 11 |
| 5.4.0 (actual) | 61.0 | **17** |

### JDK 17: hace falta `--add-opens`

En JDK 16+ la encapsulación fuerte del JDK ([JEP 403](https://openjdk.org/jeps/403)) bloquea el
acceso reflexivo a internos de `java.base`. La cadena `snomed-owl-toolkit` → `owlapi-api` 4.1.3 →
**Guice 4.0** (2015) usa un cglib antiguo que invoca `ClassLoader.defineClass` por reflexión, y
falla al instanciar `AxiomRelationshipConversionService`:

```
java.lang.ExceptionInInitializerError
  at org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager(OWLManager.java:58)
  at org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService.<init>(...)
Caused by: java.lang.reflect.InaccessibleObjectException: Unable to make protected final
  java.lang.Class java.lang.ClassLoader.defineClass(...) accessible:
  module java.base does not "opens java.lang" to unnamed module
```

La solución, **sin cambios de código**, es abrir ese paquete al arrancar:

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED \
     -jar target/TClosure_Definition_Refset_Indexer.jar <modo> <args...>
```

Con ese flag el pipeline completo —incluida la conversión de axiomas OWL— termina correctamente
en JDK 17. Los modos que no tocan OWL (`-INFERRED_INDEX`, `-REFSET_INDEX`, `*2MONGO`) funcionan
en JDK 17 **sin** ningún flag, porque nunca llegan a instanciar el toolkit.

Para no depender de quien lanza el proceso, el flag se puede fijar en el propio artefacto
añadiendo `Add-Opens: java.base/java.lang` al manifiesto del `maven-assembly-plugin`, o
exportando `JDK_JAVA_OPTIONS="--add-opens java.base/java.lang=ALL-UNNAMED"` (respetado por el
lanzador `java` desde JDK 9).

### Por qué actualizar el toolkit no elimina el flag

Subir `snomed-owl-toolkit` de 3.0.10 a **5.4.0 no evita el `--add-opens`**, porque la rama 5.4.x
sigue dependiendo exactamente de las mismas versiones que causan el problema:

```
org.snomed.otf:snomed-owl-toolkit:5.4.0
  \- net.sourceforge.owlapi:owlapi-api:4.1.3
       +- com.google.inject:guice:4.0
       +- com.google.inject.extensions:guice-assistedinject:4.0
       \- com.google.inject.extensions:guice-multibindings:4.0
```

Tampoco funciona forzar **sólo** Guice a 5.1.0: las extensiones 4.0 quedan en el árbol
referenciando una API interna eliminada
(`NoSuchMethodError: com.google.inject.internal.BytecodeGen.getClassLoader`). Haría falta
actualizar o excluir también `guice-multibindings` y `guice-assistedinject`, y revalidar owlapi
entero.

Conclusión práctica: mientras la cadena arrastre owlapi 4.1.3, **el flag es obligatorio en
JDK 16+**, con cualquier versión del toolkit.

> **Nota de seguridad, al margen de la versión de Java:** el árbol arrastra
> `log4j-core` 2.13.0 (vía `sct2-utilities`), afectado por Log4Shell (CVE-2021-44228), y
> `log4j 1.2.14`, sin soporte desde hace años.

## Compilación

```bash
mvn clean package
```

Genera dos artefactos en `target/`:

- `mongo-tclosure-util-1.8-SNAPSHOT.jar` — sólo las clases del proyecto.
- `TClosure_Definition_Refset_Indexer.jar` — uber-jar ejecutable (`maven-assembly-plugin`,
  descriptor `jar-with-dependencies`, ~45 MB).

## Uso

```bash
java -jar target/TClosure_Definition_Refset_Indexer.jar <modo> <arg1> ... <arg9>
```

> **Importante:** `config/validation-rules.xml` se lee mediante una ruta **relativa al directorio
> de trabajo**. Ejecute el jar desde la raíz del proyecto (o desde una carpeta que contenga
> `config/validation-rules.xml`); si no existe, `sct2-utilities` recurre a la copia empaquetada
> dentro de su propio jar.

### Argumentos

Los modos de índice a archivo exigen **exactamente 10 argumentos** posicionales:

| Pos. | Nombre | Descripción |
|---|---|---|
| `0` | modo | Ver [Modos de ejecución](#modos-de-ejecución). Se evalúa con `contains`, por lo que admite sufijos como `_WITH_PREF`. |
| `1` | carpeta de entrada | Raíz del release RF2 / salida del clasificador. Se recorre **recursivamente**. |
| `2` | servidor Mongo | En los modos `*_INDEX` sólo se usa como **segmento de la ruta de salida**; no se abre ninguna conexión. |
| `3` | base de datos | Nombre de la BD. Se usa en los `.metadata.json` y como nombre de la carpeta final del dump. |
| `4` | prefijo de colección | Prefijo antepuesto al nombre de cada colección. |
| `5` | puerto Mongo | Sólo se usa en los modos `*2MONGO`. |
| `6` | pathId | Sufijo de colección (identificador de rama/path de autoría). |
| `7` | código de idioma | Idioma preferido para el término por defecto (`en`, `es`, ...). |
| `8` | carpeta base de salida | Raíz bajo la cual se crea el árbol `indexes/...`. |
| `9` | refsets de idioma | Lista de `refsetId` separados por `---`, **en orden de prioridad descendente**, para el fallback de idioma. Sólo se usa con `_WITH_PREF`. |

Ejemplo:

```bash
java -jar target/TClosure_Definition_Refset_Indexer.jar \
  -ALL_INDEX_WITH_PREF \
  /data/releases/SnomedCT_InternationalRF2/Snapshot \
  mongo-host \
  snomedIndexes \
  int \
  27017 \
  MAIN \
  en \
  /data/output \
  900000000000509007---900000000000508004
```

### Modos de ejecución

#### Modos de volcado a archivos (`*_INDEX`) — 10 argumentos

| Modo | Genera | Notas |
|---|---|---|
| `-ALL_INDEX` | clausura transitiva inferida + definiciones inferidas + refsets + clausura y definiciones **stated** (desde OWL) | El flujo completo. |
| `-STATED_INFERRED_INDEX` | clausura transitiva + definiciones inferidas + clausura y definiciones stated | Igual que `-ALL_INDEX` **sin refsets**. Busca los archivos de relaciones/concrete domains exigiendo `snapshot` en el nombre. |
| `-INFERRED_INDEX` | clausura transitiva + definiciones inferidas | Sin refsets ni stated. |
| `-REFSET_INDEX` | sólo refsets | Aun así exige los 10 argumentos. |

Cualquiera de ellos admite el sufijo `_WITH_PREF` (p. ej. `-ALL_INDEX_WITH_PREF`) para activar el
[fallback de idioma](#término-preferido-y-fallback-de-idioma) y poblar el campo `dt`
(*default term*) de la colección de definiciones.

#### Modos de inserción directa en Mongo (`*2MONGO`)

| Modo | Argumentos | Comportamiento |
|---|---|---|
| `-ALL2MONGO` | 8 (`modo`, carpeta, server, db, prefijo, puerto, pathId, langCode) | Importa clausura transitiva, definiciones y refsets. **Elimina (`drop`) las colecciones previas** y crea los índices al terminar. |
| `-DEF2MONGO` | ≥7 | Pese al nombre, hoy **sólo ejecuta `TClosureImporter`** (el bloque de definiciones está comentado). `args[1]` es la **ruta del archivo** de relaciones, no una carpeta. No valida el número de argumentos. |
| `-REF2MONGO` | 7 u 11+ | Importa refsets. Con 7 argumentos la carpeta se toma de `args[1]`; con más de 10, de `args[10]`. Con 8, 9 o 10 argumentos lanza excepción. |

Estos modos se conectan a `mongodb://<server>:<port>` sin autenticación.

## Archivos de entrada

Los archivos **no se localizan por nombre** sino por su **cabecera**: `FileHelper.getFile` recorre
recursivamente la carpeta, se queda con los `.txt` no ocultos que cumplen los filtros de nombre y
compara la primera línea contra las expresiones regulares de
[config/validation-rules.xml](config/validation-rules.xml).

| Tipo (`fileType`) | Filtros de nombre aplicados | Uso |
|---|---|---|
| `rf2-relationships` | debe **no** contener `stated` (en `-STATED_INFERRED_INDEX`, además debe contener `snapshot`) | Relaciones inferidas: `IS A` para la clausura transitiva, resto para definiciones |
| `rf2-inferred-concrete-domains` | ídem | Valores concretos (`RelationshipConcreteValues`) |
| `rf2-concepts` | no contiene `stated` | `moduleId` y estado de definición (primitivo/definido) |
| `rf2-descriptions` | no contiene `stated` | Término por defecto y *semantic tag* |
| `rf2-owl-expression` | debe contener `expression` | Axiomas OWL → vista **stated** |
| `rf2-language` | snapshot y delta | Fallback de idioma (`_WITH_PREF`) |

Para los refsets el criterio es distinto: se recorre toda la carpeta y se acepta cualquier archivo
cuya cabecera empiece por `id  effectiveTime  active  moduleId  refsetId  referencedComponentId`
(separado por tabuladores), excluyendo por nombre los refsets de metadatos (`_Language`,
`RefsetDescriptor`, `ModuleDependency`, `_DescriptionType`, `MRCM*`, `OWLExpression`) y por
`refsetId` una lista negra de ~30 refsets de metadatos/histórico definida en
[ExportIndexToFile.java](src/main/java/com/termmed/dump/ExportIndexToFile.java#L74).

Sólo se procesan filas **activas** (`active = 1`).

## Salida generada

### Estructura de carpetas

```
<args[8]>/indexes/<server>/<db>/<pathId><modo>/<db>/
    <prefijo>ancestor<pathId>.bson
    <prefijo>ancestor<pathId>.metadata.json
    <prefijo>descendant<pathId>.bson
    <prefijo>descendant<pathId>.metadata.json
    <prefijo>definition<pathId>.bson
    <prefijo>definition<pathId>.metadata.json
    <prefijo>refsets<pathId>.bson
    <prefijo>refsets<pathId>.metadata.json
    <prefijo>stancestor<pathId>.bson        (vista stated)
    <prefijo>stdescendant<pathId>.bson      (vista stated)
    <prefijo>stdefinition<pathId>.bson      (vista stated)
    ... y sus .metadata.json
```

La última carpeta lleva el nombre de la base de datos porque es el layout que espera `mongorestore`.
Las colecciones *stated* se obtienen pasando `prefijo + "st"` como prefijo de colección.

### Colecciones y esquema de documentos

Los nombres de campo son de una o dos letras para reducir el tamaño en disco.

**`<prefijo>ancestor<pathId>` / `<prefijo>stancestor<pathId>`** — ancestros transitivos:

```json
{ "c": "80146002", "a": ["71388002", "128927009", "138875005", "..."] }
```

**`<prefijo>descendant<pathId>` / `<prefijo>stdescendant<pathId>`** — descendientes transitivos:

```json
{ "c": "71388002", "d": ["80146002", "..."] }
```

**`<prefijo>definition<pathId>` / `<prefijo>stdefinition<pathId>`** — definición del concepto:

```json
{
  "c":  "80146002",
  "g":  [
    { "rg": [
      { "t": "116676008",
        "d": "129264002",
        "q": 2 }
    ]}
  ],
  "m":  "900000000000207008",
  "p":  "0",
  "st": "procedure",
  "dt": "Appendectomy"
}
```

| Campo | Significado |
|---|---|
| `c` | `conceptId` |
| `g` | Grupos de relación, ordenados por número de grupo |
| `g.rg` | Relaciones dentro del grupo |
| `g.rg.t` | `typeId` de la relación |
| `g.rg.d` | `destinationId`, o el valor literal si es un *concrete domain* |
| `g.rg.q` | Nº de relaciones de ese tipo en todo el concepto |
| `m` | `moduleId` |
| `p` | `1` = primitivo, `0` = totalmente definido |
| `st` | *semantic tag* |
| `dt` | Término por defecto (ver fallback de idioma) |

`st` y `dt` sólo aparecen si existe descripción para el concepto.

**`<prefijo>refsets<pathId>`** — miembros activos por refset:

```json
{ "r": "447562003", "s": ["80146002", "..."] }
```

Los `.metadata.json` contienen las definiciones de índice que `mongorestore` aplicará
(plantillas en [Constants.java](src/main/java/com/termmed/util/Constants.java)):
`c` en ancestor/descendant/definition, `r` en refsets, y además `g.rg.t + g.rg.d`, `m`, `st`, `p`
en definition.

### Restauración en MongoDB

```bash
mongorestore --host <host> --port <port> \
  --db <db> \
  <args[8]>/indexes/<server>/<db>/<pathId><modo>/<db>
```

## Término preferido y fallback de idioma

Con el sufijo `_WITH_PREF` el proceso construye un `LanguageFallbackProcessor`:

1. `args[9]` se parte por `---`; la posición en la lista es la **prioridad** (0 = mayor).
2. Se leen las descripciones de tipo **sinónimo** (`900000000000013009`) como candidatas.
3. Se leen los refsets de idioma (snapshot **y** delta) y, por cada descripción, se registra la
   aceptabilidad en cada refset priorizado. Sólo cuenta como válida la aceptabilidad
   **preferred** (`900000000000548007`).
4. Cuando una descripción deja de ser preferida (inactiva o con otra aceptabilidad) en un refset
   de prioridad alta, también se marca inactiva en los refsets de menor prioridad.
5. `getTerm(conceptId)` devuelve el término del primer refset por prioridad que conserve una
   descripción activa; ese valor sobrescribe `dt` en la colección de definiciones.

Si no se encuentra término, el proceso imprime un recuento de conceptos sin término preferido y
conserva el término obtenido del archivo de descripciones.

Sin `_WITH_PREF`, `dt` es el FSN (`900000000000003001`) en el idioma `args[7]`, con estos
respaldos sucesivos: FSN en cualquier otro idioma → cualquier descripción activa del concepto.

## Documentación adicional

- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) — recorrido clase por clase, algoritmos,
  formatos RF2, flujo de datos y notas de mantenimiento.

## Problemas conocidos

Ver el detalle y la ubicación exacta en
[docs/ARQUITECTURA.md](docs/ARQUITECTURA.md). Los más relevantes:

- **El dump de `descendant` no se cierra**: `TClosure.createDumpCollections` no llama a
  `close()` sobre el último `BsonGenerator`, por lo que se pueden perder hasta 16 KB del final
  del archivo.
- **Bucle infinito ante una línea en blanco** en `TClosure.loadIsas`, `DefinitionLoader.loadRels`
  y `RefsetsLoader.loadRefsets` (se hace `continue` sin avanzar el lector).
- **El concepto raíz no se excluye** de la colección `descendant`: se intenta borrar con una clave
  `Long` en un mapa con claves `String`.
- **No hay validación de argumentos** antes de leer `args[0]`; ejecutar el jar sin parámetros
  produce un `ArrayIndexOutOfBoundsException`.
- **El proyecto no tiene tests.**
