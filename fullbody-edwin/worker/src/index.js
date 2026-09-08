const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET,POST,DELETE,OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const { pathname, searchParams } = url;

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    try {
      if (pathname === "/api/health") {
        return json({ ok: true });
      }

      if (pathname === "/api/logs" && request.method === "GET") {
        const date = searchParams.get("date");
        const exercise = searchParams.get("exercise");
        if (date) {
          const { results } = await env.DB.prepare(
            "SELECT * FROM logs WHERE date = ? ORDER BY exercise, set_number"
          )
            .bind(date)
            .all();
          return json(results);
        }
        if (exercise) {
          const limit = Number(searchParams.get("limit") || 200);
          const { results } = await env.DB.prepare(
            "SELECT * FROM logs WHERE exercise = ? ORDER BY date DESC, set_number LIMIT ?"
          )
            .bind(exercise, limit)
            .all();
          return json(results);
        }
        return json({ error: "Falta date o exercise" }, 400);
      }

      if (pathname === "/api/last" && request.method === "GET") {
        const exercise = searchParams.get("exercise");
        if (!exercise) return json({ error: "Falta exercise" }, 400);
        const { results } = await env.DB.prepare(
          `SELECT set_number, weight, reps, rir, date FROM logs
           WHERE exercise = ? AND date = (
             SELECT MAX(date) FROM logs WHERE exercise = ?
           )
           ORDER BY set_number`
        )
          .bind(exercise, exercise)
          .all();
        return json(results);
      }

      if (pathname === "/api/logs" && request.method === "POST") {
        const body = await request.json();
        const entries = Array.isArray(body) ? body : [body];
        const stmt = env.DB.prepare(
          `INSERT INTO logs (date, session, exercise, set_number, weight, reps, rir)
           VALUES (?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(date, exercise, set_number) DO UPDATE SET
             weight = excluded.weight,
             reps = excluded.reps,
             rir = excluded.rir`
        );
        const batch = entries.map((e) =>
          stmt.bind(
            e.date,
            e.session,
            e.exercise,
            e.set_number,
            e.weight ?? null,
            e.reps ?? null,
            e.rir ?? null
          )
        );
        await env.DB.batch(batch);
        return json({ ok: true, count: batch.length });
      }

      if (pathname.startsWith("/api/logs/") && request.method === "DELETE") {
        const id = pathname.split("/").pop();
        await env.DB.prepare("DELETE FROM logs WHERE id = ?").bind(id).run();
        return json({ ok: true });
      }

      return json({ error: "Not found" }, 404);
    } catch (err) {
      return json({ error: String(err) }, 500);
    }
  },
};
