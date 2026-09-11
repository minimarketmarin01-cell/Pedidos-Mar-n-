#!/usr/bin/env node
/**
 * Prueba empírica en Square SANDBOX (no producción):
 * 1. Crea un item de catálogo de prueba.
 * 2. Crea una orden en estado OPEN con ese item.
 * 3. Busca esa orden filtrando por state_filter=OPEN y confirma
 *    que trae los line_items completos (producto + cantidad).
 *
 * Requiere credenciales de DESARROLLADOR + SANDBOX (Access Token de
 * la sandbox, no de producción). No procesa pagos reales ni requiere
 * que el negocio esté habilitado para operar en ningún país: la
 * sandbox es solo para evaluar la API.
 *
 * Uso:
 *   SQUARE_ACCESS_TOKEN=xxx node test-open-order.js
 * o crear un archivo .env junto a este script (ver .env.example).
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// --- Carga simple de .env (sin dependencias) ---
function loadEnvFile() {
  const envPath = path.join(__dirname, '.env');
  if (!fs.existsSync(envPath)) return;
  const content = fs.readFileSync(envPath, 'utf8');
  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const idx = trimmed.indexOf('=');
    if (idx === -1) continue;
    const key = trimmed.slice(0, idx).trim();
    let value = trimmed.slice(idx + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env)) process.env[key] = value;
  }
}
loadEnvFile();

const ACCESS_TOKEN = process.env.SQUARE_ACCESS_TOKEN;
const SQUARE_VERSION = process.env.SQUARE_VERSION || '2024-08-21';
const BASE_URL = 'https://connect.squareupsandbox.com';
let LOCATION_ID = process.env.SQUARE_LOCATION_ID || null;

if (!ACCESS_TOKEN) {
  console.error(
    'Falta SQUARE_ACCESS_TOKEN. Definilo como variable de entorno o en tools/square-sandbox-test/.env\n' +
      '(Usá el Access Token de SANDBOX del dashboard de desarrollador Square, no el de producción.)'
  );
  process.exit(1);
}

function log(title, data) {
  console.log(`\n=== ${title} ===`);
  console.log(JSON.stringify(data, null, 2));
}

async function squareRequest(method, endpoint, body) {
  const res = await fetch(`${BASE_URL}${endpoint}`, {
    method,
    headers: {
      Authorization: `Bearer ${ACCESS_TOKEN}`,
      'Content-Type': 'application/json',
      'Square-Version': SQUARE_VERSION,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    console.error(`\nERROR ${method} ${endpoint} -> HTTP ${res.status}`);
    console.error(JSON.stringify(json, null, 2));
    throw new Error(`Square API error en ${endpoint}: HTTP ${res.status}`);
  }
  return json;
}

async function getSandboxLocationId() {
  if (LOCATION_ID) return LOCATION_ID;
  const data = await squareRequest('GET', '/v2/locations');
  if (!data.locations || data.locations.length === 0) {
    throw new Error(
      'La cuenta sandbox no tiene ubicaciones (locations). Creá al menos una en el dashboard de Square Sandbox.'
    );
  }
  const loc = data.locations[0];
  console.log(`\nUsando location de sandbox: ${loc.name} (${loc.id})`);
  return loc.id;
}

async function createTestCatalogItem() {
  const itemTempId = '#TestItem_' + crypto.randomUUID();
  const varTempId = '#TestVar_' + crypto.randomUUID();

  const body = {
    idempotency_key: crypto.randomUUID(),
    object: {
      type: 'ITEM',
      id: itemTempId,
      item_data: {
        name: 'Producto Prueba Sandbox - Coca Cola 500ml',
        variations: [
          {
            type: 'ITEM_VARIATION',
            id: varTempId,
            item_variation_data: {
              item_id: itemTempId,
              name: 'Unidad',
              pricing_type: 'FIXED_PRICING',
              price_money: { amount: 1000, currency: 'USD' },
            },
          },
        ],
      },
    },
  };

  const data = await squareRequest('POST', '/v2/catalog/object', body);
  log('Catalogo creado (respuesta cruda)', data);

  const realItemId = data.id_mappings.find((m) => m.client_object_id === itemTempId)
    .object_id;
  const realVarId = data.id_mappings.find((m) => m.client_object_id === varTempId)
    .object_id;

  console.log(`\nItem catalogo creado: ${realItemId}`);
  console.log(`Variacion (SKU vendible) creada: ${realVarId}`);

  return { itemId: realItemId, variationId: realVarId };
}

async function createOpenOrder(locationId, variationId) {
  const body = {
    idempotency_key: crypto.randomUUID(),
    order: {
      location_id: locationId,
      state: 'OPEN',
      line_items: [
        {
          catalog_object_id: variationId,
          quantity: '3',
        },
      ],
    },
  };

  const data = await squareRequest('POST', '/v2/orders', body);
  log('Orden OPEN creada (respuesta cruda)', data);
  return data.order;
}

async function searchOpenOrders(locationId) {
  const body = {
    location_ids: [locationId],
    query: {
      filter: {
        state_filter: { states: ['OPEN'] },
      },
    },
  };

  const data = await squareRequest('POST', '/v2/orders/search', body);
  return data.orders || [];
}

async function batchRetrieveOrder(orderId) {
  const body = { order_ids: [orderId] };
  const data = await squareRequest('POST', '/v2/orders/batch-retrieve', body);
  return (data.orders || [])[0];
}

(async function main() {
  console.log('--- Prueba Square SANDBOX: orden OPEN con line_items ---');
  console.log(`Square-Version: ${SQUARE_VERSION}`);

  const locationId = await getSandboxLocationId();
  const { variationId } = await createTestCatalogItem();
  const createdOrder = await createOpenOrder(locationId, variationId);

  console.log('\nEsperando 1s para dar tiempo a indexar antes de buscar...');
  await new Promise((r) => setTimeout(r, 1000));

  const openOrders = await searchOpenOrders(locationId);
  const found = openOrders.find((o) => o.id === createdOrder.id);

  const retrieved = await batchRetrieveOrder(createdOrder.id);

  console.log('\n\n=========== RESULTADO FINAL ===========');
  if (!found) {
    console.log(
      '❌ La orden NO aparecio en la busqueda por state_filter=OPEN. Revisar respuesta de /v2/orders/search arriba.'
    );
  } else {
    console.log('✅ La orden aparecio en la busqueda por state_filter=OPEN.');
    console.log(`   order_id: ${found.id}`);
    console.log(`   state: ${found.state}`);
    console.log('   line_items:');
    for (const li of found.line_items || []) {
      console.log(
        `     - ${li.name || '(sin nombre)'} | cantidad: ${li.quantity} | catalog_object_id: ${li.catalog_object_id}`
      );
    }
    const hasFullLineItems =
      (found.line_items || []).length > 0 &&
      found.line_items.every((li) => li.name && li.quantity);
    console.log(
      hasFullLineItems
        ? '   ✅ line_items trae producto (name) y cantidad (quantity) completos.'
        : '   ❌ line_items esta incompleto (falta name o quantity).'
    );
  }

  console.log('\n--- Confirmacion via batch-retrieve (GET directo de la orden) ---');
  log('Orden recuperada por ID', retrieved);

  console.log('\nListo. Revisa los JSON de arriba para el detalle completo.');
})().catch((err) => {
  console.error('\nFallo la prueba:', err.message);
  process.exit(1);
});
