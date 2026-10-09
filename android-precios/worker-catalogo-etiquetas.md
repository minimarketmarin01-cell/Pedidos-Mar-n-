# Acción liviana `catalogo_etiquetas` (para los dos Workers)

**Estado: NO está publicada.** La app ya la pide primero y, si el servidor no la tiene, usa el catálogo
completo. Publicarla solo hace la actualización más rápida y liviana (no se calculan ventas, pendientes,
favoritos, costeo, etc.).

Respuesta: `{"ok":true,"productos":[["sku","nombre",precio,"barcode"], …]}` — solo lectura (un `SELECT`).

```js
      if (action === "catalogo_etiquetas") {
        const { results } = await env.DB.prepare("SELECT sku, nombre, precio, barcode FROM productos").all();
        return json({
          ok: true,
          productos: (results || []).map((p) => [p.sku, p.nombre, Math.round(p.precio || 0), p.barcode || ""])
        });
      }
```

## Dónde pegarlo
- **marin376-api**: dentro de `if (request.method === "GET") {`, justo antes de `if (action === "ficha_producto") {`.
- **argomedo455**: justo antes de `if (action === "catalogo") {`.

Pégalo en el código fuente original del Worker (el proyecto de `wrangler`), no en el código ya empaquetado
del panel de Cloudflare, porque el próximo `wrangler deploy` lo borraría. Luego `wrangler deploy`.

## Cómo comprobarlo
Abre en el navegador:
- https://marin376-api.minimarketmarin01.workers.dev/?action=catalogo_etiquetas
- https://argomedo455.minimarketmarin01.workers.dev/?action=catalogo_etiquetas

Debe verse `{"ok":true,"productos":[[…` con todos los productos.
