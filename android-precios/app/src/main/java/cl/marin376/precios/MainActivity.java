package cl.marin376.precios;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla igual a la venta de Loyverse: barra verde, categoría arriba (o buscar), lista con foto,
 * nombre y precio. Tocar un producto abre "Imprimir etiqueta" con cantidad y vencimiento.
 */
public class MainActivity extends Activity {
  private static final int VERDE_BARRA = Color.parseColor("#4CAF50"), VERDE = Color.parseColor("#43A047"),
      VERDE_BOTON = Color.parseColor("#7CB342"), GRIS = Color.parseColor("#E6E6E6"), GRIS_FOTO = Color.parseColor("#E0E0E0"),
      LINEA = Color.parseColor("#DDDDDD"), ROJO = Color.parseColor("#B3261E"), NEGRO = Color.parseColor("#212121"),
      PLOMO = Color.parseColor("#616161"), AVISO = Color.parseColor("#FFF8E1");
  private static final long VIEJO_MS = 6L * 3600 * 1000;   // se actualiza sola si la lista tiene más de 6 h
  private static final String TODOS = "Todos los artículos";

  private final Handler ui = new Handler(Looper.getMainLooper());
  private SharedPreferences prefs;
  private Ajustes aj;
  private Catalogo cat;
  private Imagenes fotos;
  private boolean cargandoLista = false, bajando = false, imprimiendo = false, buscando = false;
  private List<Producto> visibles = new ArrayList<>();

  private TextView txtTitulo, txtMensaje, txtVacio;
  private TextView spCategoria;   // selector de categoría (abre una lista grande)
  private String categoria = null; // null = todos los artículos
  private EditText buscador;
  private Button btnBuscar;
  private ListView lista;
  private final Adaptador adaptador = new Adaptador();
  private final Runnable buscarLuego = this::filtrar;

  // ---------------------------------------------------------------- utilidades de vista
  private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

  private Button boton(String t, int color, int txt) {
    Button b = new Button(this);
    b.setText(t);
    b.setAllCaps(false);
    b.setTextSize(20);
    b.setTypeface(Typeface.DEFAULT_BOLD);
    b.setTextColor(txt);
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(dp(6));
    b.setBackground(g);
    b.setPadding(dp(10), dp(6), dp(10), dp(6));
    return b;
  }

  private void colorear(Button b, int color, int txt) {
    ((GradientDrawable) b.getBackground()).setColor(color);
    b.setTextColor(txt);
  }

  private LinearLayout.LayoutParams lp(int w, int h, int mt) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
    p.topMargin = dp(mt);
    return p;
  }

  /** Rueda girando (centrada) para las esperas largas; arranca oculta. */
  private ProgressBar ruedita(LinearLayout c) {
    ProgressBar pb = new ProgressBar(this);
    pb.setIndeterminate(true);
    pb.setVisibility(View.GONE);
    LinearLayout.LayoutParams p = lp(dp(46), dp(46), 6);
    p.gravity = Gravity.CENTER_HORIZONTAL;
    c.addView(pb, p);
    return pb;
  }

  /**
   * Animación mientras se espera a Loyverse: el botón late, dice «Creando…» con puntos que se
   * mueven, gira la rueda y se cuentan los segundos. Devuelve lo que hay que llamar al terminar.
   */
  private Runnable ocupado(final Button b, final TextView estado, final View rueda, final String verbo, final String detalle) {
    final long t0 = System.currentTimeMillis();
    final boolean[] vivo = {true};
    final android.animation.ObjectAnimator pulso = android.animation.ObjectAnimator.ofFloat(b, "alpha", 1f, 0.5f);
    pulso.setDuration(700);
    pulso.setRepeatMode(android.animation.ValueAnimator.REVERSE);
    pulso.setRepeatCount(android.animation.ValueAnimator.INFINITE);
    pulso.start();
    rueda.setVisibility(View.VISIBLE);
    final Runnable[] tick = new Runnable[1];
    tick[0] = () -> {
      if (!vivo[0]) return;
      long ms = System.currentTimeMillis() - t0;
      b.setText(verbo + "...".substring(0, (int) (ms / 400 % 4)));
      estado.setTextColor(NEGRO);
      estado.setText(detalle + " " + (ms / 1000) + " s" + (ms > 12000 ? "\nLoyverse está lento; sigo esperando, no cierres." : ""));
      ui.postDelayed(tick[0], 400);
    };
    tick[0].run();
    return () -> {
      vivo[0] = false;
      ui.removeCallbacks(tick[0]);
      pulso.cancel();
      b.setAlpha(1f);
      rueda.setVisibility(View.GONE);
    };
  }

  /** Falla que pudo haber llegado a Loyverse (se cortó esperando): conviene comprobar antes de reintentar. */
  private static boolean incierto(Exception e) {
    String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
    return e instanceof java.net.SocketTimeoutException || m.contains("timeout") || m.contains("timed out") || m.contains("aborted");
  }

  private LinearLayout.LayoutParams peso(int h, int ml) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, h, 1);
    p.leftMargin = dp(ml);
    return p;
  }

  private TextView texto(String t, float sp, boolean negrita, int color) {
    TextView v = new TextView(this);
    v.setText(t);
    v.setTextSize(sp);
    v.setTextColor(color);
    if (negrita) v.setTypeface(Typeface.DEFAULT_BOLD);
    return v;
  }

  private LinearLayout fila() {
    LinearLayout f = new LinearLayout(this);
    f.setOrientation(LinearLayout.HORIZONTAL);
    f.setGravity(Gravity.CENTER_VERTICAL);
    return f;
  }

  private View linea() {
    View v = new View(this);
    v.setBackgroundColor(LINEA);
    return v;
  }

  /** $1.990 — con punto de miles, sin depender del idioma del equipo. */
  static String precioTxt(int p) {
    if (p <= 0) return "";
    String s = String.valueOf(p);
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      if (i > 0 && (s.length() - i) % 3 == 0) b.append('.');
      b.append(s.charAt(i));
    }
    return "$" + b;
  }

  private static String miles(int n) { String t = precioTxt(n); return t.isEmpty() ? "0" : t.substring(1); }

  private static String horaTxt(long t) {
    if (t <= 0) return "sin fecha";
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(t);
    return String.format(Locale.ROOT, "%02d/%02d %02d:%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1,
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
  }

  private Local local() { return Local.de(aj.local); }

  // ---------------------------------------------------------------- ciclo de vida
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("precios", MODE_PRIVATE);
    aj = Ajustes.cargar(this);
    cat = new Catalogo(local());
    fotos = new Imagenes(this, dp(48));
    construir();
    abrirLocal();
    new Actualizador(this).revisar(false);   // si hay versión nueva, ofrece actualizar
  }

  @Override
  public void onBackPressed() {
    if (buscando) { cerrarBusqueda(); return; }
    super.onBackPressed();
  }

  /** Carga la lista guardada del local (en segundo plano) y la actualiza si está vieja. */
  private void abrirLocal() {
    final Local l = local();
    cat = new Catalogo(l);
    cargandoLista = true;
    txtTitulo.setText("Etiquetas · " + l.nombre);
    pintarCategorias();
    filtrar();
    new Thread(() -> {
      final Catalogo c = Catalogo.cargar(getApplicationContext(), l);
      ui.post(() -> {
        if (!l.id.equals(aj.local)) return;   // cambiaron de local mientras cargaba
        cat = c;
        cargandoLista = false;
        pintarCategorias();
        filtrar();
        if (c.productos.isEmpty() || System.currentTimeMillis() - c.hora > VIEJO_MS) actualizarProductos();
      });
    }).start();
  }

  // ---------------------------------------------------------------- pantalla principal
  private void construir() {
    LinearLayout raiz = new LinearLayout(this);
    raiz.setOrientation(LinearLayout.VERTICAL);
    raiz.setBackgroundColor(Color.WHITE);

    // Barra verde (como Loyverse): ≡  título
    LinearLayout barra = fila();
    barra.setBackgroundColor(VERDE_BARRA);
    barra.setPadding(dp(4), 0, dp(12), 0);
    TextView menu = texto("☰", 28, false, Color.WHITE);
    menu.setGravity(Gravity.CENTER);
    menu.setOnClickListener(v -> abrirMenu());
    barra.addView(menu, new LinearLayout.LayoutParams(dp(56), dp(56)));
    txtTitulo = texto("Etiquetas", 21, true, Color.WHITE);
    txtTitulo.setSingleLine(true);
    txtTitulo.setEllipsize(TextUtils.TruncateAt.END);
    txtTitulo.setOnClickListener(v -> elegirLocal());
    barra.addView(txtTitulo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    TextView verdura = texto("＋ Verdura", 18, true, Color.WHITE);
    verdura.setGravity(Gravity.CENTER);
    verdura.setPadding(dp(10), 0, dp(4), 0);
    verdura.setOnClickListener(v -> abrirVerduraRapida());
    barra.addView(verdura, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)));
    raiz.addView(barra, new LinearLayout.LayoutParams(-1, dp(56)));

    // Fila: categoría (o buscador) + lupa
    LinearLayout filtro = fila();
    filtro.setPadding(dp(8), 0, 0, 0);
    // Selector propio (no Spinner): en el POS con Android 8.1 el desplegable nativo no abría bien.
    spCategoria = texto(TODOS + "  ▾", 20, false, NEGRO);
    spCategoria.setSingleLine(true);
    spCategoria.setEllipsize(TextUtils.TruncateAt.END);
    spCategoria.setGravity(Gravity.CENTER_VERTICAL);
    spCategoria.setPadding(dp(8), 0, dp(8), 0);
    spCategoria.setClickable(true);
    spCategoria.setOnClickListener(v -> elegirCategoria());
    filtro.addView(spCategoria, new LinearLayout.LayoutParams(0, dp(64), 1));
    buscador = new EditText(this);
    buscador.setHint("Buscar: nombre, código o SKU");
    buscador.setTextSize(20);
    buscador.setSingleLine(true);
    buscador.setBackground(null);
    buscador.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    buscador.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    buscador.setVisibility(View.GONE);
    buscador.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable e) {
        ui.removeCallbacks(buscarLuego);
        ui.postDelayed(buscarLuego, 150);
      }
    });
    // Lector de códigos (escribe el código + Enter): si es exacto, abre directo la etiqueta.
    buscador.setOnEditorActionListener((v, id, ev) -> {
      boolean enter = id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_DONE
          || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN);
      if (!enter) return false;
      String q = buscador.getText().toString().trim();
      Producto p = cat.exacto(q);
      if (p == null) { List<Producto> r = cat.buscar(q, 2); if (r.size() == 1) p = r.get(0); }
      if (p != null) { buscador.setText(""); abrirEtiqueta(p); } else filtrar();
      return true;
    });
    filtro.addView(buscador, new LinearLayout.LayoutParams(0, dp(64), 1));
    View sep = linea();
    filtro.addView(sep, new LinearLayout.LayoutParams(dp(1), dp(64)));
    btnBuscar = new Button(this);
    btnBuscar.setText("🔍");
    btnBuscar.setTextSize(24);
    btnBuscar.setTextColor(PLOMO);
    btnBuscar.setBackground(null);
    btnBuscar.setOnClickListener(v -> { if (buscando) cerrarBusqueda(); else abrirBusqueda(); });
    filtro.addView(btnBuscar, new LinearLayout.LayoutParams(dp(72), dp(64)));
    raiz.addView(filtro, new LinearLayout.LayoutParams(-1, dp(64)));
    raiz.addView(linea(), new LinearLayout.LayoutParams(-1, dp(1)));

    txtMensaje = texto("", 16, false, NEGRO);
    txtMensaje.setBackgroundColor(AVISO);
    txtMensaje.setPadding(dp(16), dp(8), dp(16), dp(8));
    txtMensaje.setVisibility(View.GONE);
    txtMensaje.setOnClickListener(v -> txtMensaje.setVisibility(View.GONE));
    raiz.addView(txtMensaje, new LinearLayout.LayoutParams(-1, -2));

    txtVacio = texto("", 18, false, PLOMO);
    txtVacio.setPadding(dp(20), dp(24), dp(20), dp(24));
    txtVacio.setGravity(Gravity.CENTER);
    txtVacio.setVisibility(View.GONE);
    raiz.addView(txtVacio, new LinearLayout.LayoutParams(-1, -2));

    lista = new ListView(this);
    lista.setAdapter(adaptador);
    lista.setDivider(null);
    lista.setFastScrollEnabled(true);
    lista.setOnItemClickListener((a, v, pos, id) -> abrirEtiqueta(visibles.get(pos)));
    raiz.addView(lista, new LinearLayout.LayoutParams(-1, 0, 1));

    setContentView(raiz);
  }

  private void mensaje(String t) {
    txtMensaje.setText(t);
    txtMensaje.setVisibility(t == null || t.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void abrirBusqueda() {
    buscando = true;
    spCategoria.setVisibility(View.GONE);
    buscador.setVisibility(View.VISIBLE);
    btnBuscar.setText("✕");
    buscador.requestFocus();
    InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (im != null) im.showSoftInput(buscador, InputMethodManager.SHOW_IMPLICIT);
    filtrar();
  }

  private void cerrarBusqueda() {
    buscando = false;
    buscador.setText("");
    InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (im != null) im.hideSoftInputFromWindow(buscador.getWindowToken(), 0);
    buscador.setVisibility(View.GONE);
    spCategoria.setVisibility(View.VISIBLE);
    btnBuscar.setText("🔍");
    filtrar();
  }

  /** Restaura la categoría guardada del local (si todavía existe en la lista). */
  private void pintarCategorias() {
    String guardada = prefs.getString("categoria_" + aj.local, "");
    categoria = null;
    for (String c : categoriasVisibles()) if (c.equalsIgnoreCase(guardada)) categoria = c;
    spCategoria.setText((categoria == null ? TODOS : categoria) + "  ▾");
  }

  private void elegirCategoria() {
    if (cat.categorias.isEmpty()) {
      new AlertDialog.Builder(this).setTitle("Sin categorías")
          .setMessage(cat.productos.isEmpty() || bajando
              ? "Todavía se están bajando los productos. Espera un momento y vuelve a tocar."
              : "La lista guardada no trae categorías. Toca «Actualizar productos» para bajarlas.")
          .setPositiveButton("Actualizar productos", (d, i) -> actualizarProductos())
          .setNegativeButton("Cerrar", null).show();
      return;
    }
    final List<String> ops = new ArrayList<>();
    ops.add(TODOS);
    ops.addAll(categoriasVisibles());
    int sel = 0;
    for (int i = 1; i < ops.size(); i++) if (ops.get(i).equals(categoria)) sel = i;
    ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, ops) {
      @Override public View getView(int pos, View v, ViewGroup p) {
        TextView t = (TextView) super.getView(pos, v, p);
        t.setTextSize(20);
        t.setTextColor(NEGRO);
        t.setMinHeight(dp(60));
        return t;
      }
    };
    new AlertDialog.Builder(this).setTitle("Categoría").setSingleChoiceItems(ad, sel, (d, i) -> {
      d.dismiss();
      categoria = i == 0 ? null : ops.get(i);
      prefs.edit().putString("categoria_" + aj.local, categoria == null ? "" : categoria).apply();
      spCategoria.setText((categoria == null ? TODOS : categoria) + "  ▾");
      filtrar();
      lista.setSelection(0);
    }).show();
  }

  private String categoriaElegida() { return categoria; }

  // ---------------------------------------------------------------- categorías a mostrar (por local)
  /** Categorías marcadas en "Elegir categorías a mostrar" (en minúsculas); null = todas. */
  private java.util.Set<String> categoriasMarcadas() {
    String raw = prefs.getString("cats_visibles_" + aj.local, null);
    if (raw == null) return null;
    java.util.Set<String> out = new java.util.HashSet<>();
    try {
      org.json.JSONArray a = new org.json.JSONArray(raw);
      for (int i = 0; i < a.length(); i++) out.add(a.optString(i).toLowerCase(Locale.ROOT));
    } catch (Exception e) { return null; }
    return out;
  }

  private List<String> categoriasVisibles() {
    java.util.Set<String> m = categoriasMarcadas();
    if (m == null) return cat.categorias;
    List<String> out = new ArrayList<>();
    for (String c : cat.categorias) if (m.contains(c.toLowerCase(Locale.ROOT))) out.add(c);
    return out;
  }

  /** Lista sin buscar: la categoría elegida, o "Todos" = solo las categorías marcadas. El buscador no usa esto. */
  private List<Producto> productosVisibles(String categoria) {
    if (categoria != null) return cat.deCategoria(categoria);
    java.util.Set<String> m = categoriasMarcadas();
    if (m == null) return cat.productos;
    List<Producto> out = new ArrayList<>();
    for (Producto p : cat.productos) if (m.contains(p.categoria.toLowerCase(Locale.ROOT))) out.add(p);
    return out;
  }

  private void elegirCategoriasVisibles() {
    if (cat.categorias.isEmpty()) { elegirCategoria(); return; }   // explica que faltan categorías
    final String[] cats = cat.categorias.toArray(new String[0]);
    final boolean[] marc = new boolean[cats.length];
    java.util.Set<String> m = categoriasMarcadas();
    for (int i = 0; i < cats.length; i++) marc[i] = m == null || m.contains(cats[i].toLowerCase(Locale.ROOT));
    final AlertDialog d = new AlertDialog.Builder(this)
        .setTitle("Categorías a mostrar en " + local().nombre)
        .setMultiChoiceItems(cats, marc, (dlg, i, on) -> marc[i] = on)
        .setPositiveButton("Guardar", null)
        .setNeutralButton("Marcar todas", null)
        .setNegativeButton("Cancelar", null)
        .create();
    d.setOnShowListener(x -> {
      // "Marcar todas" / "Quitar todas" sin cerrar la ventana
      final Button bTodas = d.getButton(AlertDialog.BUTTON_NEUTRAL);
      boolean todasAl = true;
      for (boolean x2 : marc) todasAl &= x2;
      bTodas.setText(todasAl ? "Quitar todas" : "Marcar todas");
      bTodas.setOnClickListener(v -> {
        boolean todas = true;
        for (boolean b : marc) todas &= b;
        for (int i = 0; i < marc.length; i++) { marc[i] = !todas; d.getListView().setItemChecked(i, !todas); }
        bTodas.setText(todas ? "Marcar todas" : "Quitar todas");
      });
      d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
        org.json.JSONArray a = new org.json.JSONArray();
        int n = 0;
        for (int i = 0; i < cats.length; i++) if (marc[i]) { a.put(cats[i]); n++; }
        if (n == 0) { Toast.makeText(this, "Marca al menos una categoría.", Toast.LENGTH_LONG).show(); return; }
        if (n == cats.length) prefs.edit().remove("cats_visibles_" + aj.local).apply();   // todas = sin filtro (incluye nuevas)
        else prefs.edit().putString("cats_visibles_" + aj.local, a.toString()).apply();
        d.dismiss();
        pintarCategorias();
        filtrar();
        Toast.makeText(this, n == cats.length ? "Se muestran todas las categorías." : "Se muestran " + n + " categorías. El buscador 🔍 sigue buscando en todos los productos.", Toast.LENGTH_LONG).show();
      });
    });
    d.show();
  }

  private void filtrar() {
    if (buscando) {
      String q = buscador.getText().toString().trim();
      visibles = q.isEmpty() ? new ArrayList<>() : cat.buscar(q, 200);
      // Código escaneado completo: abre directo (el lector escribe todo de golpe).
      Producto ex = cat.exacto(q);
      if (ex != null && q.length() >= 8 && q.matches("[0-9]+")) { buscador.setText(""); abrirEtiqueta(ex); }
    } else {
      visibles = productosVisibles(categoriaElegida());
    }
    adaptador.notifyDataSetChanged();
    String vacio = "";
    if (cargandoLista) vacio = "Abriendo lista guardada…";
    else if (cat.productos.isEmpty()) vacio = bajando ? "Bajando productos… (solo la primera vez demora)" : "No hay productos guardados.\nToca ☰ → «Actualizar productos».";
    else if (visibles.isEmpty()) vacio = buscando
        ? (buscador.getText().toString().trim().isEmpty() ? "Escribe el nombre o pasa el lector por el código." : "No encontré «" + buscador.getText().toString().trim() + "».")
        : "Esta categoría no tiene productos.";
    txtVacio.setText(vacio);
    txtVacio.setVisibility(vacio.isEmpty() ? View.GONE : View.VISIBLE);
  }

  /** Fila igual a Loyverse: foto 48 dp, nombre, precio a la derecha, línea abajo desde el texto. */
  private class Adaptador extends BaseAdapter {
    public int getCount() { return visibles.size(); }
    public Object getItem(int i) { return visibles.get(i); }
    public long getItemId(int i) { return i; }

    public View getView(int pos, View v, ViewGroup parent) {
      Fila f;
      if (v == null) {
        f = new Fila();
        LinearLayout cont = new LinearLayout(MainActivity.this);
        cont.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r = fila();
        r.setPadding(dp(16), 0, dp(16), 0);
        f.foto = new ImageView(MainActivity.this);
        f.foto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable g = new GradientDrawable();
        g.setColor(GRIS_FOTO);
        g.setCornerRadius(dp(3));
        f.foto.setBackground(g);
        r.addView(f.foto, new LinearLayout.LayoutParams(dp(48), dp(48)));
        f.nombre = texto("", 18, false, NEGRO);
        f.nombre.setMaxLines(2);
        f.nombre.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams pn = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        pn.leftMargin = dp(20);
        r.addView(f.nombre, pn);
        f.precio = texto("", 18, false, NEGRO);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.leftMargin = dp(12);
        r.addView(f.precio, pp);
        cont.addView(r, new LinearLayout.LayoutParams(-1, dp(72)));
        View l = linea();
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(1));
        pl.leftMargin = dp(84);
        cont.addView(l, pl);
        cont.setTag(f);
        v = cont;
      } else f = (Fila) v.getTag();
      Producto p = visibles.get(pos);
      f.nombre.setText(p.nombre);
      f.precio.setText(p.precio > 0 ? precioTxt(p.precio) : "—");
      fotos.poner(f.foto, p.imagen);
      return v;
    }
  }

  private static class Fila { ImageView foto; TextView nombre, precio; }

  // ---------------------------------------------------------------- menú
  private void abrirMenu() {
    String[] ops = {
      "Local: " + local().nombre + " (cambiar)",
      "Verdura rápida (crear producto en Loyverse)",
      "Calculadora",
      "Impresora: " + (aj.mac.isEmpty() ? "sin elegir" : (aj.nombreImpresora.isEmpty() ? aj.mac : aj.nombreImpresora)),
      "Imprimir etiqueta de prueba",
      "Actualizar productos",
      "Ajustes de impresión",
      "Elegir categorías a mostrar",
      "Buscar actualización de la app (versión " + Actualizador.nombreInstalado(this) + ")",
    };
    String lista = cat.productos.isEmpty() ? "Sin lista guardada" : miles(cat.productos.size()) + " productos · actualizada " + horaTxt(cat.hora);
    new AlertDialog.Builder(this).setTitle(lista).setItems(ops, (d, i) -> {
      switch (i) {
        case 0: elegirLocal(); break;
        case 1: abrirVerduraRapida(); break;
        case 2: Calculadora.abrir(this, null, null); break;
        case 3: elegirImpresora(null); break;
        case 4: imprimirPrueba(); break;
        case 5: actualizarProductos(); break;
        case 6: abrirAjustes(); break;
        case 7: elegirCategoriasVisibles(); break;
        default: new Actualizador(this).revisar(true); break;
      }
    }).show();
  }

  private void elegirLocal() {
    String[] nombres = new String[Local.TODOS.length];
    int sel = 0;
    for (int i = 0; i < nombres.length; i++) { nombres[i] = Local.TODOS[i].nombre; if (Local.TODOS[i].id.equals(aj.local)) sel = i; }
    new AlertDialog.Builder(this).setTitle("¿De qué local son las etiquetas?").setSingleChoiceItems(nombres, sel, (d, i) -> {
      d.dismiss();
      Local l = Local.TODOS[i];
      if (l.id.equals(aj.local)) return;
      aj.local = l.id;
      aj.guardar(this);
      if (buscando) cerrarBusqueda();
      mensaje("");
      abrirLocal();
    }).show();
  }

  // ---------------------------------------------------------------- catálogo
  private void actualizarProductos() {
    if (bajando) return;
    bajando = true;
    final Local l = local();
    mensaje("Actualizando productos de " + l.nombre + "…");
    filtrar();
    new Thread(() -> {
      try {
        List<Producto> nuevos = Api.catalogo(l);
        final Catalogo c = new Catalogo(l);
        c.poner(nuevos, System.currentTimeMillis());
        c.guardar(getApplicationContext());
        ui.post(() -> {
          bajando = false;
          if (!l.id.equals(aj.local)) return;
          cat = c;
          mensaje("");
          Toast.makeText(this, "Productos actualizados (" + miles(c.productos.size()) + ").", Toast.LENGTH_SHORT).show();
          pintarCategorias();
          filtrar();
        });
      } catch (final Exception e) {
        ui.post(() -> {
          bajando = false;
          if (!l.id.equals(aj.local)) return;
          mensaje(cat.productos.isEmpty()
              ? "Sin internet y sin lista guardada. Conéctate a internet y toca ☰ → «Actualizar productos»."
              : "Sin internet: uso la lista guardada (" + horaTxt(cat.hora) + ").");
          filtrar();
        });
      }
    }).start();
  }

  // ---------------------------------------------------------------- ventana "Imprimir etiqueta"
  private class Ventana {
    Producto p;
    int cantidad = 1, confirmando = 0;
    boolean creando = false;
    String vence = "", elab = "";
    boolean venceManual = false;
    AlertDialog dlg;
    TextView txtPrecio, txtCodigo, txtCant, txtAviso, txtEstado, txtVenceInfo;
    ImageView vista;
    TextView txtNombre;
    Button btnNombre;
    Button btnImprimir, btnVence, btnSinVence, btnCodigo, btnPrecio, btnSinElab, btnElabHoy, btnElab;
  }

  private Ventana ven;

  private void abrirEtiqueta(final Producto p) {
    final Ventana w = new Ventana();
    ven = w;
    w.p = p;
    ScrollView sv = new ScrollView(this);
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(18), dp(14), dp(18), dp(14));
    sv.addView(c);

    LinearLayout filaNombre = fila();
    filaNombre.setGravity(Gravity.CENTER_VERTICAL);
    w.txtNombre = texto(p.nombre, 22, true, NEGRO);
    filaNombre.addView(w.txtNombre, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    // Cambiar el nombre del artículo (queda en Loyverse). Por ahora solo Marín 376.
    w.btnNombre = boton("✏ Nombre", Color.WHITE, Color.parseColor("#12A88A"));
    w.btnNombre.setTextSize(15);
    w.btnNombre.setOnClickListener(v -> { if (w.confirmando == 0 && !w.creando && !imprimiendo) abrirCambiarNombre(w); });
    w.btnNombre.setVisibility(Local.MARIN.id.equals(aj.local) ? View.VISIBLE : View.GONE);
    filaNombre.addView(w.btnNombre, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
    c.addView(filaNombre);
    LinearLayout filaPrecio = fila();
    filaPrecio.setGravity(Gravity.CENTER_VERTICAL);
    w.txtPrecio = texto("", 30, true, NEGRO);
    filaPrecio.addView(w.txtPrecio, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    // Cambiar precio/costo al tiro (misma calculadora que Verdura rápida). Por ahora solo Marín.
    w.btnPrecio = boton("✏ Cambiar precio", Color.WHITE, Color.parseColor("#12A88A"));
    w.btnPrecio.setTextSize(16);
    w.btnPrecio.setOnClickListener(v -> { if (w.confirmando == 0 && !w.creando && !imprimiendo) abrirCambiarPrecio(w); });
    w.btnPrecio.setVisibility(Local.MARIN.id.equals(aj.local) ? View.VISIBLE : View.GONE);
    filaPrecio.addView(w.btnPrecio, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));
    c.addView(filaPrecio, lp(-1, -2, 2));
    w.txtCodigo = texto("", 15, false, PLOMO);
    c.addView(w.txtCodigo, lp(-1, -2, 2));
    w.btnCodigo = boton("", VERDE, Color.WHITE);
    w.btnCodigo.setTextSize(18);
    w.btnCodigo.setOnClickListener(v -> crearCodigo(w));
    c.addView(w.btnCodigo, lp(-1, dp(56), 6));

    w.vista = new ImageView(this);
    w.vista.setAdjustViewBounds(true);
    w.vista.setBackgroundColor(Color.parseColor("#DDDDDD"));
    w.vista.setPadding(dp(5), dp(5), dp(5), dp(5));
    c.addView(w.vista, lp(-1, -2, 10));

    c.addView(texto("Cantidad de etiquetas", 18, true, NEGRO), lp(-1, -2, 14));
    LinearLayout filaCant = fila();
    Button menos = boton("−", GRIS, NEGRO);
    menos.setTextSize(28);
    menos.setOnClickListener(v -> { w.cantidad = Math.max(1, w.cantidad - 1); pintarVentana(w); });
    w.txtCant = texto("1", 30, true, NEGRO);
    w.txtCant.setGravity(Gravity.CENTER);
    Button mas = boton("+", GRIS, NEGRO);
    mas.setTextSize(28);
    mas.setOnClickListener(v -> { w.cantidad = Math.min(100, w.cantidad + 1); pintarVentana(w); });
    filaCant.addView(menos, new LinearLayout.LayoutParams(dp(64), dp(60)));
    filaCant.addView(w.txtCant, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
    filaCant.addView(mas, new LinearLayout.LayoutParams(dp(64), dp(60)));
    for (final int n : new int[]{5, 10}) {
      Button bn = boton(String.valueOf(n), GRIS, NEGRO);
      bn.setOnClickListener(v -> { w.cantidad = n; pintarVentana(w); });
      filaCant.addView(bn, peso(dp(60), 6));
    }
    c.addView(filaCant, lp(-1, -2, 4));

    c.addView(texto("Elaboración (opcional)", 18, true, NEGRO), lp(-1, -2, 14));
    LinearLayout filaElab = fila();
    w.btnSinElab = boton("Sin fecha", GRIS, NEGRO);
    w.btnSinElab.setTextSize(18);
    w.btnSinElab.setOnClickListener(v -> { w.elab = ""; pintarVentana(w); });
    filaElab.addView(w.btnSinElab, peso(dp(56), 0));
    w.btnElabHoy = boton("Hoy", GRIS, NEGRO);
    w.btnElabHoy.setTextSize(18);
    w.btnElabHoy.setOnClickListener(v -> { w.elab = fmt(Calendar.getInstance()); pintarVentana(w); });
    filaElab.addView(w.btnElabHoy, peso(dp(56), 8));
    w.btnElab = boton("Otra fecha", GRIS, NEGRO);
    w.btnElab.setTextSize(18);
    w.btnElab.setOnClickListener(v -> pedirFecha(w.elab, f -> { w.elab = f; pintarVentana(w); }));
    filaElab.addView(w.btnElab, peso(dp(56), 8));
    c.addView(filaElab, lp(-1, -2, 4));

    c.addView(texto("Vencimiento (opcional)", 18, true, NEGRO), lp(-1, -2, 14));
    LinearLayout filaVence = fila();
    w.btnSinVence = boton("Sin fecha", GRIS, NEGRO);
    w.btnSinVence.setTextSize(18);
    w.btnSinVence.setOnClickListener(v -> { w.vence = ""; w.venceManual = true; w.txtVenceInfo.setText(""); pintarVentana(w); });
    filaVence.addView(w.btnSinVence, peso(dp(56), 0));
    w.btnVence = boton("Poner fecha", GRIS, NEGRO);
    w.btnVence.setTextSize(18);
    w.btnVence.setOnClickListener(v -> elegirFecha(w));
    filaVence.addView(w.btnVence, peso(dp(56), 8));
    c.addView(filaVence, lp(-1, -2, 4));
    w.txtVenceInfo = texto("", 14, false, PLOMO);
    c.addView(w.txtVenceInfo, lp(-1, -2, 2));

    w.txtAviso = texto("", 16, true, ROJO);
    c.addView(w.txtAviso, lp(-1, -2, 10));
    w.btnImprimir = boton("Imprimir", VERDE_BOTON, Color.WHITE);
    w.btnImprimir.setTextSize(24);
    w.btnImprimir.setOnClickListener(v -> imprimir(w));
    c.addView(w.btnImprimir, lp(-1, dp(76), 6));
    w.txtEstado = texto("", 16, false, NEGRO);
    c.addView(w.txtEstado, lp(-1, -2, 8));
    Button cerrar = boton("Cerrar", Color.WHITE, PLOMO);
    cerrar.setTextSize(18);
    cerrar.setOnClickListener(v -> w.dlg.dismiss());
    c.addView(cerrar, lp(-1, dp(52), 6));

    w.dlg = new AlertDialog.Builder(this).setView(sv).create();
    w.dlg.setOnDismissListener(d -> { if (ven == w) ven = null; });
    w.dlg.show();
    pintarVentana(w);
    confirmarConServidor(w);
  }

  private EtiquetaPrecio etiquetaDe(Ventana w) {
    EtiquetaPrecio e = new EtiquetaPrecio();
    e.nombre = w.p.nombre;
    e.precio = precioTxt(w.p.precio);
    e.codigo = w.p.codigo();
    e.vence = w.vence;
    e.elab = w.elab;
    return e;
  }

  private LabelRenderer.Resultado render(EtiquetaPrecio e) {
    return LabelRenderer.dibujar(e, aj.anchoPuntos, aj.altoPuntos(), aj.texto);
  }

  private void pintarVentana(Ventana w) {
    w.txtPrecio.setText(w.p.precio > 0 ? precioTxt(w.p.precio) : "Sin precio");
    w.txtPrecio.setTextColor(w.p.precio > 0 ? NEGRO : ROJO);
    w.txtCodigo.setText("Código: " + w.p.codigo() + (w.p.barcode.isEmpty() ? " (SKU, sin código de barras)" : "")
        + (w.confirmando > 0 ? "\nConfirmando precio con el servidor…" : ""));
    boolean sinCodigo = w.p.barcode.isEmpty();
    if (sinCodigo) {
      colorear(w.btnCodigo, VERDE, Color.WHITE);
      w.btnCodigo.setText(w.creando ? "Creando código…" : "Crear código de barras");
    } else {
      colorear(w.btnCodigo, GRIS, NEGRO);
      w.btnCodigo.setText(w.creando ? "Creando código…" : "Cambiar código de barras");
    }
    boolean puedeCodigo = !w.creando && w.confirmando == 0 && !imprimiendo;
    w.btnCodigo.setEnabled(puedeCodigo);
    w.btnCodigo.setAlpha(puedeCodigo ? 1f : 0.45f);
    w.txtNombre.setText(w.p.nombre);
    w.btnNombre.setEnabled(puedeCodigo);
    w.btnNombre.setAlpha(puedeCodigo ? 1f : 0.45f);
    w.btnPrecio.setEnabled(puedeCodigo);
    w.btnPrecio.setAlpha(puedeCodigo ? 1f : 0.45f);
    w.txtCant.setText(String.valueOf(w.cantidad));
    String hoy = fmt(Calendar.getInstance());
    boolean elabHoy = w.elab.equals(hoy), elabOtra = !w.elab.isEmpty() && !elabHoy;
    colorear(w.btnSinElab, w.elab.isEmpty() ? VERDE : GRIS, w.elab.isEmpty() ? Color.WHITE : NEGRO);
    colorear(w.btnElabHoy, elabHoy ? VERDE : GRIS, elabHoy ? Color.WHITE : NEGRO);
    colorear(w.btnElab, elabOtra ? VERDE : GRIS, elabOtra ? Color.WHITE : NEGRO);
    w.btnElab.setText(elabOtra ? w.elab : "Otra fecha");
    boolean hay = !w.vence.isEmpty();
    colorear(w.btnSinVence, hay ? GRIS : VERDE, hay ? NEGRO : Color.WHITE);
    colorear(w.btnVence, hay ? VERDE : GRIS, hay ? Color.WHITE : NEGRO);
    w.btnVence.setText(hay ? "Vence " + w.vence : "Poner fecha");
    LabelRenderer.Resultado r = render(etiquetaDe(w));
    w.vista.setImageBitmap(r.vista);
    String aviso = r.aviso;
    if (sinCodigo) aviso = "Este producto no tiene código de barras: la etiqueta sale con la SKU. Toca «Crear código de barras» para que la caja lo lea.";
    if (w.p.precio <= 0) aviso = "Ojo: este producto no tiene precio en el sistema.";
    if (aj.mac.isEmpty()) aviso = "Falta elegir la impresora: al tocar Imprimir te la pido.";
    w.txtAviso.setText(aviso);
    w.txtAviso.setVisibility(aviso.isEmpty() ? View.GONE : View.VISIBLE);
    boolean ok = !imprimiendo && w.confirmando == 0 && !w.creando;
    w.btnImprimir.setEnabled(ok);
    w.btnImprimir.setAlpha(ok ? 1f : 0.45f);
    w.btnImprimir.setText(imprimiendo ? "Imprimiendo…" : (w.confirmando > 0 ? "Confirmando precio…"
        : "Imprimir " + w.cantidad + " etiqueta" + (w.cantidad == 1 ? "" : "s")));
  }

  /**
   * Pide al servidor el precio y código actuales (la lista guardada puede tener horas) y la fecha
   * del lote más próximo a vencer (como la web). Si no responde en 6 s se imprime con la lista.
   */
  private void confirmarConServidor(final Ventana w) {
    final Producto p = w.p;
    final Local l = local();
    w.confirmando = 1;
    final Runnable rendirse = () -> {
      if (w.confirmando == 0) return;
      w.confirmando = 0;
      w.txtEstado.setTextColor(PLOMO);
      w.txtEstado.setText("No pude confirmar el precio (sin internet): uso la lista guardada.");
      pintarVentana(w);
    };
    ui.postDelayed(rendirse, 6000);
    new Thread(() -> {
      Api.Ficha f = null;
      try { f = Api.ficha(l, p.sku); } catch (Exception ignore) {}
      final Api.Ficha ff = f;
      ui.post(() -> {
        ui.removeCallbacks(rendirse);
        boolean seRindio = w.confirmando == 0;
        w.confirmando = 0;
        if (ff != null) {
          List<String> cambios = new ArrayList<>();
          if (ff.precio != null && ff.precio != p.precio) {
            cambios.add("precio " + precioTxt(p.precio) + " → " + precioTxt(ff.precio));
            p.precio = ff.precio;
          }
          boolean costoNuevo = ff.costo != null && ff.costo > 0 && ff.costo != p.costo;
          if (costoNuevo) p.costo = ff.costo;   // para "Cambiar precio" (no se avisa)
          if (ff.barcode != null && !ff.barcode.equals(p.barcode)) {
            cambios.add("código " + p.codigo() + " → " + (ff.barcode.isEmpty() ? p.sku : ff.barcode));
            p.barcode = ff.barcode;
          }
          if (costoNuevo && cambios.isEmpty() && l.id.equals(aj.local)) {
            final Catalogo cg = cat;
            new Thread(() -> cg.guardar(getApplicationContext())).start();
          }
          if (!cambios.isEmpty() && l.id.equals(aj.local)) {
            cat.poner(new ArrayList<>(cat.productos), cat.hora);
            final Catalogo cg = cat;
            new Thread(() -> cg.guardar(getApplicationContext())).start();
            adaptador.notifyDataSetChanged();
            w.txtEstado.setTextColor(VERDE);
            w.txtEstado.setText("Actualizado desde el servidor: " + TextUtils.join(", ", cambios) + ".");
          } else if (!seRindio) w.txtEstado.setText("");
          if (!w.venceManual && ff.vence != null) {
            w.vence = ff.vence;
            w.txtVenceInfo.setText("Fecha del lote más próximo a vencer. Puedes cambiarla.");
          }
        } else if (!seRindio) {
          w.txtEstado.setTextColor(PLOMO);
          w.txtEstado.setText("No pude confirmar el precio (sin internet): uso la lista guardada.");
        }
        pintarVentana(w);
      });
    }).start();
  }

  // ---------------------------------------------------------------- crear código (igual que la web)
  private void crearCodigo(final Ventana w) {
    if (w.creando || w.confirmando > 0) return;
    if (!w.p.barcode.isEmpty()) {
      new AlertDialog.Builder(this).setTitle("¿Cambiar el código de barras?")
          .setMessage("Este producto ya tiene el código «" + w.p.barcode + "».\n\nSi lo cambias, las etiquetas viejas dejarán de funcionar en la caja.")
          .setPositiveButton("Sí, cambiarlo", (d, i) -> guardarCodigoNuevo(w))
          .setNegativeButton("No", null).show();
      return;
    }
    guardarCodigoNuevo(w);
  }

  private void guardarCodigoNuevo(final Ventana w) {
    java.util.HashSet<String> usados = new java.util.HashSet<>();
    for (Producto p : cat.productos) if (!p.barcode.isEmpty()) usados.add(p.barcode.trim());
    final String codigo = Barras.generarInterno(usados, new java.util.Random());
    if (codigo == null) { Toast.makeText(this, "No se pudo crear un código único. Intenta de nuevo.", Toast.LENGTH_LONG).show(); return; }
    final Local l = local();
    final Producto p = w.p;
    w.creando = true;
    w.txtEstado.setTextColor(NEGRO);
    w.txtEstado.setText("Guardando el código en el sistema…");
    pintarVentana(w);
    new Thread(() -> {
      String err = null;
      try { Api.guardarCodigo(l, p.sku, codigo); }
      catch (Exception e) { err = e instanceof java.io.IOException ? "Sin internet: no se pudo guardar el código. Inténtalo de nuevo." : (e.getMessage() == null ? "No se pudo guardar el código." : e.getMessage()); }
      final String fe = err;
      ui.post(() -> {
        w.creando = false;
        if (fe == null) {
          p.barcode = codigo;
          if (l.id.equals(aj.local)) {
            cat.poner(new ArrayList<>(cat.productos), cat.hora);
            final Catalogo cg = cat;
            new Thread(() -> cg.guardar(getApplicationContext())).start();
            adaptador.notifyDataSetChanged();
          }
          w.txtEstado.setTextColor(VERDE);
          w.txtEstado.setText("Código creado: " + codigo + ". Ya quedó en Loyverse; imprime la etiqueta.");
        } else {
          w.txtEstado.setTextColor(ROJO);
          w.txtEstado.setText(fe);
        }
        if (ven == w) pintarVentana(w);
      });
    }).start();
  }

  private void elegirFecha(final Ventana w) {
    pedirFecha(w.vence, f -> {
      w.vence = f;
      w.venceManual = true;
      w.txtVenceInfo.setText("");
      pintarVentana(w);
    });
  }

  private interface AlElegir { void fecha(String ddmmaaaa); }

  private static String fmt(Calendar c) {
    return String.format(Locale.ROOT, "%02d/%02d/%04d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
  }

  /** Calendario del equipo, partiendo de la fecha actual del campo (o de hoy). */
  private void pedirFecha(String actual, final AlElegir listo) {
    Calendar c = Calendar.getInstance();
    if (actual != null && !actual.isEmpty()) {
      try {
        String[] d = actual.split("/");
        c.set(Integer.parseInt(d[2]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[0]));
      } catch (Exception ignore) {}
    }
    new DatePickerDialog(this, (v, y, m, d) -> listo.fecha(String.format(Locale.ROOT, "%02d/%02d/%04d", d, m + 1, y)),
        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
  }

  // ---------------------------------------------------------------- Verdura rápida (crear producto)
  // Mismo flujo que "Verdura rápida" de la app web (cpAbrir quick): categoría VERDURAS y
  // ENSALADAS, IVA, inventario activo sin stock inicial, precio = costo / (1 − margen) con margen
  // 30 % por defecto; tras crear queda lista para la siguiente. Extra: crea el código de barras
  // interno 04… para poder imprimir la etiqueta al tiro.
  private static final String CAT_VERDURAS = "VERDURAS y ENSALADAS";
  private org.json.JSONObject catalogoLoyverse;   // categorías e impuestos (se pide una vez)

  /** "1.500" = 1500 (punto de miles), "2,5" = 2.5, "32.5" = 32.5. */
  static double numero(String t) {
    t = t == null ? "" : t.trim().replace("$", "").replace(" ", "");
    if (t.contains(",")) t = t.replace(".", "").replace(',', '.');
    else if (t.matches("\\d{1,3}(\\.\\d{3})+")) t = t.replace(".", "");
    try { return Double.parseDouble(t); } catch (Exception x) { return 0; }
  }

  private static double numero(EditText e) { return numero(e.getText().toString()); }

  /**
   * Calculadora de precio (la de Verdura rápida, también en "Cambiar precio"): costo + 🧮,
   * Con/Sin IVA, tipo de producto (divisor) o Manual (% producción + margen), chips con precios
   * cobrables (00/50/90), precio editable y margen real con aviso bajo 30 %.
   */
  private final class PanelPrecio {
    EditText costo, produccion, margen, precio;
    TextView txtCalc, txtMargen;
    LinearLayout chips, cajaManual;
    Button bCon, bSin;
    final Button[] bTipos = new Button[Precio.TIPOS.length], chipsProd = new Button[4];
    final int[] pcts = {0, 10, 20, 30};
    final String claveTipo;
    boolean conIva = true, tocado = false, poniendo = false;
    /** true: al cambiar el costo se vuelve a sugerir el precio aunque ya haya uno escrito. */
    boolean costoRecalcula = false;
    int tipo, calc = 0;
    /** Costo real ya guardado (solo para mostrar el margen mientras no se escriba uno nuevo). */
    int costoGuardado = 0;
    /** Precio que queda si se borra el costo (0 = vacío). */
    int precioInicial = 0;
    /** Aviso a la ventana dueña cada vez que cambia algo (opcional). */
    Runnable alCambiar;

    PanelPrecio(LinearLayout c, String claveTipo, String tituloCosto) {
      this.claveTipo = claveTipo;
      tipo = Math.max(0, Math.min(Precio.TIPOS.length - 1, prefs.getInt(claveTipo, 0)));
      LinearLayout titulo = fila();
      titulo.addView(texto(tituloCosto, 17, true, NEGRO), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
      Button bCalc = boton("🧮 Calculadora", Color.WHITE, Color.parseColor("#12A88A"));
      bCalc.setTextSize(15);
      titulo.addView(bCalc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
      c.addView(titulo, lp(-1, -2, 10));
      LinearLayout filaCosto = fila();
      costo = new EditText(MainActivity.this);
      // Calculadora tipo Samsung (igual a la de la web); "Usar como costo" pasa el resultado al campo.
      bCalc.setOnClickListener(v -> Calculadora.abrir(MainActivity.this, "Usar como costo", val -> {
        double r = Math.round(val * 100) / 100.0;
        costo.setText(r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r).replace('.', ','));
        costo.setSelection(costo.getText().length());
      }));
      costo.setTextSize(22);
      costo.setHint("$");
      costo.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
      filaCosto.addView(costo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
      bCon = boton("Con IVA", VERDE, Color.WHITE);
      bSin = boton("Sin IVA", GRIS, NEGRO);
      bCon.setTextSize(16);
      bSin.setTextSize(16);
      LinearLayout.LayoutParams pc = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50));
      pc.leftMargin = dp(6);
      filaCosto.addView(bCon, pc);
      LinearLayout.LayoutParams ps = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50));
      ps.leftMargin = dp(6);
      filaCosto.addView(bSin, ps);
      c.addView(filaCosto, lp(-1, -2, 4));

      // Tipo de producto: precio = costo ÷ divisor (incluye producción y ~40 % de ganancia real).
      // "Manual" deja el cálculo con % producción y margen.
      c.addView(texto("Tipo de producto (divisor)", 17, true, NEGRO), lp(-1, -2, 10));
      LinearLayout filaTipo1 = fila(), filaTipo2 = fila();
      for (int i = 0; i < Precio.TIPOS.length; i++) {
        String etq = i == 0 ? "Manual" : Precio.TIPOS[i] + "\n÷ " + String.format(Locale.ROOT, "%.2f", Precio.DIVISOR[i]).replace('.', ',');
        Button b = boton(etq, GRIS, NEGRO);
        b.setTextSize(15);
        bTipos[i] = b;
        (i < 3 ? filaTipo1 : filaTipo2).addView(b, peso(dp(58), i % 3 == 0 ? 0 : 6));
      }
      c.addView(filaTipo1, lp(-1, -2, 4));
      c.addView(filaTipo2, lp(-1, -2, 6));
      cajaManual = new LinearLayout(MainActivity.this);   // % producción + margen (solo en Manual)
      cajaManual.setOrientation(LinearLayout.VERTICAL);
      c.addView(cajaManual, lp(-1, -2, 0));

      // + % producción (mano de obra, materiales, luz): se suma al costo antes de calcular el precio.
      cajaManual.addView(texto("+ % producción (mano de obra, materiales, luz)", 17, true, NEGRO), lp(-1, -2, 10));
      LinearLayout filaProd = fila();
      produccion = new EditText(MainActivity.this);
      produccion.setTextSize(22);
      produccion.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
      produccion.setText(prefs.getString("pctProduccion", "0"));
      filaProd.addView(produccion, new LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT));
      for (int i = 0; i < pcts.length; i++) {
        final int pct = pcts[i];
        Button b = boton(pct + " %", GRIS, NEGRO);
        b.setTextSize(16);
        b.setOnClickListener(v -> produccion.setText(String.valueOf(pct)));
        chipsProd[i] = b;
        filaProd.addView(b, peso(dp(48), 6));
      }
      cajaManual.addView(filaProd, lp(-1, -2, 4));

      cajaManual.addView(texto("Margen sobre venta (%)", 17, true, NEGRO), lp(-1, -2, 10));
      margen = new EditText(MainActivity.this);
      margen.setTextSize(22);
      margen.setText("30");
      margen.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
      cajaManual.addView(margen);
      txtCalc = texto("", 15, false, PLOMO);
      c.addView(txtCalc, lp(-1, -2, 4));

      c.addView(texto("Precio de venta", 17, true, NEGRO), lp(-1, -2, 10));
      chips = fila();
      c.addView(chips, lp(-1, -2, 4));
      precio = new EditText(MainActivity.this);
      precio.setTextSize(26);
      precio.setTypeface(Typeface.DEFAULT_BOLD);
      precio.setHint("$");
      precio.setInputType(InputType.TYPE_CLASS_NUMBER);
      c.addView(precio, lp(-1, -2, 4));
      txtMargen = texto("", 15, false, PLOMO);
      c.addView(txtMargen, lp(-1, -2, 2));

      costo.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence x, int a, int b, int d) {}
        public void onTextChanged(CharSequence x, int a, int b, int d) {}
        public void afterTextChanged(Editable e) { if (costoRecalcula) tocado = false; recalcular(); }
      });
      TextWatcher cambio = new TextWatcher() {
        public void beforeTextChanged(CharSequence x, int a, int b, int d) {}
        public void onTextChanged(CharSequence x, int a, int b, int d) {}
        public void afterTextChanged(Editable e) { recalcular(); }
      };
      produccion.addTextChangedListener(cambio);
      margen.addTextChangedListener(cambio);
      precio.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence x, int a, int b, int d) {}
        public void onTextChanged(CharSequence x, int a, int b, int d) {}
        public void afterTextChanged(Editable e) { if (!poniendo) { tocado = true; recalcular(); } }
      });
      bCon.setOnClickListener(v -> { conIva = true; if (costoRecalcula) tocado = false; recalcular(); });
      bSin.setOnClickListener(v -> { conIva = false; if (costoRecalcula) tocado = false; recalcular(); });
      for (int i = 0; i < bTipos.length; i++) {
        final int t = i;
        bTipos[i].setOnClickListener(v -> {
          tipo = t;
          tocado = false;   // el precio vuelve a calcularse con el tipo elegido
          prefs.edit().putInt(claveTipo, t).apply();
          recalcular();
        });
      }
      recalcular();
    }

    double costoIva() { return Precio.costoBruto(numero(costo), conIva); }

    /** Costo real: materia + producción del tipo, o + % producción en Manual. 0 si no hay costo. */
    double costoReal() {
      double ci = costoIva();
      if (ci <= 0) return 0;
      return tipo > 0 ? ci + Precio.PRODUCCION[tipo] : Precio.conProduccion(ci, numero(produccion));
    }

    int precioVenta() { return (int) numero(precio); }

    void ponerPrecio(int v) {
      poniendo = true;
      precio.setText(v > 0 ? String.valueOf(v) : "");
      poniendo = false;
    }

    void guardarPrefs() {
      prefs.edit().putString("pctProduccion", produccion.getText().toString().trim()).putInt(claveTipo, tipo).apply();
    }

    void recalcular() {
      double pct = numero(produccion);
      for (int i = 0; i < pcts.length; i++) {
        boolean on = pct == pcts[i];
        colorear(chipsProd[i], on ? VERDE : GRIS, on ? Color.WHITE : NEGRO);
      }
      for (int i = 0; i < bTipos.length; i++) {
        boolean on = tipo == i;
        colorear(bTipos[i], on ? VERDE : GRIS, on ? Color.WHITE : NEGRO);
      }
      colorear(bCon, conIva ? VERDE : GRIS, conIva ? Color.WHITE : NEGRO);
      colorear(bSin, conIva ? GRIS : VERDE, conIva ? NEGRO : Color.WHITE);
      cajaManual.setVisibility(tipo == 0 ? View.VISIBLE : View.GONE);
      double cIva = costoIva();
      double cb = costoReal();
      double cbMargen = cb > 0 ? cb : costoGuardado;
      String detalle = "";
      if (tipo > 0) {
        // Divisor por tipo: precio desde la materia prima; costo real = materia + producción del tipo.
        calc = Precio.desdeDivisor(cIva, Precio.DIVISOR[tipo]);
        if (cIva > 0) {
          detalle = Precio.TIPOS[tipo] + ": " + precioTxt((int) Math.round(cIva)) + (conIva ? "" : " (con IVA)")
              + " ÷ " + String.format(Locale.ROOT, "%.2f", Precio.DIVISOR[tipo]).replace('.', ',')
              + "\nCosto real con producción: " + precioTxt((int) Math.round(cb))
              + (Precio.PRODUCCION[tipo] > 0 ? " (+" + precioTxt(Precio.PRODUCCION[tipo]) + ")" : "") + "\n";
        }
      } else {
        calc = Precio.desdeMargen(cb, numero(margen));
        if (cIva > 0) {
          detalle = "Costo total: " + precioTxt((int) Math.round(cb));
          if (!conIva || pct > 0) {
            detalle += " (" + precioTxt((int) Math.round(numero(costo)));
            if (!conIva) detalle += " + 19 % IVA";
            if (pct > 0) detalle += " + " + CalcMotor.fmtNum(String.valueOf(pct).replaceAll("\\.0$", "")) + " % producción";
            detalle += ")";
          }
          detalle += "\n";
        }
      }
      txtCalc.setText(calc > 0
          ? detalle + "Precio calculado: " + precioTxt(calc) + " → " + precioTxt(Precio.sugerido(calc))
          : (costoGuardado > 0 ? "Escribe el costo nuevo para calcular, o cambia el precio directo."
              : "Escribe el costo y el margen para calcular el precio."));
      if (!tocado) ponerPrecio(calc > 0 ? Precio.sugerido(calc) : precioInicial);
      int pv = precioVenta();
      double mr = pv > 0 && cbMargen > 0 ? Precio.margenReal(pv, cbMargen) : 0;
      if (pv > 0 && cbMargen > 0 && mr < 30) {
        // Bajo el mínimo recomendado (30 % real): pasa con verdura muy barata, donde casi todo es mano de obra.
        int minimo = Precio.sugerido((int) Math.round(cbMargen / 0.70));
        txtMargen.setTextColor(ROJO);
        txtMargen.setText(String.format(Locale.ROOT, "⚠ Margen real %.1f %%: bajo el mínimo de 30 %%. Precio mínimo: %s", mr, precioTxt(minimo)));
      } else {
        txtMargen.setTextColor(PLOMO);
        txtMargen.setText(pv > 0 && cbMargen > 0 ? String.format(Locale.ROOT, "Margen real: %.1f %%", mr)
            + (cb > 0 ? "" : " (con el costo de Loyverse)") : "");
      }
      chips.removeAllViews();
      // Solo precios cobrables en pesos chilenos: terminados en 00, 50 o 90 (nunca $1.125).
      for (final int r : Precio.redondeos(calc)) {
        boolean on = r == pv;
        Button ch = boton(precioTxt(r), on ? VERDE : GRIS, on ? Color.WHITE : NEGRO);
        ch.setTextSize(16);
        ch.setOnClickListener(v -> { tocado = true; ponerPrecio(r); recalcular(); });
        chips.addView(ch, peso(dp(48), chips.getChildCount() == 0 ? 0 : 6));
      }
      if (alCambiar != null) alCambiar.run();
    }
  }

  // ---------------------------------------------------------------- Cambiar nombre (desde la etiqueta)
  private void abrirCambiarNombre(final Ventana w) {
    final Producto p = w.p;
    final Local l = local();
    final boolean[] guardando = {false};
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(18), dp(14), dp(18), dp(14));
    c.addView(texto("Cambiar nombre", 24, true, NEGRO));
    c.addView(texto("SKU " + p.sku + " · queda en Loyverse y en la etiqueta.", 14, false, PLOMO), lp(-1, -2, 2));
    final EditText nombre = new EditText(this);
    nombre.setTextSize(22);
    nombre.setText(p.nombre);
    nombre.setSelection(nombre.getText().length());
    nombre.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    c.addView(nombre, lp(-1, -2, 10));
    final TextView estado = texto("", 16, true, NEGRO);
    c.addView(estado, lp(-1, -2, 8));
    final ProgressBar rueda = ruedita(c);
    final Button bGuardar = boton("Guardar nombre", VERDE_BOTON, Color.WHITE);
    bGuardar.setTextSize(22);
    c.addView(bGuardar, lp(-1, dp(70), 6));
    Button bCerrar = boton("Cancelar", Color.WHITE, PLOMO);
    bCerrar.setTextSize(18);
    c.addView(bCerrar, lp(-1, dp(50), 6));
    final AlertDialog dlg = new AlertDialog.Builder(this).setView(c).create();
    bCerrar.setOnClickListener(v -> dlg.dismiss());
    bGuardar.setOnClickListener(v -> {
      if (guardando[0]) return;
      final String nuevo = nombre.getText().toString().trim().replaceAll("\\s+", " ");
      if (nuevo.isEmpty()) { estado.setTextColor(ROJO); estado.setText("El nombre no puede quedar vacío."); return; }
      if (nuevo.equals(p.nombre)) { estado.setTextColor(PLOMO); estado.setText("No cambiaste nada."); return; }
      final String antes = p.nombre;
      guardando[0] = true;
      bGuardar.setEnabled(false);
      final Runnable parar = ocupado(bGuardar, estado, rueda, "Guardando", "Guardando en Loyverse…");
      new Thread(() -> {
        String err = null;
        try { Api.editarNombre(l, p.sku, nuevo); }
        catch (Exception e) {
          if (incierto(e)) {
            // Se cortó la espera: se mira si Loyverse alcanzó a guardarlo (el nombre no viene en la ficha,
            // así que se baja la lista).
            try {
              Thread.sleep(2500);
              boolean ok = false;
              for (Producto q : Api.catalogo(l)) if (q.sku.equals(p.sku)) { ok = q.nombre.equals(nuevo); break; }
              if (!ok) err = "Loyverse se demoró y NO guardó el nombre. Toca «Guardar nombre» otra vez.";
            } catch (Exception e2) {
              err = "Loyverse se demoró y no pude comprobar si se guardó. Actualiza productos para ver el nombre actual.";
            }
          } else if (e instanceof java.io.IOException) err = "Sin internet: no se guardó. Revisa la conexión e inténtalo de nuevo.";
          else err = e.getMessage() == null ? "No se pudo cambiar el nombre." : e.getMessage();
        }
        final String fe = err;
        ui.post(() -> {
          parar.run();
          guardando[0] = false;
          bGuardar.setEnabled(true);
          bGuardar.setText("Guardar nombre");
          if (fe != null) { estado.setTextColor(ROJO); estado.setText(fe); return; }
          p.nombre = nuevo;
          if (l.id.equals(aj.local)) {
            cat.poner(new ArrayList<>(cat.productos), cat.hora);   // reordena y actualiza la búsqueda
            final Catalogo cg = cat;
            new Thread(() -> cg.guardar(getApplicationContext())).start();
            adaptador.notifyDataSetChanged();
          }
          w.txtEstado.setTextColor(VERDE);
          w.txtEstado.setText("✓ Nombre guardado en Loyverse: " + antes + " → " + nuevo + ". Imprime la etiqueta nueva.");
          dlg.dismiss();
          if (ven == w) pintarVentana(w);
        });
      }).start();
    });
    dlg.show();
  }

  // ---------------------------------------------------------------- Cambiar precio (desde la etiqueta)
  // Misma calculadora que Verdura rápida. Guarda con editar_precio (igual que la Ficha de producto
  // de la web: queda en Loyverse y en el historial de precios) y la etiqueta sale con el precio nuevo.
  private void abrirCambiarPrecio(final Ventana w) {
    final Producto p = w.p;
    final Local l = local();
    final boolean[] guardando = {false};
    ScrollView sv = new ScrollView(this);
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(18), dp(14), dp(18), dp(14));
    sv.addView(c);
    c.addView(texto("Cambiar precio", 24, true, NEGRO));
    c.addView(texto(p.nombre, 17, true, PLOMO), lp(-1, -2, 2));
    c.addView(texto("Precio actual: " + (p.precio > 0 ? precioTxt(p.precio) : "—")
        + "   ·   Costo en Loyverse: " + (p.costo > 0 ? precioTxt(p.costo) : "—"), 16, false, NEGRO), lp(-1, -2, 4));

    final PanelPrecio pp = new PanelPrecio(c, "tipoPrecio", "Costo nuevo (con o sin IVA)");
    final TextView txtCambio = texto("", 17, true, NEGRO);
    c.addView(txtCambio, lp(-1, -2, 8));
    final TextView estado = texto("", 16, true, NEGRO);
    c.addView(estado, lp(-1, -2, 8));
    final ProgressBar rueda = ruedita(c);
    final Button bGuardar = boton("Guardar en Loyverse", VERDE_BOTON, Color.WHITE);
    bGuardar.setTextSize(22);
    c.addView(bGuardar, lp(-1, dp(70), 6));
    Button bCerrar = boton("Cancelar", Color.WHITE, PLOMO);
    bCerrar.setTextSize(18);
    c.addView(bCerrar, lp(-1, dp(50), 6));

    pp.alCambiar = () -> {
      int pv = pp.precioVenta();
      if (pv <= 0 || pv == p.precio) { txtCambio.setText(pv == p.precio && pv > 0 ? "Mismo precio que ahora." : ""); return; }
      String t = "Etiqueta: " + (p.precio > 0 ? precioTxt(p.precio) + " → " : "") + precioTxt(pv);
      if (p.precio > 0) t += String.format(Locale.ROOT, " (%+.0f %%)", (pv - p.precio) * 100.0 / p.precio);
      txtCambio.setText(t);
    };
    // Parte con el precio actual y el margen sobre el costo de Loyverse. Al escribir un costo nuevo
    // (o cambiar IVA/tipo) se sugiere el precio; también se puede escribir el precio directo.
    pp.costoRecalcula = true;
    pp.costoGuardado = p.costo;
    pp.precioInicial = p.precio;
    if (p.costo > 0) pp.costo.setHint("Actual " + precioTxt(p.costo));
    pp.tocado = true;
    pp.ponerPrecio(p.precio);
    pp.recalcular();

    final AlertDialog dlg = new AlertDialog.Builder(this).setView(sv).create();
    bCerrar.setOnClickListener(v -> dlg.dismiss());

    final Runnable[] guardar = new Runnable[1];
    guardar[0] = () -> {
      final int pv = pp.precioVenta();
      final int cr = (int) Math.round(pp.costoReal());
      final Integer nuevoPrecio = pv != p.precio ? pv : null;
      // El costo solo se cambia si se escribió uno nuevo.
      final Integer nuevoCosto = cr > 0 && cr != p.costo ? cr : null;
      final int antes = p.precio;
      pp.guardarPrefs();
      guardando[0] = true;
      bGuardar.setEnabled(false);
      final Runnable parar = ocupado(bGuardar, estado, rueda, "Guardando", "Guardando en Loyverse…");
      new Thread(() -> {
        String err = null;
        org.json.JSONObject res = null;
        try { res = Api.editarPrecio(l, p.sku, nuevoPrecio, nuevoCosto); }
        catch (Exception e) {
          if (incierto(e)) {
            // Se cortó la espera: se mira si Loyverse alcanzó a guardarlo.
            try {
              Thread.sleep(2000);
              Api.Ficha f = Api.ficha(l, p.sku);
              if (f != null && f.precio != null && (nuevoPrecio == null || f.precio.intValue() == nuevoPrecio.intValue())
                  && (nuevoCosto == null || (f.costo != null && f.costo.intValue() == nuevoCosto.intValue()))) {
                res = new org.json.JSONObject().put("precio", f.precio.intValue());
                if (f.costo != null) res.put("costo", f.costo.intValue());
              } else err = "Loyverse se demoró y NO guardó el cambio. Toca «Guardar en Loyverse» otra vez.";
            } catch (Exception e2) {
              err = "Loyverse se demoró y no pude comprobar si se guardó. Cierra y abre el producto para ver el precio actual.";
            }
          } else if (e instanceof java.io.IOException) err = "Sin internet: no se guardó. Revisa la conexión e inténtalo de nuevo.";
          else err = e.getMessage() == null ? "No se pudo guardar el precio." : e.getMessage();
        }
        final String fe = err;
        final org.json.JSONObject fres = res;
        ui.post(() -> {
          parar.run();
          guardando[0] = false;
          bGuardar.setEnabled(true);
          bGuardar.setText("Guardar en Loyverse");
          if (fe != null) { estado.setTextColor(ROJO); estado.setText(fe); return; }
          p.precio = fres.has("precio") && !fres.isNull("precio") ? (int) Math.round(fres.optDouble("precio", pv)) : pv;
          if (fres.has("costo") && !fres.isNull("costo")) p.costo = (int) Math.round(fres.optDouble("costo", p.costo));
          else if (nuevoCosto != null) p.costo = nuevoCosto;
          if (l.id.equals(aj.local)) {
            cat.poner(new ArrayList<>(cat.productos), cat.hora);
            final Catalogo cg = cat;
            new Thread(() -> cg.guardar(getApplicationContext())).start();
            adaptador.notifyDataSetChanged();
          }
          String m = antes != p.precio
              ? "✓ Precio guardado en Loyverse: " + precioTxt(antes) + " → " + precioTxt(p.precio) + ". Imprime la etiqueta nueva."
              : "✓ Costo guardado en Loyverse: " + precioTxt(p.costo) + ".";
          if (!fres.optString("advertencia", "").isEmpty()) m += "\n⚠ " + fres.optString("advertencia");
          w.txtEstado.setTextColor(VERDE);
          w.txtEstado.setText(m);
          dlg.dismiss();
          if (ven == w) pintarVentana(w);
        });
      }).start();
    };
    bGuardar.setOnClickListener(v -> {
      if (guardando[0]) return;
      int pv = pp.precioVenta();
      if (pv <= 0) { estado.setTextColor(ROJO); estado.setText("Falta el precio de venta."); return; }
      if (pv % 10 != 0) {
        // Pesos chilenos: la moneda más chica es $10.
        int ajustado = Precio.aDecena(pv);
        pp.tocado = true;
        pp.ponerPrecio(ajustado);
        pp.recalcular();
        estado.setTextColor(ROJO);
        estado.setText("Precio ajustado a " + precioTxt(ajustado) + " (en Chile no hay monedas bajo $10). Revisa y toca Guardar otra vez.");
        return;
      }
      int cr = (int) Math.round(pp.costoReal());
      if (pv == p.precio && (cr <= 0 || cr == p.costo)) { estado.setTextColor(PLOMO); estado.setText("No cambiaste nada."); return; }
      int costoVs = cr > 0 ? cr : p.costo;
      if (costoVs > 0 && pv <= costoVs) {
        new AlertDialog.Builder(this).setTitle("Precio bajo el costo")
            .setMessage("Vendes a " + precioTxt(pv) + " y te cuesta " + precioTxt(costoVs) + ": pierdes plata en cada venta.\n\n¿Guardar igual?")
            .setPositiveButton("Guardar igual", (d, i) -> guardar[0].run())
            .setNegativeButton("No", null).show();
        return;
      }
      guardar[0].run();
    });
    dlg.show();
  }

  private void abrirVerduraRapida() {
    if (!Local.MARIN.id.equals(aj.local)) {
      Toast.makeText(this, "Verdura rápida por ahora funciona solo en Marín 376.", Toast.LENGTH_LONG).show();
      return;
    }
    final Local l = local();
    final boolean[] kg = {false}, creando = {false};
    final Producto[] ultimo = {null};

    ScrollView sv = new ScrollView(this);
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(18), dp(14), dp(18), dp(14));
    sv.addView(c);
    c.addView(texto("Verdura rápida", 24, true, NEGRO));
    c.addView(texto("Se crea en Loyverse, categoría " + CAT_VERDURAS + ".", 14, false, PLOMO), lp(-1, -2, 2));

    c.addView(texto("Nombre", 17, true, NEGRO), lp(-1, -2, 12));
    final EditText nombre = new EditText(this);
    nombre.setTextSize(22);
    nombre.setSingleLine(true);
    nombre.setHint("Ej: Apio");
    nombre.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    c.addView(nombre);

    c.addView(texto("Se vende por", 17, true, NEGRO), lp(-1, -2, 10));
    LinearLayout filaVenta = fila();
    final Button bU = boton("Unidad", VERDE, Color.WHITE), bKg = boton("Peso (kg)", GRIS, NEGRO);
    bU.setTextSize(18);
    bKg.setTextSize(18);
    filaVenta.addView(bU, peso(dp(54), 0));
    filaVenta.addView(bKg, peso(dp(54), 8));
    c.addView(filaVenta, lp(-1, -2, 4));

    final PanelPrecio pp = new PanelPrecio(c, "tipoVerdura", "Costo (por unidad o por kg)");

    final CheckBox crearCod = new CheckBox(this);
    crearCod.setText("Crear código de barras (04…) para la etiqueta");
    crearCod.setTextSize(16);
    crearCod.setChecked(true);
    c.addView(crearCod, lp(-1, -2, 10));

    final TextView estado = texto("", 16, true, NEGRO);
    c.addView(estado, lp(-1, -2, 10));
    final ProgressBar rueda = ruedita(c);
    final Button bCrear = boton("Crear en Loyverse", VERDE_BOTON, Color.WHITE);
    bCrear.setTextSize(22);
    c.addView(bCrear, lp(-1, dp(70), 6));
    final Button bImp = boton("", GRIS, NEGRO);
    bImp.setTextSize(18);
    bImp.setVisibility(View.GONE);
    c.addView(bImp, lp(-1, dp(56), 6));
    Button bCerrar = boton("Cerrar", Color.WHITE, PLOMO);
    bCerrar.setTextSize(18);
    c.addView(bCerrar, lp(-1, dp(50), 6));

    final AlertDialog dlg = new AlertDialog.Builder(this).setView(sv).create();
    bCerrar.setOnClickListener(v -> dlg.dismiss());

    bU.setOnClickListener(v -> { kg[0] = false; colorear(bU, VERDE, Color.WHITE); colorear(bKg, GRIS, NEGRO); });
    bKg.setOnClickListener(v -> { kg[0] = true; colorear(bKg, VERDE, Color.WHITE); colorear(bU, GRIS, NEGRO); });

    final Runnable[] crear = new Runnable[1];
    crear[0] = () -> {
      final String nom = nombre.getText().toString().trim();
      final int pv = pp.precioVenta();
      // Costo que se guarda: el real (materia + producción del tipo, o + % producción en Manual).
      final double cb = pp.costoReal();
      pp.guardarPrefs();   // se recuerda para la próxima
      final boolean conCodigo = crearCod.isChecked();
      final boolean esKg = kg[0];
      creando[0] = true;
      bCrear.setEnabled(false);
      final Runnable parar = ocupado(bCrear, estado, rueda, "Creando", "Creando en Loyverse…");
      final java.util.HashSet<String> skusAntes = new java.util.HashSet<>();
      for (Producto q : cat.productos) skusAntes.add(q.sku);
      new Thread(() -> {
        String err = null;
        org.json.JSONObject res = null;
        Producto hallado = null;
        String categoria = CAT_VERDURAS, codigo = "";
        try {
          if (catalogoLoyverse == null) catalogoLoyverse = Api.catalogoCrear(l);
          org.json.JSONArray cats = catalogoLoyverse.optJSONArray("categorias");
          String catId = null;
          String objetivo = Catalogo.norm(CAT_VERDURAS);
          for (int i = 0; cats != null && i < cats.length() && catId == null; i++)
            if (Catalogo.norm(cats.getJSONObject(i).optString("name")).equals(objetivo)) { catId = cats.getJSONObject(i).optString("id"); categoria = cats.getJSONObject(i).optString("name"); }
          for (int i = 0; cats != null && i < cats.length() && catId == null; i++)
            if (Catalogo.norm(cats.getJSONObject(i).optString("name")).contains(objetivo)) { catId = cats.getJSONObject(i).optString("id"); categoria = cats.getJSONObject(i).optString("name"); }
          if (catId == null) throw new Exception("No encontré la categoría " + CAT_VERDURAS + " en Loyverse.");
          org.json.JSONArray taxIds = new org.json.JSONArray();
          org.json.JSONArray imps = catalogoLoyverse.optJSONArray("impuestos");
          for (int i = 0; imps != null && i < imps.length(); i++) {
            org.json.JSONObject t = imps.getJSONObject(i);
            if (t.optString("name").toLowerCase(Locale.ROOT).contains("iva") || t.optDouble("rate", 0) == 19) { taxIds.put(t.optString("id")); break; }
          }
          if (conCodigo) {
            java.util.HashSet<String> usados = new java.util.HashSet<>();
            for (Producto p : cat.productos) if (!p.barcode.isEmpty()) usados.add(p.barcode.trim());
            codigo = Barras.generarInterno(usados, new java.util.Random());
            if (codigo == null) codigo = "";
          }
          org.json.JSONObject payload = new org.json.JSONObject()
              .put("nombre", nom).put("categoryId", catId).put("categoriaNombre", categoria)
              .put("soldByWeight", esKg).put("precio", pv).put("coste", Math.max(0, Math.round(cb)))
              .put("barcode", codigo).put("trackStock", true).put("stockInicial", 0)
              .put("stockMinimo", org.json.JSONObject.NULL).put("fechaVencimiento", "")
              .put("taxIds", taxIds).put("activo", true)
              .put("proveedor_id", org.json.JSONObject.NULL).put("sector", org.json.JSONObject.NULL)
              .put("responsable", "App Precios");
          res = Api.crearProducto(l, payload);
        } catch (Exception e) {
          if (incierto(e)) {
            // Loyverse se demoró y se cortó la espera: puede que igual lo haya creado. Se mira la
            // lista antes de dejar reintentar, para no crear el mismo producto dos veces.
            try {
              Thread.sleep(3000);
              String nn = Catalogo.norm(nom);
              for (Producto q : Api.catalogo(l))
                if (!skusAntes.contains(q.sku) && Catalogo.norm(q.nombre).equals(nn)) { hallado = q; break; }
              if (hallado == null) err = "Loyverse se demoró y NO creó «" + nom + "». Toca «Crear en Loyverse» otra vez.";
            } catch (Exception e2) {
              err = "Loyverse se demoró y no pude comprobar si se creó «" + nom + "». Búscalo en Loyverse antes de intentar otra vez.";
            }
          } else if (e instanceof java.io.IOException) {
            err = "Sin internet: no se creó. Revisa la conexión e inténtalo de nuevo.";
          } else {
            err = e.getMessage() == null ? "No se pudo crear el producto." : e.getMessage();
          }
        }
        final String fe = err, fcat = categoria;
        final org.json.JSONObject fres = res;
        final Producto fhallado = hallado;
        ui.post(() -> {
          parar.run();
          creando[0] = false;
          bCrear.setEnabled(true);
          bCrear.setText("Crear en Loyverse");
          if (fe != null) { estado.setTextColor(ROJO); estado.setText(fe); return; }
          org.json.JSONObject pj = fres == null ? null : fres.optJSONObject("producto");
          Producto p = fhallado;
          if (p == null) {
            p = new Producto();
            p.sku = pj.optString("ref");
            p.nombre = pj.optString("nombre", nom);
            p.precio = pj.isNull("precio") ? pv : (int) Math.round(pj.optDouble("precio", pv));
            p.barcode = pj.optString("barcode", "");
            p.categoria = fcat;
          }
          if (l.id.equals(aj.local)) {
            List<Producto> nueva = new ArrayList<>(cat.productos);
            nueva.add(p);
            cat.poner(nueva, cat.hora);
            final Catalogo cg = cat;
            new Thread(() -> cg.guardar(getApplicationContext())).start();
            pintarCategorias();
            filtrar();
          }
          String m = "✓ Creado: " + p.nombre + " · SKU " + p.sku + " · " + precioTxt(p.precio)
              + (p.barcode.isEmpty() ? "" : " · código " + p.barcode);
          if (pj == null) m += "\n(Loyverse se demoró, pero sí quedó creado.)";
          else if (pj.has("confirmado") && !pj.optBoolean("confirmado", true)) m += "\n⚠ NO verificado en Loyverse";
          else if (pj.has("enEstaTienda") && !pj.optBoolean("enEstaTienda", true)) m += "\n⚠ No quedó asignado a tu tienda";
          if (fres != null && !fres.optString("advertencia", "").isEmpty()) m += "\n" + fres.optString("advertencia");
          estado.setTextColor(VERDE);
          estado.setText(m + "\nListo para la siguiente verdura.");
          ultimo[0] = p;
          bImp.setText("Imprimir etiqueta de " + p.nombre);
          bImp.setVisibility(View.VISIBLE);
          // Como la web: queda lista para la siguiente (se conserva venta, IVA y margen).
          nombre.setText("");
          pp.costo.setText("");
          pp.tocado = false;
          pp.recalcular();
          nombre.requestFocus();
        });
      }).start();
    };
    bCrear.setOnClickListener(v -> {
      if (creando[0]) return;
      String nom = nombre.getText().toString().trim();
      if (nom.isEmpty()) { estado.setTextColor(ROJO); estado.setText("Falta el nombre."); return; }
      if (pp.precioVenta() <= 0) { estado.setTextColor(ROJO); estado.setText("Falta el precio de venta."); return; }
      int escrito = pp.precioVenta();
      if (escrito % 10 != 0) {
        // Pesos chilenos: la moneda más chica es $10 (y el efectivo se redondea a la decena).
        int ajustado = Precio.aDecena(escrito);
        pp.ponerPrecio(ajustado);
        pp.recalcular();
        estado.setTextColor(ROJO);
        estado.setText("Precio ajustado a " + precioTxt(ajustado) + " (en Chile no hay monedas bajo $10). Revisa y toca Crear otra vez.");
        return;
      }
      Producto igual = null;
      String nn = Catalogo.norm(nom);
      for (Producto p : cat.productos) if (Catalogo.norm(p.nombre).equals(nn)) { igual = p; break; }
      if (igual != null) {
        new AlertDialog.Builder(this).setTitle("Ya existe")
            .setMessage("Ya hay un producto «" + igual.nombre + "» (SKU " + igual.sku + ", " + precioTxt(igual.precio) + ").\n\n¿Crear otro igual de todas formas?")
            .setPositiveButton("Crear igual", (d, i) -> crear[0].run())
            .setNegativeButton("No", null).show();
        return;
      }
      crear[0].run();
    });
    bImp.setOnClickListener(v -> { if (ultimo[0] != null) abrirEtiqueta(ultimo[0]); });
    dlg.show();
    nombre.requestFocus();
  }

  // ---------------------------------------------------------------- impresión
  /** Bytes de N copias + avance final para que la última etiqueta salga entera por el borde de corte. */
  private byte[] bytesDe(LabelRenderer.Resultado r, int copias) {
    if (aj.modo == Ajustes.MODO_TSPL)
      return Raster.tspl(r.bits, r.w, r.h, aj.anchoMm, aj.altoMm, aj.gapMm, aj.invertir, copias);
    byte[] una = Raster.escpos(r.bits, r.w, r.h, aj.avance);
    byte[] sep = Raster.separador(r.w, aj.espacioMm);   // espacio + línea punteada entre etiquetas
    byte[] fin = Raster.avanzar(aj.finalMm);
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    for (int i = 0; i < copias; i++) {
      out.write(una, 0, una.length);
      if (i < copias - 1) out.write(sep, 0, sep.length);
    }
    out.write(fin, 0, fin.length);
    return out.toByteArray();
  }

  private void imprimir(final Ventana w) {
    if (imprimiendo || w.confirmando > 0) return;
    if (aj.mac.isEmpty()) { elegirImpresora(() -> imprimir(w)); return; }
    final int n = w.cantidad;
    enviar(bytesDe(render(etiquetaDe(w)), n), w,
        "Listo: " + n + " etiqueta" + (n == 1 ? "" : "s") + ". Corta cuando termine de salir el papel.");
  }

  private void imprimirPrueba() {
    if (aj.mac.isEmpty()) { elegirImpresora(this::imprimirPrueba); return; }
    EtiquetaPrecio e = new EtiquetaPrecio();
    e.nombre = "PRUEBA Coca Cola 1,5 L";
    e.precio = "$1.990";
    e.codigo = "0412345678905";   // código interno (empieza en 0) → Code 128
    e.elab = fmt(Calendar.getInstance());
    e.vence = "31/12/2026";
    enviar(bytesDe(render(e), 1), null, "Etiqueta de prueba enviada. Pasa el lector: debe leer 0412345678905.");
  }

  private void estado(Ventana w, String t, int color) {
    if (w != null && ven == w) { w.txtEstado.setTextColor(color); w.txtEstado.setText(t); pintarVentana(w); }
    else { Toast.makeText(this, t, Toast.LENGTH_LONG).show(); }
  }

  private void enviar(final byte[] datos, final Ventana w, final String ok) {
    imprimiendo = true;
    estado(w, "Conectando con la impresora…", NEGRO);
    new Thread(() -> {
      String err = null;
      try (Impresora imp = new Impresora(aj.mac)) {
        ui.post(() -> { if (w != null && ven == w) w.txtEstado.setText("Imprimiendo…"); });
        imp.enviar(datos);
      } catch (Exception e) {
        err = e.getMessage() == null ? "No se pudo imprimir." : e.getMessage();
      }
      final String fe = err;
      ui.post(() -> {
        imprimiendo = false;
        // Bien impreso: se cierra la ventana y se vuelve a la lista (como en Loyverse).
        if (fe == null && w != null && ven == w) { w.dlg.dismiss(); Toast.makeText(this, ok, Toast.LENGTH_LONG).show(); return; }
        estado(w, fe == null ? ok : fe, fe == null ? VERDE : ROJO);
      });
    }).start();
  }

  // ---------------------------------------------------------------- impresora y ajustes
  private void elegirImpresora(final Runnable despues) {
    BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
    if (ad == null) { Toast.makeText(this, "Este equipo no tiene Bluetooth.", Toast.LENGTH_LONG).show(); return; }
    if (!ad.isEnabled()) {
      new AlertDialog.Builder(this).setTitle("Enciende el Bluetooth")
          .setMessage("El Bluetooth del equipo está apagado. Enciéndelo (desliza la barra de arriba hacia abajo y toca Bluetooth) e inténtalo de nuevo.")
          .setPositiveButton("Entendido", null).show();
      return;
    }
    final List<BluetoothDevice> devs = new ArrayList<>(ad.getBondedDevices());
    if (devs.isEmpty()) {
      new AlertDialog.Builder(this).setTitle("No hay impresoras emparejadas")
          .setMessage("Primero empareja la impresora en Ajustes del equipo → Bluetooth (clave habitual: 0000 o 1234). Luego vuelve aquí.\n\nSi la impresora viene dentro del POS, suele aparecer como «InnerPrinter».")
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
          if (ven != null) pintarVentana(ven);
          if (despues != null) despues.run();
        }).setNegativeButton("Cancelar", null).show();
  }

  private EditText campoNum(LinearLayout cont, String rotulo, int valor) {
    cont.addView(texto(rotulo, 16, false, NEGRO), lp(-1, -2, 10));
    EditText e = new EditText(this);
    e.setInputType(InputType.TYPE_CLASS_NUMBER);
    e.setText(String.valueOf(valor));
    e.setTextSize(20);
    cont.addView(e);
    return e;
  }

  private Spinner listaOpciones(LinearLayout cont, String rotulo, String[] opciones, int sel) {
    cont.addView(texto(rotulo, 16, false, NEGRO), lp(-1, -2, 10));
    Spinner s = new Spinner(this);
    s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, opciones));
    s.setSelection(sel);
    cont.addView(s);
    return s;
  }

  private static int num(EditText e, int def, int min, int max) {
    try { return Math.max(min, Math.min(max, Integer.parseInt(e.getText().toString().trim()))); } catch (Exception x) { return def; }
  }

  private void abrirAjustes() {
    ScrollView sv = new ScrollView(this);
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(18), dp(4), dp(18), dp(8));
    sv.addView(c);
    final Spinner texto = listaOpciones(c, "Tamaño de la letra", new String[]{"Pequeña", "Normal", "Grande"}, aj.texto);
    final EditText alto = campoNum(c, "Alto de la etiqueta en mm (32 es lo normal; las barras deben medir 12 mm o más)", aj.altoMm);
    final EditText espacio = campoNum(c, "Espacio para cortar entre etiquetas, en mm (con línea punteada; 0 = pegadas)", aj.espacioMm);
    final EditText fin = campoNum(c, "Papel extra al terminar, en mm (para cortar sin cortar las barras; 12 en el POS)", aj.finalMm);
    final Spinner modo = listaOpciones(c, "Lenguaje de la impresora", new String[]{"ESC/POS (la mayoría de 58 mm)", "TSPL (impresoras de etiquetas)"}, aj.modo);
    final EditText ancho = campoNum(c, "Ancho imprimible en puntos (384 es lo normal en 58 mm)", aj.anchoPuntos);
    final Spinner av = listaOpciones(c, "Entre etiqueta y etiqueta (ESC/POS)", new String[]{"Nada (seguidas)", "Avanzar a la siguiente etiqueta (FF)", "Avanzar a la siguiente etiqueta (GS FF)", "Avanzar 5 mm"}, aj.avance);
    final EditText anchoMm = campoNum(c, "TSPL: ancho de la etiqueta en mm", aj.anchoMm);
    final EditText gap = campoNum(c, "TSPL: separación entre etiquetas en mm", aj.gapMm);
    final CheckBox inv = new CheckBox(this);
    inv.setText("TSPL: invertir colores (si sale todo negro)");
    inv.setTextSize(16);
    inv.setChecked(aj.invertir);
    c.addView(inv, lp(-1, -2, 10));
    new AlertDialog.Builder(this).setTitle("Ajustes de impresión").setView(sv)
        .setPositiveButton("Guardar", (d, w) -> {
          aj.texto = texto.getSelectedItemPosition();
          aj.altoMm = num(alto, 32, 20, 100);
          aj.finalMm = num(fin, 12, 0, 40);
          aj.espacioMm = num(espacio, 6, 0, 30);
          aj.modo = modo.getSelectedItemPosition();
          aj.anchoPuntos = num(ancho, 384, 200, 832);
          aj.avance = av.getSelectedItemPosition();
          aj.anchoMm = num(anchoMm, 58, 20, 110);
          aj.gapMm = num(gap, 3, 0, 10);
          aj.invertir = inv.isChecked();
          aj.guardar(this);
          if (ven != null) pintarVentana(ven);
        }).setNegativeButton("Cancelar", null).show();
  }
}
