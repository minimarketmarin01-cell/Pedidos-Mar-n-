package cl.marin376.precios;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja la etiqueta de precio en blanco y negro. 8 puntos = 1 mm (impresora de 203 dpi).
 * Mismo diseño que etImprimir() de la app web: borde redondeado, arriba nombre + precio, al medio
 * el código de barras ocupando todo el alto que sobra, abajo el código en texto + "Vence: …".
 * Las barras se pintan al final directo en la imagen de 1 bit (sin suavizado, módulo entero).
 */
public final class LabelRenderer {
  private LabelRenderer() {}

  public static final int MM = 8;
  public static final float BARRAS_MIN_MM = 12f;

  public static final class Resultado {
    public Bitmap vista;          // exactamente lo que se imprime
    public byte[] bits;           // imagen 1 bit empaquetada por filas
    public int w, h;
    public String tipo = "";      // EAN-13 / Code 128 / Code 39
    public float barrasMm = 0, moduloMm = 0;
    public String aviso = "";     // texto para el usuario si algo puede fallar al escanear
  }

  private static String cortar(String t, Paint p, float ancho) {
    if (p.measureText(t) <= ancho) return t;
    while (t.length() > 1 && p.measureText(t + "…") > ancho) t = t.substring(0, t.length() - 1);
    return t.trim() + "…";
  }

  /** Parte en 2 líneas por palabras; la segunda se corta con "…" si no cabe. */
  private static List<String> dosLineas(String txt, Paint p, float ancho) {
    List<String> l = new ArrayList<>();
    String[] pal = txt.split("\\s+");
    String a = "";
    int i = 0;
    for (; i < pal.length; i++) {
      String prueba = a.isEmpty() ? pal[i] : a + " " + pal[i];
      if (p.measureText(prueba) > ancho && !a.isEmpty()) break;
      a = prueba;
    }
    l.add(cortar(a, p, ancho));
    StringBuilder b = new StringBuilder();
    for (; i < pal.length; i++) b.append(b.length() == 0 ? "" : " ").append(pal[i]);
    if (b.length() > 0) l.add(cortar(b.toString(), p, ancho));
    return l;
  }

  /** 09/10/2026 → 09/10/26 */
  private static String corta(String f) {
    return f.matches("\\d{2}/\\d{2}/\\d{4}") ? f.substring(0, 6) + f.substring(8) : f;
  }

  private static float baseline(Paint p, float top, float alto) {
    Paint.FontMetrics fm = p.getFontMetrics();
    return top + (alto - (fm.descent - fm.ascent)) / 2f - fm.ascent;
  }

  public static Resultado dibujar(EtiquetaPrecio e, int w, int h, int texto) {
    float[] mm = Ajustes.TEXTO_MM[Math.max(0, Math.min(2, texto))];
    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    c.drawColor(Color.WHITE);

    int borde = 3;                          // 0,4 mm
    float padX = borde + 1.0f * MM;         // 1 mm
    float padY = borde + 0.6f * MM;         // 0,6 mm
    float gap = 0.4f * MM;
    float interior = w - 2 * padX;

    Paint marco = new Paint(Paint.ANTI_ALIAS_FLAG);
    marco.setStyle(Paint.Style.STROKE);
    marco.setStrokeWidth(borde);
    marco.setColor(Color.BLACK);
    c.drawRoundRect(new RectF(borde / 2f, borde / 2f, w - borde / 2f, h - borde / 2f), 1.2f * MM, 1.2f * MM, marco);

    Paint pPrecio = new Paint(Paint.ANTI_ALIAS_FLAG);
    pPrecio.setColor(Color.BLACK);
    pPrecio.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    pPrecio.setTextSize(mm[1] * MM);
    pPrecio.setTextAlign(Paint.Align.RIGHT);
    float anchoPrecio = e.precio.isEmpty() ? 0 : pPrecio.measureText(e.precio);

    Paint pNom = new Paint(Paint.ANTI_ALIAS_FLAG);
    pNom.setColor(Color.BLACK);
    pNom.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    float anchoNom = interior - anchoPrecio - (anchoPrecio > 0 ? 1f * MM : 0);
    String nombre = e.nombre.trim();
    float tNom = mm[0] * MM;
    pNom.setTextSize(tNom);
    while (pNom.measureText(nombre) > anchoNom && tNom > mm[0] * MM * 0.75f) { tNom -= 1; pNom.setTextSize(tNom); }

    // Fila de abajo: código (monoespaciado) + vencimiento
    Paint pCod = new Paint(Paint.ANTI_ALIAS_FLAG);
    pCod.setColor(Color.BLACK);
    pCod.setTypeface(Typeface.MONOSPACE);
    Paint pFecha = new Paint(Paint.ANTI_ALIAS_FLAG);
    pFecha.setColor(Color.BLACK);
    pFecha.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    float tCod = mm[2] * MM;
    // Con fecha de elaboración las dos fechas van en una fila propia (año en 2 dígitos para que
    // quepan): "Elab: 09/10/26   Vence: 12/10/26"; abajo queda solo el código.
    boolean conElab = !e.elab.isEmpty();
    String fecha = conElab || e.vence.isEmpty() ? "" : "Vence: " + e.vence;
    String fElab = conElab ? "Elab: " + corta(e.elab) : "";
    String fVence = conElab && !e.vence.isEmpty() ? "Vence: " + corta(e.vence) : "";
    float tFechas = mm[2] * MM;
    if (conElab) {
      while (true) {
        pFecha.setTextSize(tFechas);
        float total = pFecha.measureText(fElab) + (fVence.isEmpty() ? 0 : pFecha.measureText(fVence) + 1f * MM);
        if (total <= interior || tFechas <= mm[2] * MM * 0.6f) break;
        tFechas -= 1;
      }
    }
    while (true) {
      pCod.setTextSize(tCod);
      pFecha.setTextSize(tCod);
      float total = pCod.measureText(e.codigo) + (fecha.isEmpty() ? 0 : pFecha.measureText(fecha) + 1f * MM);
      if (total <= interior || tCod <= mm[2] * MM * 0.6f) break;
      tCod -= 1;
    }
    float altoAbajo = (e.codigo.isEmpty() && fecha.isEmpty()) ? 0 : tCod * 1.1f;
    float altoFechas = conElab ? tFechas * 1.1f : 0;
    float abajoTop = h - padY - altoAbajo - altoFechas;

    // Fila de arriba: 1 línea; si el nombre no cabe, 2 líneas solo si las barras siguen ≥ 12 mm.
    float altoUna = Math.max(tNom, e.precio.isEmpty() ? 0 : mm[1] * MM) * 1.15f;
    List<String> lineas = new ArrayList<>();
    if (pNom.measureText(nombre) <= anchoNom) lineas.add(nombre);
    else {
      float barrasCon2 = abajoTop - gap - (padY + altoUna + tNom * 1.15f + gap);
      if (barrasCon2 >= BARRAS_MIN_MM * MM) lineas = dosLineas(nombre, pNom, anchoNom);
      else lineas.add(cortar(nombre, pNom, anchoNom));
    }
    float ny = baseline(pNom, padY, altoUna);
    float py = baseline(pPrecio, padY, altoUna);
    float base = Math.max(ny, py);   // misma línea base (como align-items: baseline)
    c.drawText(lineas.get(0), padX, base, pNom);
    if (!e.precio.isEmpty()) c.drawText(e.precio, w - padX, base, pPrecio);
    float arribaFin = padY + altoUna;
    if (lineas.size() > 1) {
      c.drawText(lineas.get(1), padX, base + tNom * 1.15f, pNom);
      arribaFin += tNom * 1.15f;
    }

    if (conElab) {
      pFecha.setTextSize(tFechas);
      float yf = baseline(pFecha, abajoTop, altoFechas);
      pFecha.setTextAlign(Paint.Align.LEFT);
      c.drawText(fElab, padX, yf, pFecha);
      if (!fVence.isEmpty()) { pFecha.setTextAlign(Paint.Align.RIGHT); c.drawText(fVence, w - padX, yf, pFecha); }
      pFecha.setTextSize(tCod);
    }
    if (altoAbajo > 0) {
      float yb = baseline(pCod, abajoTop + altoFechas, altoAbajo);
      pCod.setTextAlign(Paint.Align.LEFT);
      c.drawText(e.codigo, padX, yb, pCod);
      if (!fecha.isEmpty()) { pFecha.setTextAlign(Paint.Align.RIGHT); c.drawText(fecha, w - padX, yb, pFecha); }
    }

    // A 1 bit
    int[] px = new int[w * h];
    bmp.getPixels(px, 0, w, 0, 0, w, h);
    boolean[] negro = new boolean[w * h];
    for (int i = 0; i < px.length; i++) {
      int r = (px[i] >> 16) & 255, g = (px[i] >> 8) & 255, b = px[i] & 255;
      negro[i] = (r * 299 + g * 587 + b * 114) / 1000 < 140;
    }

    Resultado res = new Resultado();
    res.w = w;
    res.h = h;
    int top = (int) Math.ceil(arribaFin + gap), bot = (int) Math.floor(abajoTop - gap);
    Barras.Codigo cod = Barras.elegir(e.codigo);
    if (cod == null) {
      res.aviso = e.codigo.isEmpty() ? "Este producto no tiene código: la etiqueta sale sin barras." : "Este código no se puede convertir en barras.";
    } else if (bot - top < 4 * MM) {
      res.aviso = "No queda espacio para las barras. Sube el alto de la etiqueta en Ajustes.";
    } else {
      int x0 = borde + 1, x1 = w - borde - 1;   // la zona de silencio puede usar el margen interior
      for (int y = top; y < bot; y++) for (int x = x0; x < x1; x++) negro[y * w + x] = false;
      int[] pos = Barras.colocar(cod, x0, x1, 4);
      if (pos[1] == 0) {
        res.aviso = "El código es demasiado largo para el ancho de la etiqueta.";
      } else {
        Barras.pintar(negro, w, cod.modulos, pos[0], pos[1], top, bot);
        res.tipo = cod.tipo;
        res.barrasMm = (bot - top) / (float) MM;
        res.moduloMm = pos[1] / (float) MM;
        if (res.barrasMm < BARRAS_MIN_MM) {
          int falta = (int) Math.ceil(BARRAS_MIN_MM - res.barrasMm);
          res.aviso = String.format(java.util.Locale.ROOT, "Barras de %.0f mm: el lector necesita 12 mm. Sube el alto en Ajustes (+%d mm) o usa letra más pequeña.", res.barrasMm, falta);
        } else if (pos[1] < 2) {
          res.aviso = "Código largo: barras muy finas. Si el lector no lo lee, aumenta el ancho en puntos en Ajustes.";
        }
      }
    }

    int[] out = new int[w * h];
    for (int i = 0; i < out.length; i++) out[i] = negro[i] ? Color.BLACK : Color.WHITE;
    bmp.setPixels(out, 0, w, 0, 0, w, h);
    res.vista = bmp;
    res.bits = Raster.empaquetar(negro, w, h);
    return res;
  }
}
