# Precios Marín: base de datos y consumo (migración D1 → Supabase)

## Qué es la app, en datos
- Corre en el POS Android (Java puro, sin librerías). **No tiene base de datos propia**: guarda un archivo de
  caché en el equipo (≈ 3.300 productos por local, ≈ 300 KB) y habla con los Workers que ya existen
  (`marin376-api` y `argomedo455`).
- Lo que lee: `productos` (sku, nombre, precio, costo, código, categoría, foto). Las fotos vienen de
  `api.loyverse.com`, no de Supabase.
- Lo que escribe (vía Worker, que ya espeja a Supabase): `editar_precio`, `editar_codigo_barras`,
  `editar_producto` (nombre), `crear_producto`.
- Por eso **no necesita schema propio** en Supabase: usa `marin376.productos` y `argomedo455.productos`, que
  ya están copiadas (3.315 y 3.276 filas, iguales a D1). Un schema nuevo sería una tercera copia sin uso.

## Qué descarga (hoy)
| Pedido | Cuándo | Tamaño aprox. |
|---|---|---|
| `?action=catalogo_etiquetas` (el Worker no lo tiene → devuelve el catálogo completo) | al abrir con la lista de más de 6 h, o «Actualizar productos» | 1,2 – 1,7 MB (medido en `sync`/`(panel)`) |
| `?action=ficha_producto&sku=…` | cada vez que se abre un producto | ≈ 8 – 15 KB (10 consultas; **estimado**) |
| `editar_*`, `crear_producto`, `catalogo_crear` | al guardar/crear | < 2 KB |

## Medición
- Desde la versión 1.17 la app manda `?app=etiquetas` en cada pedido y los Workers (PR #102 / #82) lo anotan en
  `uso_diario` con `app = 'etiquetas'`, **todas** las respuestas (también las de menos de 50 KB).
- Ver: `?action=uso_diario` (promedio de 7 días y proyección a 30 días por app).

## Cómo baja ahora (versión 1.18)
- **Solo se guarda en el equipo lo elegido**: los productos de las categorías de Loyverse marcadas en ☰ → «Elegir
  categorías» (por local). Sin categorías marcadas = todas.
- **Se actualiza por cambios**: la app guarda el número del registro de cambios del servidor (`cursor`) y pregunta
  `catalogo_etiquetas&desde=N&cats=[…]`. Si no cambió nada responde ~80 bytes; si cambió algo, solo esas filas. Se revisa
  al abrir si pasaron más de 20 min. Solo se baja de nuevo todo cuando se **agrega** una categoría (quitar una solo
  borra del equipo) o si el servidor no reconoce el número.
- **Lo que no está guardado se busca a pedido**: lo que se escribe en 🔍 (3 letras o más), un código escaneado o pasado por
  el lector y no encontrado en el equipo se busca en el servidor (`buscar_etiquetas`: todas las categorías, hasta 40
  resultados, sin importar acentos).
- **Ficha corta**: `ficha_etiqueta` (precio, costo, código, lote) con 2 consultas en vez de ~10.
- Servidor viejo (sin estas acciones): la app lo detecta por la primera clave de la respuesta, no baja el catálogo y usa
  el camino de antes.
- Limitación: el registro de cambios del servidor también anota cambios de stock, así que un producto vendido aparece en
  la respuesta aunque su precio no cambie; la app compara y no cuenta lo que no cambió (nombre, precio, código,
  categoría, foto o costo).

Pruebas: `migracion/probar_etiquetas.mjs` en los dos Workers y `android-precios/pruebas/api/probar_api.sh` (lectura de
las respuestas reales del Worker, en el CI).
