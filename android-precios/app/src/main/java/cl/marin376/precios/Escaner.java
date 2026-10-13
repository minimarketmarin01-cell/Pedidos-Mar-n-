package cl.marin376.precios;

/**
 * Lee códigos de barras desde una imagen en gris (el plano Y de la cámara). Java puro, sin
 * Android ni librerías, para poder probarlo en el PC. Lee EAN-13 / UPC-A, EAN-8 y Code 128 (lo que
 * imprime esta app y lo que traen los productos). Recorre líneas horizontales y verticales (así
 * da igual cómo se sostenga el equipo) y en los dos sentidos. Todos llevan dígito verificador, así
 * que una lectura equivocada casi no existe.
 */
public final class Escaner {
  private Escaner() {}

  private static final double MAX_PROM = 0.48, MAX_IND = 0.7;   // tolerancia de EAN (como ZXing)
  private static final double MAX_PROM_128 = 0.25;
  private static final double MIN_MODULO = 1.5;   // px por módulo: menos que eso es ruido, no un código

  // ---------------------------------------------------------------- patrones (anchos de barras)
  private static final int[][] PL = new int[10][], PG = new int[10][], PR = new int[10][];
  private static final int[][] P128 = new int[107][];
  private static final int[] GUARDA = {1, 1, 1}, CENTRO = {1, 1, 1, 1, 1}, STOP = {2, 3, 3, 1, 1, 1, 2};

  static {
    for (int d = 0; d < 10; d++) {
      String l = Barras.L[d], c = complemento(l);
      PL[d] = corridas(l);
      PR[d] = corridas(c);
      PG[d] = corridas(new StringBuilder(c).reverse().toString());
    }
    for (int i = 0; i < 107; i++) P128[i] = corridas(Code128.BARS[i]);
  }

  private static String complemento(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) b.append(s.charAt(i) == '0' ? '1' : '0');
    return b.toString();
  }

  /** "0001101" -> {3,2,1,1} (largo de cada tramo, partiendo por el primer carácter). */
  private static int[] corridas(String m) {
    java.util.ArrayList<Integer> r = new java.util.ArrayList<>();
    int n = 1;
    for (int i = 1; i <= m.length(); i++) {
      if (i < m.length() && m.charAt(i) == m.charAt(i - 1)) n++;
      else { r.add(n); n = 1; }
    }
    int[] o = new int[r.size()];
    for (int i = 0; i < o.length; i++) o[i] = r.get(i);
    return o;
  }

  // ---------------------------------------------------------------- imagen
  /** Plano Y (un byte por píxel, filas seguidas). null si no hay un código legible. */
  public static String leerImagen(byte[] y, int w, int h) {
    if (y == null || y.length < w * h || w < 32 || h < 32) return null;
    int[] linea = new int[Math.max(w, h)];
    // Del centro hacia afuera: lo normal es apuntar con el código al medio.
    for (int k = 0; k <= 24; k++) {
      for (int signo = (k == 0 ? 1 : -1); signo <= 1; signo += 2) {
        int fila = h / 2 + signo * k * h / 56;
        if (fila >= 0 && fila < h) {
          for (int x = 0; x < w; x++) linea[x] = y[fila * w + x] & 255;
          String r = leerLinea(linea, w);
          if (r != null) return r;
        }
        int col = w / 2 + signo * k * w / 56;
        if (col >= 0 && col < w) {
          for (int j = 0; j < h; j++) linea[j] = y[j * w + col] & 255;
          String r = leerLinea(linea, h);
          if (r != null) return r;
        }
      }
    }
    return null;
  }

  /** Una línea de píxeles (0 = negro, 255 = blanco). Prueba los dos sentidos. */
  public static String leerLinea(int[] v, int n) {
    // Contraste mínimo: sin él es un fondo liso.
    int[] copia = java.util.Arrays.copyOf(v, n);
    java.util.Arrays.sort(copia);
    int lo = copia[n / 50], hi = copia[n * 49 / 50];   // 2 % / 98 %: un código angosto es poca parte de la línea
    if (hi - lo < 40) return null;
    // Umbral local (promedio de la vecindad, un poco hacia lo oscuro) → negro/blanco.
    int ventana = Math.max(15, n / 8) | 1, mitad = ventana / 2;
    long[] acum = new long[n + 1];
    for (int i = 0; i < n; i++) acum[i + 1] = acum[i] + v[i];
    double margen = 0.10 * (hi - lo);
    int[] r = new int[n];
    int m = 0;
    boolean negro0 = false, actual = false;
    int largo = 0;
    for (int i = 0; i < n; i++) {
      int a = Math.max(0, i - mitad), b = Math.min(n, i + mitad + 1);
      double media = (acum[b] - acum[a]) / (double) (b - a);
      boolean negro = v[i] < media - margen;
      if (i == 0) { negro0 = negro; actual = negro; largo = 1; continue; }
      if (negro == actual) largo++;
      else { r[m++] = largo; actual = negro; largo = 1; }
    }
    r[m++] = largo;
    String s = leerCorridas(r, m, negro0);
    if (s != null) return s;
    // Al revés: se invierte el orden de los tramos.
    int[] inv = new int[m];
    for (int i = 0; i < m; i++) inv[i] = r[m - 1 - i];
    boolean ultimaNegra = negro0 == (m % 2 == 1);   // el último tramo es del color del primero si m es impar
    return leerCorridas(inv, m, ultimaNegra);
  }

  static String leerCorridas(int[] r, int m, boolean negroPrimero) {
    String s = leerEan(r, m, negroPrimero, 6);
    if (s == null) s = leerEan(r, m, negroPrimero, 4);
    if (s == null) s = leerCode128(r, m, negroPrimero);
    return s;
  }

  private static boolean esNegra(int i, boolean negroPrimero) { return (i % 2 == 0) == negroPrimero; }

  /** Variación de 'n' tramos (desde off) contra un patrón: 0 = igual, grande = distinto. */
  private static double variacion(int[] c, int off, int n, int[] pat) {
    int total = 0, ptotal = 0;
    for (int i = 0; i < n; i++) { total += c[off + i]; ptotal += pat[i]; }
    if (total < ptotal) return Double.MAX_VALUE;
    double unidad = (double) total / ptotal, maxInd = MAX_IND * unidad, tv = 0;
    for (int i = 0; i < n; i++) {
      double v = Math.abs(c[off + i] - pat[i] * unidad);
      if (v > maxInd) return Double.MAX_VALUE;
      tv += v;
    }
    return tv / total;
  }

  // ---------------------------------------------------------------- EAN-13 / UPC-A (izq = 6) y EAN-8 (izq = 4)
  private static String leerEan(int[] r, int m, boolean negroPrimero, int izq) {
    for (int i = 1; i + 3 < m; i++) {
      if (!esNegra(i, negroPrimero)) continue;
      double u = (r[i] + r[i + 1] + r[i + 2]) / 3.0;
      if (u < MIN_MODULO || variacion(r, i, 3, GUARDA) > MAX_PROM) continue;
      if (r[i - 1] < 3 * u) continue;   // zona de silencio
      String s = decodificarEan(r, m, i + 3, izq, u);
      if (s != null) return s;
    }
    return null;
  }

  private static String decodificarEan(int[] r, int m, int pos, int izq, double u) {
    StringBuilder d = new StringBuilder();
    StringBuilder paridad = new StringBuilder();
    for (int k = 0; k < izq; k++) {
      if (pos + 4 > m) return null;
      int mejor = -1;
      char tipo = 'L';
      double vm = MAX_PROM;
      for (int n = 0; n < 10; n++) {
        double a = variacion(r, pos, 4, PL[n]);
        if (a < vm) { vm = a; mejor = n; tipo = 'L'; }
        if (izq == 6) {
          double b = variacion(r, pos, 4, PG[n]);
          if (b < vm) { vm = b; mejor = n; tipo = 'G'; }
        }
      }
      if (mejor < 0) return null;
      d.append((char) ('0' + mejor));
      paridad.append(tipo);
      pos += 4;
    }
    if (pos + 5 > m || variacion(r, pos, 5, CENTRO) > MAX_PROM) return null;
    pos += 5;
    for (int k = 0; k < izq; k++) {
      if (pos + 4 > m) return null;
      int mejor = -1;
      double vm = MAX_PROM;
      for (int n = 0; n < 10; n++) {
        double a = variacion(r, pos, 4, PR[n]);
        if (a < vm) { vm = a; mejor = n; }
      }
      if (mejor < 0) return null;
      d.append((char) ('0' + mejor));
      pos += 4;
    }
    if (pos + 3 > m || variacion(r, pos, 3, GUARDA) > MAX_PROM) return null;
    if (pos + 3 < m && r[pos + 3] < 3 * u) return null;   // zona de silencio final
    String out = d.toString();
    if (izq == 6) {
      int primero = -1;
      for (int n = 0; n < 10; n++) if (Barras.PARIDAD[n].equals(paridad.toString())) { primero = n; break; }
      if (primero < 0) return null;
      out = (char) ('0' + primero) + out;
    }
    return checksumEan(out) ? out : null;
  }

  private static boolean checksumEan(String c) {
    int suma = 0, n = c.length();
    for (int i = 0; i < n - 1; i++) {
      int dig = c.charAt(i) - '0';
      suma += ((n - 1 - i) % 2 == 1) ? dig * 3 : dig;   // desde la derecha: ×3, ×1, ×3…
    }
    return (10 - suma % 10) % 10 == c.charAt(n - 1) - '0';
  }

  // ---------------------------------------------------------------- Code 128
  private static String leerCode128(int[] r, int m, boolean negroPrimero) {
    for (int i = 1; i + 6 < m; i++) {
      if (!esNegra(i, negroPrimero)) continue;
      int inicio = -1;
      double vm = MAX_PROM_128;
      for (int s = 103; s <= 105; s++) {
        double v = variacion(r, i, 6, P128[s]);
        if (v < vm) { vm = v; inicio = s; }
      }
      if (inicio < 0) continue;
      int suma6 = 0;
      for (int k = 0; k < 6; k++) suma6 += r[i + k];
      double u = suma6 / 11.0;
      if (u < MIN_MODULO || r[i - 1] < 3 * u) continue;   // zona de silencio
      String s = decodificar128(r, m, i + 6, inicio, u);
      if (s != null) return s;
    }
    return null;
  }

  private static String decodificar128(int[] r, int m, int pos, int inicio, double u) {
    java.util.ArrayList<Integer> vals = new java.util.ArrayList<>();
    while (true) {
      double vStop = pos + 7 <= m ? variacion(r, pos, 7, STOP) : Double.MAX_VALUE;
      int mejor = -1;
      double vs = MAX_PROM_128;
      if (pos + 6 <= m) {
        for (int s = 0; s < 106; s++) {
          double v = variacion(r, pos, 6, P128[s]);
          if (v < vs) { vs = v; mejor = s; }
        }
      }
      if (vStop < MAX_PROM_128 && (mejor < 0 || vStop < vs)) {
        if (pos + 7 < m && r[pos + 7] < 3 * u) return null;   // zona de silencio final
        break;
      }
      if (mejor < 0 || vals.size() > 60) return null;
      vals.add(mejor);
      pos += 6;
    }
    int n = vals.size();
    if (n < 2) return null;
    long suma = inicio;
    for (int k = 0; k < n - 1; k++) suma += (long) (k + 1) * vals.get(k);
    if (suma % 103 != vals.get(n - 1)) return null;   // dígito verificador
    StringBuilder out = new StringBuilder();
    int juego = inicio == 103 ? 0 : inicio == 104 ? 1 : 2;   // A, B, C
    for (int k = 0; k < n - 1; k++) {
      int v = vals.get(k);
      if (juego == 2) {
        if (v <= 99) out.append((char) ('0' + v / 10)).append((char) ('0' + v % 10));
        else if (v == 100) juego = 1;
        else if (v == 101) juego = 0;
      } else {
        if (v <= 95) out.append((char) (juego == 0 && v < 64 ? 32 + v : juego == 0 ? v - 64 : 32 + v));
        else if (v == 99) juego = 2;
        else if (v == 100) juego = 1;
        else if (v == 101) juego = 0;
      }
    }
    return out.length() == 0 ? null : out.toString();
  }
}
