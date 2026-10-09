package cl.marin376.precios;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes de impresión y de la etiqueta guardados en el equipo. */
public class Ajustes {
  public static final int MODO_ESCPOS = 0, MODO_TSPL = 1;
  public static final int TEXTO_PEQUENO = 0, TEXTO_NORMAL = 1, TEXTO_GRANDE = 2;
  // Igual que ET_TEXTO_PRESETS de la app web (alto de letra en mm): nombre, precio, código/fecha.
  public static final float[][] TEXTO_MM = {{3.8f, 4.6f, 2.8f}, {5.0f, 5.8f, 4.0f}, {6.0f, 6.8f, 4.8f}};

  public String mac = "", nombreImpresora = "";
  public int anchoPuntos = 384;   // ancho imprimible (58 mm de papel = 384 puntos a 203 dpi)
  public int altoMm = 32;         // mismo alto por defecto que la app web (etCargarTamano)
  public int anchoMm = 58;        // solo TSPL
  public int gapMm = 3;           // solo TSPL
  public int modo = MODO_ESCPOS;
  public int avance = Raster.AVANCE_NADA;
  public boolean invertir = false; // solo TSPL
  public int texto = TEXTO_NORMAL;
  // Papel que se avanza al terminar de imprimir, para que la última etiqueta salga entera por el
  // borde de corte (en el POS la cabeza térmica queda ~1 cm antes del borde dentado).
  public int finalMm = 12;
  public String local = Local.MARIN.id;

  public int altoPuntos() { return altoMm * 8; }

  public static Ajustes cargar(Context c) {
    SharedPreferences p = c.getSharedPreferences("precios", Context.MODE_PRIVATE);
    Ajustes a = new Ajustes();
    a.mac = p.getString("mac", "");
    a.nombreImpresora = p.getString("impNombre", "");
    a.anchoPuntos = p.getInt("anchoPuntos", 384);
    a.altoMm = p.getInt("altoMm", 32);
    a.anchoMm = p.getInt("anchoMm", 58);
    a.gapMm = p.getInt("gapMm", 3);
    a.modo = p.getInt("modo", MODO_ESCPOS);
    a.avance = p.getInt("avance", Raster.AVANCE_NADA);
    a.invertir = p.getBoolean("invertir", false);
    a.texto = Math.max(0, Math.min(2, p.getInt("texto", TEXTO_NORMAL)));
    a.finalMm = p.getInt("finalMm", 12);
    a.local = Local.de(p.getString("local", Local.MARIN.id)).id;
    return a;
  }

  public void guardar(Context c) {
    c.getSharedPreferences("precios", Context.MODE_PRIVATE).edit()
        .putString("mac", mac).putString("impNombre", nombreImpresora)
        .putInt("anchoPuntos", anchoPuntos).putInt("altoMm", altoMm).putInt("anchoMm", anchoMm)
        .putInt("gapMm", gapMm).putInt("modo", modo).putInt("avance", avance)
        .putBoolean("invertir", invertir).putInt("texto", texto).putInt("finalMm", finalMm).putString("local", local).apply();
  }
}
