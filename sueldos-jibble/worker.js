var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// worker.js
var JIBBLE_TOKEN_URL = "https://identity.prod.jibble.io/connect/token";
var JIBBLE_PEOPLE_URL = "https://workspace.prod.jibble.io/v1/People";
var JIBBLE_TIME_ENTRIES_URL = "https://time-tracking.prod.jibble.io/v1/TimeEntries";
var CUENTAS_SOPORTADAS = ["1", "2", "3", "4"];
var CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, PUT, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type"
};
var cachedTokens = /* @__PURE__ */ new Map();
function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS }
  });
}
__name(json, "json");
var NOMBRES_POR_DEFECTO = { "1": "Mar\xEDn", "2": "Argomedo" };
function getCuentaConfig(env, cuenta) {
  const suffix = !cuenta || cuenta === "1" ? "" : `_${cuenta}`;
  return {
    id: cuenta || "1",
    clientId: env[`JIBBLE_CLIENT_ID${suffix}`],
    clientSecret: env[`JIBBLE_CLIENT_SECRET${suffix}`],
    employeeNames: env[`JIBBLE_EMPLOYEE_NAMES${suffix}`] || "",
    nombre: env[`JIBBLE_CUENTA_NOMBRE${suffix}`] || NOMBRES_POR_DEFECTO[cuenta || "1"] || `Cuenta ${cuenta}`
  };
}
__name(getCuentaConfig, "getCuentaConfig");
function listCuentasConfiguradas(env) {
  return CUENTAS_SOPORTADAS.map((id) => getCuentaConfig(env, id)).filter(
    (c) => c.clientId && c.clientSecret
  );
}
__name(listCuentasConfiguradas, "listCuentasConfiguradas");
async function getJibbleToken(cuentaConfig) {
  const { id: cuenta, clientId, clientSecret } = cuentaConfig;
  const cached = cachedTokens.get(cuenta);
  const now = Date.now();
  if (cached && now < cached.expiresAt) return cached.token;
  const body = new URLSearchParams({
    grant_type: "client_credentials",
    client_id: clientId,
    client_secret: clientSecret
  });
  const res = await fetch(JIBBLE_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    const err = new Error(
      `AUTH_FAILED: Jibble rechaz\xF3 las credenciales de la cuenta "${cuenta}" (HTTP ${res.status}). ${detail}`.slice(0, 500)
    );
    err.status = res.status === 401 || res.status === 403 ? res.status : 502;
    throw err;
  }
  const data = await res.json();
  const token = data.access_token;
  cachedTokens.set(cuenta, { token, expiresAt: now + Math.max((data.expires_in || 3600) - 60, 30) * 1e3 });
  return token;
}
__name(getJibbleToken, "getJibbleToken");
async function jibbleGet(url, token) {
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    const err = new Error(`Jibble respondi\xF3 HTTP ${res.status} en ${url}. ${detail}`.slice(0, 500));
    err.status = res.status === 401 || res.status === 403 ? res.status : 502;
    throw err;
  }
  return res.json();
}
__name(jibbleGet, "jibbleGet");
function extractArray(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.value)) return payload.value;
  if (Array.isArray(payload?.data)) return payload.data;
  if (Array.isArray(payload?.entries)) return payload.entries;
  return [];
}
__name(extractArray, "extractArray");
function odataUrl(base, params) {
  const parts = Object.entries(params).map(
    ([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`
  );
  return `${base}?${parts.join("&")}`;
}
__name(odataUrl, "odataUrl");
async function fetchAllPages(firstUrl, token, maxPages = 20) {
  let url = firstUrl;
  let all = [];
  for (let i = 0; i < maxPages && url; i++) {
    const payload = await jibbleGet(url, token);
    all = all.concat(extractArray(payload));
    url = payload?.["@odata.nextLink"] || null;
  }
  return all;
}
__name(fetchAllPages, "fetchAllPages");
async function getPeopleMap(token) {
  const url = odataUrl(JIBBLE_PEOPLE_URL, { $select: "id,fullName" });
  const people = await fetchAllPages(url, token);
  const map = /* @__PURE__ */ new Map();
  for (const p of people) {
    const id = p.id ?? p.personId ?? p.memberId;
    const name = p.fullName ?? p.name ?? [p.firstName, p.lastName].filter(Boolean).join(" ");
    if (id && name) map.set(id, name);
  }
  return map;
}
__name(getPeopleMap, "getPeopleMap");
async function fetchTimeEntries(token, from, to) {
  const url = odataUrl(JIBBLE_TIME_ENTRIES_URL, {
    $filter: `belongsToDate ge ${from} and belongsToDate le ${to}`,
    $orderby: "personId,time",
    $top: "500"
  });
  return fetchAllPages(url, token);
}
__name(fetchTimeEntries, "fetchTimeEntries");
function chileLocalDate(isoTime) {
  return CHILE_DATE_FMT.format(new Date(isoTime));
}
__name(chileLocalDate, "chileLocalDate");
var CHILE_DATE_FMT = new Intl.DateTimeFormat("en-CA", {
  timeZone: "America/Santiago",
  year: "numeric",
  month: "2-digit",
  day: "2-digit"
});
function findDayBoundary(fromTime, toTime) {
  const startDate = chileLocalDate(fromTime);
  let lo = new Date(fromTime).getTime();
  let hi = new Date(toTime).getTime();
  while (hi - lo > 1e3) {
    const mid = Math.floor((lo + hi) / 2);
    if (chileLocalDate(mid) === startDate) lo = mid;
    else hi = mid;
  }
  return hi;
}
__name(findDayBoundary, "findDayBoundary");
function splitTramoPorDia(fromTime, toTime) {
  const segments = [];
  let cursor = fromTime;
  let cursorDate = chileLocalDate(cursor);
  const endDate = chileLocalDate(toTime);
  let guard = 0;
  while (cursorDate !== endDate && guard < 10) {
    const boundary = findDayBoundary(cursor, toTime);
    const hrs = (boundary - new Date(cursor).getTime()) / 36e5;
    if (hrs > 0) segments.push({ dia: cursorDate, horas: hrs });
    cursor = new Date(boundary).toISOString();
    cursorDate = chileLocalDate(cursor);
    guard++;
  }
  const hrsLast = (new Date(toTime).getTime() - new Date(cursor).getTime()) / 36e5;
  if (hrsLast > 0) segments.push({ dia: cursorDate, horas: hrsLast });
  return segments;
}
__name(splitTramoPorDia, "splitTramoPorDia");
function computeWorkedHours(entries, rangeToISOEnd) {
  const byPerson = /* @__PURE__ */ new Map();
  for (const e of entries) {
    if (!e.personId || !e.time || !e.type) continue;
    if (e.status === "Archived") continue;
    if (!byPerson.has(e.personId)) byPerson.set(e.personId, []);
    byPerson.get(e.personId).push(e);
  }
  const totals = /* @__PURE__ */ new Map();
  const now = Date.now();
  const rangeEnd = Math.min(now, new Date(rangeToISOEnd).getTime());
  for (const [personId, list] of byPerson) {
    list.sort((a, b) => new Date(a.time) - new Date(b.time));
    let pendingIn = null;
    let pendingBreak = null;
    let total = 0;
    const porDia = /* @__PURE__ */ new Map();
    const eventosPorDia = /* @__PURE__ */ new Map();
    const addTramo = /* @__PURE__ */ __name((fromEntry, toTime) => {
      total += (new Date(toTime) - new Date(fromEntry.time)) / 36e5;
      for (const seg of splitTramoPorDia(fromEntry.time, toTime)) {
        porDia.set(seg.dia, (porDia.get(seg.dia) || 0) + seg.horas);
      }
    }, "addTramo");
    const setDuracion = /* @__PURE__ */ __name((eventoRef, fromTime, toTime) => {
      let effectiveEnd = new Date(toTime).getTime();
      if (chileLocalDate(fromTime) !== chileLocalDate(effectiveEnd)) {
        effectiveEnd = findDayBoundary(fromTime, toTime);
      }
      const hrs = (effectiveEnd - new Date(fromTime).getTime()) / 36e5;
      eventoRef.duracionHoras = hrs > 0 ? Math.round(hrs * 100) / 100 : 0;
    }, "setDuracion");
    for (const e of list) {
      const dia = e.belongsToDate || e.time.slice(0, 10);
      if (!eventosPorDia.has(dia)) eventosPorDia.set(dia, []);
      const eventoRef = { tipo: e.type, hora: e.time, duracionHoras: null };
      eventosPorDia.get(dia).push(eventoRef);
      if (e.type === "In") {
        if (pendingIn) {
          addTramo(pendingIn, e.time);
          setDuracion(pendingIn.eventoRef, pendingIn.time, e.time);
        }
        if (pendingBreak) {
          setDuracion(pendingBreak.eventoRef, pendingBreak.time, e.time);
          pendingBreak = null;
        }
        pendingIn = { time: e.time, belongsToDate: e.belongsToDate, eventoRef };
      } else if (e.type === "Out" && pendingIn) {
        addTramo(pendingIn, e.time);
        setDuracion(pendingIn.eventoRef, pendingIn.time, e.time);
        pendingIn = null;
      } else if (e.type === "StartBreak" && pendingIn) {
        addTramo(pendingIn, e.time);
        setDuracion(pendingIn.eventoRef, pendingIn.time, e.time);
        pendingIn = null;
        pendingBreak = { time: e.time, belongsToDate: e.belongsToDate, eventoRef };
      }
    }
    if (pendingIn && rangeEnd > new Date(pendingIn.time).getTime()) {
      addTramo(pendingIn, rangeEnd);
      setDuracion(pendingIn.eventoRef, pendingIn.time, rangeEnd);
    }
    if (pendingBreak && rangeEnd > new Date(pendingBreak.time).getTime()) {
      setDuracion(pendingBreak.eventoRef, pendingBreak.time, rangeEnd);
    }
    totals.set(personId, { total, porDia, eventosPorDia });
  }
  return totals;
}
__name(computeWorkedHours, "computeWorkedHours");
function currentWeekRangeChile() {
  const now = /* @__PURE__ */ new Date();
  const chileNow = new Date(now.toLocaleString("en-US", { timeZone: "America/Santiago" }));
  const day = chileNow.getDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(chileNow);
  monday.setDate(chileNow.getDate() + diffToMonday);
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  const fmt = /* @__PURE__ */ __name((d) => d.toISOString().slice(0, 10), "fmt");
  return { from: fmt(monday), to: fmt(sunday) };
}
__name(currentWeekRangeChile, "currentWeekRangeChile");
function getCuentaConfigOrThrow(env, cuentaId) {
  const cuenta = getCuentaConfig(env, cuentaId);
  if (!cuenta.clientId || !cuenta.clientSecret) {
    const err = new Error(
      `La cuenta "${cuentaId}" no tiene credenciales configuradas (faltan JIBBLE_CLIENT_ID${cuentaId === "1" ? "" : "_" + cuentaId} / JIBBLE_CLIENT_SECRET${cuentaId === "1" ? "" : "_" + cuentaId} como Secret).`
    );
    err.status = 400;
    throw err;
  }
  return cuenta;
}
__name(getCuentaConfigOrThrow, "getCuentaConfigOrThrow");
async function handleHorasSemana(url, env) {
  const params = url.searchParams;
  const cuenta = getCuentaConfigOrThrow(env, params.get("cuenta") || "1");
  const { from: defaultFrom, to: defaultTo } = currentWeekRangeChile();
  const from = params.get("from") || defaultFrom;
  const to = params.get("to") || defaultTo;
  const token = await getJibbleToken(cuenta);
  const [peopleMap, entries] = await Promise.all([
    getPeopleMap(token),
    fetchTimeEntries(token, from, to)
  ]);
  const totals = computeWorkedHours(entries, `${to}T23:59:59Z`);
  const allowedNames = cuenta.employeeNames.split(",").map((n) => n.trim()).filter(Boolean);
  const result = [];
  for (const [personId, { total, porDia, eventosPorDia }] of totals) {
    const nombre = peopleMap.get(personId) || personId;
    if (allowedNames.length && !allowedNames.includes(nombre)) continue;
    result.push({
      nombre,
      horasTrabajadas: Math.round(total * 100) / 100,
      porDia: roundPorDia(porDia),
      marcajesPorDia: eventosPorDiaToObj(eventosPorDia)
    });
  }
  return { cuenta: cuenta.id, nombreCuenta: cuenta.nombre, from, to, empleados: result };
}
__name(handleHorasSemana, "handleHorasSemana");
function roundPorDia(porDiaMap) {
  const out = {};
  for (const [fecha, horas] of porDiaMap) out[fecha] = Math.round(horas * 100) / 100;
  return out;
}
__name(roundPorDia, "roundPorDia");
function eventosPorDiaToObj(eventosPorDiaMap) {
  const out = {};
  for (const [fecha, eventos] of eventosPorDiaMap) out[fecha] = eventos;
  return out;
}
__name(eventosPorDiaToObj, "eventosPorDiaToObj");
async function handleGetEstado(url, env) {
  const cuenta = url.searchParams.get("cuenta") || "1";
  const [estadoRaw, feriadosRaw] = await Promise.all([
    env.ESTADO.get(`estado:${cuenta}`),
    env.ESTADO.get("feriadosExtra")
  ]);
  const estado = estadoRaw ? JSON.parse(estadoRaw) : {};
  return {
    sueldos: estado.sueldos || {},
    modos: estado.modos || {},
    pagadas: estado.pagadas || {},
    efectivo: estado.efectivo || {},
    feriadosExtra: feriadosRaw ? JSON.parse(feriadosRaw) : {}
  };
}
__name(handleGetEstado, "handleGetEstado");
async function handlePutEstado(request, url, env) {
  const cuenta = url.searchParams.get("cuenta") || "1";
  const body = await request.json();
  if (body.feriadosExtra !== void 0) {
    await env.ESTADO.put("feriadosExtra", JSON.stringify(body.feriadosExtra));
  }
  if (body.sueldos !== void 0 || body.modos !== void 0 || body.pagadas !== void 0 || body.efectivo !== void 0) {
    const current = await env.ESTADO.get(`estado:${cuenta}`);
    const estado = current ? JSON.parse(current) : {};
    if (body.sueldos !== void 0) estado.sueldos = body.sueldos;
    if (body.modos !== void 0) estado.modos = body.modos;
    if (body.pagadas !== void 0) estado.pagadas = body.pagadas;
    if (body.efectivo !== void 0) estado.efectivo = body.efectivo;
    await env.ESTADO.put(`estado:${cuenta}`, JSON.stringify(estado));
  }
  return { ok: true };
}
__name(handlePutEstado, "handlePutEstado");
var worker_default = {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }
    try {
      if (url.pathname === "/horas-semana" && request.method === "GET") {
        const data = await handleHorasSemana(url, env);
        return json(data);
      }
      if (url.pathname === "/debug" && request.method === "GET") {
        const params = url.searchParams;
        const cuenta = getCuentaConfigOrThrow(env, params.get("cuenta") || "1");
        const { from: defaultFrom, to: defaultTo } = currentWeekRangeChile();
        const from = params.get("from") || defaultFrom;
        const to = params.get("to") || defaultTo;
        const token = await getJibbleToken(cuenta);
        const [peopleMap, entries] = await Promise.all([
          getPeopleMap(token),
          fetchTimeEntries(token, from, to)
        ]);
        const totals = computeWorkedHours(entries, `${to}T23:59:59Z`);
        const empleados = [...totals].map(([personId, { total, porDia }]) => ({
          personId,
          nombre: peopleMap.get(personId) || personId,
          horasTrabajadas: Math.round(total * 100) / 100,
          porDia: roundPorDia(porDia)
        }));
        return json({ cuenta: cuenta.id, from, to, empleados, cantidadDeMarcajes: entries.length, marcajesCrudos: entries.slice(0, 20) });
      }
      if (url.pathname === "/cuentas" && request.method === "GET") {
        const cuentas = listCuentasConfiguradas(env).map((c) => ({ id: c.id, nombre: c.nombre }));
        return json({ cuentas });
      }
      if (url.pathname === "/estado" && request.method === "GET") {
        return json(await handleGetEstado(url, env));
      }
      if (url.pathname === "/estado" && request.method === "PUT") {
        return json(await handlePutEstado(request, url, env));
      }
      return json({ error: "Ruta no encontrada. Usa GET /horas-semana, GET /debug, GET /cuentas o GET|PUT /estado." }, 404);
    } catch (err) {
      const status = err.status || 500;
      return json({ error: err.message || "Error interno" }, status);
    }
  }
};
export {
  worker_default as default
};
//# sourceMappingURL=worker.js.map
