package cl.marin376.precios;

import android.util.JsonReader;
import android.util.JsonToken;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Conexión con el servidor del local (el mismo que usa la app web). Solo lectura. */
public final class Api {
  private Api() {}

  /**
   * Baja el catálogo. Primero pide la acción liviana "catalogo_etiquetas" (solo sku, nombre,
   * precio, código). Si el servidor todavía no la tiene:
   *  - Marín 376 responde igual con el catálogo completo (se aprovecha esa misma respuesta).
   *  - Argomedo 455 responde un mensaje sin productos → se pide el catálogo completo.
   * Se lee "en streaming": del catálogo completo solo se guardan esos 4 datos y se salta todo lo
   * demás (ventas, pendientes…) sin cargarlo en memoria.
   */
  public static List<Producto> catalogo(Local l) throws Exception {
    List<Producto> p = leerCatalogo(l.url + "?action=catalogo_etiquetas");
    if (p == null) p = leerCatalogo(l.url);
    if (p == null) throw new IOException("El servidor no envió productos");
    return p;
  }

  private static HttpURLConnection abrir(String url, int lecturaMs) throws IOException {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(lecturaMs);
    return c;
  }

  private static InputStream cuerpo(HttpURLConnection c) throws IOException {
    int code = c.getResponseCode();
    if (code >= 400) throw new IOException("El servidor respondió " + code);
    // Android ya pide y descomprime gzip solo (menos datos por la red del local).
    return new BufferedInputStream(c.getInputStream(), 32768);
  }

  /** null si la respuesta no trae productos en ninguno de los dos formatos. */
  private static List<Producto> leerCatalogo(String url) throws Exception {
    HttpURLConnection c = abrir(url, 120000);
    try (JsonReader r = new JsonReader(new InputStreamReader(cuerpo(c), "UTF-8"))) {
      return parsear(r);
    } finally {
      c.disconnect();
    }
  }

  /**
   * Lee {productos:[[sku,nombre,precio,barcode,categoria,imagen],…]} o {items:{rows:{sku:{…}}}}.
   * null si no hay ninguno. En el catálogo completo la categoría viene en "cat" (Argomedo, donde
   * "prov" es el proveedor) o en "prov" (Marín, donde "prov" ya es la categoría de Loyverse).
   */
  static List<Producto> parsear(JsonReader r) throws IOException {
    List<Producto> out = null;
    r.beginObject();
    while (r.hasNext()) {
      String k = r.nextName();
      if ("productos".equals(k) && r.peek() == JsonToken.BEGIN_ARRAY) {
        out = new ArrayList<>();
        r.beginArray();
        while (r.hasNext()) {
          Producto p = fila(r);
          if (!p.sku.isEmpty() && !p.nombre.isEmpty()) out.add(p);
        }
        r.endArray();
      } else if ("items".equals(k) && r.peek() == JsonToken.BEGIN_OBJECT) {
        r.beginObject();
        while (r.hasNext()) {
          if ("rows".equals(r.nextName()) && r.peek() == JsonToken.BEGIN_OBJECT) {
            out = new ArrayList<>();
            r.beginObject();
            while (r.hasNext()) {
              String sku = r.nextName();
              Producto p = new Producto();
              p.sku = sku;
              String prov = "", cat = null;
              r.beginObject();
              while (r.hasNext()) {
                String f = r.nextName();
                if ("ref".equals(f)) p.sku = texto(r);
                else if ("nombre".equals(f)) p.nombre = texto(r);
                else if ("precio".equals(f)) p.precio = entero(r);
                else if ("barcode".equals(f)) p.barcode = texto(r);
                else if ("imagen".equals(f)) p.imagen = texto(r);
                else if ("cat".equals(f)) cat = texto(r);
                else if ("prov".equals(f)) prov = texto(r);
                else r.skipValue();
              }
              r.endObject();
              p.categoria = cat != null ? cat : prov;
              if (!p.sku.isEmpty() && !p.nombre.isEmpty()) out.add(p);
            }
            r.endObject();
          } else r.skipValue();
        }
        r.endObject();
      } else r.skipValue();
    }
    r.endObject();
    return out;
  }

  /** [sku, nombre, precio, barcode, categoria?, imagen?] — mismo formato que el archivo guardado. */
  static Producto fila(JsonReader r) throws IOException {
    r.beginArray();
    Producto p = new Producto();
    p.sku = texto(r);
    p.nombre = texto(r);
    p.precio = r.hasNext() ? entero(r) : 0;
    p.barcode = r.hasNext() ? texto(r) : "";
    p.categoria = r.hasNext() ? texto(r) : "";
    p.imagen = r.hasNext() ? texto(r) : "";
    while (r.hasNext()) r.skipValue();
    r.endArray();
    return p;
  }

  static String texto(JsonReader r) throws IOException {
    JsonToken t = r.peek();
    if (t == JsonToken.NULL) { r.nextNull(); return ""; }
    if (t == JsonToken.STRING || t == JsonToken.NUMBER) return r.nextString().trim();
    r.skipValue();
    return "";
  }

  static int entero(JsonReader r) throws IOException {
    JsonToken t = r.peek();
    if (t == JsonToken.NUMBER) return (int) Math.round(r.nextDouble());
    if (t == JsonToken.STRING) { try { return (int) Math.round(Double.parseDouble(r.nextString())); } catch (NumberFormatException e) { return 0; } }
    r.skipValue();
    return 0;
  }

  /**
   * Guarda un código de barras nuevo (mismo endpoint que la app web: editar_codigo_barras). El
   * servidor lo graba en Loyverse y rechaza códigos que ya use otro producto.
   */
  public static void guardarCodigo(Local l, String sku, String codigo) throws Exception {
    HttpURLConnection c = abrir(l.url, 60000);
    try {
      c.setRequestMethod("POST");
      c.setDoOutput(true);
      c.setRequestProperty("Content-Type", "application/json");
      JSONObject payload = new JSONObject().put("sku", sku).put("barcode", codigo).put("responsable", "App Precios");
      byte[] body = new JSONObject().put("action", "editar_codigo_barras").put("payload", payload).toString().getBytes("UTF-8");
      try (java.io.OutputStream os = c.getOutputStream()) { os.write(body); }
      InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      if (in != null) {
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
      }
      JSONObject j;
      try { j = new JSONObject(o.toString("UTF-8")); } catch (Exception e) { throw new IOException("El servidor no respondió bien"); }
      if (!j.optBoolean("ok", false)) throw new Exception(j.optString("error", "No se pudo guardar el código"));
    } finally {
      c.disconnect();
    }
  }

  /** Datos frescos de UN producto (precio, código y vencimiento del lote más próximo). */
  public static final class Ficha {
    public Integer precio;
    public String barcode, vence;   // vence = DD/MM/AAAA o null
  }

  public static Ficha ficha(Local l, String sku) throws Exception {
    HttpURLConnection c = abrir(l.url + "?action=ficha_producto&sku=" + URLEncoder.encode(sku, "UTF-8"), 20000);
    String txt;
    try {
      InputStream in = cuerpo(c);
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) > 0) o.write(b, 0, n);
      in.close();
      txt = o.toString("UTF-8");
    } finally {
      c.disconnect();
    }
    JSONObject j = new JSONObject(txt);
    if (!j.optBoolean("ok", false)) return null;
    JSONObject f = j.optJSONObject("ficha");
    if (f == null) return null;
    Ficha out = new Ficha();
    JSONObject p = f.optJSONObject("producto");
    if (p != null) {
      if (p.has("precio") && !p.isNull("precio")) out.precio = (int) Math.round(p.optDouble("precio", 0));
      out.barcode = p.isNull("barcode") ? "" : p.optString("barcode", "");
    }
    JSONArray lotes = f.optJSONArray("lotes");
    if (lotes != null && lotes.length() > 0) out.vence = fechaDDMMAAAA(lotes.optJSONObject(0).optString("fechaVencimiento", ""));
    return out;
  }

  /** Acepta DD/MM/AAAA, D/M/AAAA o AAAA-MM-DD. Devuelve DD/MM/AAAA o null. */
  static String fechaDDMMAAAA(String s) {
    s = s == null ? "" : s.trim();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{2,4})$").matcher(s);
    if (m.find()) {
      String a = m.group(3);
      if (a.length() == 2) a = "20" + a;
      return String.format(java.util.Locale.ROOT, "%02d/%02d/%s", Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), a);
    }
    m = java.util.regex.Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})").matcher(s);
    if (m.find()) return m.group(3) + "/" + m.group(2) + "/" + m.group(1);
    return null;
  }
}
