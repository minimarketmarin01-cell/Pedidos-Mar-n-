package cl.marin376.etiquetas;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONObject;

/** Conexión con el servidor de Marín 376 (el mismo que usa la app web). */
public final class Api {
  public static final String URL_BASE = "https://marin376-api.minimarketmarin01.workers.dev";
  private Api() {}

  private static String leer(HttpURLConnection c) throws Exception {
    InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    byte[] b = new byte[16384];
    int n;
    while ((n = in.read(b)) > 0) o.write(b, 0, n);
    in.close();
    return o.toString("UTF-8");
  }

  /** Baja el catálogo (misma carga que hace la app web al abrir). */
  public static List<Producto> productos() throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(URL_BASE).openConnection();
    c.setConnectTimeout(20000);
    c.setReadTimeout(90000);
    c.setRequestMethod("GET");
    String txt = leer(c);
    JSONObject j = new JSONObject(txt);
    if (!j.optBoolean("ok", false)) throw new Exception("El servidor no respondió bien");
    JSONObject rows = j.getJSONObject("items").getJSONObject("rows");
    List<Producto> out = new ArrayList<>();
    Iterator<String> it = rows.keys();
    while (it.hasNext()) {
      JSONObject r = rows.getJSONObject(it.next());
      Producto p = new Producto();
      p.sku = r.optString("ref", "");
      p.nombre = r.optString("nombre", "");
      p.barcode = r.optString("barcode", "");
      p.precio = (int) Math.round(r.optDouble("precio", 0));
      if (!p.sku.isEmpty() && !p.nombre.isEmpty()) out.add(p);
    }
    return out;
  }

  /** Código de barras actual de un producto en el servidor (para no imprimir uno viejo). */
  public static String codigoActual(String sku) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(URL_BASE + "?action=ficha_producto&sku=" + java.net.URLEncoder.encode(sku, "UTF-8")).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(30000);
    JSONObject j = new JSONObject(leer(c));
    if (!j.optBoolean("ok", false)) return null;
    return j.getJSONObject("ficha").getJSONObject("producto").optString("barcode", "");
  }

  /** Guarda un código de barras nuevo en el producto (mismo endpoint que la app web). */
  public static void guardarCodigo(String sku, String codigo) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(URL_BASE).openConnection();
    c.setConnectTimeout(20000);
    c.setReadTimeout(60000);
    c.setRequestMethod("POST");
    c.setDoOutput(true);
    JSONObject payload = new JSONObject().put("sku", sku).put("barcode", codigo).put("responsable", "");
    JSONObject body = new JSONObject().put("action", "editar_codigo_barras").put("payload", payload);
    OutputStream os = c.getOutputStream();
    os.write(body.toString().getBytes("UTF-8"));
    os.close();
    JSONObject j = new JSONObject(leer(c));
    if (!j.optBoolean("ok", false)) throw new Exception(j.optString("error", "No se pudo guardar el código"));
  }
}
