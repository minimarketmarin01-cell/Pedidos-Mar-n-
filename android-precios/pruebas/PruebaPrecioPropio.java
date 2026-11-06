import cl.marin376.precios.Precio;

/** Precio de una pieza de peso variable (sandía): kg × precio por kg, subido a la decena; sin resultados absurdos. */
public class PruebaPrecioPropio {
  static int n = 0;

  static void igual(String que, int esperado, int real) {
    if (esperado != real) throw new AssertionError(que + ": esperaba " + esperado + " y salió " + real);
    System.out.println("ok " + (++n) + " " + que + " = " + real);
  }

  public static void main(String[] a) {
    igual("5,82 kg × $1.000", 5820, Precio.porPeso(5.82, 1000));
    igual("5,827 kg × $1.000 sube a la decena", 5830, Precio.porPeso(5.827, 1000));
    igual("6 kg × $990", 5940, Precio.porPeso(6, 990));
    igual("0,5 kg × $1.999 (999,5 → 1.000)", 1000, Precio.porPeso(0.5, 1999));
    igual("8,25 kg × $1.200", 9900, Precio.porPeso(8.25, 1200));
    igual("sin peso", 0, Precio.porPeso(0, 1000));
    igual("peso negativo", 0, Precio.porPeso(-1, 1000));
    igual("sin precio por kg", 0, Precio.porPeso(5, 0));
    igual("peso que no es número", 0, Precio.porPeso(Double.NaN, 1000));
    igual("61 kg: casi seguro mal escrito", 0, Precio.porPeso(61, 1000));
    igual("5500 kg (era 5,5 con punto de miles)", 0, Precio.porPeso(5500, 1000));
    igual("60 kg × $1.000 todavía vale", 60000, Precio.porPeso(60, 1000));
    System.out.println("\n" + n + " pruebas OK");
  }
}
