# Auditoría completa: App Marín 376 (Pedidos + Fiados + API Cloudflare)

**Fecha**: 2026-11-08
**Modo**: Salud general del repositorio
**Fuente**: `Pedidos-Mar-n-` (index.html, fiados.html, sw.js) + `marin376-cloudflare` (src/index.js), rama `claude/audit-marin-app-5opmh4`
**Lentes ejecutados**: 10/10 (adaptados: no hay React ni Rust; "Frontend" = JS/CSS plano, "Backend" = Worker de Cloudflare en JS, "DX" = experiencia del operador/mantenedor)
**Lentes omitidos**: ninguno

Etiquetas de confianza: **[Certeza]** verificado en el código · **[Probable]** inferencia fuerte · **[Suposición]** sin verificar.

---

## Veredicto general: NECESITA REHACER (seguridad del API)

La app funciona y tiene mucho cuidado operativo (polling liviano, no repinta mientras el usuario escribe, logs de webhooks). Pero el API de escritura no tiene ninguna autenticación: cualquiera que lea la URL del Worker (está en el código público de la página) puede cambiar precios, borrar productos en Loyverse y borrar líneas de fiados. Eso, sumado a que la página publica costos y utilidad por producto, pesa más que todo lo demás.

## Veredicto por lente

| # | Lente | Veredicto | Críticos | Recomendaciones |
|---|-------|-----------|----------|-----------------|
| 1 | Diseño | APROBAR CON CAMBIOS | 0 | 3 |
| 2 | UX | APROBAR CON CAMBIOS | 0 | 3 |
| 3 | Frontend | NECESITA REHACER | 1 | 4 |
| 4 | Backend (Worker) | NECESITA REHACER | 2 | 3 |
| 5 | Estructura | APROBAR CON CAMBIOS | 0 | 3 |
| 6 | Mantenedor del repo | APROBAR CON CAMBIOS | 0 | 3 |
| 7 | Listo para producción | NECESITA REHACER | 2 | 3 |
| 8 | Experiencia del operador | APROBAR CON CAMBIOS | 0 | 2 |
| 9 | Rendimiento | APROBAR CON CAMBIOS | 1 | 2 |
| 10 | Accesibilidad | APROBAR CON CAMBIOS | 0 | 3 |

## Todos los críticos (arreglar primero)

| # | Problema | Lentes | Archivo(s) | Impacto | Arreglo |
|---|----------|--------|-----------|---------|---------|
| C1 | **API sin autenticación.** CORS `*`, ningún POST valida identidad. `editar_precio`, `aplicar_precio_abc`, `eliminar_productos` (borra en Loyverse, permanente), `eliminar_proveedor`, `fiados_eliminar_linea`, etc. aceptan cualquier llamada. | Backend, Prod | `marin376-cloudflare/src/index.js:66-69`, `:8011-8170` | Cualquier persona puede cambiar precios del POS o borrar el catálogo con un `curl`. | Token compartido en header `X-Marin-Key` (secreto de Wrangler), validado antes del `switch`. CORS limitado a `https://minimarketmarin01-cell.github.io`. Ver propuesta abajo. |
| C2 | **Datos de negocio publicados.** `<script id="appdata">` incrusta ~600 KB con 2.043 productos: venta, utilidad (`prof`), costo, stock (abr–jun 2026). Los GET de reportes (`stock_reporte`, `fiados_resumen`, `clientes_buscar`) también son abiertos. | Prod, Backend | `index.html:4253`, `src/index.js:7633-7660` | [Probable] la página está en GitHub Pages pública → competencia o cualquiera ve márgenes, y nombres/deudas de clientes de fiado (datos personales). | Sacar `appdata` del HTML (la app ya trae todo del Worker) y proteger los GET con el mismo token. |
| C3 | **XSS almacenado.** 252 `innerHTML`, solo 2 funciones `esc()` locales. Nombres de proveedor/producto/cliente se insertan sin escapar (`index.html:9149`, `:10036`, `:16736`, `:17194`; `fiados.html:494`). Con C1, un atacante crea un proveedor llamado `<img src=x onerror=…>` y se ejecuta en el celular de cada trabajador. | Frontend, Backend | index.html, fiados.html | Robo del token futuro, acciones en nombre del usuario. | Una sola `escHtml()` global y usarla en todo texto que venga del servidor; o `textContent` en vez de `innerHTML`. |
| C4 | **Chequeo de versión baja 2,4 MB cada 5 min.** `chequearVersion()` hace `fetch(location.pathname, {cache:'no-store'})` del index.html completo, cada 5 min y en cada `visibilitychange`. | Rendimiento, Prod | `index.html:6013-6028` | [Probable] ~0,8 MB comprimido por chequeo × 120/día × aparato ≈ 100 MB/día de datos móviles por celular, y batería. | Publicar `version.json` (≈50 bytes) y consultar solo eso. |
| C5 | **Ícono de notificación roto.** `sw.js` usa `icono-192.png`; el archivo real es `icon-192.png`. | Prod, Diseño | `sw.js:39-40` | Las notificaciones push salen sin ícono (404). | Cambiar a `icon-192.png`. Arreglo de 2 caracteres. |

## Problemas transversales

| Problema | Marcado por | Causa raíz | Arreglo unificado |
|----------|------------|-----------|-------------------|
| Confianza total en el cliente | Backend, Frontend, Prod | Se diseñó como app interna, pero corre en internet público. | Token + CORS (C1) + escape (C3). Juntos cierran la cadena. |
| Archivo único de 18.858 líneas / 2,4 MB | Estructura, Mantenedor, Rendimiento | Todo (datos, íconos base64, CSS, JS) en index.html. | Sacar `appdata` y los PNG base64 a archivos. Baja a ~1,3 MB sin tocar lógica. |
| Errores tragados en silencio | Prod, Operador | 139 `catch(e){}` vacíos en index.html. | Mantener los de UI; en los de red/escritura, `showToast` + log. |

## Todas las recomendaciones

| # | Recomendación | Lente | Archivo(s) | Arreglo |
|---|--------------|-------|-----------|---------|
| R1 | `notificationclick` enfoca cualquier pestaña pero no navega a `url` | UX | sw.js:52-58 | `client.navigate(targetUrl)` antes de `focus()` |
| R2 | Endpoints `diag_*` y `migrar_sector`/`migrar_proveedores_mayuscula` expuestos en producción | Backend | src/index.js:7663+, 7806 | Detrás de `MIGRACION_CLAVE` como `migrar_*` |
| R3 | Clave de migración viaja por query string (`?clave=`) | Backend | src/index.js:7565 | Header; las URLs quedan en logs e historial |
| R4 | 170 `addEventListener` vs 6 `removeEventListener` | Rendimiento | index.html | Revisar modales que se reabren; usar `{once:true}` o delegación |
| R5 | 4 `onclick` en `<div>/<span>` sin teclado | A11y | index.html | `<button>` |
| R6 | Íconos PNG en base64 dentro del JS (`I192`, ~50 KB c/u) cuando ya existen como archivos | Mantenedor | index.html:4259 | `href="icon-192.png"` |
| R7 | `appdata` estático puede mostrar datos de junio si falla la red al abrir | UX | index.html:4266 | Mostrar "sin conexión" en vez de datos viejos |
| R8 | Sin tests del Worker; `test_*.json` sueltos en raíz | Mantenedor | marin376-cloudflare | Un script mínimo con `wrangler dev` para las acciones de escritura |
| R9 | Cargar `index.js` (8.308 líneas) en un solo módulo | Estructura | src/index.js | Separar por dominio: fiados, precios, mermas, loyverse |

---

# Informes por lente

---

## 1. Diseño

**Veredicto**: APROBAR CON CAMBIOS

- [Certeza] Hay sistema de íconos SVG único (`ICONOS`, `ico()`), con `aria-hidden` — consistente.
- [Certeza] Solo 2 casos de fuente <12px. Bien para celular.
- [Certeza] C5: ícono de push roto.
- [Suposición] No revisé modo oscuro ni contraste con capturas; no hay capturas en el repo.

Recomendaciones: arreglar ícono push; sacar estilos inline repetidos (`style="padding:10px 12px;border-bottom:1px solid var(--line)…"` aparece en muchas plantillas) a clases; verificar contraste de `--wa` sobre blanco.

## 2. UX

**Veredicto**: APROBAR CON CAMBIOS

Recorrido: abrir → carga desde Worker (o `appdata` de respaldo) → polling cada 5 s de "¿cambió algo?" → repinta solo si el usuario está inactivo.
- [Certeza] Muy bueno: `usuarioActivo()` evita que la tabla se mueva mientras se escribe una cantidad (`index.html:6623`).
- [Certeza] Actualización automática no interrumpe si hay un modal abierto (`hayAlgoAbierto`).
- R1, R7.

## 3. Frontend (JS/CSS plano)

**Veredicto**: NECESITA REHACER (por C3)

- [Certeza] C3: escape inconsistente. Ejemplo `index.html:9149`: `` `<option value="${p.id}">${p.nombre}</option>` ``.
- [Certeza] `iniciarPollingTiempoReal()` limpia solo uno de sus 3 `setInterval`; hoy se llama una sola vez (`:6479`), así que no hay fuga, pero es frágil si alguien lo vuelve a llamar.
- [Certeza] Cámara: `_scanToken` invalida aperturas viejas y detiene tracks — bien hecho.
- R4, R6.

## 4. Backend (Worker Cloudflare)

**Veredicto**: NECESITA REHACER

Análisis de rutas de error:
- [Certeza] Webhook Loyverse: valida token y deja log de rechazos — correcto.
- [Certeza] Migraciones: validan `MIGRACION_CLAVE` — correcto.
- [Certeza] **Todo lo demás: sin validación.** C1, C2.
- [Certeza] Modo `?db=supabase` es solo lectura para POST — correcto.
- [Certeza] Batch D1 en lotes; borrado en Loyverse con timeout de 20 s y respaldo previo en `auditoria` — buen manejo.
- R2, R3.

Propuesta para C1 (mínima, compatible):
```js
// al inicio de _servidor.fetch, después del webhook y migraciones:
const ORIGEN = "https://minimarketmarin01-cell.github.io";
if (request.headers.get("X-Marin-Key") !== env.APP_KEY) return json({ ok:false, error:"No autorizado" }, 401);
// CORS_HEADERS: "Access-Control-Allow-Origin": ORIGEN, "Access-Control-Allow-Headers": "Content-Type, X-Marin-Key"
```
En la app: la clave se pide una vez por aparato y se guarda en `localStorage`. Las apps Android (etiquetas, POS, precios) y `minimarket-dashboard` deben mandar el mismo header — desplegar app y Worker juntos o aceptar ambos modos una semana.

## 5. Estructura

**Veredicto**: APROBAR CON CAMBIOS

Mapa: GitHub Pages (index.html, fiados.html) → Worker `marin376-api` → D1 (+ espejo a Supabase en segundo plano) ↔ Loyverse API / webhook. Segundo Worker `argomedo455`.
- [Certeza] Sin ciclos (mapa-codigo/GRAPH_REPORT.md).
- [Certeza] `showToast` con 88 conexiones: bien como utilidad central.
- R9; y el problema transversal del archivo único.

## 6. Mantenedor del repo

**Veredicto**: APROBAR CON CAMBIOS

- [Certeza] Comentarios en español, explican el porqué — muy buen nivel.
- [Certeza] `mapa-codigo/` regenerable — útil.
- [Certeza] Duplicados: dos `esc` locales idénticos (`:16759`, `:16800`), dos lógicas "hold" de stepper (`:11103`, `:15681`).
- R6, R8.

## 7. Listo para producción

**Veredicto**: NECESITA REHACER

| Falla | Qué pasa hoy |
|-------|-------------|
| Alguien descubre la URL del API | Puede editar/borrar todo (C1) |
| Sin red al abrir | Muestra `appdata` de junio sin aviso claro (R7) |
| Loyverse pierde un webhook | Cubierto: respaldo cada 10 min y cron horario |
| Límite de lecturas D1 | Mitigado: marca liviana cada 5 s |
| Push | Sale sin ícono (C5) |

## 8. Experiencia del operador / mantenedor

**Veredicto**: APROBAR CON CAMBIOS

- [Certeza] Mensajes de error al usuario son accionables ("Falta id de Loyverse — toca ♻️ Catálogo y vuelve a intentar").
- [Certeza] 139 `catch(e){}` vacíos: algunos ocultan fallas de guardado. Revisar los que rodean `fetch` POST.

## 9. Rendimiento

**Veredicto**: APROBAR CON CAMBIOS (con C4 crítico)

| Recurso | Costo estimado |
|---------|---------------|
| Carga inicial index.html | 2,4 MB sin comprimir |
| `chequearVersion` | 2,4 MB cada 5 min por aparato (C4) |
| Polling marca | 1 fila cada 5 s, solo visible — correcto |
| Timers globales | 11 `setInterval`, todos de vida de la página |

## 10. Accesibilidad

**Veredicto**: APROBAR CON CAMBIOS

- [Certeza] 93 `aria-label`, 42 `role=`; viewport sin bloquear zoom — bien.
- [Certeza] R5: 4 elementos clicables no enfocables.
- [Suposición] No probé lector de pantalla; uso real es por personal con vista normal, prioridad baja.
