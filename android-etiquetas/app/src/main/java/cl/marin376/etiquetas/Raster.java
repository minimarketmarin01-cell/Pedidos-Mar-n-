package cl.marin376.etiquetas;

import java.io.ByteArrayOutputStream;

/** Empaqueta una imagen 1 bit (1 = punto negro) para impresoras térmicas. Java puro. */
public final class Raster {
  private Raster() {}

  public static final int AVANCE_NADA = 0, AVANCE_FF = 1, AVANCE_GS_FF = 2, AVANCE_5MM = 3;

  /** pixeles: w*h, true = negro. Devuelve filas empaquetadas MSB primero. */
  public static byte[] empaquetar(boolean[] negro, int w, int h) {
    int wb = (w + 7) / 8;
    byte[] out = new byte[wb * h];
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++)
        if (negro[y * w + x]) out[y * wb + (x >> 3)] |= (byte) (0x80 >> (x & 7));
    return out;
  }

  /** ESC/POS: GS v 0 (imagen raster) + avance opcional tras la etiqueta. */
  public static byte[] escpos(byte[] bits, int w, int h, int avance) {
    int wb = (w + 7) / 8;
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    o.write(0x1B); o.write(0x40);                       // ESC @ (inicializar)
    o.write(0x1D); o.write(0x76); o.write(0x30); o.write(0x00);
    o.write(wb & 0xFF); o.write((wb >> 8) & 0xFF);
    o.write(h & 0xFF); o.write((h >> 8) & 0xFF);
    o.write(bits, 0, bits.length);
    switch (avance) {
      case AVANCE_FF: o.write(0x0C); break;
      case AVANCE_GS_FF: o.write(0x1D); o.write(0x0C); break;
      case AVANCE_5MM: o.write(0x1B); o.write(0x4A); o.write(40); break; // ESC J 40 puntos = 5 mm
      default: break;
    }
    return o.toByteArray();
  }

  /** TSPL: una etiqueta. invertir = true si la impresora toma 1 como "blanco". */
  public static byte[] tspl(byte[] bits, int w, int h, int anchoMm, int altoMm, int gapMm, boolean invertir) {
    int wb = (w + 7) / 8;
    byte[] datos = bits.clone();
    if (invertir) for (int i = 0; i < datos.length; i++) datos[i] = (byte) ~datos[i];
    ByteArrayOutputStream o = new ByteArrayOutputStream();
    String cab = "SIZE " + anchoMm + " mm," + altoMm + " mm\r\nGAP " + gapMm + " mm,0 mm\r\nDIRECTION 0\r\nCLS\r\n"
        + "BITMAP 0,0," + wb + "," + h + ",0,";
    byte[] c = cab.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    o.write(c, 0, c.length);
    o.write(datos, 0, datos.length);
    byte[] f = "\r\nPRINT 1,1\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    o.write(f, 0, f.length);
    return o.toByteArray();
  }
}
