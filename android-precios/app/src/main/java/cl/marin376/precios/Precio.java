package cl.marin376.precios;

import java.util.ArrayList;
import java.util.List;

/** Mismo cálculo que PriceCalc / pcSugerirRedondeos de la app web (Java puro, probado en el PC). */
public final class Precio {
  private Precio() {}

  public static final double IVA = 0.19;

  /** Costo bruto: si el costo escrito es SIN IVA se le suma 19 %; si es CON IVA se usa tal cual. */
  public static double costoBruto(double costo, boolean conIva) {
    if (costo <= 0) return 0;
    return conIva ? costo : costo * (1 + IVA);
  }

  /**
   * Costo de producción: costo + % por mano de obra, materiales y luz (sobre el costo).
   * Ej.: 500 + 10 % = 550. Este es el costo que se usa para el precio y el que se guarda.
   */
  public static double conProduccion(double costoBruto, double pctProduccion) {
    if (costoBruto <= 0) return 0;
    return costoBruto * (1 + Math.max(0, pctProduccion) / 100.0);
  }

  /** Margen SOBRE VENTA: Precio = Costo / (1 − Margen). 0 si faltan datos. */
  public static int desdeMargen(double costoBruto, double margenPct) {
    if (costoBruto <= 0 || margenPct <= 0 || margenPct >= 100) return 0;
    return (int) Math.round(costoBruto / (1 - margenPct / 100.0));
  }

  /** Margen real de un precio dado (%). */
  public static double margenReal(int precio, double costoBruto) {
    if (precio <= 0) return 0;
    return (precio - costoBruto) / precio * 100.0;
  }

  /** Terminaciones 50, 90 y 00 (como pcSugerirRedondeos): ordenadas de menor a mayor. */
  public static List<Integer> redondeos(int precio) {
    List<Integer> out = new ArrayList<>();
    if (precio <= 0) return out;
    for (int term : new int[]{50, 90, 0}) {
      int cand = (precio / 100) * 100 + term;
      if (cand < precio - 50) cand += 100;
      if (!out.contains(cand)) out.add(cand);
    }
    java.util.Collections.sort(out);
    return out;
  }

  /** Precio psicológico de Marín 376: sube a la siguiente terminación en 90 ($1.437 → $1.490). */
  public static int sugerido(int precio) {
    if (precio <= 0) return 0;
    int r = (precio / 100) * 100 + 90;
    if (r < precio) r += 100;
    return r;
  }
}
