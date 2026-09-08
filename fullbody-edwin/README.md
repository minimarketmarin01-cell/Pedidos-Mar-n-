# Full Body — Edwin

App de registro de entrenamientos para el programa "Full Body — Edwin" (hipertrofia, 3 días/semana). Frontend estático (PWA) + API en Cloudflare Workers + base de datos Cloudflare D1.

## Estructura

```
fullbody-edwin/
├── index.html        # App (PWA, sin dependencias, funciona en el celular)
├── manifest.json
├── sw.js
├── icon-192.png / icon-512.png
└── worker/            # Backend (Cloudflare Worker + D1)
    ├── wrangler.toml
    ├── src/index.js
    └── migrations/0001_init.sql
```

## 1. Frontend (GitHub Pages)

Ya está en este repo. Para publicarlo:

1. En GitHub: **Settings → Pages**.
2. Source: `Deploy from a branch` → branch `main` (o la que uses en producción) → carpeta `/ (root)`.
3. La app queda disponible en `https://<usuario>.github.io/<repo>/fullbody-edwin/`.

Si el repo ya usa GitHub Pages para otra app en la raíz, esta carpeta no la afecta: queda en su propia ruta.

## 2. Backend (Cloudflare Worker + D1)

La base de datos D1 **ya está creada** (`fullbody-edwin`, database_id en `worker/wrangler.toml`). Falta desplegar el Worker desde tu cuenta de Cloudflare:

```bash
cd fullbody-edwin/worker
npm install -g wrangler   # si no lo tienes
wrangler login
wrangler deploy
```

Wrangler te entrega una URL tipo `https://fullbody-edwin-api.<tu-subdominio>.workers.dev`.

Si prefieres usar tu propia base D1 en vez de la ya creada, créala y actualiza `database_id` en `wrangler.toml`, luego aplica el esquema:

```bash
wrangler d1 execute fullbody-edwin --remote --file=migrations/0001_init.sql
```

## 3. Conectar la app con la API

1. Abre la app en el celular.
2. Ve a **Ajustes**.
3. Pega la URL del Worker (sin `/` final) y toca **Guardar**.
4. Toca **Probar conexión** para confirmar.

La URL queda guardada en el celular (localStorage), no hay que repetirlo cada vez.

## Uso

- **Hoy**: elige sesión A/B/C, registra peso y reps por serie. Cada serie se guarda al toque de "Ok". Si ya entrenaste ese día, los datos se recargan solos.
- **Historial**: progresión de carga por ejercicio (barras + tabla).
- **Volumen**: series semanales objetivo por grupo muscular (según el programa, referencia fija).
- **Ajustes**: URL de la API.

## Datos del programa (fuente: Programa_Full_Body_Edwin.xlsx)

- Objetivo: hipertrofia/estética. RIR 2 en básicos, 1-1.5 en aislados.
- Progresión: doble progresión (sube carga al completar el techo de reps en todas las series).
- Deload: cada 5-6 semanas.
