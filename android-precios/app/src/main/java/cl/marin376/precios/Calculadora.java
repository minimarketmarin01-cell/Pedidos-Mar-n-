package cl.marin376.precios;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Calculadora tipo celular Samsung (modo claro), igual a la "Calculadora normal" de la app web:
 * se escribe la operación completa, abajo se ve el resultado en vivo y "=" lo confirma. Flota a
 * pantalla completa encima de la ventana actual; al cerrarla (✕ o Atrás) se vuelve exactamente
 * a lo que se estaba haciendo. La lógica está en CalcMotor (probada contra la de la web).
 */
public final class Calculadora {
  private static final int VERDE_AGUA = Color.parseColor("#12A88A"), TECLA = Color.parseColor("#F2F2F4"),
      GRIS = Color.parseColor("#8A8A8F"), NEGRO = Color.parseColor("#111111"), ROJO = Color.parseColor("#D93025");

  /** Al tocar "Usar este número" (opcional): recibe el resultado. */
  public interface AlUsar { void usar(double valor); }

  private Calculadora() {}

  public static void abrir(final Activity a, final String textoUsar, final AlUsar alUsar) {
    final float den = a.getResources().getDisplayMetrics().density;
    final CalcMotor m = new CalcMotor();
    final Dialog d = new Dialog(a, android.R.style.Theme_Material_Light_NoActionBar);
    d.requestWindowFeature(Window.FEATURE_NO_TITLE);

    LinearLayout raiz = new LinearLayout(a);
    raiz.setOrientation(LinearLayout.VERTICAL);
    raiz.setBackgroundColor(Color.WHITE);
    raiz.setPadding(px(den, 14), px(den, 10), px(den, 14), px(den, 16));

    // Arriba: "Calculadora" y ✕
    LinearLayout top = new LinearLayout(a);
    top.setGravity(Gravity.CENTER_VERTICAL);
    TextView titulo = new TextView(a);
    titulo.setText("Calculadora");
    titulo.setTextSize(16);
    titulo.setTextColor(Color.parseColor("#6B6B6B"));
    titulo.setTypeface(Typeface.DEFAULT_BOLD);
    top.addView(titulo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    Button cerrar = redondo(a, "✕", Color.parseColor("#F0F0F2"), NEGRO, 20);
    cerrar.setOnClickListener(v -> d.dismiss());
    top.addView(cerrar, new LinearLayout.LayoutParams(px(den, 46), px(den, 46)));
    raiz.addView(top);

    // Pantalla: operación grande a la derecha + resultado en gris debajo
    final TextView expr = new TextView(a);
    expr.setGravity(Gravity.END | Gravity.BOTTOM);
    expr.setTextColor(NEGRO);
    LinearLayout.LayoutParams pe = new LinearLayout.LayoutParams(-1, 0, 1);
    raiz.addView(expr, pe);
    final TextView abajo = new TextView(a);
    abajo.setGravity(Gravity.END);
    abajo.setTextSize(28);
    abajo.setTextColor(GRIS);
    abajo.setMinHeight(px(den, 40));
    raiz.addView(abajo, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

    // Barra: "Usar este número" (si corresponde) + borrar
    LinearLayout barra = new LinearLayout(a);
    barra.setGravity(Gravity.CENTER_VERTICAL);
    final Button usar = new Button(a);
    usar.setAllCaps(false);
    usar.setTextSize(17);
    usar.setTypeface(Typeface.DEFAULT_BOLD);
    usar.setTextColor(Color.WHITE);
    GradientDrawable gu = new GradientDrawable();
    gu.setColor(VERDE_AGUA);
    gu.setCornerRadius(px(den, 22));
    usar.setBackground(gu);
    usar.setPadding(px(den, 16), 0, px(den, 16), 0);
    usar.setVisibility(alUsar == null ? android.view.View.GONE : android.view.View.VISIBLE);
    barra.addView(usar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(den, 46)));
    barra.addView(new android.view.View(a), new LinearLayout.LayoutParams(0, 1, 1));
    Button borrar = new Button(a);
    borrar.setText("⌫");
    borrar.setTextSize(28);
    borrar.setTextColor(VERDE_AGUA);
    borrar.setBackground(null);
    barra.addView(borrar, new LinearLayout.LayoutParams(px(den, 72), px(den, 56)));
    raiz.addView(barra, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

    android.view.View linea = new android.view.View(a);
    linea.setBackgroundColor(Color.parseColor("#ECECEF"));
    LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, px(den, 1));
    pl.bottomMargin = px(den, 10);
    raiz.addView(linea, pl);

    final Runnable pintar = () -> {
      String t = m.pantalla();
      int n = t.length();
      expr.setTextSize(n <= 7 ? 60 : n <= 10 ? 50 : n <= 14 ? 40 : n <= 20 ? 32 : 26);
      expr.setText(t);
      abajo.setText(m.abajo());
      abajo.setTextColor(m.hayError() ? ROJO : GRIS);
      abajo.setTextSize(m.hayError() ? 20 : 28);
      if (alUsar != null) {
        double v = m.valor();
        boolean ok = !Double.isNaN(v) && !Double.isInfinite(v) && v > 0;
        usar.setEnabled(ok);
        usar.setAlpha(ok ? 1f : 0.4f);
        usar.setText(textoUsar + (ok ? ": " + CalcMotor.fmtExpr(CalcMotor.resStr(Math.round(v * 100) / 100.0)) : ""));
      }
    };
    borrar.setOnClickListener(v -> { m.tecla("back"); pintar.run(); });
    usar.setOnClickListener(v -> {
      double val = m.valor();
      if (Double.isNaN(val) || val <= 0) return;
      d.dismiss();
      alUsar.usar(val);
    });

    // Teclado 5 × 4 (mismo orden que la web / Samsung)
    String[][] filas = {
      {"C", "c"}, {"( )", "par"}, {"%", "%"}, {"÷", "/"},
      {"7", "7"}, {"8", "8"}, {"9", "9"}, {"×", "*"},
      {"4", "4"}, {"5", "5"}, {"6", "6"}, {"−", "-"},
      {"1", "1"}, {"2", "2"}, {"3", "3"}, {"+", "+"},
      {"+/−", "sign"}, {"0", "0"}, {",", "."}, {"=", "="},
    };
    String funciones = "c par % / * - +";
    LinearLayout fila = null;
    for (int i = 0; i < filas.length; i++) {
      if (i % 4 == 0) {
        fila = new LinearLayout(a);
        LinearLayout.LayoutParams pf = new LinearLayout.LayoutParams(-1, px(den, 70));
        pf.topMargin = i == 0 ? 0 : px(den, 10);
        raiz.addView(fila, pf);
      }
      final String k = filas[i][1];
      boolean eq = k.equals("=");
      boolean fn = (" " + funciones + " ").contains(" " + k + " ");
      Button b = redondo(a, filas[i][0], eq ? VERDE_AGUA : TECLA, eq ? Color.WHITE : (fn ? VERDE_AGUA : NEGRO), k.equals("sign") ? 24 : 30);
      b.setOnClickListener(v -> { m.tecla(k); pintar.run(); });
      LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, -1, 1);
      if (i % 4 != 0) pb.leftMargin = px(den, 10);
      fila.addView(b, pb);
    }

    // Teclado físico (si el POS tiene uno)
    d.setOnKeyListener((dlg, code, ev) -> {
      if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
      String k = null;
      int u = ev.getUnicodeChar();
      if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) k = String.valueOf(code - KeyEvent.KEYCODE_0);
      else if (code == KeyEvent.KEYCODE_DEL) k = "back";
      else if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_EQUALS || code == KeyEvent.KEYCODE_NUMPAD_ENTER) k = "=";
      else if (u == '+' || u == '-' || u == '*' || u == '/' || u == '%') k = String.valueOf((char) u);
      else if (u == '.' || u == ',') k = ".";
      else if (u == '(' || u == ')') k = "par";
      if (k == null) return false;
      m.tecla(k);
      pintar.run();
      return true;
    });

    d.setContentView(raiz);
    pintar.run();
    d.show();
  }

  private static int px(float den, int dp) { return Math.round(dp * den); }

  private static Button redondo(Activity a, String t, int fondo, int color, int sp) {
    Button b = new Button(a);
    b.setText(t);
    b.setAllCaps(false);
    b.setTextSize(sp);
    b.setTextColor(color);
    b.setPadding(0, 0, 0, 0);
    GradientDrawable g = new GradientDrawable();
    g.setColor(fondo);
    g.setCornerRadius(999);
    b.setBackground(g);
    b.setStateListAnimator(null);
    return b;
  }
}
