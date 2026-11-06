// Prueba la caché "del día" de vencimientos y ABC de la web: uso `node pruebas-web/probar_cache_dia.mjs index.html`.
// Una vez por equipo y por día; se vuelve a pedir al día siguiente o si se cargó antes del cierre nocturno (06:30).
import assert from "assert";
import fs from "fs";
const fuente = fs.readFileSync(process.argv[2] || "index.html", "utf8");
const ini = fuente.indexOf("function _cacheDelDiaVigente(ts){");
const fin = fuente.indexOf("let _in2VencCache=null;");
assert(ini > 0 && fin > ini, "no se encontró el bloque de la caché del día");
const bloque = fuente.slice(ini, fin);
let n = 0; const ok = m => console.log("ok", ++n, m);

// reloj y almacenamiento simulados
const armar = (ahora) => {
  const memoria = new Map();
  const localStorage = { getItem: k => memoria.has(k) ? memoria.get(k) : null, setItem: (k, v) => memoria.set(k, v) };
  const RealDate = Date;
  const D = class extends RealDate { constructor(...a) { super(...(a.length ? a : [ahora.t])); } static now() { return ahora.t; } };
  const api = new Function("Date", "localStorage", "SYNC_URL", bloque + "; return { _cacheDelDiaVigente, vencDiaLeer, vencDiaGuardar };")(D, localStorage, "https://x");
  return { api, memoria };
};
const L = s => new Date(s).getTime();
const reloj = { t: L("2026-10-24T09:00:00") };
const { api, memoria } = armar(reloj);

assert.strictEqual(api.vencDiaLeer(), null);
ok("sin datos guardados, hay que pedirlos");
api.vencDiaGuardar([{ id: 1 }, { id: 2 }]);
reloj.t = L("2026-10-24T15:30:00");
assert.strictEqual(api.vencDiaLeer().lotes.length, 2);
ok("cargados a las 09:00 siguen valiendo a las 15:30 (no se vuelven a pedir)");
// otra sesión del mismo equipo (recarga de la web): lee lo guardado en el teléfono
const otra = armar(reloj);
memoria.forEach((v, k) => otra.memoria.set(k, v));
assert.strictEqual(otra.api.vencDiaLeer().lotes.length, 2);
ok("tras recargar la web se leen del teléfono, sin pedirlos de nuevo");
reloj.t = L("2026-10-25T08:00:00");
assert.strictEqual(api.vencDiaLeer(), null);
ok("al día siguiente se vuelven a pedir");
reloj.t = L("2026-10-25T05:00:00"); api.vencDiaGuardar([{ id: 3 }]);
reloj.t = L("2026-10-25T09:00:00");
assert.strictEqual(api.vencDiaLeer(), null);
ok("lo cargado antes del cierre de las 06:30 no vale después de él");
reloj.t = L("2026-10-25T09:00:00"); api.vencDiaGuardar([{ id: 4 }]);
reloj.t = L("2026-10-25T23:59:00");
assert.strictEqual(api.vencDiaLeer().lotes[0].id, 4);
ok("lo cargado a las 09:00 vale hasta la medianoche");
assert(fuente.includes("vencDiaGuardar(j.lotes)"), "editar un lote debe refrescar la caché del día");
assert(/function vcRefreshCurrentView[\s\S]*?vencDiaGuardar\(j\.lotes\)/.test(fuente), "vcRefreshCurrentView debe guardar los lotes frescos");
assert(/async function vcLoad[\s\S]*?vencDiaGuardar\(j\.lotes\)/.test(fuente), "vcLoad debe guardar los lotes frescos");
ok("editar un lote o abrir Vencimientos deja la caché del día al día");
assert(!/Date\.now\(\)-_abcTs<60\*60\*1000/.test(fuente) && /abc_dia:/.test(fuente));
ok("el ABC se guarda por día (ya no por hora)");
console.log("\n" + n + " pruebas OK");
