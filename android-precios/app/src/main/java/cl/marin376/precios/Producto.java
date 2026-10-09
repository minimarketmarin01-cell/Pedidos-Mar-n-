package cl.marin376.precios;

/** Producto del catálogo (solo lo necesario para la etiqueta de precio). */
public class Producto {
  public String sku = "", nombre = "", barcode = "";
  public int precio = 0;
  /** Texto normalizado (sin acentos, minúsculas) para buscar rápido. */
  String nombreNorm = "";

  /** Lo que se imprime como código: el código de barras, o la SKU si no tiene (igual que la web). */
  public String codigo() { return barcode != null && !barcode.trim().isEmpty() ? barcode.trim() : sku; }
}
