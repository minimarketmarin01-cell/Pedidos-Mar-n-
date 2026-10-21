import cl.marin376.precios.*;
import java.util.*;
public class PruebaApi {
  static int n = 0;
  static void ok(String m) { System.out.println("ok " + (++n) + " " + m); }
  public static void main(String[] a) throws Exception {
    java.lang.reflect.Constructor<Local> k = Local.class.getDeclaredConstructor(String.class, String.class, String.class);
    k.setAccessible(true);
    Local l = k.newInstance("t", "Prueba", "http://127.0.0.1:" + a[0]);
    boolean viejo = a.length > 1 && a[1].equals("viejo");
    if (viejo) {
      if (Api.catalogoLiviano(l, null, null) != null) throw new AssertionError("servidor viejo: debía dar null");
      if (Api.categorias(l) != null) throw new AssertionError("categorias: null");
      if (Api.buscar(l, "x") != null) throw new AssertionError("buscar: null");
      ok("servidor viejo: las 3 acciones devuelven null (se corta la lectura sin bajar el catálogo)");
      Api.Ficha f = Api.ficha(l, "S1");   // ficha_etiqueta y ficha_producto devuelven el panel: sin ficha → null, sin romper
      ok("servidor viejo: ficha no rompe (" + f + ")");
      return;
    }
    Api.Lite c = Api.catalogoLiviano(l, Arrays.asList("CATEGORIA 3", "CATEGORIA 4"), null);
    if (!c.completo || c.cursor < 0 || c.productos.size() != 71 || !c.borrados.isEmpty()) throw new AssertionError(c.completo + " " + c.cursor + " " + c.productos.size());
    Producto p = c.productos.get(0);
    if (p.sku.isEmpty() || p.nombre.isEmpty() || p.precio != 1290 || p.costo != 900 || p.categoria.isEmpty()) throw new AssertionError(p.sku + "|" + p.nombre + "|" + p.precio + "|" + p.costo);
    ok("catalogo completo: " + c.productos.size() + " productos, cursor " + c.cursor + ", primera fila " + p.sku + " " + p.precio + "/" + p.costo + " (" + p.categoria + ")");
    Api.Lite i = Api.catalogoLiviano(l, null, 7L);
    if (i.completo || !i.productos.isEmpty() || !i.borrados.isEmpty() || i.cursor < 0) throw new AssertionError("igual");
    ok("sin cambios: lista vacía, cursor " + i.cursor);
    Api.Lite d = Api.catalogoLiviano(l, null, 6L);
    if (d.completo || d.productos.size() != 1 || d.productos.get(0).precio != 1590 || !d.borrados.contains("S5")) throw new AssertionError("delta " + d.productos.size() + d.borrados);
    ok("por cambios: 1 fila (" + d.productos.get(0).sku + " a $" + d.productos.get(0).precio + ") y borrados " + d.borrados);
    List<String> cs = Api.categorias(l);
    if (cs.size() != 21 || !cs.contains("CATEGORIA 1") || !cs.contains("SIN CATEGORÍA")) throw new AssertionError("categorias " + cs);
    ok("categorias: " + cs.size() + " (incluye 'SIN CATEGORÍA')");
    List<Producto> b = Api.buscar(l, "limon");
    if (b == null || b.isEmpty() || !b.get(0).sku.equals("S7")) throw new AssertionError("buscar " + b);
    ok("buscar: " + b.size() + " resultado(s), «" + b.get(0).nombre + "»");
    Api.Ficha f = Api.ficha(l, "S1");
    if (f == null || f.precio != 1290 || f.costo != 900 || !"7800000001".equals(f.barcode) || !"15/10/2026".equals(f.vence)) throw new AssertionError("ficha");
    ok("ficha corta: precio " + f.precio + ", costo " + f.costo + ", código " + f.barcode + ", vence " + f.vence);
  }
}
