# DreamCraft — Prompt: Bloque Synt (sprites activo/inactivo para resource pack)

Genera una **hoja de sprites de videojuego** para el resource pack de DreamCraft: dos **texturas de ítem pixel art 2D** de un núcleo arcano de forma cúbica, vistas **ESTRICTAMENTE DE FRENTE** (una sola cara plana, como las texturas de los ítems de Minecraft: piedra tallada con runas grabadas en la cara frontal). Colocadas lado a lado en una fila horizontal. Son la misma entidad en dos estados: `synt_activo` (izquierda) y `synt_inactivo` (derecha). El servidor las mostrará como ícono de inventario según la actividad de la protección: encendido cuando protege, apagado cuando no.

**Esto NO es una interfaz y NO es un render 3D.** Prohibido: perspectiva isométrica, vista 3/4, ángulo con arista frontal, caras superior o laterales visibles, volumen tridimensional, render de modelo de juego. Prohibido copiar los cubos isométricos del logo/brand de DreamCraft. Es UNA cara plana pintada píxel a píxel, como un ítem del inventario. Sin paneles, sin tarjetas, sin botones, sin ventanas, sin marcos de UI, sin pantallas, sin texto, sin rótulos, sin logotipos, sin suelo ni escenario: solo las dos caras runadas flotando sobre fondo 100% transparente.

**Las dos texturas deben ser idénticas en forma, tamaño y encuadre** — misma silueta píxel a píxel, mismo centrado en su mitad de la imagen, misma dirección de luz. Únicamente cambia el estado de energía:

- `synt_activo`: cara de piedra arcana encendida. Runas con núcleo luminoso visible, color cian `#42D9E8` con glow controlado, detalles azul eléctrico `#287BFF`, reflejos violeta `#8855FF`, cuerpo en azul profundo `#0A1230`. Energía estable y acogedora, nunca agresiva.
- `synt_inactivo`: exactamente la misma cara apagada. Runas sin brillo, paleta apagada azul noche `#05091A` y azul profundo `#0A1230`, sin glow, sin partículas, sin destellos. La forma y las runas se conservan idénticas; solo falta la luz.

Estilo pixel art limpio con grilla visible, silueta legible a tamaño pequeño de slot de inventario (32×32 px), borde claramente definido alrededor de la cara cúbica para que la silueta se lea aun plana. Atmósfera nocturna, mágica, premium y acogedora.

No copies assets oficiales de Minecraft ni de otros juegos; no añadas criaturas, manos, paisajes ni elementos narrativos; no uses cyberpunk, estética esports, neón urbano ni exceso de ornamentos.

Entrega: **una sola imagen PNG con fondo transparente**, dos sprites planos separados por un margen claro e igual al borde, listos para recortarse como dos texturas individuales sin retoques.
