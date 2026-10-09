package cl.marin376.etiquetas;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pantalla única: elegir ensalada -> cantidad -> Imprimir. Pensada para letra grande y pocos toques. */
public class MainActivity extends Activity {
  private static final int VERDE = Color.parseColor("#1B7F5C"), GRIS = Color.parseColor("#E6E6E6"),
      ROJO = Color.parseColor("#B3261E"), NEGRO = Color.parseColor("#111111");

  static class Sel { Producto p; int cant = 1; String nombre = "", peso = "200 g"; }

  private final Handler ui = new Handler(Looper.getMainLooper());
  private SharedPreferences prefs;
  private Ajustes aj;
  private List<Producto> productos = new ArrayList<>();
  private final List<Sel> sel = new ArrayList<>();
  private final Calendar elab = Calendar.getInstance();
  private int venceDias = 0;
  private boolean imprimiendo = false, creando = false;

  private TextView txtImpresora, txtEstado, txtAviso;
  private EditText buscador;
  private LinearLayout cajaFichas, cajaSel, cajaVence;
  private TextView txtElab;
  private ImageView vista;
  private Button btnImprimir;

  private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

  private Button boton(String t, int color, int txt) {
    Button b = new Button(this);
    b.setText(t);
    b.setAllCaps(false);
    b.setTextSize(20);
    b.setTypeface(Typeface.DEFAULT_BOLD);
    b.setTextColor(txt);
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(12));
    b.setBackground(g);
    b.setPadding(dp(12), dp(10), dp(12), dp(10));
    return b;
  }

  private LinearLayout.LayoutParams lp(int w, int h, int mt) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
    p.topMargin = dp(mt);
    return p;
  }

  private TextView texto(String t, int sp, boolean negrita, int color) {
    TextView v = new TextView(this);
    v.setText(t);
    v.setTextSize(sp);
    v.setTextColor(color);
    if (negrita) v.setTypeface(Typeface.DEFAULT_BOLD);
    return v;
  }

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("etiquetas", MODE_PRIVATE);
    aj = Ajustes.cargar(this);
    cargarProductosLocal();
    construir();
    pintarTodo();
    if (productos.isEmpty() || System.currentTimeMillis() - prefs.getLong("catalogoHora", 0) > 12L * 3600 * 1000) actualizarProductos(false);
  }

  // ---------------------------------------------------------------- pantalla
  private void construir() {
    ScrollView sv = new ScrollView(this);
    sv.setBackgroundColor(Color.WHITE);
    LinearLayout raiz = new LinearLayout(this);
    raiz.setOrientation(LinearLayout.VERTICAL);
    raiz.setPadding(dp(16), dp(14), dp(16), dp(30));
    sv.addView(raiz);

    raiz.addView(texto("Etiquetas de ensaladas", 28, true, NEGRO));

    LinearLayout filaImp = new LinearLayout(this);
    filaImp.setOrientation(LinearLayout.HORIZONTAL);
    filaImp.setGravity(Gravity.CENTER_VERTICAL);
    txtImpresora = texto("", 18, false, NEGRO);
    filaImp.addView(txtImpresora, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    Button bImp = boton("Impresora", GRIS, NEGRO);
    bImp.setTextSize(17);
    bImp.setOnClickListener(v -> elegirImpresora());
    filaImp.addView(bImp);
    Button bAj = boton("Ajustes", GRIS, NEGRO);
    bAj.setTextSize(17);
    bAj.setOnClickListener(v -> abrirAjustes());
    LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    pa.leftMargin = dp(8);
    filaImp.addView(bAj, pa);
    raiz.addView(filaImp, lp(-1, -2, 10));

    raiz.addView(texto("1. Elige la ensalada", 22, true, NEGRO), lp(-1, -2, 18));
    buscador = new EditText(this);
    buscador.setHint("Escribe: lechuga, repollo, espinaca");
    buscador.setTextSize(22);
    buscador.setSingleLine(true);
    buscador.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    buscador.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable e) { pintarFichas(); }
    });
    raiz.addView(buscador, lp(-1, -2, 6));
    cajaFichas = new LinearLayout(this);
    cajaFichas.setOrientation(LinearLayout.VERTICAL);
    raiz.addView(cajaFichas, lp(-1, -2, 6));

    raiz.addView(texto("2. Cuántas etiquetas", 22, true, NEGRO), lp(-1, -2, 18));
    cajaSel = new LinearLayout(this);
    cajaSel.setOrientation(LinearLayout.VERTICAL);
    raiz.addView(cajaSel, lp(-1, -2, 6));

    raiz.addView(texto("3. Fechas", 22, true, NEGRO), lp(-1, -2, 18));
    LinearLayout filaElab = new LinearLayout(this);
    filaElab.setOrientation(LinearLayout.HORIZONTAL);
    filaElab.setGravity(Gravity.CENTER_VERTICAL);
    Button menos = boton("‹", GRIS, NEGRO);
    menos.setOnClickListener(v -> { elab.add(Calendar.DAY_OF_MONTH, -1); pintarTodo(); });
    Button mas = boton("›", GRIS, NEGRO);
    mas.setOnClickListener(v -> { elab.add(Calendar.DAY_OF_MONTH, 1); pintarTodo(); });
    txtElab = texto("", 22, true, NEGRO);
    txtElab.setGravity(Gravity.CENTER);
    filaElab.addView(menos, new LinearLayout.LayoutParams(dp(64), dp(60)));
    filaElab.addView(txtElab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    filaElab.addView(mas, new LinearLayout.LayoutParams(dp(64), dp(60)));
    raiz.addView(filaElab, lp(-1, -2, 6));
    raiz.addView(texto("Vence (opcional):", 18, false, NEGRO), lp(-1, -2, 10));
    cajaVence = new LinearLayout(this);
    cajaVence.setOrientation(LinearLayout.HORIZONTAL);
    raiz.addView(cajaVence, lp(-1, -2, 4));

    raiz.addView(texto("Así queda la etiqueta", 18, true, NEGRO), lp(-1, -2, 18));
    vista = new ImageView(this);
    vista.setAdjustViewBounds(true);
    vista.setBackgroundColor(Color.parseColor("#DDDDDD"));
    vista.setPadding(dp(6), dp(6), dp(6), dp(6));
    raiz.addView(vista, lp(-1, -2, 6));

    txtAviso = texto("", 18, true, ROJO);
    raiz.addView(txtAviso, lp(-1, -2, 12));
    btnImprimir = boton("Imprimir etiquetas", VERDE, Color.WHITE);
    btnImprimir.setTextSize(26);
    btnImprimir.setOnClickListener(v -> imprimir());
    raiz.addView(btnImprimir, lp(-1, dp(84), 6));
    txtEstado = texto("", 18, false, NEGRO);
    raiz.addView(txtEstado, lp(-1, -2, 10));

    Button bAct = boton("Actualizar productos", GRIS, NEGRO);
    bAct.setTextSize(17);
    bAct.setOnClickListener(v -> actualizarProductos(true));
    raiz.addView(bAct, lp(-1, -2, 20));
    Button bPrueba = boton("Imprimir etiqueta de prueba", GRIS, NEGRO);
    bPrueba.setTextSize(17);
    bPrueba.setOnClickListener(v -> imprimirPrueba());
    raiz.addView(bPrueba, lp(-1, -2, 8));

    setContentView(sv);
  }

  // ---------------------------------------------------------------- datos
  private static String norm(String s) {
    String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return n.toLowerCase(Locale.ROOT).trim();
  }

  private static String limpiarNombre(String n) { return n == null ? "" : n.replaceAll("\\s+\\d{3,5}\\s*$", "").trim(); }

  private void cargarProductosLocal() {
    productos = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs.getString("catalogo", "[]"));
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        Producto p = new Producto();
        p.sku = o.optString("s");
        p.nombre = o.optString("n");
        p.barcode = o.optString("b");
        p.precio = o.optInt("p");
        productos.add(p);
      }
    } catch (Exception ignore) {}
  }

  private void guardarProductosLocal() {
    try {
      JSONArray a = new JSONArray();
      for (Producto p : productos) a.put(new JSONObject().put("s", p.sku).put("n", p.nombre).put("b", p.barcode).put("p", p.precio));
      prefs.edit().putString("catalogo", a.toString()).putLong("catalogoHora", System.currentTimeMillis()).apply();
    } catch (Exception ignore) {}
  }

  private Producto porSku(String sku) {
    for (Producto p : productos) if (p.sku.equals(sku)) return p;
    return null;
  }

  private void actualizarProductos(final boolean avisar) {
    txtEstado.setText("Actualizando productos…");
    new Thread(() -> {
      try {
        final List<Producto> nuevos = Api.productos();
        ui.post(() -> {
          productos = nuevos;
          guardarProductosLocal();
          for (Sel s : sel) { Producto p = porSku(s.p.sku); if (p != null) s.p = p; }
          txtEstado.setText("Productos actualizados (" + nuevos.size() + ").");
          pintarTodo();
        });
      } catch (final Exception e) {
        ui.post(() -> txtEstado.setText(productos.isEmpty()
            ? "No se pudo bajar la lista de productos. Revisa el internet y toca «Actualizar productos»."
            : (avisar ? "Sin internet: se usa la lista guardada." : "")));
      }
    }).start();
  }

  // ---------------------------------------------------------------- recientes
  private JSONArray recientes() {
    try { return new JSONArray(prefs.getString("recientes", "[]")); } catch (Exception e) { return new JSONArray(); }
  }

  private JSONObject recienteDe(String sku) {
    JSONArray a = recientes();
    for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null && sku.equals(o.optString("s"))) return o; }
    return null;
  }

  private void guardarRecientes() {
    try {
      JSONArray a = recientes();
      List<JSONObject> lista = new ArrayList<>();
      for (int i = sel.size() - 1; i >= 0; i--) {
        Sel s = sel.get(i);
        lista.add(new JSONObject().put("s", s.p.sku).put("c", s.cant).put("n", s.nombre).put("w", s.peso));
      }
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.optJSONObject(i);
        boolean ya = false;
        for (JSONObject l : lista) if (l.optString("s").equals(o.optString("s"))) ya = true;
        if (!ya) lista.add(o);
      }
      JSONArray out = new JSONArray();
      for (int i = 0; i < Math.min(8, lista.size()); i++) out.put(lista.get(i));
      prefs.edit().putString("recientes", out.toString()).apply();
    } catch (Exception ignore) {}
  }

  // ---------------------------------------------------------------- pintar
  private void pintarTodo() {
    txtImpresora.setText(aj.mac.isEmpty() ? "Impresora: sin elegir" : "Impresora: " + (aj.nombreImpresora.isEmpty() ? aj.mac : aj.nombreImpresora));
    txtElab.setText("Elaboración: " + fmt(elab));
    pintarFichas();
    pintarSel();
    pintarVence();
    pintarVista();
    validar();
  }

  private static String fmt(Calendar c) {
    return String.format(Locale.ROOT, "%02d/%02d/%04d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
  }

  private String venceTxt() {
    if (venceDias <= 0) return "";
    Calendar c = (Calendar) elab.clone();
    c.add(Calendar.DAY_OF_MONTH, venceDias);
    return fmt(c);
  }

  private static String precioTxt(int p) {
    if (p <= 0) return "";
    return "$" + String.format(Locale.ROOT, "%,d", p).replace(',', '.');
  }

  private List<Producto> fichasData() {
    String q = norm(buscador == null ? "" : buscador.getText().toString());
    List<Producto> out = new ArrayList<>();
    if (q.length() >= 2) {
      String[] pal = q.split("\\s+");
      for (Producto p : productos) {
        String h = norm(p.nombre + " " + p.sku + " " + p.barcode);
        boolean ok = true;
        for (String w : pal) if (!h.contains(w)) { ok = false; break; }
        if (ok) out.add(p);
        if (out.size() >= 8) break;
      }
      return out;
    }
    JSONArray rec = recientes();
    for (int i = 0; i < rec.length() && out.size() < 8; i++) {
      Producto p = porSku(rec.optJSONObject(i).optString("s"));
      if (p != null) out.add(p);
    }
    if (!out.isEmpty()) return out;
    for (Producto p : productos) {
      if (norm(p.nombre).contains("ensalada")) out.add(p);
      if (out.size() >= 8) break;
    }
    return out;
  }

  private void pintarFichas() {
    if (cajaFichas == null) return;
    cajaFichas.removeAllViews();
    List<Producto> lista = fichasData();
    boolean buscando = norm(buscador.getText().toString()).length() >= 2;
    if (lista.isEmpty()) {
      cajaFichas.addView(texto(buscando ? "No encontré esa ensalada. Prueba con otra palabra." : (productos.isEmpty() ? "Cargando productos…" : "Escribe arriba el nombre de la ensalada."), 18, false, NEGRO));
      return;
    }
    if (!buscando && recientes().length() > 0) cajaFichas.addView(texto("Las que usaste antes:", 18, false, NEGRO));
    for (final Producto p : lista) {
      boolean on = false;
      for (Sel s : sel) if (s.p.sku.equals(p.sku)) on = true;
      Button b = boton(limpiarNombre(p.nombre) + (on ? "   ✓ agregada" : ""), on ? Color.parseColor("#CFE8DC") : GRIS, NEGRO);
      b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      b.setOnClickListener(v -> agregar(p));
      cajaFichas.addView(b, lp(-1, dp(64), 6));
    }
  }

  private void agregar(Producto p) {
    for (Sel s : sel) if (s.p.sku.equals(p.sku)) { s.cant = Math.min(50, s.cant + 1); pintarTodo(); return; }
    Sel s = new Sel();
    s.p = p;
    s.nombre = limpiarNombre(p.nombre);
    JSONObject r = recienteDe(p.sku);
    if (r != null) { s.cant = Math.max(1, Math.min(50, r.optInt("c", 1))); s.nombre = r.optString("n", s.nombre); s.peso = r.optString("w", s.peso); }
    sel.add(s);
    pintarTodo();
    final Sel fs = s;
    new Thread(() -> {   // confirma el código con el servidor para no imprimir uno desactualizado
      try {
        final String real = Api.codigoActual(fs.p.sku);
        if (real != null && !real.equals(fs.p.barcode)) ui.post(() -> { fs.p.barcode = real; guardarProductosLocal(); pintarTodo(); });
      } catch (Exception ignore) {}
    }).start();
  }

  private void pintarSel() {
    cajaSel.removeAllViews();
    if (sel.isEmpty()) { cajaSel.addView(texto("Todavía no elegiste ninguna ensalada.", 18, false, NEGRO)); return; }
    for (final Sel s : new ArrayList<>(sel)) {
      LinearLayout fila = new LinearLayout(this);
      fila.setOrientation(LinearLayout.VERTICAL);
      fila.setPadding(dp(12), dp(10), dp(12), dp(12));
      GradientDrawable g = new GradientDrawable();
      g.setColor(Color.parseColor("#F4F4F4"));
      g.setCornerRadius(dp(12));
      fila.setBackground(g);

      LinearLayout top = new LinearLayout(this);
      top.setOrientation(LinearLayout.HORIZONTAL);
      top.setGravity(Gravity.CENTER_VERTICAL);
      top.addView(texto(s.nombre, 22, true, NEGRO), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
      Button q = boton("Quitar", GRIS, ROJO);
      q.setTextSize(16);
      q.setOnClickListener(v -> { sel.remove(s); pintarTodo(); });
      top.addView(q);
      fila.addView(top);

      LinearLayout cant = new LinearLayout(this);
      cant.setOrientation(LinearLayout.HORIZONTAL);
      cant.setGravity(Gravity.CENTER_VERTICAL);
      Button m = boton("−", GRIS, NEGRO);
      m.setTextSize(28);
      m.setOnClickListener(v -> { s.cant = Math.max(1, s.cant - 1); pintarTodo(); });
      TextView n = texto(String.valueOf(s.cant), 30, true, NEGRO);
      n.setGravity(Gravity.CENTER);
      Button pl = boton("+", GRIS, NEGRO);
      pl.setTextSize(28);
      pl.setOnClickListener(v -> { s.cant = Math.min(50, s.cant + 1); pintarTodo(); });
      cant.addView(m, new LinearLayout.LayoutParams(dp(72), dp(64)));
      cant.addView(n, new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT));
      cant.addView(pl, new LinearLayout.LayoutParams(dp(72), dp(64)));
      cant.addView(texto("  etiquetas", 18, false, NEGRO));
      fila.addView(cant, lp(-1, -2, 8));

      if (Code128.modules(s.p.barcode) == null) {
        fila.addView(texto("Esta ensalada todavía no tiene código de barras.", 17, true, ROJO), lp(-1, -2, 8));
        Button cr = boton(creando ? "Creando…" : "Crear código", VERDE, Color.WHITE);
        cr.setEnabled(!creando);
        cr.setOnClickListener(v -> crearCodigo(s));
        fila.addView(cr, lp(-1, dp(60), 6));
      } else {
        fila.addView(texto("Código: " + s.p.barcode, 16, false, NEGRO), lp(-1, -2, 6));
      }
      cajaSel.addView(fila, lp(-1, -2, 10));
    }
  }

  private void pintarVence() {
    cajaVence.removeAllViews();
    final int[] dias = {0, 2, 3, 4};
    String[] txt = {"Sin\nvence", "+2\ndías", "+3\ndías", "+4\ndías"};
    for (int i = 0; i < 4; i++) {
      final int d = dias[i];
      boolean on = venceDias == d;
      Button b = boton(txt[i], on ? VERDE : GRIS, on ? Color.WHITE : NEGRO);
      b.setTextSize(17);
      b.setOnClickListener(v -> { venceDias = d; pintarTodo(); });
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(72), 1);
      p.rightMargin = dp(6);
      cajaVence.addView(b, p);
    }
    if (venceDias > 0) {
      // (la fecha de vencimiento se ve en la vista previa)
    }
  }

  private Etiqueta etiquetaDe(Sel s) {
    Etiqueta e = new Etiqueta();
    e.nombre = s.nombre;
    e.peso = s.peso;
    e.precio = precioTxt(s.p.precio);
    e.codigo = s.p.barcode;
    e.elab = fmt(elab);
    e.vence = venceTxt();
    return e;
  }

  private void pintarVista() {
    Sel primera = null;
    for (Sel s : sel) if (Code128.modules(s.p.barcode) != null) { primera = s; break; }
    if (primera == null) { vista.setImageBitmap(null); vista.setMinimumHeight(0); return; }
    vista.setImageBitmap(LabelRenderer.dibujar(etiquetaDe(primera), aj.anchoPuntos, aj.altoPuntos()));
  }

  private int totalEtiquetas() { int t = 0; for (Sel s : sel) t += s.cant; return t; }

  private String motivo() {
    if (sel.isEmpty()) return "Elige al menos una ensalada.";
    List<String> sin = new ArrayList<>();
    for (Sel s : sel) if (Code128.modules(s.p.barcode) == null) sin.add(s.nombre);
    if (!sin.isEmpty()) return "Falta crear el código de: " + android.text.TextUtils.join(", ", sin) + ".";
    if (aj.mac.isEmpty()) return "Falta elegir la impresora (botón «Impresora»).";
    return "";
  }

  private void validar() {
    String m = motivo();
    txtAviso.setText(m);
    btnImprimir.setEnabled(m.isEmpty() && !imprimiendo);
    btnImprimir.setAlpha(btnImprimir.isEnabled() ? 1f : 0.45f);
    int t = totalEtiquetas();
    btnImprimir.setText(imprimiendo ? "Imprimiendo…" : (t > 0 && m.isEmpty() ? "Imprimir " + t + " etiqueta" + (t == 1 ? "" : "s") : "Imprimir etiquetas"));
  }

  // ---------------------------------------------------------------- crear código
  private String generarCodigo() {
    java.util.HashSet<String> usados = new java.util.HashSet<>();
    for (Producto p : productos) if (p.barcode != null && !p.barcode.isEmpty()) usados.add(p.barcode.trim());
    Random r = new Random();
    for (int k = 0; k < 200; k++) {
      StringBuilder c = new StringBuilder("04");
      for (int i = 0; i < 10; i++) c.append(r.nextInt(10));
      int suma = 0;
      for (int i = 0; i < 12; i++) { int d = c.charAt(i) - '0'; suma += (i % 2 == 0) ? d : d * 3; }
      c.append((10 - (suma % 10)) % 10);
      if (!usados.contains(c.toString())) return c.toString();
    }
    return null;
  }

  private void crearCodigo(final Sel s) {
    if (creando) return;
    final String codigo = generarCodigo();
    if (codigo == null) { Toast.makeText(this, "No se pudo crear un código. Intenta de nuevo.", Toast.LENGTH_LONG).show(); return; }
    creando = true;
    pintarTodo();
    new Thread(() -> {
      String err = null;
      try { Api.guardarCodigo(s.p.sku, codigo); } catch (Exception e) { err = e.getMessage() == null ? "Error de conexión" : e.getMessage(); }
      final String fe = err;
      ui.post(() -> {
        creando = false;
        if (fe == null) { s.p.barcode = codigo; guardarProductosLocal(); txtEstado.setText("Código creado: " + codigo); }
        else Toast.makeText(MainActivity.this, fe, Toast.LENGTH_LONG).show();
        pintarTodo();
      });
    }).start();
  }

  // ---------------------------------------------------------------- impresión
  private byte[] bytesDe(Etiqueta e) {
    Bitmap bmp = LabelRenderer.dibujar(e, aj.anchoPuntos, aj.altoPuntos());
    byte[] bits = LabelRenderer.aBits(bmp);
    if (aj.modo == Ajustes.MODO_TSPL) return Raster.tspl(bits, aj.anchoPuntos, aj.altoPuntos(), aj.anchoMm, aj.altoMm, aj.gapMm, aj.invertir);
    return Raster.escpos(bits, aj.anchoPuntos, aj.altoPuntos(), aj.avance);
  }

  private void imprimir() {
    if (imprimiendo || !motivo().isEmpty()) return;
    final List<Etiqueta> lista = new ArrayList<>();
    for (Sel s : sel) for (int i = 0; i < s.cant; i++) lista.add(etiquetaDe(s));
    guardarRecientes();
    enviar(lista, "Listo: se imprimieron " + lista.size() + " etiqueta" + (lista.size() == 1 ? "" : "s") + ".");
  }

  private void imprimirPrueba() {
    if (aj.mac.isEmpty()) { Toast.makeText(this, "Primero elige la impresora.", Toast.LENGTH_LONG).show(); return; }
    Etiqueta e = new Etiqueta();
    e.nombre = "Ensalada de prueba";
    e.peso = "200 g";
    e.precio = "$1.000";
    e.codigo = "0412345678905";
    e.elab = fmt(elab);
    e.vence = venceTxt();
    List<Etiqueta> l = new ArrayList<>();
    l.add(e);
    enviar(l, "Etiqueta de prueba enviada.");
  }

  private void enviar(final List<Etiqueta> lista, final String ok) {
    imprimiendo = true;
    validar();
    txtEstado.setText("Conectando con la impresora…");
    new Thread(() -> {
      String err = null;
      try (Impresora imp = new Impresora(aj.mac)) {
        ui.post(() -> txtEstado.setText("Imprimiendo…"));
        for (Etiqueta e : lista) imp.enviar(bytesDe(e));
      } catch (Exception e) {
        err = e.getMessage() == null ? "No se pudo imprimir." : e.getMessage();
      }
      final String fe = err;
      ui.post(() -> {
        imprimiendo = false;
        txtEstado.setText(fe == null ? ok : fe);
        txtEstado.setTextColor(fe == null ? VERDE : ROJO);
        validar();
      });
    }).start();
  }

  // ---------------------------------------------------------------- impresora y ajustes
  private void elegirImpresora() {
    BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
    if (ad == null) { Toast.makeText(this, "Este equipo no tiene Bluetooth.", Toast.LENGTH_LONG).show(); return; }
    if (!ad.isEnabled()) { Toast.makeText(this, "Enciende el Bluetooth del equipo y vuelve a tocar «Impresora».", Toast.LENGTH_LONG).show(); return; }
    Set<BluetoothDevice> pares = ad.getBondedDevices();
    final List<BluetoothDevice> devs = new ArrayList<>(pares);
    if (devs.isEmpty()) {
      new AlertDialog.Builder(this).setTitle("No hay impresoras emparejadas")
          .setMessage("Primero empareja la impresora en Ajustes del equipo → Bluetooth (clave habitual: 0000 o 1234). Luego vuelve aquí.")
          .setPositiveButton("Entendido", null).show();
      return;
    }
    String[] nombres = new String[devs.size()];
    for (int i = 0; i < nombres.length; i++) nombres[i] = (devs.get(i).getName() == null ? "(sin nombre)" : devs.get(i).getName()) + "\n" + devs.get(i).getAddress();
    new AlertDialog.Builder(this).setTitle("Elige tu impresora")
        .setItems(nombres, (d, i) -> {
          aj.mac = devs.get(i).getAddress();
          aj.nombreImpresora = devs.get(i).getName() == null ? "" : devs.get(i).getName();
          aj.guardar(this);
          pintarTodo();
        }).setNegativeButton("Cancelar", null).show();
  }

  private EditText campoNum(LinearLayout cont, String rotulo, int valor) {
    cont.addView(texto(rotulo, 16, false, NEGRO), lp(-1, -2, 8));
    EditText e = new EditText(this);
    e.setInputType(InputType.TYPE_CLASS_NUMBER);
    e.setText(String.valueOf(valor));
    e.setTextSize(20);
    cont.addView(e);
    return e;
  }

  private static int num(EditText e, int def, int min, int max) {
    try { return Math.max(min, Math.min(max, Integer.parseInt(e.getText().toString().trim()))); } catch (Exception x) { return def; }
  }

  private void abrirAjustes() {
    ScrollView sv = new ScrollView(this);
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(18), dp(8), dp(18), dp(8));
    sv.addView(c);
    c.addView(texto("Lenguaje de la impresora", 16, false, NEGRO));
    final Spinner modo = new Spinner(this);
    modo.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"ESC/POS (la mayoría de 58 mm)", "TSPL (impresoras de etiquetas)"}));
    modo.setSelection(aj.modo);
    c.addView(modo);
    final EditText ancho = campoNum(c, "Ancho imprimible en puntos (384 es lo normal en 58 mm; prueba 448 o 464 si queda corto)", aj.anchoPuntos);
    final EditText alto = campoNum(c, "Alto de la etiqueta en mm", aj.altoMm);
    c.addView(texto("Al terminar cada etiqueta (ESC/POS)", 16, false, NEGRO), lp(-1, -2, 8));
    final Spinner av = new Spinner(this);
    av.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"No avanzar", "Avanzar a la siguiente etiqueta (FF)", "Avanzar a la siguiente etiqueta (GS FF)", "Avanzar 5 mm"}));
    av.setSelection(aj.avance);
    c.addView(av);
    final EditText anchoMm = campoNum(c, "TSPL: ancho de la etiqueta en mm", aj.anchoMm);
    final EditText gap = campoNum(c, "TSPL: separación entre etiquetas en mm", aj.gapMm);
    final CheckBox inv = new CheckBox(this);
    inv.setText("TSPL: invertir colores (si sale todo negro)");
    inv.setChecked(aj.invertir);
    c.addView(inv, lp(-1, -2, 8));
    new AlertDialog.Builder(this).setTitle("Ajustes de impresión").setView(sv)
        .setPositiveButton("Guardar", (d, w) -> {
          aj.modo = modo.getSelectedItemPosition();
          aj.anchoPuntos = num(ancho, 384, 200, 832);
          aj.altoMm = num(alto, 40, 15, 100);
          aj.avance = av.getSelectedItemPosition();
          aj.anchoMm = num(anchoMm, 58, 20, 110);
          aj.gapMm = num(gap, 3, 0, 10);
          aj.invertir = inv.isChecked();
          aj.guardar(this);
          pintarTodo();
        }).setNegativeButton("Cancelar", null).show();
  }
}
