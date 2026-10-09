package cl.marin376.etiquetas;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes de impresión guardados en el equipo. */
public class Ajustes {
  public static final int MODO_ESCPOS = 0, MODO_TSPL = 1;
  public String mac = "", nombreImpresora = "";
  public int anchoPuntos = 384;   // ancho imprimible (58 mm de papel ~ 384 puntos a 203 dpi)
  public int altoMm = 40;
  public int anchoMm = 58;        // solo TSPL
  public int gapMm = 3;           // solo TSPL
  public int modo = MODO_ESCPOS;
  public int avance = Raster.AVANCE_NADA;
  public boolean invertir = false; // solo TSPL

  public int altoPuntos() { return altoMm * 8; }

  public static Ajustes cargar(Context c) {
    SharedPreferences p = c.getSharedPreferences("etiquetas", Context.MODE_PRIVATE);
    Ajustes a = new Ajustes();
    a.mac = p.getString("mac", "");
    a.nombreImpresora = p.getString("impNombre", "");
    a.anchoPuntos = p.getInt("anchoPuntos", 384);
    a.altoMm = p.getInt("altoMm", 40);
    a.anchoMm = p.getInt("anchoMm", 58);
    a.gapMm = p.getInt("gapMm", 3);
    a.modo = p.getInt("modo", MODO_ESCPOS);
    a.avance = p.getInt("avance", Raster.AVANCE_NADA);
    a.invertir = p.getBoolean("invertir", false);
    return a;
  }

  public void guardar(Context c) {
    c.getSharedPreferences("etiquetas", Context.MODE_PRIVATE).edit()
        .putString("mac", mac).putString("impNombre", nombreImpresora)
        .putInt("anchoPuntos", anchoPuntos).putInt("altoMm", altoMm).putInt("anchoMm", anchoMm)
        .putInt("gapMm", gapMm).putInt("modo", modo).putInt("avance", avance)
        .putBoolean("invertir", invertir).apply();
  }
}
