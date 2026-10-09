package cl.marin376.precios;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Catálogo de UN local guardado en un archivo del equipo (abre al instante, sin internet). */
public final class Catalogo {
  public final Local local;
  public List<Producto> productos = new ArrayList<>();
  public long hora = 0;   // cuándo se bajó (ms)
  private Map<String, Producto> porCodigo = new HashMap<>();

  public Catalogo(Local l) { local = l; }

  private static File archivo(Context c, Local l) { return new File(c.getFilesDir(), "catalogo_" + l.id + ".json"); }

  public static String norm(String s) {
    String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return n.toLowerCase(Locale.ROOT).trim();
  }

  public void poner(List<Producto> lista, long cuando) {
    Map<String, Producto> m = new HashMap<>();
    for (Producto p : lista) {
      p.nombreNorm = norm(p.nombre);
      m.put(p.sku, p);
      if (p.barcode != null && !p.barcode.isEmpty()) m.put(p.barcode, p);
    }
    for (Producto p : lista) m.put(p.sku, p);   // la SKU manda si choca con un código
    Collections.sort(lista, (a, b) -> a.nombreNorm.compareTo(b.nombreNorm));
    productos = lista;
    porCodigo = m;
    hora = cuando;
  }

  public Producto exacto(String codigo) { return codigo == null ? null : porCodigo.get(codigo.trim()); }

  /** Busca por nombre (todas las palabras, sin importar acentos ni mayúsculas), SKU o código. */
  public List<Producto> buscar(String q, int max) {
    List<Producto> out = new ArrayList<>();
    String n = norm(q);
    if (n.isEmpty()) return out;
    Producto ex = exacto(q.trim());
    if (ex != null) out.add(ex);
    String[] pal = n.split("\\s+");
    List<Producto> empiezan = new ArrayList<>(), resto = new ArrayList<>();
    for (Producto p : productos) {
      if (p == ex) continue;
      boolean ok = true;
      for (String w : pal) {
        if (!p.nombreNorm.contains(w) && !p.sku.toLowerCase(Locale.ROOT).contains(w) && !p.barcode.contains(w)) { ok = false; break; }
      }
      if (!ok) continue;
      if (p.nombreNorm.startsWith(pal[0]) || p.barcode.startsWith(n) || p.sku.startsWith(n)) empiezan.add(p); else resto.add(p);
      if (empiezan.size() >= max) break;
    }
    for (Producto p : empiezan) if (out.size() < max) out.add(p);
    for (Producto p : resto) if (out.size() < max) out.add(p);
    return out;
  }

  public static Catalogo cargar(Context c, Local l) {
    Catalogo cat = new Catalogo(l);
    File f = archivo(c, l);
    if (!f.exists()) return cat;
    List<Producto> lista = new ArrayList<>();
    long hora = 0;
    try (JsonReader r = new JsonReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
      r.beginObject();
      while (r.hasNext()) {
        String k = r.nextName();
        if ("hora".equals(k)) hora = r.nextLong();
        else if ("p".equals(k)) {
          r.beginArray();
          while (r.hasNext()) {
            r.beginArray();
            Producto p = new Producto();
            p.sku = Api.texto(r);
            p.nombre = Api.texto(r);
            p.precio = Api.entero(r);
            p.barcode = Api.texto(r);
            while (r.hasNext()) r.skipValue();
            r.endArray();
            lista.add(p);
          }
          r.endArray();
        } else r.skipValue();
      }
      r.endObject();
    } catch (Exception e) {
      return cat;   // archivo dañado: se vuelve a bajar
    }
    cat.poner(lista, hora);
    return cat;
  }

  public void guardar(Context c) {
    File f = archivo(c, local), tmp = new File(f.getPath() + ".tmp");
    try (JsonWriter w = new JsonWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"))) {
      w.beginObject().name("hora").value(hora).name("p").beginArray();
      for (Producto p : productos) w.beginArray().value(p.sku).value(p.nombre).value(p.precio).value(p.barcode).endArray();
      w.endArray().endObject();
    } catch (Exception e) {
      tmp.delete();
      return;
    }
    if (!tmp.renameTo(f)) tmp.delete();
  }
}
