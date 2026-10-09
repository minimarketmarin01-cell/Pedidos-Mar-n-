import cl.marin376.precios.*;
import java.io.*;

/**
 * Prueba en el PC (sin Android): arma la zona de barras de una etiqueta de 48 x 32 mm (384 x 256
 * puntos) con el mismo Barras.colocar/pintar que usa LabelRenderer, la empaqueta en ESC/POS y en
 * TSPL (Raster) y guarda los bytes en out/. Después decodificar.py los lee con pyzbar.
 */
public class PruebaBarras {
  static final String[] CODIGOS = {
    "7802820005455", "7790895000997",                    // EAN-13 de fábrica (no empiezan en 0)
    "0412345678905", "0400000000017",                    // internos 04… → Code 128
    "7801234567891", "780282000545",                     // verificador malo / 12 dígitos → Code 128
    "10234", "ABC-12", "abc123", "ESP-ESPINACA",         // SKU → Code 128
    "C39_10234", "C39_ABC-12"                            // Code 39 forzado (respaldo)
  };

  /** Códigos internos como los crea la app (y la web): 04 + 10 al azar + verificador, sin repetir. */
  static boolean probarGenerador(java.util.List<String> salida) {
    java.util.Random r = new java.util.Random(376);
    java.util.HashSet<String> usados = new java.util.HashSet<>();
    usados.add("0412345678905");
    boolean ok = true;
    for (int i = 0; i < 5000; i++) {
      String c = Barras.generarInterno(usados, r);
      boolean bien = c != null && c.matches("04[0-9]{11}") && Barras.ean13Valido(c) && !usados.contains(c)
          && Barras.C128.equals(Barras.elegir(c).tipo);
      if (!bien) { System.out.println("FALLA generador: " + c); ok = false; break; }
      usados.add(c);
      if (i < 3) salida.add(c);
    }
    // Si todos los códigos posibles ya están usados, debe devolver null (no repetir).
    java.util.Set<String> todos = new java.util.AbstractSet<String>() {
      public boolean contains(Object o) { return true; }
      public java.util.Iterator<String> iterator() { return java.util.Collections.<String>emptyIterator(); }
      public int size() { return Integer.MAX_VALUE; }
    };
    if (Barras.generarInterno(todos, r) != null) { System.out.println("FALLA generador: repitió un código usado"); ok = false; }
    System.out.println((ok ? "OK    " : "FALLA ") + "generador: 5000 códigos 04…, verificador correcto, sin repetir, salen en Code 128");
    return ok;
  }

  public static void main(String[] a) throws Exception {
    new File("out").mkdirs();
    java.util.List<String> lista = new java.util.ArrayList<>(java.util.Arrays.asList(a.length > 0 ? a : CODIGOS));
    boolean ok = probarGenerador(lista);   // agrega 3 códigos generados para que pyzbar también los lea
    for (String cod : lista) {
      boolean forzar39 = cod.startsWith("C39_");
      if (forzar39) cod = cod.substring(4);
      Barras.Codigo c = forzar39 ? new Barras.Codigo(Barras.C39, Barras.code39(cod), 10) : Barras.elegir(cod);
      int w = 384, h = 256, borde = 3;
      boolean[] n = new boolean[w * h];
      for (int x = 0; x < w; x++) for (int t = 0; t < borde; t++) { n[t * w + x] = true; n[(h - 1 - t) * w + x] = true; }
      for (int y = 0; y < h; y++) for (int t = 0; t < borde; t++) { n[y * w + t] = true; n[y * w + w - 1 - t] = true; }
      int[] pos = Barras.colocar(c, borde + 1, w - borde - 1, 4);   // igual que LabelRenderer
      int x0 = pos[0], mw = pos[1];
      int top = 60, bot = top + 12 * 8;                             // 12 mm: el mínimo que exige la app
      Barras.pintar(n, w, c.modulos, x0, mw, top, bot);
      byte[] bits = Raster.empaquetar(n, w, h);
      String nombre = "out/" + (forzar39 ? "C39_" : "") + cod;
      try (FileOutputStream f = new FileOutputStream(nombre + ".escpos")) { f.write(Raster.escpos(bits, w, h, Raster.AVANCE_5MM)); }
      try (FileOutputStream f = new FileOutputStream(nombre + ".tspl")) { f.write(Raster.tspl(bits, w, h, 58, 32, 3, false, 3)); }
      int silencioIzq = (x0 - borde) / mw, silencioDer = (w - borde - (x0 + c.modulos.length() * mw)) / mw;
      boolean bien = mw >= 2 && Math.min(silencioIzq, silencioDer) >= 10;
      ok &= bien;
      System.out.println((bien ? "OK    " : "FALLA ") + cod + " → " + c.tipo + ", módulo " + mw + " puntos (" + (mw * 0.125) + " mm), ancho "
          + (c.modulos.length() * mw / 8.0) + " mm, silencio " + Math.min(silencioIzq, silencioDer) + " módulos, barras " + (bot - top) / 8 + " mm");
    }
    if (!ok) System.exit(1);
  }
}
