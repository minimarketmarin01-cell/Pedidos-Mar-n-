// Horas de Jibble en Supabase (reemplaza al Worker marin376-jibble-horas de Cloudflare).
// sueldos-jibble/worker.js es el mismo código que corría en Cloudflare; aquí solo cambia de dónde salen:
//  - las claves de Jibble: secretos de la función (Supabase → Edge Functions → Secrets) o Vault "jibble__<NOMBRE>";
//  - el KV "ESTADO": la tabla jibble.estado (clave, valor) de Postgres, con la misma interfaz get/put.
import worker from "../../../sueldos-jibble/worker.js";
import postgres from "npm:postgres@3.4.5";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });

const ESTADO = {
  async get(clave: string) {
    const f = await sql`select valor from jibble.estado where clave = ${clave}`;
    return f.length ? f[0].valor : null;
  },
  async put(clave: string, valor: string) {
    await sql`insert into jibble.estado (clave, valor) values (${clave}, ${String(valor)})
              on conflict (clave) do update set valor = excluded.valor, actualizado = now()`;
  }
};

let envListo: Promise<Record<string, unknown>> | null = null;
function obtenerEnv() {
  if (!envListo) envListo = (async () => {
    const env: Record<string, unknown> = { ESTADO };
    const filas = await sql`select name, decrypted_secret from vault.decrypted_secrets where name like 'jibble\_\_%'`;
    for (const f of filas) env[String(f.name).slice(8)] = f.decrypted_secret;
    for (const n of ["JIBBLE_CLIENT_ID", "JIBBLE_CLIENT_SECRET", "JIBBLE_CLIENT_ID_2", "JIBBLE_CLIENT_SECRET_2",
                     "JIBBLE_EMPLOYEE_NAMES", "JIBBLE_EMPLOYEE_NAMES_2", "JIBBLE_CUENTA_NOMBRE", "JIBBLE_CUENTA_NOMBRE_2"]) {
      const v = Deno.env.get(n); if (v) env[n] = v;
    }
    return env;
  })().catch(e => { envListo = null; throw e; });
  return envListo;
}

Deno.serve(async (req) => {
  const u = new URL(req.url);
  u.pathname = u.pathname.replace(/^(\/functions\/v1)?\/jibble-api(?=\/|$)/, "") || "/";
  const env = await obtenerEnv();
  return worker.fetch(new Request(u.toString(), req), env, { waitUntil() {}, passThroughOnException() {} });
});
