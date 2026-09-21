# Arquitectura interna

Documento de referencia para quien vaya a mantener o extender `mongo-tclosure-util`.
Para el uso desde línea de comandos ver el [README](../README.md).

## Índice

- [Visión general](#visión-general)
- [Mapa de paquetes](#mapa-de-paquetes)
- [Flujo de ejecución](#flujo-de-ejecución)
- [Formatos RF2 consumidos](#formatos-rf2-consumidos)
- [Clases en detalle](#clases-en-detalle)
  - [com.termmed.runner.Runner](#comtermmedrunnerrunner)
  - [com.termmed.dump.ExportIndexToFile](#comtermmeddumpexportindextofile)
  - [com.termmed.util.TClosure](#comtermmedutiltclosure)
  - [com.termmed.util.DefinitionLoader](#comtermmedutildefinitionloader)
  - [com.termmed.util.TClosureAndDefinitionOwlLoader](#comtermmedutiltclosureanddefinitionowlloader)
  - [com.termmed.util.RefsetsLoader](#comtermmedutilrefsetsloader)
  - [com.termmed.util.LanguageFallbackProcessor](#comtermmedutillanguagefallbackprocessor)
  - [Serialización: BsonGenerator y MetadataGenerator](#serialización-bsongenerator-y-metadatagenerator)
  - [Importadores directos a Mongo](#importadores-directos-a-mongo)
  - [Modelos de datos](#modelos-de-datos)
- [Concepts SNOMED CT usados como constantes](#concepts-snomed-ct-usados-como-constantes)
- [Vista inferida vs. vista stated](#vista-inferida-vs-vista-stated)
- [Rendimiento y consumo de memoria](#rendimiento-y-consumo-de-memoria)
- [Código muerto o sin uso](#código-muerto-o-sin-uso)
- [Problemas conocidos](#problemas-conocidos)
- [Estado del repositorio](#estado-del-repositorio)

---

## Visión general

El proyecto es un **proceso ETL por lotes, monolítico y en memoria**. No hay framework, ni
inyección de dependencias, ni capa de configuración: todo se controla con argumentos posicionales
de `main`. El patrón se repite en las tres familias de índices:

```
archivo RF2  →  Loader (estructuras en memoria)  →  createDumpCollections() → .bson + .metadata.json
                                                 └→ toMongo(collection)     → inserción directa
```

Cada *loader* (`TClosure`, `DefinitionLoader`, `RefsetsLoader`) expone ambos destinos, de modo que
los modos `*_INDEX` y `*2MONGO` comparten la lógica de carga y difieren sólo en la salida.

## Mapa de paquetes

```
com.termmed.runner
  └─ Runner                          Punto de entrada; parsea args, despacha por modo,
                                     carga conceptos/descripciones compartidos.
com.termmed.dump
  └─ ExportIndexToFile               Orquesta el volcado a archivos; calcula la carpeta de salida;
                                     descubre archivos de refset.
com.termmed.importer
  ├─ TClosureImporter                Inserción directa en Mongo de ancestor/descendant.
  ├─ DefinitionImporter              Inserción directa en Mongo de definition.
  └─ RefsetsImporter                 Inserción directa en Mongo de refsets (+ descubrimiento de archivos).
com.termmed.util
  ├─ TClosure                        Jerarquía IS A y cálculo de clausura transitiva.
  ├─ DefinitionLoader                Definiciones (grupos de relación) + metadatos de concepto.
  ├─ TClosureAndDefinitionOwlLoader  Axiomas OWL → alimenta TClosure y DefinitionLoader (vista stated).
  ├─ RefsetsLoader                   Miembros por refset.
  ├─ LanguageFallbackProcessor       Término preferido según prioridad de refsets de idioma.
  ├─ FallbackConfig                  Mapa refsetId ↔ prioridad.
  ├─ BsonGenerator                   Escritura de documentos BSON a archivo.
  ├─ MetadataGenerator               Escritura del .metadata.json de cada colección.
  ├─ Constants                       Plantillas de metadata e ids SNOMED.
  ├─ ConceptData                     module + primitive.
  ├─ DescriptionData                 defaultTerm + langCode + semTag.
  └─ TermSelected                    Candidato a término preferido (descId, prioridad, effTime, active).
```

## Flujo de ejecución

Ejemplo con `-ALL_INDEX_WITH_PREF` (el modo más completo):

```
Runner.main
 ├─ FileHelper.getFile(...)                    localiza los archivos RF2 por cabecera
 ├─ new ExportIndexToFile(args)                crea <out>/indexes/<server>/<db>/<pathId><modo>/<db>/
 │
 ├─ exportTClosure(relsInferidas)              ── vista INFERIDA ──────────────────────────
 │    └─ new TClosure(file).loadIsas()         lee IS A activas
 │    └─ createDumpCollections()               ancestor + descendant  (.bson/.metadata.json)
 │
 ├─ loadConceptsAndDescriptions()              HashMaps compartidos de conceptos y descripciones
 ├─ getLanguageFallbackProcessor()             sólo si el modo contiene WITH_PREF
 ├─ getPreferreds()                            sobrescribe defaultTerm con el término preferido
 │
 ├─ exportDefinition(rels, concreteRels, ...)
 │    └─ new DefinitionLoader(...)             agrupa relaciones por (source, grupo, tipo)
 │    └─ createDumpCollections()               definition
 │
 ├─ exportRefset(carpeta)
 │    └─ processFolderRec()                    detecta archivos de refset por cabecera
 │    └─ new RefsetsLoader(...)                refsetId → TreeSet<referencedComponentId>
 │    └─ createDumpCollections()               refsets
 │
 └─ exportStatedTClosureAndDefinition(owlFile) ── vista STATED ────────────────────────────
      └─ new TClosureAndDefinitionOwlLoader(tClos, definitionLoader, owlFile).load()
      └─ tClos.createDumpCollections(prefijo + "st")        stancestor + stdescendant
      └─ definitionLoader.createDumpCollections(prefijo+"st") stdefinition
```

Los mapas `concepts` y `descriptions` se cargan **una vez** en `Runner` y se reutilizan en las
dos vistas (inferida y stated), evitando releer el archivo de descripciones (que es el más
pesado del release).

## Formatos RF2 consumidos

Todos los archivos son TSV UTF-8 con cabecera. Los índices de columna aparecen literalmente en el
código, sin constantes; esta tabla es la referencia:

| Archivo | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|---|---|---|
| Concept | id | effectiveTime | active | moduleId | definitionStatusId | | | | | |
| Description | id | effectiveTime | active | moduleId | conceptId | languageCode | typeId | term | caseSignificanceId | |
| Relationship | id | effectiveTime | active | moduleId | sourceId | destinationId | relationshipGroup | typeId | characteristicTypeId | modifierId |
| RelationshipConcreteValues | id | effectiveTime | active | moduleId | sourceId | **value** | relationshipGroup | typeId | characteristicTypeId | modifierId |
| Refset (simple) | id | effectiveTime | active | moduleId | refsetId | referencedComponentId | | | | |
| Language refset | id | effectiveTime | active | moduleId | refsetId | referencedComponentId (descriptionId) | acceptabilityId | | | |
| OWL expression refset | id | effectiveTime | active | moduleId | refsetId | referencedComponentId (conceptId) | owlExpression | | | |

El `split` se hace con `line.split("\\t", -1)` (conserva campos vacíos finales), salvo en
`TClosure.loadIsas`, que usa `split("\\t")` sin límite.

## Clases en detalle

### `com.termmed.runner.Runner`

[src/main/java/com/termmed/runner/Runner.java](../src/main/java/com/termmed/runner/Runner.java)

Despacha por modo con una cadena de `if (args[0].contains(...))`. El orden importa: `-ALL_INDEX`
se comprueba primero, y el sufijo `_WITH_PREF` se detecta con un `contains` independiente.

Además del despacho, implementa la carga compartida:

- **`getConceptData(concFile)`** — sólo conceptos activos. Guarda `moduleId` y `primitive`
  (`"1"` si `definitionStatusId == 900000000000074008`, `"0"` en otro caso).
- **`getDescData(descFile, langCode)`** — elige el término por defecto de cada concepto en dos
  pasadas:
  1. FSN (`900000000000003001`) activo. Prioriza el idioma `langCode`; si aún no hay entrada para
     el concepto acepta el FSN de cualquier otro idioma. El *semantic tag* se extrae del texto
     entre el último `(` y el último `)`.
  2. Si quedaron conceptos sin término, relee el archivo y acepta **cualquier** descripción activa.
     El conjunto `ableToChange` marca los conceptos cubiertos por una descripción que no es FSN,
     para permitir que un FSN posterior la sustituya.
- **`getPreferreds(...)`** — sobrescribe `defaultTerm` con el resultado de
  `LanguageFallbackProcessor.getTerm()`. Registra cuántos conceptos quedan sin término preferido
  e imprime los 10 primeros como muestra.
- **`getLanguageFallbackProcessor(...)`** — localiza los refsets de idioma (snapshot y delta) y
  construye el procesador. Si falta alguno de los dos tipos de archivo, imprime
  `Cannot process fallback` y devuelve `null`.

`getConceptData`, `getDescData` y `getPreferreds` están **duplicados** casi literalmente en
`DefinitionLoader`; ver [Código muerto o sin uso](#código-muerto-o-sin-uso).

### `com.termmed.dump.ExportIndexToFile`

[src/main/java/com/termmed/dump/ExportIndexToFile.java](../src/main/java/com/termmed/dump/ExportIndexToFile.java)

- El constructor calcula y crea la carpeta de salida:
  `args[8] + "/indexes/" + args[2] + "/" + args[3] + "/" + args[6] + args[0] + "/" + args[3]`.
  Nótese que incorpora el **modo completo** (`args[0]`, con su sufijo `_WITH_PREF` si lo hay) al
  nombre de la carpeta, de modo que ejecuciones con distinto modo no se pisan.
- `exportTClosure`, `exportDefinition`, `exportRefset` y `exportStatedTClosureAndDefinition`
  instancian el loader correspondiente y llaman a `createDumpCollections`. Cada método anula
  la referencia al loader (`x = null`) al terminar para liberar memoria antes del siguiente paso.
- `processFolderRec` / `isRefsetFile` / `nameIsEnable` descubren los archivos de refset: recorrido
  recursivo, descarte por nombre (metadatos, MRCM, OWL, idioma) y confirmación leyendo la primera
  línea del archivo.

### `com.termmed.util.TClosure`

[src/main/java/com/termmed/util/TClosure.java](../src/main/java/com/termmed/util/TClosure.java)

Dos índices invertidos en memoria:

```java
HashMap<String, HashSet<String>> parentHier;    // hijo   → padres directos
HashMap<String, HashSet<String>> childrenHier;  // padre  → hijos directos
```

- **`loadIsas()`** (constructor con archivo) — acepta filas con `typeId == 116680003` (IS A),
  `active == 1` y `sourceId != 138875005` (raíz SNOMED CT).
- **`addRel(parent, child)`** — alimenta ambos mapas. Ignora auto-relaciones (`parent == child`)
  imprimiendo un aviso. Es también el punto de entrada que usa el cargador OWL.
- **`getParentList` / `getChildrenList`** — recorrido en profundidad con un conjunto `hControl`
  compartido que actúa a la vez como *visitados* y como **acumulador del resultado**; por eso se
  reinicia antes de cada concepto y se pasa directamente al documento BSON. Esta reutilización de
  un campo de instancia hace la clase **no reentrante ni thread-safe**.
- **`createDumpCollections`** — escribe `<prefijo>ancestor<pathId>` y `<prefijo>descendant<pathId>`
  con sus metadatos.
- **`toMongo`** — equivalente insertando documento a documento.

### `com.termmed.util.DefinitionLoader`

[src/main/java/com/termmed/util/DefinitionLoader.java](../src/main/java/com/termmed/util/DefinitionLoader.java)

Estructura principal:

```java
HashMap<String, TreeMap<Integer, TreeMap<String, String>>> definitions;
//      source        grupo            typeId  destino

HashMap<String, HashMap<String, Integer>> typeCounter;
//      source        typeId  nº de apariciones en todo el concepto
```

Los `TreeMap` garantizan que los grupos y las relaciones salgan ordenados de forma estable en el
BSON, lo que hace comparables dos ejecuciones sobre el mismo release.

- **`loadRels(file, "inferred"|"concrete")`** — sólo filas activas **y** con
  `characteristicTypeId == 900000000000011006` (inferida). Para archivos de *concrete domains*
  limpia las comillas del valor (`"3"` → `3`) antes de usarlo como destino.
  Las relaciones **IS A también entran en la definición**, no sólo en la clausura transitiva.
- **`addRel(dest, source, type, groupNr)`**
  - Colisión de tipo dentro del mismo grupo: como la clave del `TreeMap` es el `typeId`, cuando ya
    existe se le añade un sufijo `#` (`116676008`, `116676008#`, `116676008##`, ...). El sufijo se
    elimina al serializar.
  - El `source` puede llegar con el sufijo `#<axiomId>` desde el cargador OWL (ver más abajo);
    se limpia (`cleanSource`) para comparar con el destino y para resolver concepto/descripción.
- **`createDumpCollections` / `toMongo`** — construyen el documento descrito en el README. El
  campo `q` (`typeCounter`) es el número total de relaciones de ese tipo en **todo el concepto**,
  no sólo en el grupo; sirve para distinguir rápidamente atributos únicos de repetidos.
- Los constructores que reciben `descFile`/`concFile` replican la carga de conceptos y
  descripciones de `Runner`; los que reciben los `HashMap` ya cargados son los que usa el flujo
  actual.

> **Consecuencia del sufijo `#<axiomId>`** (cambio reciente, aún sin commit): en la vista stated
> se emite **un documento por axioma**, por lo que la colección `stdefinition` puede contener
> varios documentos con el mismo valor de `c`. Quien consulte esa colección debe agregarlos.

### `com.termmed.util.TClosureAndDefinitionOwlLoader`

[src/main/java/com/termmed/util/TClosureAndDefinitionOwlLoader.java](../src/main/java/com/termmed/util/TClosureAndDefinitionOwlLoader.java)

Convierte el refset de expresiones OWL en relaciones, usando
`AxiomRelationshipConversionService` de *snomed-owl-toolkit* (configurado con
`Concepts.LATERALITY_LONG` como atributo concreto conocido).

Por cada fila activa del refset `733073007`:

1. `convertOwlExpressionToOWLAxiom` → `convertAxiomToRelationships`. Si el axioma no es
   convertible se salta la fila; una `ConversionException` se imprime y el proceso continúa.
2. **Detección de axiomas invertidos (GCI):** si el concepto de la fila (`referencedComponentId`)
   no es el concepto nombrado ni en el lado izquierdo ni en el derecho, se busca si aparece como
   **destino** de alguna relación del lado derecho. Si es así se trata como *inverted IS A* y el
   destino real pasa a ser el concepto nombrado del lado izquierdo. Si no, la fila se descarta.
3. Se toman los grupos de relaciones del lado derecho y, si existen, los del izquierdo
   (**estos últimos sobrescriben a los primeros**).
4. Por cada relación:
   - si `typeId == IS A` → `tClos.addRel(destino, sourceId)`;
   - en todos los casos → `definitionLoader.addRel(destino_o_valor, sourceId + "#" + axiomId, typeId, grupo)`.

Al terminar imprime el recuento de IS A y de relaciones definitorias cargadas.

### `com.termmed.util.RefsetsLoader`

[src/main/java/com/termmed/util/RefsetsLoader.java](../src/main/java/com/termmed/util/RefsetsLoader.java)

`HashMap<String, TreeSet<String>> refsets` = `refsetId` → miembros activos ordenados.
Sólo filas activas y cuyo `refsetId` no esté en la lista negra `notEnabledToIndex`.
Un mismo `refsetId` puede llegar desde varios archivos; las entradas se acumulan.

### `com.termmed.util.LanguageFallbackProcessor`

[src/main/java/com/termmed/util/LanguageFallbackProcessor.java](../src/main/java/com/termmed/util/LanguageFallbackProcessor.java)

Resuelve qué término mostrar cuando hay varios refsets de idioma con distinta prioridad
(p. ej. una extensión nacional por delante del refset internacional).

```java
HashMap<Long, TreeMap<Integer, List<TermSelected>>> terms;
//      conceptId     prioridad    candidatos en ese refset
```

- `getCandidates()` indexa las descripciones activas del tipo configurado (sinónimo) →
  `descriptionId → conceptId` y `descriptionId → term`.
- `getFallback()` recorre los refsets de idioma (snapshot y delta) y llama a `processFallback`
  por cada miembro cuya descripción sea candidata.
- `processFallback(...)`:
  - Ignora refsets que no estén en `FallbackConfig` (no priorizados).
  - Un candidato se marca `active = 1` sólo si su `acceptabilityId` es *preferred*
    (`900000000000548007`); en otro caso se fuerza `active = 0`.
  - Si ya existía una entrada para esa descripción en esa prioridad, sólo se actualiza cuando el
    `effectiveTime` de la fila nueva es mayor (así el **delta gana al snapshot**).
  - `setInactiveInLowerPriorityRefsets` propaga la desactivación hacia las prioridades menores
    (valor numérico mayor), de modo que una descripción retirada en el refset prioritario no
    reaparezca por un refset secundario.
- `getTermSelected(conceptId)` recorre el `TreeMap` por prioridad ascendente y devuelve el primer
  candidato activo; `getTerm` traduce ese `descriptionId` a texto.

`FallbackConfig` sólo mantiene la doble tabla `prioridad ↔ refsetId` a partir de la cadena
`args[9]` separada por `---`.

### Serialización: `BsonGenerator` y `MetadataGenerator`

- **`BsonGenerator`** envuelve un `BufferedOutputStream` (16 KB) y un `BasicBSONEncoder`.
  Cada `generate(Document)` escribe el documento codificado, uno tras otro, que es exactamente el
  formato que espera `mongorestore`. **Requiere `close()` explícito** para vaciar el buffer.
- **`MetadataGenerator`** escribe el `.metadata.json` correspondiente, resultado de sustituir
  `===DB===`, `===COLL===` y `===PATH===` en las plantillas de `Constants`.

### Importadores directos a Mongo

`TClosureImporter`, `DefinitionImporter` y `RefsetsImporter` siguen el mismo guion:

1. `MongoClients.create("mongodb://" + server + ":" + port)` (sin credenciales ni opciones).
2. `getCollection(prefijo + nombre + pathId)`.
3. **`drop()`** de la colección previa (envuelto en try/catch que sólo imprime el error).
4. Carga con el loader correspondiente y `toMongo(collection)` — inserciones de **uno en uno**,
   sin `insertMany` ni *bulk write*.
5. `createIndex(...)` con los mismos índices que declaran los `.metadata.json`.
6. `mongoClient.close()`.

`RefsetsImporter` duplica el descubrimiento de archivos y la lista negra de refsets de
`ExportIndexToFile`.

### Modelos de datos

| Clase | Campos | Uso |
|---|---|---|
| `ConceptData` | `module`, `primitive` | Campos `m` y `p` del documento de definición |
| `DescriptionData` | `defaultTerm`, `langCode`, `semTag` | Campos `dt` y `st` |
| `TermSelected` | `descriptionId`, `priority`, `effTime`, `active` | Candidato dentro del fallback de idioma |

## Concepts SNOMED CT usados como constantes

| Id | Significado | Dónde |
|---|---|---|
| `138875005` | SNOMED CT Concept (raíz) | `TClosure.ROOT_CONCEPT` |
| `116680003` | IS A | `TClosure.ISARELATIONSHIPTYPEID` |
| `900000000000074008` | Primitivo (definition status) | `Runner`, `DefinitionLoader` |
| `900000000000003001` | FSN (tipo de descripción) | `Runner`, `DefinitionLoader` |
| `900000000000013009` | Sinónimo | `Constants.SYNONYM_TYPE` |
| `900000000000548007` | Preferred (aceptabilidad) | `Constants.PREFERRED_ACCEPTABILITY` |
| `900000000000011006` | Inferred (characteristic type) | `DefinitionLoader.INFERRED_CHARACTERISTIC_TYPE` |
| `733073007` | OWL axiom refset | `Constants.AXIOM_REFSET` |

## Vista inferida vs. vista stated

| | Inferida | Stated |
|---|---|---|
| Fuente | `sct2_Relationship_*` + `sct2_RelationshipConcreteValues_*` | Refset `733073007` de expresiones OWL |
| Cargador | `TClosure(file)` + `DefinitionLoader.loadRels` | `TClosureAndDefinitionOwlLoader` |
| Colecciones | `…ancestor…`, `…descendant…`, `…definition…` | `…stancestor…`, `…stdescendant…`, `…stdefinition…` |
| Clave `c` | única por concepto | puede repetirse (un documento por axioma) |

Ambas vistas comparten las clases `TClosure` y `DefinitionLoader`: la diferencia está en quién
llama a `addRel`. Las colecciones *stated* se obtienen simplemente añadiendo `"st"` al prefijo.

## Rendimiento y consumo de memoria

- Todo el grafo se mantiene **en memoria**. Para el release internacional completo conviene
  dimensionar el heap (`-Xmx`) con holgura: el mapa de descripciones y el de definiciones son los
  dominantes.
- `getParentList` y `getChildrenList` son **recursivos**; con jerarquías muy profundas podría
  aparecer `StackOverflowError`. La protección frente a ciclos es el conjunto `hControl`.
- La clausura transitiva se materializa entera (para cada concepto, el conjunto completo de
  ancestros y de descendientes). La colección `descendant` es, con diferencia, la más voluminosa.
- Los loaders se liberan (`= null`) entre etapas en `ExportIndexToFile` para que el GC pueda
  recuperar memoria antes de la siguiente.
- El progreso se reporta imprimiendo un `.` cada 100 000 filas procesadas.

## Código muerto o sin uso

Detectado al documentar; nada de esto se invoca desde el flujo actual:

- `Constants.STATED_ANCESTOR_METADATA_JSON`, `STATED_DESCENDANT_METADATA_JSON` y
  `STATED_DEFINITION_METADATA_JSON`: la vista stated reutiliza las plantillas no-stated pasando
  `prefijo + "st"`, con lo que el resultado es idéntico.
- `TClosure.toFile(...)`, `writeHierarchy`, `writeParents`, `addTClosureFileHeader`,
  `getReader(String)`, `isAncestorOf`, `getParent`, `getChildren`.
- Sobrecargas de `ExportIndexToFile.exportDefinition` y `exportStatedTClosureAndDefinition` que
  reciben rutas de archivo (`descFile`, `concFile`) en lugar de los `HashMap` precargados, junto
  con los constructores de `DefinitionLoader` que las acompañan y sus métodos privados
  `getConceptData` / `getDescriptionData` / `getPreferreds` — duplicados de los de `Runner`.
- `LanguageFallbackProcessor.addLangRefsetLine`, `FallbackConfig.getRefsetIdFromPriority` y el
  constructor `FallbackConfig(TreeMap)`.
- Imports sin uso: `java.net.CookieStore` en `RefsetsLoader`,
  `javax.naming.directory.BasicAttribute` en `RefsetsImporter`, `BasicDBObject`/`BasicBSONEncoder`
  en `TClosure`.
- La lista negra de refsets (`notEnabledToIndex`, ~30 ids) está **duplicada literalmente** en
  `ExportIndexToFile` y en `RefsetsImporter`. En ambas copias el id `900000000000530003` aparece
  dos veces.

## Problemas conocidos

| # | Dónde | Descripción |
|---|---|---|
| 1 | [TClosure.java:190-199](../src/main/java/com/termmed/util/TClosure.java#L190-L199) | `createDumpCollections` **no cierra** el `BsonGenerator` de `descendant`. Al no vaciarse el buffer de 16 KB, el archivo `.bson` queda truncado al final. El de `ancestor` sí se cierra. **Confirmado empíricamente**: en una ejecución de prueba `…descendantMAIN.bson` y `…stdescendantMAIN.bson` salen con **0 bytes** (todo el contenido cabía en el buffer), mientras el resto de colecciones se escriben bien. Con volúmenes reales sólo se pierde la cola, lo que lo hace difícil de detectar. Ocurre igual en Java 8 y en 17: no depende del JDK. |
| 2 | [TClosure.java:192](../src/main/java/com/termmed/util/TClosure.java#L192) | `childrenHier.remove(Long.parseLong(ROOT_CONCEPT))` busca una clave `Long` en un `HashMap<String, …>`: **nunca coincide**, por lo que el concepto raíz sí acaba en la colección `descendant` con todos los conceptos como descendientes. Lo mismo en `toMongo`. |
| 3 | [TClosure.java:43](../src/main/java/com/termmed/util/TClosure.java#L43), [DefinitionLoader.java:228](../src/main/java/com/termmed/util/DefinitionLoader.java#L228), [RefsetsLoader.java:35](../src/main/java/com/termmed/util/RefsetsLoader.java#L35) | `if (line.trim().equals("")) { continue; }` **sin avanzar el lector** → bucle infinito si el archivo contiene una línea en blanco intermedia. En `Runner.getConceptData`/`getDescData` el mismo patrón sí hace `line = br.readLine()` antes del `continue`. |
| 4 | [Runner.java:19](../src/main/java/com/termmed/runner/Runner.java#L19) | Se lee `args[0]` sin comprobar `args.length`: ejecutar el jar sin argumentos lanza `ArrayIndexOutOfBoundsException`. No hay mensaje de ayuda ni modo por defecto. |
| 5 | [Runner.java:133](../src/main/java/com/termmed/runner/Runner.java#L133) | En `-INFERRED_INDEX`, `exportDefinition` se llama **fuera** del `if (conceptFile != null && descFile != null)`, de modo que si falta alguno de esos archivos se pasa `concepts`/`descriptions` en `null` y el volcado falla con `NullPointerException`. |
| 6 | [Runner.java:196](../src/main/java/com/termmed/runner/Runner.java#L196) | `-REF2MONGO` sólo acepta 7 u 11+ argumentos; con 8, 9 o 10 lanza `Wrong params number`. |
| 7 | [Runner.java:179](../src/main/java/com/termmed/runner/Runner.java#L179) | `-DEF2MONGO` no importa definiciones: el bloque de `DefinitionImporter` está comentado y sólo corre `TClosureImporter`. El nombre del modo es engañoso. |
| 8 | [DefinitionLoader.java:356](../src/main/java/com/termmed/util/DefinitionLoader.java#L356) | `toMongo` **no** aplica la limpieza del sufijo `#<axiomId>` que sí hace `createDumpCollections`. Hoy no se manifiesta porque la ruta `toMongo` no se usa con el cargador OWL, pero divergirá si se conecta. |
| 9 | `TClosure`, `DefinitionLoader` | El campo de instancia `hControl` se usa como acumulador temporal: las clases no son reentrantes ni seguras entre hilos. |
| 10 | `TClosureAndDefinitionOwlLoader` | Una `ConversionException` se imprime por consola y la fila se omite en silencio: el proceso termina con éxito aunque haya axiomas no convertidos. |
| 11 | Todo el proyecto | **No hay tests** (`src/test` no existe) ni ningún tipo de validación de la salida. El logging es `System.out.println`. |
| 12 | `Runner` / `DefinitionLoader` | Duplicación literal de `getConceptData`, `getDescData`/`getDescriptionData` y `getPreferreds`; cualquier corrección debe aplicarse en ambos sitios. |

## Estado del repositorio

- Rama de trabajo: `1.8.0` (creada desde `TS-1.6`); rama principal: `master`.
- Versión en `pom.xml`: `1.8-SNAPSHOT`.
- Cambios recientes aún **sin commitear** en el momento de escribir este documento:
  - `pom.xml`: subida a `1.8-SNAPSHOT`, `sct2-utilities` 1.6.0 → 1.8.0, `snomed-owl-toolkit`
    3.0.4 → **5.4.0** y `maven-compiler-plugin` con `<release>17</release>`.
  - `DefinitionLoader`: filtro por `characteristicTypeId` inferido y soporte del sufijo
    `#<axiomId>` en la clave de `source`.
  - `TClosureAndDefinitionOwlLoader`: emisión de la clave `sourceId#axiomId` y eliminación de un
    import de `com.sun.org.apache.bcel.*` (interno del JDK, incompatible con JDK 9+).

> **Efecto colateral de las subidas de `snomed-owl-toolkit`:** cada salto elevó el JRE mínimo
> (3.0.4 → Java 8, 3.0.10 → Java 11, **5.4.0 → Java 17**), aunque el `pom.xml` siga declarando
> `source`/`target` 8. Además, en **JDK 16+** los modos que procesan OWL requieren
> `--add-opens java.base/java.lang=ALL-UNNAMED` por el Guice 4.0 que arrastra owlapi 4.1.3;
> la rama 5.4.x **no** lo soluciona, porque depende de ese mismo owlapi.
> El detalle, con los errores exactos y las alternativas, está en
> [README § Compatibilidad con versiones de Java](../README.md#compatibilidad-con-versiones-de-java).

- `target/` y los archivos de `.idea/` aparecen sin ignorar en el árbol de trabajo; no hay
  `.gitignore` en el repositorio.
