# Prueba Square Sandbox: ¿los "Open Tickets" traen line_items?

Objetivo: confirmar de forma empírica, **en entorno SANDBOX** (sin plata
real, sin declarar operación comercial en ningún país), si una orden en
estado `OPEN` creada vía Orders API se puede volver a leer con el detalle
completo de productos y cantidades — algo que Loyverse no expone para
tickets abiertos.

Este test NO reemplaza la duda real pendiente: si el "Open Ticket" que un
cajero deja abierto **desde la app física de Square POS** aparece como
orden `OPEN` en esta misma API. Esto solo confirma la mitad controlada por
API (crear orden OPEN vía API + leerla de vuelta). La otra mitad (POS físico
-> Orders API) solo se puede confirmar teniendo el POS de verdad en modo
sandbox, o preguntando directo a soporte/foro de Square.

## Requisitos

- Cuenta de **desarrollador** Square (gratis) en developer.squareup.com.
- Una app dentro de esa cuenta, con su pestaña "Sandbox" (se crea sola).
- El **Access Token de Sandbox** (no el de producción) de esa pestaña.
- Node.js 18+ (usa `fetch` nativo, sin dependencias).

## Uso

```bash
cd tools/square-sandbox-test
cp .env.example .env
# editar .env y pegar el SQUARE_ACCESS_TOKEN de sandbox
node test-open-order.js
```

## Qué hace el script

1. Toma (o crea automáticamente) una `location_id` de la cuenta sandbox.
2. Crea un ítem de catálogo de prueba ("Producto Prueba Sandbox - Coca Cola
   500ml") vía `POST /v2/catalog/object`.
3. Crea una orden en `state: "OPEN"` con ese producto y cantidad 3 vía
   `POST /v2/orders`.
4. Busca esa orden de vuelta vía `POST /v2/orders/search` filtrando
   `state_filter: { states: ["OPEN"] }`.
5. Además la recupera directo por ID vía `POST /v2/orders/batch-retrieve`
   como doble confirmación.
6. Imprime si `line_items` viene completo (nombre del producto + cantidad).

## Qué NO hace (a propósito)

- No usa ni pide credenciales de producción.
- No cobra nada ni mueve plata real (sandbox no tiene dinero real).
- No crea ni configura una cuenta Square en vivo declarando operar en un
  país donde el negocio no opera — eso sigue fuera de los límites de este
  proyecto.
