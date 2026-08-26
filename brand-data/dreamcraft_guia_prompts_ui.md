# DreamCraft — Guía de referencias para prompts de UI

> Documento operativo para un agente encargado de redactar prompts de diseño para la estructura básica de menús y para los bloques **Synt**, **Nexo** y **Ciudad**.
>
> Fuente: `dreamcraft_brand_identity_book_.pdf`, Brand Identity Book v2.0.

## 1. Objetivo del agente

Convertir requisitos funcionales en prompts visuales y de interfaz coherentes con DreamCraft, sin inventar información de producto.

El agente debe:

1. Mantener la identidad visual y verbal de DreamCraft.
2. Separar con claridad **marca**, **función**, **contenido** y **estado interactivo**.
3. Definir jerarquía, navegación, componentes, estados y comportamiento responsive.
4. Redactar prompts ejecutables por otra IA de diseño, imagen, UI o prototipado.
5. Marcar como variable o pedir confirmación cuando falte información.

## 2. Regla crítica sobre Synt, Nexo y Ciudad

El Brand Identity Book **no define ni menciona** los módulos llamados **Synt**, **Nexo** o **Ciudad**. Por lo tanto:

- No atribuirles funciones, historia, colores, símbolos, mecánicas ni jerarquías sin una especificación adicional.
- Tratarlos provisionalmente como nombres de módulos o destinos de navegación.
- Usar variables explícitas cuando falte contexto:
  - `[OBJETIVO_DE_SYNT]`
  - `[OBJETIVO_DE_NEXO]`
  - `[OBJETIVO_DE_CIUDAD]`
  - `[ACCIONES_PRINCIPALES]`
  - `[DATOS_A_MOSTRAR]`
  - `[ESTADO_DEL_USUARIO]`
- Si se necesitan colores diferenciadores, no asignarlos arbitrariamente. La arquitectura de modalidades del manual solo autoriza colores auxiliares para **Survival, Skyblock, Creativo, Minigames y Eventos**.

## 3. Esencia de marca

DreamCraft es un servidor de Minecraft y futura network para la comunidad hispanohablante. Su universo invita a explorar, crear y compartir en un entorno mágico, social y seguro.

### Propósito

Crear un espacio de juego luminoso y acogedor donde cada persona pueda expresarse, descubrir y construir con otros.

### Promesa

Cada jugador debe sentir que entra a un mundo vivo: ordenado, seguro y lleno de posibilidades para comenzar su propia aventura.

### Pilares

- **Magia y aventura:** portales, gemas, nebulosas y caminos de luz.
- **Comunidad y pertenencia:** aldeas, plazas y escenas de colaboración.
- **Seguridad y confianza:** interfaces claras e iconografía amable.
- **Creatividad:** bloques, estructuras y personalización.
- **Escalabilidad:** sistema modular de color e iconos.

### Frase rectora

> Un mundo mágico para compartir la aventura.

## 4. Personalidad, tono y microcopy

DreamCraft debe sentirse:

- Cercano, humano, amable y directo.
- Épico, inspirador y memorable, sin agresividad.
- Seguro, ordenado y acogedor, sin autoritarismo.
- Juvenil, pero nunca infantilizado.
- Inclusivo para distintas edades y niveles de experiencia.

### Reglas de redacción

- Usar español internacional.
- Preferir frases cortas.
- Invitar antes que imponer.
- Usar verbos de acción: **construye, explora, descubre, comparte, crea**.
- Evitar frialdad corporativa, jerga cerrada, amenazas y exageración competitiva.
- Escribir siempre **DreamCraft**, con D y C mayúsculas.
- No escribir `DREAMCRAFT`, `Dreamcraft` ni `dreamcraft`.

### Mensajes de referencia

- “Bienvenido a DreamCraft. Construye, explora y comparte tu aventura.”
- “Cada construcción, historia y amistad forma parte de nuestro mundo.”
- “Una nueva aventura está a punto de comenzar.”
- “Queremos que DreamCraft sea un espacio seguro para todos.”

## 5. Dirección visual

### Atmósfera

- Nocturna, mágica, acogedora y luminosa.
- Profundidad azul con energía cian y dimensión violeta.
- Arquitectura original de bloques y portales brillantes.
- Sensación premium, aventurera y escalable.
- Nunca amenazante, agresiva ni excesivamente oscura.

### Materialidad

- Superficies cristalinas, translúcidas y facetadas.
- Bordes con halo cian.
- Sombras en azul oscuro.
- Gemas y reflejos como puntos de énfasis, no como ruido constante.

### Iluminación

- Luz volumétrica.
- Fuentes internas y externas.
- Glow cian controlado.
- Puntos blancos de máxima luz escasos y estratégicos.
- Violeta y magenta como luz interior o transición; nunca como fondo dominante.

### Formas y composición

- Cubos isométricos.
- Grilla pixelada.
- Marcos modulares de borde fino.
- Nodos o acentos cian.
- Divisores heráldicos pixelados usados con moderación.
- Caminos luminosos para guiar recorridos y progresión.
- Jerarquía clara y legibilidad antes que decoración.

### Elementos recurrentes permitidos

- Cubos isométricos.
- Gemas facetadas.
- Portales mágicos.
- Estrellas de cuatro puntas.
- Nebulosas azul–violeta–rosa.
- Partículas flotantes.
- Ornamentos heráldicos.
- Caminos luminosos.
- Montañas flotantes.
- Plazas comunitarias.

### Evitar absolutamente

- Cyberpunk sin contexto mágico.
- Vectores lisos como lenguaje principal.
- Pixel art retro plano.
- Estética esports agresiva.
- Horror o armas prominentes.
- Copias de assets oficiales de Minecraft.
- Diseño infantil o caricaturesco.
- Saturación de glow, gemas, destellos u ornamentos.

## 6. Paleta oficial

| Rol | Nombre | Código documentado | Uso |
|---|---|---:|---|
| Fondo dominante | Azul noche | `#05091A` | Fondo principal |
| Superficie | Azul profundo | `#0A1230` | Paneles, tarjetas y profundidad |
| Atención máxima | Cian brillante | `#7FFFF` | Cara superior del cubo |
| Acción/estructura | Azul eléctrico | `#287BFF` | Bordes activos, estructura y energía |
| Sombra/volumen | Azul oscuro | `#1A3A99` | Volumen, sombra y contraste |
| Glow | Cian luminoso | `#42D9E8` | Halos y acentos controlados |
| Gradiente claro | Azul cian | `#5BB8FF` | Inicio del gradiente del logotipo |
| Profundidad mágica | Violeta | `#8855FF` | Transiciones y profundidad |
| Sombra mágica | Magenta profundo | `#6622CC` | Base de gradiente y sombras |
| Reflejo premium | Cian blanco | `#B8F0FF` | Gemas, diamantes y reflejos |
| Partículas | Rosa violeta | `#CC44FF` | Nebulosa interior y partículas |
| Máxima luz | Blanco puro | `#FFFF` | Destellos puntuales |
| Exclusivo | Dorado especial | `#FFC857` | Rangos, recompensas y eventos |

> **Advertencia de fidelidad:** el PDF transcribe `#7FFFF` y `#FFFF`, que no tienen la longitud hexadecimal CSS habitual. El agente no debe corregirlos ni completarlos por intuición. Debe conservarlos como referencia documental y solicitar los valores válidos antes de generar código de producción.

### Uso de color en UI

- El azul noche debe dominar.
- Los paneles deben apoyarse en azul profundo.
- El azul eléctrico y el cian se reservan para acciones, foco, selección y guía visual.
- Violeta y magenta aportan profundidad y magia, no dominancia.
- El dorado es exclusivo para rangos, recompensas y eventos.
- No crear un color exclusivo para Synt, Nexo o Ciudad sin aprobación.

## 7. Tipografía

| Rol | Dirección aprobada | Uso |
|---|---|---|
| Logotipo | Pixel art gótico medieval con serifs angulares escalonados | Exclusivo de la marca; no sustituir |
| Títulos | Pixel art gótico coherente con el logo | Portadas, campañas y alto impacto |
| UI y subtítulos | Orbitron / Exo 2 / Montserrat SemiBold | Navegación, etiquetas y datos |
| Texto corrido | Inter / Montserrat Regular | Reglas, anuncios y textos extensos |

### Regla central

El pixel art gótico identifica; la sans serif informa. La UI, las normas y los textos largos deben priorizar legibilidad para todas las edades.

## 8. Logo e isotipo

### Firma oficial

- Composición vertical: isotipo —cubo con D— arriba, logotipo DreamCraft al centro y ornamento heráldico como cierre.
- El cubo representa construcción y creatividad.
- Su interior funciona como portal hacia una dimensión compartida de aventura, comunidad y descubrimiento.

### Aplicación

- Web y materiales oficiales: logo vertical completo.
- Headers y banners: variante horizontal.
- Iconos, favicon, avatar, Discord o launcher: isotipo solo.
- En tamaños mínimos, retirar detalles que comprometan la silueta.

### Protección

Siempre:

- Conservar proporciones, colores y glow.
- Usar sobre fondos oscuros con contraste suficiente.
- Respetar un área de seguridad equivalente al ancho de la gema inferior.

Nunca:

- Deformar, inclinar, recolorear o separar elementos.
- Reemplazar la tipografía.
- Añadir sombras pesadas o efectos metálicos ajenos.
- Aplicar estética esports.

## 9. Iconografía

La iconografía usa grilla pixelada, volumen sutil, glow cian y acentos violeta.

| Concepto | Representación recomendada | Tono |
|---|---|---|
| Seguridad | Escudo suave con gema cian | Protector, no autoritario |
| Ayuda | Burbuja de diálogo o farol mágico | Amable y accesible |
| Normas | Pergamino pixelado iluminado | Claro y positivo |
| Comunidad | Figuras o gemas conectadas | Inclusivo y cálido |
| Eventos | Cometa o estrella facetada | Festivo y mágico |
| Recompensas | Cofre mágico o gema dorada | Especial y aspiracional |

No asignar un icono específico a Synt, Nexo o Ciudad hasta conocer su función.

## 10. Estructura básica recomendada de menús

Esta estructura es una recomendación funcional, no una arquitectura definida por el manual.

### 10.1 Barra superior global

Debe contemplar:

- Isotipo o firma DreamCraft.
- Acceso a navegación principal.
- Estado de conexión o servidor, si aplica.
- Perfil del jugador.
- Acción contextual primaria.
- Acceso a ayuda o soporte.

Dirección visual:

- Fondo azul noche o panel azul profundo.
- Borde fino y nodos cian.
- Estado activo con azul eléctrico y glow controlado.
- Texto de UI en Orbitron, Exo 2 o Montserrat SemiBold.
- No usar el pixel art gótico para etiquetas pequeñas.

### 10.2 Navegación principal

Destinos provisionales:

1. Synt
2. Nexo
3. Ciudad

Cada entrada debe incluir:

- Nombre.
- Descripción breve basada en información confirmada.
- Icono semántico confirmado.
- Estado: `default`, `hover`, `focus`, `active`, `disabled`.
- Indicador de notificación solo si existe una regla funcional.

### 10.3 Navegación secundaria o contextual

- Breadcrumb o título de ubicación.
- Pestañas internas si el módulo tiene subsecciones.
- Acción primaria visible.
- Acción secundaria de retorno o ayuda.
- No superar la claridad con ornamentos innecesarios.

### 10.4 Pie o zona de soporte

- Ayuda.
- Normas.
- Seguridad.
- Comunidad.
- Estado técnico, si corresponde.

Usar iconografía cálida y mensajes positivos, nunca punitivos.

## 11. Anatomía común de un bloque

Todo prompt para un bloque debe definir:

1. **Propósito:** qué tarea resuelve.
2. **Audiencia:** para quién se muestra.
3. **Jerarquía:** título, resumen, datos, acciones y ayuda.
4. **Contenido:** texto real o placeholders explícitos.
5. **Componentes:** tarjetas, listas, tabs, chips, botones, paneles o estados.
6. **Estados:** vacío, carga, error, bloqueado, disponible, seleccionado y completado, según corresponda.
7. **Interacción:** hover, focus, teclado, selección y retorno.
8. **Responsive:** escritorio, tablet y móvil.
9. **Accesibilidad:** contraste, foco visible, etiquetas claras y legibilidad.
10. **Dirección visual:** paleta, materialidad, iconos, glow y nivel ornamental.
11. **Restricciones:** elementos prohibidos y datos que no deben inventarse.
12. **Salida esperada:** wireframe, mockup, imagen, especificación o código.

## 12. Bloque Synt — plantilla sin supuestos

### Información obligatoria pendiente

- Objetivo de Synt: `[OBJETIVO_DE_SYNT]`
- Usuario principal: `[USUARIO_DE_SYNT]`
- Datos: `[DATOS_DE_SYNT]`
- Acciones: `[ACCIONES_DE_SYNT]`
- Estados: `[ESTADOS_DE_SYNT]`
- Relación con Nexo y Ciudad: `[RELACION_SYNT_NEXO_CIUDAD]`

### Prompt base

> Diseña el bloque **Synt** para DreamCraft. Su función confirmada es `[OBJETIVO_DE_SYNT]`. Organiza la interfaz con una jerarquía clara: título, explicación breve, `[DATOS_DE_SYNT]`, acción primaria `[ACCION_PRIMARIA_SYNT]`, acciones secundarias `[ACCIONES_SECUNDARIAS_SYNT]` y ayuda contextual. Incluye los estados `[ESTADOS_DE_SYNT]`. Mantén fondo dominante azul noche, paneles azul profundo, bordes pixelados finos, acentos cian y azul eléctrico, profundidad violeta y glow controlado. Usa una sans serif aprobada para UI y reserva el pixel art gótico para un título de alto impacto, solo si conserva legibilidad. La atmósfera debe ser mágica, luminosa, premium y acogedora. No inventes icono, color propio, mecánicas ni contenido de Synt. Evita cyberpunk, esports, pixel art retro plano, estética infantil y exceso de ornamentos. Entrega `[TIPO_DE_SALIDA]` en variantes escritorio y móvil, con estados de interacción y foco accesible.

## 13. Bloque Nexo — plantilla sin supuestos

### Información obligatoria pendiente

- Objetivo de Nexo: `[OBJETIVO_DE_NEXO]`
- Usuario principal: `[USUARIO_DE_NEXO]`
- Datos: `[DATOS_DE_NEXO]`
- Acciones: `[ACCIONES_DE_NEXO]`
- Estados: `[ESTADOS_DE_NEXO]`
- Relación con Synt y Ciudad: `[RELACION_SYNT_NEXO_CIUDAD]`

### Prompt base

> Diseña el bloque **Nexo** para DreamCraft. Su función confirmada es `[OBJETIVO_DE_NEXO]`. Presenta `[DATOS_DE_NEXO]` con una navegación clara y una acción primaria `[ACCION_PRIMARIA_NEXO]`. Añade `[ACCIONES_SECUNDARIAS_NEXO]`, ayuda contextual y los estados `[ESTADOS_DE_NEXO]`. Aplica el sistema DreamCraft: azul noche dominante, paneles azul profundo, estructura azul eléctrico, halos cian, transiciones violeta y detalles cristalinos o facetados usados con moderación. Si la función confirmada implica conexión o tránsito, se puede usar el motivo de portal o camino luminoso; de lo contrario, no asumirlo. No inventes color, símbolo, historia o mecánica para Nexo. Mantén microcopy breve, cercano, épico y seguro. Entrega `[TIPO_DE_SALIDA]` responsive con estados `default`, `hover`, `focus`, `active`, `loading`, `empty` y `error` solo cuando sean pertinentes.

## 14. Bloque Ciudad — plantilla sin supuestos

### Información obligatoria pendiente

- Objetivo de Ciudad: `[OBJETIVO_DE_CIUDAD]`
- Usuario principal: `[USUARIO_DE_CIUDAD]`
- Datos: `[DATOS_DE_CIUDAD]`
- Acciones: `[ACCIONES_DE_CIUDAD]`
- Estados: `[ESTADOS_DE_CIUDAD]`
- Relación con Synt y Nexo: `[RELACION_SYNT_NEXO_CIUDAD]`

### Prompt base

> Diseña el bloque **Ciudad** para DreamCraft. Su función confirmada es `[OBJETIVO_DE_CIUDAD]`. Estructura el contenido en título, resumen, `[DATOS_DE_CIUDAD]`, acción primaria `[ACCION_PRIMARIA_CIUDAD]`, acciones secundarias `[ACCIONES_SECUNDARIAS_CIUDAD]` y ayuda contextual. Incluye `[ESTADOS_DE_CIUDAD]`. La dirección visual debe sentirse nocturna, mágica, acogedora y viva, con azul noche dominante, paneles azul profundo, acentos cian y azul eléctrico, y luces violetas o magentas interiores. Las plazas comunitarias, arquitectura original de bloques o caminos luminosos solo deben utilizarse si la función confirmada del bloque los justifica. No copiar assets oficiales de Minecraft ni inventar edificios, servicios o mecánicas. Entrega `[TIPO_DE_SALIDA]` para escritorio y móvil, con navegación por teclado y foco visible.

## 15. Meta-prompt para generar cualquier bloque

> Actúa como director de UI y prompt designer de DreamCraft. Convierte la siguiente especificación funcional en un prompt preciso para una IA de diseño: `[ESPECIFICACION_FUNCIONAL]`.
>
> Antes de redactar, separa:
> 1. hechos confirmados;
> 2. variables pendientes;
> 3. decisiones visuales permitidas por la marca;
> 4. supuestos prohibidos.
>
> El prompt final debe incluir propósito, usuario, jerarquía, contenido, componentes, acciones, estados, responsive, accesibilidad, microcopy, dirección visual, restricciones y salida esperada. Usa la identidad DreamCraft: atmósfera nocturna, mágica, luminosa y acogedora; azul noche dominante; paneles azul profundo; energía cian y azul eléctrica; profundidad violeta; superficies cristalinas; grilla pixelada; marcos finos y glow controlado. Prioriza legibilidad. No inventes funciones, colores, iconos, datos o lore. Si falta un dato esencial, mantenlo como `[VARIABLE]` y enuméralo al final bajo “Información pendiente”.

## 16. Formato de salida que debe producir el agente

```markdown
### [NOMBRE_DEL_BLOQUE]

#### Hechos confirmados
- ...

#### Información pendiente
- `[VARIABLE]`: ...

#### Prompt final
> ...

#### Estados requeridos
- Default
- Hover
- Focus
- Active
- Loading, vacío, error o bloqueado: solo si corresponde

#### Restricciones
- ...

#### Criterios de aceptación
- ...
```

## 17. Arquitectura de modalidades oficialmente documentada

Esta información solo debe usarse si Synt, Nexo o Ciudad pertenecen de forma confirmada a una modalidad.

| Modalidad | Color auxiliar | Hex | Símbolo |
|---|---|---:|---|
| Survival | Verde esmeralda | `#2ECC71` | Hoja o brújula pixelada |
| Skyblock | Turquesa | `#1ABC9C` | Isla flotante pixelada |
| Creativo | Dorado | `#FFC857` | Bloque o herramienta pixelada |
| Minigames | Naranja | `#E67E22` | Trofeo o estrella pixelada |
| Eventos | Rosa mágico | `#FF6EB4` | Cometa o fuegos artificiales |

Principio: la marca se reconoce primero; la modalidad se descubre después. El color auxiliar identifica, pero no reemplaza el sistema DreamCraft.

## 18. Checklist antes de aceptar un prompt

- [ ] ¿La pieza se reconoce como DreamCraft sin depender del nombre?
- [ ] ¿Predominan azul noche, cian, azul eléctrico y profundidad violeta?
- [ ] ¿La magia es luminosa y acogedora, no agresiva ni amenazante?
- [ ] ¿La composición respeta una lógica pixelada, modular y de bloques?
- [ ] ¿El texto es legible?
- [ ] ¿DreamCraft conserva su capitalización exacta?
- [ ] ¿Gemas, destellos y ornamentos aportan foco en vez de ruido?
- [ ] ¿Se definieron propósito, jerarquía, acciones, estados y responsive?
- [ ] ¿Se evitó inventar información sobre Synt, Nexo o Ciudad?
- [ ] ¿Los datos pendientes aparecen como variables explícitas?
- [ ] ¿Se evitó corregir por intuición los códigos hex dudosos del PDF?
- [ ] ¿Se incluyeron accesibilidad y foco visible?

## 19. Prioridad de fuentes

Cuando existan contradicciones, aplicar este orden:

1. Requisitos funcionales confirmados por el responsable del proyecto.
2. Brand Identity Book v2.0.
3. Esta guía operativa.
4. Decisiones propuestas por el agente, siempre rotuladas como propuestas.

El agente nunca debe presentar una propuesta propia como regla oficial de DreamCraft.
