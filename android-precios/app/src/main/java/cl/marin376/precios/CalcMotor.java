package cl.marin376.precios;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lógica de la calculadora tipo celular Samsung, traducida 1:1 de Calc (index.html, "Calculadora
 * normal"): se escribe la operación completa, abajo se ve el resultado en vivo y "=" lo confirma.
 * Precedencia (× ÷ antes que + −), paréntesis, menos unario y % estilo Samsung:
 * 200+10% = 220, 200×10% = 20. Java puro (se prueba en el PC contra el código de la web).
 */
public final class CalcMotor {
  private String expr = "", anterior = "", msg = "";
  private boolean done = false;
  private static final String OPS = "+-*/";
  private static final Pattern NUM_FINAL = Pattern.compile("(\\d*\\.?\\d*)$");
  private static final Pattern NUM = Pattern.compile("^(\\d+\\.?\\d*|\\.\\d+)");
  private static final Pattern NUM_TXT = Pattern.compile("\\d+\\.?\\d*|\\.\\d+");

  private static boolean esDig(String c) { return c.length() == 1 && c.charAt(0) >= '0' && c.charAt(0) <= '9'; }
  private String ultimo() { return expr.isEmpty() ? "" : expr.substring(expr.length() - 1); }
  private int abiertos() { int n = 0; for (char c : expr.toCharArray()) { if (c == '(') n++; else if (c == ')') n--; } return n; }
  private String numFinal() { Matcher m = NUM_FINAL.matcher(expr); return m.find() ? m.group(1) : ""; }

  // ------------------------------------------------------------------ evaluador
  private static final class Error extends RuntimeException { Error(String m) { super(m); } }

  private static final class Ev {
    final String t; int i = 0;
    Ev(String t) { this.t = t; }
    char c() { return i < t.length() ? t.charAt(i) : '\0'; }

    double[] primario() {   // {valor, esPorcentaje}
      boolean neg = false;
      while (c() == '-' || c() == '+') { if (c() == '-') neg = !neg; i++; }
      double v;
      if (c() == '(') { i++; v = suma(); if (c() != ')') throw new Error("fmt"); i++; }
      else {
        Matcher m = NUM.matcher(t.substring(i));
        if (!m.find()) throw new Error("fmt");
        v = Double.parseDouble(m.group(1));
        i += m.group(1).length();
      }
      if (neg) v = -v;
      boolean p = false;
      while (c() == '%') { v = v / 100; p = true; i++; }
      return new double[]{v, p ? 1 : 0};
    }

    double[] termino() {
      double[] a = primario();
      double v = a[0];
      boolean multi = false;
      while (c() == '*' || c() == '/') {
        char op = t.charAt(i++);
        double[] b = primario();
        multi = true;
        if (op == '*') v = v * b[0];
        else { if (b[0] == 0) throw new Error("div0"); v = v / b[0]; }
      }
      return new double[]{v, (a[1] == 1 && !multi) ? 1 : 0};
    }

    double suma() {
      double v = termino()[0];
      while (c() == '+' || c() == '-') {
        char op = t.charAt(i++);
        double[] b = termino();
        double d = b[1] == 1 ? v * b[0] : b[0];   // 200+10% => 200+20
        v = op == '+' ? v + d : v - d;
      }
      return v;
    }
  }

  static double evaluar(String txt) {
    Ev e = new Ev(txt);
    double r = e.suma();
    if (e.i != txt.length()) throw new Error("fmt");
    return r;
  }

  /**
   * Igual que resStr de la web: Number(n.toPrecision(15)); String(n) de JavaScript, y solo si
   * JavaScript usaría notación científica (|n| < 1e-6) se pasa a toFixed(12) sin ceros finales.
   */
  static String resStr(double n) {
    if (Double.isNaN(n) || Double.isInfinite(n) || Math.abs(n) >= 1e15) throw new Error("big");
    double d = n == 0 ? 0 : new BigDecimal(n).round(new MathContext(15)).doubleValue();
    String s;
    if (d != 0 && Math.abs(d) < 1e-6) {
      s = new BigDecimal(d).setScale(12, java.math.RoundingMode.HALF_UP).toPlainString();
      if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
    } else {
      s = new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();   // repr. más corta, como JS
    }
    if (s.equals("-0") || s.matches("-0(\\.0*)?")) return "0";
    return s;
  }

  /** 1234.5 -> "1.234,5" (punto de miles, coma decimal). */
  static String fmtNum(String t) {
    String[] p = t.split("\\.", -1);
    String e = p[0].replaceAll("\\B(?=(\\d{3})+(?!\\d))", ".");
    if (e.isEmpty()) e = "0";
    return e + (p.length > 1 ? "," + p[1] : "");
  }

  static String fmtExpr(String t) {
    Matcher m = NUM_TXT.matcher(t);
    StringBuffer sb = new StringBuffer();
    while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(fmtNum(m.group())));
    m.appendTail(sb);
    return sb.toString().replace('*', '×').replace('/', '÷').replace('-', '−');
  }

  /** Texto evaluable: sin operador/punto colgando y con los ")" que falten. */
  private String limpio() {
    String t = expr;
    while (!t.isEmpty() && (OPS.indexOf(t.charAt(t.length() - 1)) >= 0 || t.endsWith("."))) t = t.substring(0, t.length() - 1);
    int ab = 0;
    for (char c : t.toCharArray()) { if (c == '(') ab++; else if (c == ')') ab--; }
    StringBuilder b = new StringBuilder(t);
    while (ab-- > 0) b.append(')');
    return b.toString();
  }

  private static boolean tieneOperacion(String t) { return t.replaceFirst("^-", "").matches(".*[-+*/%()].*"); }

  private String vistaPrevia() {
    if (!tieneOperacion(expr)) return "";
    String t = limpio();
    if (t.isEmpty() || t.equals("()")) return "";
    try { return fmtExpr(resStr(evaluar(t))); } catch (RuntimeException e) { return ""; }
  }

  // ------------------------------------------------------------------ teclas
  private void reiniciarSiHecho() { if (done) { done = false; anterior = ""; } }

  private void digito(String d) {
    if (done) { expr = ""; done = false; anterior = ""; }
    String l = ultimo();
    if (l.equals(")") || l.equals("%")) expr += "*";
    String nf = numFinal();
    if (nf.replace(".", "").length() >= 15) return;
    if (nf.equals("0")) expr = expr.substring(0, expr.length() - 1);
    expr += d;
  }

  private void punto() {
    if (done) { expr = ""; done = false; anterior = ""; }
    if (numFinal().contains(".")) return;
    String l = ultimo();
    if (l.equals(")") || l.equals("%")) expr += "*";
    l = ultimo();
    if (!esDig(l) || l.equals(")") || l.equals("%")) expr += "0";
    expr += ".";
  }

  private void operador(String o) {
    reiniciarSiHecho();
    if (expr.isEmpty()) { if (o.equals("-")) expr = "-"; return; }
    String l = ultimo();
    if (l.equals("(")) { if (o.equals("-")) expr += "-"; return; }
    if (expr.equals("-")) return;
    if (OPS.contains(l)) {
      if (o.equals("-") && (l.equals("*") || l.equals("/"))) { expr += "(-"; return; }   // 5×-3 => 5×(-3
      expr = expr.substring(0, expr.length() - 1) + o;
      return;
    }
    if (l.equals(".")) expr = expr.substring(0, expr.length() - 1);
    expr += o;
  }

  private void parentesis() {
    reiniciarSiHecho();
    String l = ultimo();
    if (expr.isEmpty() || OPS.contains(l) || l.equals("(")) expr += "(";
    else if (abiertos() > 0 && (esDig(l) || l.equals(")") || l.equals("%") || l.equals("."))) expr += ")";
    else expr += "*(";
  }

  private void porcentaje() {
    reiniciarSiHecho();
    String l = ultimo();
    if (esDig(l) || l.equals(")") || l.equals("%")) expr += "%";
  }

  private void signo() {
    reiniciarSiHecho();
    Matcher neg = Pattern.compile("\\(-(\\d+\\.?\\d*|\\.\\d+)\\)$").matcher(expr);
    if (neg.find()) { expr = expr.substring(0, neg.start()) + neg.group(1); return; }
    Matcher m = Pattern.compile("(\\d+\\.?\\d*|\\.\\d+)$").matcher(expr);
    if (m.find()) { expr = expr.substring(0, m.start()) + "(-" + m.group(1) + ")"; return; }
    expr += "(-";
  }

  private void atras() {
    reiniciarSiHecho();
    if (!expr.isEmpty()) expr = expr.substring(0, expr.length() - 1);
  }

  private void igual() {
    if (done || expr.isEmpty()) return;
    String t = limpio();
    if (!tieneOperacion(t)) return;   // solo un número: nada que calcular
    try {
      String r = resStr(evaluar(t));
      anterior = fmtExpr(t) + " =";
      expr = r;
      done = true;
    } catch (Error e) {
      msg = e.getMessage().equals("div0") ? "No se puede dividir por cero" : (e.getMessage().equals("big") ? "El resultado es demasiado grande" : "Formato incorrecto");
    }
  }

  /** Teclas: "0".."9", ".", "+", "-", "*", "/", "=", "%", "par", "sign", "back", "c". */
  public void tecla(String k) {
    msg = "";
    if (esDig(k)) digito(k);
    else if (k.equals(".")) punto();
    else if (k.length() == 1 && OPS.contains(k)) operador(k);
    else if (k.equals("=")) igual();
    else if (k.equals("%")) porcentaje();
    else if (k.equals("par")) parentesis();
    else if (k.equals("sign")) signo();
    else if (k.equals("back")) atras();
    else if (k.equals("c")) { expr = ""; anterior = ""; done = false; }
  }

  /** Lo que va en grande. */
  public String pantalla() { return expr.isEmpty() ? "0" : fmtExpr(expr); }

  /** Lo que va abajo en gris (resultado en vivo, la operación anterior, o el error). */
  public String abajo() { return !msg.isEmpty() ? msg : (done ? anterior : vistaPrevia()); }

  public boolean hayError() { return !msg.isEmpty(); }

  /** Valor numérico actual si es un solo número (ej. después de "="); NaN si no. */
  public double valor() {
    String t = done ? expr : limpio();
    try { return evaluar(t.isEmpty() ? "0" : t); } catch (RuntimeException e) { return Double.NaN; }
  }
}
