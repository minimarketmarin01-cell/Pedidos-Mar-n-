import http.server, urllib.parse, sys, os
M = sys.argv[2]   # carpeta con las respuestas reales del Worker (generadas con migracion/probar_etiquetas.mjs)
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def do_GET(self):
        q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        a = (q.get("action") or [""])[0]; d = (q.get("desde") or [""])[0]
        assert (q.get("app") or [""])[0] == "etiquetas", "falta app=etiquetas: " + self.path
        modo = os.environ.get("MODO", "nuevo")
        if modo == "viejo": f = "viejo"          # servidor sin las acciones nuevas: devuelve el panel completo
        elif a == "catalogo_etiquetas": f = "igual" if d == "7" else ("delta" if d else "completo")
        elif a == "categorias_etiquetas": f = "categorias"
        elif a == "buscar_etiquetas": f = "buscar"
        elif a in ("ficha_etiqueta", "ficha_producto"): f = "ficha" if modo == "nuevo" else "viejo"
        else: f = "viejo"
        b = open(os.path.join(M, f + ".json"), "rb").read()
        self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
http.server.HTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
