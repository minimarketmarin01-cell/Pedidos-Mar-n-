package cl.marin376.etiquetas;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.List;

/** Dibuja la etiqueta (blanco y negro) como imagen. 8 puntos = 1 mm. */
public final class LabelRenderer {
  private LabelRenderer() {}

  private static List<String> envolver(String txt, Paint p, float ancho) {
    List<String> lineas = new ArrayList<>();
    String actual = "";
    for (String pal : txt.split("\\s+")) {
      if (pal.isEmpty()) continue;
      String prueba = actual.isEmpty() ? pal : actual + " " + pal;
      if (p.measureText(prueba) <= ancho) { actual = prueba; continue; }
      if (!actual.isEmpty()) { lineas.add(actual); actual = ""; }
      while (p.measureText(pal) > ancho && pal.length() > 1) {   // palabra más ancha que la línea
        int n = pal.length();
        while (n > 1 && p.measureText(pal.substring(0, n)) > ancho) n--;
        lineas.add(pal.substring(0, n));
        pal = pal.substring(n);
      }
      actual = pal;
    }
    if (!actual.isEmpty()) lineas.add(actual);
    return lineas;
  }

  public static Bitmap dibujar(Etiqueta e, int w, int h) {
    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    c.drawColor(Color.WHITE);
    float s = w / 384f;
    float m = 10 * s;
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.BLACK);
    p.setTypeface(Typeface.DEFAULT_BOLD);

    // Cabecera
    p.setTextSize(21 * s);
    p.setTextAlign(Paint.Align.CENTER);
    float y = m + 19 * s;
    c.drawText("MARÍN 376 MINI MARKET", w / 2f, y, p);
    p.setStrokeWidth(2);
    c.drawLine(m, y + 7 * s, w - m, y + 7 * s, p);
    y += 7 * s + 10 * s;

    // Nombre (izquierda) + peso y precio (derecha)
    p.setTextAlign(Paint.Align.RIGHT);
    float tPeso = 30 * s, tPrecio = 40 * s;
    float rW = 0, rH = 0;
    if (!e.peso.isEmpty()) { p.setTextSize(tPeso); rW = Math.max(rW, p.measureText(e.peso)); rH += tPeso * 1.1f; }
    if (!e.precio.isEmpty()) { p.setTextSize(tPrecio); rW = Math.max(rW, p.measureText(e.precio)); rH += tPrecio * 1.1f; }
    float nombreAncho = w - 2 * m - rW - (rW > 0 ? 12 * s : 0);
    p.setTextAlign(Paint.Align.LEFT);
    String nom = e.nombre.toUpperCase(java.util.Locale.ROOT);
    List<String> lineas = null;
    float tNom = 32 * s;
    for (; tNom >= 20 * s; tNom -= 2 * s) {
      p.setTextSize(tNom);
      lineas = envolver(nom, p, nombreAncho);
      if (lineas.size() <= 2) break;
    }
    if (lineas.size() > 2) {
      lineas = new ArrayList<>(lineas.subList(0, 2));
      String ult = lineas.get(1);
      while (ult.length() > 1 && p.measureText(ult + "…") > nombreAncho) ult = ult.substring(0, ult.length() - 1);
      lineas.set(1, ult + "…");
    }
    float ny = y + tNom * 0.9f;
    for (String l : lineas) { c.drawText(l, m, ny, p); ny += tNom * 1.1f; }
    float rY = y;
    p.setTextAlign(Paint.Align.RIGHT);
    if (!e.peso.isEmpty()) { p.setTextSize(tPeso); rY += tPeso * 0.9f; c.drawText(e.peso, w - m, rY, p); rY += tPeso * 0.2f; }
    if (!e.precio.isEmpty()) { p.setTextSize(tPrecio); rY += tPrecio * 0.9f; c.drawText(e.precio, w - m, rY, p); rY += tPrecio * 0.2f; }
    float filaFin = Math.max(y + lineas.size() * tNom * 1.1f, y + rH);

    // Pie: ELAB / VENCE
    float tPie = 24 * s;
    String izq = "ELAB: " + e.elab, der = e.vence.isEmpty() ? "" : "VENCE: " + e.vence;
    p.setTextSize(tPie);
    while (tPie > 14 * s && p.measureText(izq) + p.measureText(der) + 16 * s > w - 2 * m) { tPie -= 1; p.setTextSize(tPie); }
    float pieBase = h - m - 4 * s;
    p.setTextAlign(Paint.Align.LEFT);
    c.drawText(izq, m, pieBase, p);
    if (!der.isEmpty()) { p.setTextAlign(Paint.Align.RIGHT); c.drawText(der, w - m, pieBase, p); }
    float pieTop = pieBase - tPie;

    // Código en texto
    float tCod = 24 * s;
    Paint pm = new Paint(Paint.ANTI_ALIAS_FLAG);
    pm.setColor(Color.BLACK);
    pm.setTypeface(Typeface.MONOSPACE);
    pm.setTextAlign(Paint.Align.CENTER);
    pm.setTextSize(tCod);
    float codBase = pieTop - 8 * s;
    c.drawText(e.codigo, w / 2f, codBase, pm);

    // Código de barras Code 128: módulo entero (nítido) y zona de silencio de 10 módulos
    String mod = Code128.modules(e.codigo);
    float barTop = filaFin + 8 * s;
    float barBot = codBase - tCod - 4 * s;
    if (mod != null && barBot - barTop > 20) {
      int mw = 1;
      for (int k = 6; k >= 1; k--) if (mod.length() * k + 20 * k <= w) { mw = k; break; }
      int totalW = mod.length() * mw;
      int x0 = (w - totalW) / 2;
      Paint b = new Paint();
      b.setColor(Color.BLACK);
      b.setAntiAlias(false);
      int top = Math.round(barTop), bot = Math.round(Math.min(barBot, barTop + 130 * s));
      int i = 0;
      while (i < mod.length()) {
        if (mod.charAt(i) == '1') {
          int j = i;
          while (j < mod.length() && mod.charAt(j) == '1') j++;
          c.drawRect(x0 + i * mw, top, x0 + j * mw, bot, b);
          i = j;
        } else i++;
      }
    }
    return bmp;
  }

  /** Imagen -> 1 bit (1 = negro), ya empaquetada por filas. */
  public static byte[] aBits(Bitmap bmp) {
    int w = bmp.getWidth(), h = bmp.getHeight();
    int[] px = new int[w * h];
    bmp.getPixels(px, 0, w, 0, 0, w, h);
    boolean[] negro = new boolean[w * h];
    for (int i = 0; i < px.length; i++) {
      int r = (px[i] >> 16) & 255, g = (px[i] >> 8) & 255, b = px[i] & 255;
      negro[i] = (r * 299 + g * 587 + b * 114) / 1000 < 150;
    }
    return Raster.empaquetar(negro, w, h);
  }
}
