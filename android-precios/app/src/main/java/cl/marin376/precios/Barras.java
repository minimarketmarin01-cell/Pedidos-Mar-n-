package cl.marin376.precios;

/**
 * Códigos de barras para etiquetas de precio. Java puro (sin Android) para poder probarlo en el PC.
 *
 * Regla (misma idea que etBarcodeSvg de la app web, ajustada para lectores láser):
 *  - EAN-13 si son 13 dígitos con dígito verificador correcto y NO empieza en 0.
 *  - Code 128 para todo lo demás que sea ASCII imprimible: códigos internos "04…", 13 dígitos con
 *    0 inicial (un EAN-13 así el lector lo devuelve como UPC-A de 12 dígitos y la caja no lo
 *    encuentra), 12 dígitos, verificador malo y SKU alfanuméricas. Code 128 devuelve el texto
 *    exacto y es ~45 % más angosto que Code 39, así que las barras salen más gruesas.
 *  - Code 39 solo como respaldo si Code 128 no puede.
 */
public final class Barras {
  private Barras() {}

  public static final String EAN13 = "EAN-13", C128 = "Code 128", C39 = "Code 39";

  /** Resultado: tipo, módulos ('1' = barra) y zona de silencio mínima en módulos por lado. */
  public static final class Codigo {
    public final String tipo, modulos;
    public final int silencio;
    public Codigo(String t, String m, int s) { tipo = t; modulos = m; silencio = s; }
  }

  public static Codigo elegir(String texto) {
    if (texto == null) return null;
    texto = texto.trim();
    if (texto.isEmpty()) return null;
    if (ean13Valido(texto) && texto.charAt(0) != '0') return new Codigo(EAN13, ean13(texto), 11);
    String m = Code128.modules(texto);
    if (m != null) return new Codigo(C128, m, 10);
    m = code39(texto);
    if (m != null) return new Codigo(C39, m, 10);
    return null;
  }

  // ------------------------------------------------------------------ EAN-13
  private static final String[] L = {"0001101", "0011001", "0010011", "0111101", "0100011",
      "0110001", "0101111", "0111011", "0110111", "0001011"};
  private static final String[] PARIDAD = {"LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
      "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"};

  public static boolean ean13Valido(String c) {
    if (c == null || !c.matches("[0-9]{13}")) return false;
    int suma = 0;
    for (int i = 0; i < 12; i++) { int d = c.charAt(i) - '0'; suma += (i % 2 == 0) ? d : d * 3; }
    return (10 - suma % 10) % 10 == c.charAt(12) - '0';
  }

  private static String complemento(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) b.append(s.charAt(i) == '0' ? '1' : '0');
    return b.toString();
  }

  public static String ean13(String c) {
    if (!ean13Valido(c)) return null;
    String par = PARIDAD[c.charAt(0) - '0'];
    StringBuilder m = new StringBuilder("101");
    for (int i = 0; i < 6; i++) {
      String l = L[c.charAt(1 + i) - '0'];
      m.append(par.charAt(i) == 'L' ? l : new StringBuilder(complemento(l)).reverse().toString());
    }
    m.append("01010");
    for (int i = 0; i < 6; i++) m.append(complemento(L[c.charAt(7 + i) - '0']));
    m.append("101");
    return m.toString();
  }

  // ------------------------------------------------------------------ Code 39
  private static final String C39_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. *";
  // Mismos patrones que Barcode39 de index.html: 9 elementos (barra, espacio, …), 1 = ancho.
  private static final String[] C39_PAT = {
    "000110100", "100100001", "001100001", "101100000", "000110001", "100110000", "001110000", "000100101",
    "100100100", "001100100", "100001001", "001001001", "101001000", "000011001", "100011000", "001011000",
    "000001101", "100001100", "001001100", "000011100", "100000011", "001000011", "101000010", "000010011",
    "100010010", "001010010", "000000111", "100000110", "001000110", "000010110", "110000001", "011000001",
    "111000000", "010010001", "110010000", "011010000", "010000101", "110000100", "011000100", "010010100"
  };

  /** Code 39 con relación ancho:angosto 3:1 (en módulos enteros). null si hay caracteres no válidos. */
  public static String code39(String texto) {
    if (texto == null || texto.isEmpty()) return null;
    String t = "*" + texto + "*";
    StringBuilder m = new StringBuilder();
    for (int i = 0; i < t.length(); i++) {
      int k = C39_CHARS.indexOf(t.charAt(i));
      if (k < 0 || (t.charAt(i) == '*' && i > 0 && i < t.length() - 1)) return null;
      String p = C39_PAT[k];
      for (int e = 0; e < 9; e++) {
        char bit = (e % 2 == 0) ? '1' : '0';
        int n = p.charAt(e) == '1' ? 3 : 1;
        for (int r = 0; r < n; r++) m.append(bit);
      }
      if (i < t.length() - 1) m.append('0');
    }
    return m.toString();
  }

  // ------------------------------------------------------------------ dibujo
  /** Ancho de módulo (puntos enteros) más grande que cabe con su zona de silencio. 0 si no cabe. */
  public static int modulo(Codigo c, int anchoDisponible, int maximo) {
    for (int mw = maximo; mw >= 1; mw--)
      if ((c.modulos.length() + 2 * c.silencio) * mw <= anchoDisponible) return mw;
    return 0;
  }

  /**
   * Ubica el código entre x0 y x1 (puntos), centrado, con el módulo más grueso que deja la zona de
   * silencio completa. Devuelve {xInicio, anchoModulo}; anchoModulo = 0 si no cabe.
   */
  public static int[] colocar(Codigo c, int x0, int x1, int maximo) {
    int mw = modulo(c, x1 - x0, maximo);
    int ancho = c.modulos.length() * mw;
    return new int[]{x0 + (x1 - x0 - ancho) / 2, mw};
  }

  /** Pinta las barras (negro = true) sin suavizado: cada módulo ocupa exactamente mw puntos. */
  public static void pintar(boolean[] negro, int w, String modulos, int x0, int mw, int top, int bot) {
    for (int i = 0; i < modulos.length(); i++) {
      boolean b = modulos.charAt(i) == '1';
      for (int x = x0 + i * mw; x < x0 + (i + 1) * mw; x++) {
        if (x < 0 || x >= w) continue;
        for (int y = top; y < bot; y++) negro[y * w + x] = b;
      }
    }
  }
}
