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

## Propuesta para bajar el consumo (pendiente de aprobar)
1. **`catalogo_etiquetas` liviano y por cambios**: solo `[sku, nombre, precio, código, categoría, foto, costo]`
   (≈ 340 KB el catálogo entero) y, con `&desde=N`, solo lo que cambió, usando el registro de cambios que el
   Worker ya tiene (`panel_version` / `panel_cambios`). Sin cambios → respuesta de ~100 bytes.
2. **`ficha_etiqueta`**: lo mismo que usa la app de `ficha_producto` (precio, costo, código, lote), con 2
   consultas en vez de 10.
3. La app cae al camino actual si el Worker no tiene las acciones nuevas.
