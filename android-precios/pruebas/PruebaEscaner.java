import cl.marin376.precios.*;
import java.util.*;

/**
 * Prueba en el PC del lector de la cámara (Escaner): arma imágenes en gris como las que entrega la
 * cámara (fondo, desenfoque, ruido, poco contraste, código girado 90°/180° y en distintas
 * posiciones) con los mismos códigos que imprime la app, y comprueba que se leen exactos. También
 * comprueba que el ruido y los fondos lisos no producen lecturas falsas.
 */
public class PruebaEscaner {
  static final String[] CODIGOS = {
    "7802820005455", "7790895000997", "0412345678905", "0400000000017",   // EAN-13 y 04… (Code 128)
    "10234", "ABC-12", "abc123", "ESP-ESPINACA", "780282000545",
    "73513537", "96385074"                                                 // EAN-8
  };

  static String modulos(String cod) {
    if (cod.matches("[0-9]{8}")) return ean8(cod);
    if (Barras.ean13Valido(cod) && cod.charAt(0) != '0') return Barras.ean13(cod);
    return Code128.modules(cod);
  }

  static String ean8(String c) {
    StringBuilder m = new StringBuilder("101");
    for (int i = 0; i < 4; i++) m.append(Barras.L[c.charAt(i) - '0']);
    m.append("01010");
    for (int i = 4; i < 8; i++) {
      String l = Barras.L[c.charAt(i) - '0'];
      for (int k = 0; k < l.length(); k++) m.append(l.charAt(k) == '0' ? '1' : '0');
    }
    return m.append("101").toString();
  }

  static byte[] imagen(String mod, int w, int h, int mw, Random r, int x0, int y0, int alto, int nivelNegro, int nivelBlanco, int ruido, boolean desenfoque) {
    int[] p = new int[w * h];
    Arrays.fill(p, nivelBlanco);
    for (int y = y0; y < Math.min(h, y0 + alto); y++)
      for (int k = 0; k < mod.length(); k++)
        if (mod.charAt(k) == '1')
          for (int t = 0; t < mw; t++) { int x = x0 + k * mw + t; if (x >= 0 && x < w) p[y * w + x] = nivelNegro; }
    if (desenfoque) {
      int[] q = p.clone();
      for (int y = 0; y < h; y++) for (int x = 1; x < w - 1; x++) q[y * w + x] = (p[y * w + x - 1] + 2 * p[y * w + x] + p[y * w + x + 1]) / 4;
      p = q;
    }
    byte[] b = new byte[w * h];
    for (int i = 0; i < p.length; i++) b[i] = (byte) Math.max(0, Math.min(255, p[i] + (ruido > 0 ? r.nextInt(2 * ruido + 1) - ruido : 0)));
    return b;
  }

  static byte[] transponer(byte[] b, int w, int h) {
    byte[] o = new byte[w * h];
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o[x * h + y] = b[y * w + x];
    return o;
  }

  static byte[] girar180(byte[] b, int w, int h) {
    byte[] o = new byte[w * h];
    for (int i = 0; i < o.length; i++) o[i] = b[o.length - 1 - i];
    return o;
  }

  public static void main(String[] a) {
    Random r = new Random(376);
    int total = 0, fallas = 0, dificiles = 0, intentosDificiles = 0;
    int[] anchos = {2, 3, 4};
    for (String cod : CODIGOS) {
      String mod = modulos(cod);
      if (mod == null) { System.out.println("FALLA no se pudo codificar " + cod); fallas++; continue; }
      int mal = 0, n = 0;
      for (int mw : anchos) {
        int w = 640, h = 480;
        if ((mod.length() + 24) * mw > w) continue;
        for (int variante = 0; variante < 8; variante++) {
          int x0 = 12 * mw + r.nextInt(w - (mod.length() + 24) * mw + 1);
          int y0 = 120 + r.nextInt(100);
          int negro = 30 + r.nextInt(50), blanco = 170 + r.nextInt(60);
          boolean borroso = variante % 2 == 1;
          byte[] img = imagen(mod, w, h, mw, r, x0, y0, 110, negro, blanco, 10, borroso);
          int ww = w, hh = h;
          if (variante % 4 >= 2) { img = girar180(img, w, h); }
          String lec;
          if (variante >= 4) { byte[] t = transponer(img, w, h); lec = Escaner.leerImagen(t, h, w); }   // código vertical
          else lec = Escaner.leerImagen(img, w, h);
          n++;
          total++;
          if (mw == 2 && borroso) intentosDificiles++;
          if (!cod.equals(lec)) {
            if (mw == 2 && borroso) { dificiles++; continue; }   // 2 px por módulo y borroso: el caso límite
            mal++; fallas++;
            System.out.println("  falla " + cod + " mw=" + mw + " variante=" + variante + " leyó=" + lec);
          }
        }
      }
      System.out.println((mal == 0 ? "OK    " : "FALLA ") + "lee " + cod + "  (" + (n - mal) + "/" + n + " imágenes)");
    }
    // Caso límite (2 px por módulo y borroso): debe leer casi siempre; la cámara además prueba muchas líneas y cuadros.
    boolean limiteOk = dificiles * 4 <= intentosDificiles;
    System.out.println((limiteOk ? "OK    " : "FALLA ") + "caso límite 2 px + borroso: falló " + dificiles + " de " + intentosDificiles);
    if (!limiteOk) fallas++;
    // Sin códigos: ruido, fondo liso y gradiente no deben dar lecturas.
    int falsos = 0;
    for (int i = 0; i < 300; i++) {
      byte[] b = new byte[640 * 480];
      for (int k = 0; k < b.length; k++) b[k] = (byte) r.nextInt(256);
      if (Escaner.leerImagen(b, 640, 480) != null) falsos++;
    }
    byte[] liso = new byte[640 * 480];
    Arrays.fill(liso, (byte) 200);
    if (Escaner.leerImagen(liso, 640, 480) != null) falsos++;
    System.out.println((falsos == 0 ? "OK    " : "FALLA ") + "sin código: 300 imágenes de ruido + fondo liso, lecturas falsas = " + falsos);
    if (falsos > 0) fallas++;
    System.out.println(fallas == 0 ? "TODO OK (" + total + " imágenes)" : "HAY FALLAS: " + fallas);
    if (fallas > 0) System.exit(1);
  }
}
