package cl.marin376.precios;

/** Codificador Code 128 (subconjuntos B y C). Java puro, sin dependencias de Android. */
public final class Code128 {
  // Misma tabla (107 patrones) que usa index.html (JsBarcode). 11 módulos por símbolo, STOP 13.
  private static final String[] BARS = {
    "11011001100",
    "11001101100",
    "11001100110",
    "10010011000",
    "10010001100",
    "10001001100",
    "10011001000",
    "10011000100",
    "10001100100",
    "11001001000",
    "11001000100",
    "11000100100",
    "10110011100",
    "10011011100",
    "10011001110",
    "10111001100",
    "10011101100",
    "10011100110",
    "11001110010",
    "11001011100",
    "11001001110",
    "11011100100",
    "11001110100",
    "11101101110",
    "11101001100",
    "11100101100",
    "11100100110",
    "11101100100",
    "11100110100",
    "11100110010",
    "11011011000",
    "11011000110",
    "11000110110",
    "10100011000",
    "10001011000",
    "10001000110",
    "10110001000",
    "10001101000",
    "10001100010",
    "11010001000",
    "11000101000",
    "11000100010",
    "10110111000",
    "10110001110",
    "10001101110",
    "10111011000",
    "10111000110",
    "10001110110",
    "11101110110",
    "11010001110",
    "11000101110",
    "11011101000",
    "11011100010",
    "11011101110",
    "11101011000",
    "11101000110",
    "11100010110",
    "11101101000",
    "11101100010",
    "11100011010",
    "11101111010",
    "11001000010",
    "11110001010",
    "10100110000",
    "10100001100",
    "10010110000",
    "10010000110",
    "10000101100",
    "10000100110",
    "10110010000",
    "10110000100",
    "10011010000",
    "10011000010",
    "10000110100",
    "10000110010",
    "11000010010",
    "11001010000",
    "11110111010",
    "11000010100",
    "10001111010",
    "10100111100",
    "10010111100",
    "10010011110",
    "10111100100",
    "10011110100",
    "10011110010",
    "11110100100",
    "11110010100",
    "11110010010",
    "11011011110",
    "11011110110",
    "11110110110",
    "10101111000",
    "10100011110",
    "10001011110",
    "10111101000",
    "10111100010",
    "11110101000",
    "11110100010",
    "10111011110",
    "10111101110",
    "11101011110",
    "11110101110",
    "11010000100",
    "11010010000",
    "11010011100",
    "1100011101011"
  };
  private static final int START_B = 104, START_C = 105, CODE_C = 99, STOP = 106;

  private Code128() {}

  /** Texto -> cadena de '1'/'0' (un carácter por módulo), o null si no se puede codificar. */
  public static String modules(String text) {
    if (text == null || text.isEmpty()) return null;
    java.util.ArrayList<Integer> v = new java.util.ArrayList<>();
    boolean digitos = text.matches("[0-9]{4,}");
    if (digitos) {
      int i = 0;
      if (text.length() % 2 == 0) {
        v.add(START_C);
      } else {
        // Cantidad impar: el primer dígito va en B, el resto en pares C (13 dígitos -> 11 símbolos).
        v.add(START_B);
        v.add(text.charAt(0) - 32);
        v.add(CODE_C);
        i = 1;
      }
      if (text.length() % 2 == 0) { /* ya en C */ }
      for (; i < text.length(); i += 2) v.add(Integer.parseInt(text.substring(i, i + 2)));
    } else {
      v.add(START_B);
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c < 32 || c > 126) return null;
        v.add(c - 32);
      }
    }
    int sum = v.get(0);
    for (int i = 1; i < v.size(); i++) sum += v.get(i) * i;
    v.add(sum % 103);
    v.add(STOP);
    StringBuilder sb = new StringBuilder();
    for (int s : v) sb.append(BARS[s]);
    return sb.toString();
  }
}
