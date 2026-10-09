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
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;

/** Pantalla única: buscar producto → cantidad → Imprimir. Letra grande y pocos toques. */
public class MainActivity extends Activity {
  private static final int VERDE = Color.parseColor("#1B7F5C"), VERDE_CLARO = Color.parseColor("#CFE8DC"),
      GRIS = Color.parseColor("#E6E6E6"), GRIS_FONDO = Color.parseColor("#F4F4F4"),
      ROJO = Color.parseColor("#B3261E"), NEGRO = Color.parseColor("#111111"), PLOMO = Color.parseColor("#555555");
  private static final long VIEJO_MS = 6L * 3600 * 1000;   // se actualiza solo si la lista tiene más de 6 h

  private final Handler ui = new Handler(Looper.getMainLooper());
  private SharedPreferences prefs;
  private Ajustes aj;
  private Catalogo cat;
  private boolean cargandoLista = false, bajando = false, imprimiendo = false;

  private Producto elegido;
  private int cantidad = 1;
  private String vence = "";           // DD/MM/AAAA
  private boolean venceManual = false;
  private int confirmando = 0;          // >0 mientras se confirma precio/código con el servidor
  private int fichaToken = 0;

  private Button[] btnLocal;
  private TextView txtImpresora, txtEstado, txtAviso, txtLista, txtVenceInfo, txtCant;
  private EditText buscador;
  private LinearLayout cajaResultados, cajaElegido;
  private ImageView vista;
  private Button btnImprimir, btnVence, btnSinVence;
  private final Runnable buscarLuego = this::pintarResultados;

  // ---------------------------------------------------------------- utilidades de vista
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
    b.setPadding(dp(12), dp(8), dp(12), dp(8));
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

  private LinearLayout.LayoutParams peso(int h, int ml) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, h, 1);
    p.leftMargin = dp(ml);
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

  private LinearLayout fila() {
    LinearLayout f = new LinearLayout(this);
    f.setOrientation(LinearLayout.HORIZONTAL);
    f.setGravity(Gravity.CENTER_VERTICAL);
    return f;
  }

  static String precioTxt(int p) {
    if (p <= 0) return "";
    return "$" + String.format(Locale.ROOT, "%,d", p).replace(',', '.');
  }

  private Local local() { return Local.de(aj.local); }

  // ---------------------------------------------------------------- ciclo de vida
  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("precios", MODE_PRIVATE);
    aj = Ajustes.cargar(this);
    cat = new Catalogo(local());
    construir();
    abrirLocal();
  }

  /** Carga la lista guardada del local (en segundo plano) y la actualiza si está vieja. */
  private void abrirLocal() {
    final Local l = local();
    cat = new Catalogo(l);
    cargandoLista = true;
    elegido = null;
    pintarTodo();
    new Thread(() -> {
      final Catalogo c = Catalogo.cargar(getApplicationContext(), l);
      ui.post(() -> {
        if (!l.id.equals(aj.local)) return;   // cambiaron de local mientras cargaba
        cat = c;
        cargandoLista = false;
        pintarTodo();
        if (c.productos.isEmpty() || System.currentTimeMillis() - c.hora > VIEJO_MS) actualizarProductos();
      });
    }).start();
  }

  // ---------------------------------------------------------------- pantalla
  private void construir() {
    ScrollView sv = new ScrollView(this);
    sv.setBackgroundColor(Color.WHITE);
    sv.setFillViewport(true);
    LinearLayout raiz = new LinearLayout(this);
    raiz.setOrientation(LinearLayout.VERTICAL);
    raiz.setPadding(dp(16), dp(12), dp(16), dp(30));
    sv.addView(raiz);

    raiz.addView(texto("Etiquetas de precio", 28, true, NEGRO));

    // Local
    LinearLayout filaLocal = fila();
    btnLocal = new Button[Local.TODOS.length];
    for (int i = 0; i < Local.TODOS.length; i++) {
      final Local l = Local.TODOS[i];
      Button bl = boton(l.nombre, GRIS, NEGRO);
      bl.setOnClickListener(v -> cambiarLocal(l));
      btnLocal[i] = bl;
      filaLocal.addView(bl, peso(dp(60), i == 0 ? 0 : 8));
    }
    raiz.addView(filaLocal, lp(-1, -2, 10));

    // Impresora
    LinearLayout filaImp = fila();
    txtImpresora = texto("", 17, false, NEGRO);
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
    raiz.addView(filaImp, lp(-1, -2, 8));

    // 1. Buscar
    raiz.addView(texto("1. Busca el producto", 22, true, NEGRO), lp(-1, -2, 16));
    LinearLayout filaBus = fila();
    buscador = new EditText(this);
    buscador.setHint("Nombre, código o SKU");
    buscador.setTextSize(24);
    buscador.setSingleLine(true);
    buscador.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    buscador.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    buscador.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable e) {
        ui.removeCallbacks(buscarLuego);
        ui.postDelayed(buscarLuego, 150);
      }
    });
    // Lector de códigos (teclado) o Enter: si el texto es un código/SKU exacto, se elige directo.
    buscador.setOnEditorActionListener((v, id, ev) -> {
      boolean enter = id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_DONE
          || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN);
      if (!enter) return false;
      String q = buscador.getText().toString().trim();
      Producto p = cat.exacto(q);
      if (p == null) {
        List<Producto> r = cat.buscar(q, 2);
        if (r.size() == 1) p = r.get(0);
      }
      if (p != null) elegir(p); else pintarResultados();
      return true;
    });
    filaBus.addView(buscador, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    Button bBorrar = boton("✕", GRIS, NEGRO);
    bBorrar.setOnClickListener(v -> { buscador.setText(""); buscador.requestFocus(); });
    LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(dp(60), dp(56));
    pb.leftMargin = dp(6);
    filaBus.addView(bBorrar, pb);
    raiz.addView(filaBus, lp(-1, -2, 4));
    cajaResultados = new LinearLayout(this);
    cajaResultados.setOrientation(LinearLayout.VERTICAL);
    raiz.addView(cajaResultados, lp(-1, -2, 4));

    // Producto elegido + cantidad + vencimiento
    cajaElegido = new LinearLayout(this);
    cajaElegido.setOrientation(LinearLayout.VERTICAL);
    raiz.addView(cajaElegido, lp(-1, -2, 6));

    raiz.addView(texto("2. Cuántas etiquetas", 22, true, NEGRO), lp(-1, -2, 16));
    LinearLayout filaCant = fila();
    Button menos = boton("−", GRIS, NEGRO);
    menos.setTextSize(30);
    menos.setOnClickListener(v -> { cantidad = Math.max(1, cantidad - 1); pintarCantidad(); });
    txtCant = texto("1", 32, true, NEGRO);
    txtCant.setGravity(Gravity.CENTER);
    Button mas = boton("+", GRIS, NEGRO);
    mas.setTextSize(30);
    mas.setOnClickListener(v -> { cantidad = Math.min(100, cantidad + 1); pintarCantidad(); });
    filaCant.addView(menos, new LinearLayout.LayoutParams(dp(72), dp(64)));
    filaCant.addView(txtCant, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
    filaCant.addView(mas, new LinearLayout.LayoutParams(dp(72), dp(64)));
    for (final int n : new int[]{5, 10, 20}) {
      Button bn = boton(String.valueOf(n), GRIS, NEGRO);
      bn.setOnClickListener(v -> { cantidad = n; pintarCantidad(); });
      filaCant.addView(bn, peso(dp(64), 6));
    }
    raiz.addView(filaCant, lp(-1, -2, 6));

    raiz.addView(texto("3. Vencimiento (opcional)", 22, true, NEGRO), lp(-1, -2, 16));
    LinearLayout filaVence = fila();
    btnSinVence = boton("Sin fecha", GRIS, NEGRO);
    btnSinVence.setOnClickListener(v -> { vence = ""; venceManual = true; pintarVence(); pintarVista(); });
    filaVence.addView(btnSinVence, peso(dp(64), 0));
    btnVence = boton("Poner fecha", GRIS, NEGRO);
    btnVence.setOnClickListener(v -> elegirFecha());
    filaVence.addView(btnVence, peso(dp(64), 8));
    raiz.addView(filaVence, lp(-1, -2, 6));
    txtVenceInfo = texto("", 16, false, PLOMO);
    raiz.addView(txtVenceInfo, lp(-1, -2, 4));

    raiz.addView(texto("Así queda la etiqueta", 18, true, NEGRO), lp(-1, -2, 16));
    vista = new ImageView(this);
    vista.setAdjustViewBounds(true);
    vista.setBackgroundColor(Color.parseColor("#DDDDDD"));
    vista.setPadding(dp(6), dp(6), dp(6), dp(6));
    raiz.addView(vista, lp(-1, -2, 6));

    txtAviso = texto("", 18, true, ROJO);
    raiz.addView(txtAviso, lp(-1, -2, 10));
    btnImprimir = boton("Imprimir", VERDE, Color.WHITE);
    btnImprimir.setTextSize(26);
    btnImprimir.setOnClickListener(v -> imprimir());
    raiz.addView(btnImprimir, lp(-1, dp(84), 6));
    txtEstado = texto("", 18, false, NEGRO);
    raiz.addView(txtEstado, lp(-1, -2, 10));

    LinearLayout filaAbajo = fila();
    Button bAct = boton("Actualizar productos", GRIS, NEGRO);
    bAct.setTextSize(17);
    bAct.setOnClickListener(v -> actualizarProductos());
    filaAbajo.addView(bAct, peso(dp(64), 0));
    Button bPrueba = boton("Imprimir etiqueta de prueba", GRIS, NEGRO);
    bPrueba.setTextSize(17);
    bPrueba.setOnClickListener(v -> imprimirPrueba());
    filaAbajo.addView(bPrueba, peso(dp(64), 8));
    raiz.addView(filaAbajo, lp(-1, -2, 22));
    txtLista = texto("", 15, false, PLOMO);
    raiz.addView(txtLista, lp(-1, -2, 8));

    setContentView(sv);
  }

  // ---------------------------------------------------------------- catálogo
  private void cambiarLocal(Local l) {
    if (l.id.equals(aj.local)) return;
    aj.local = l.id;
    aj.guardar(this);
    buscador.setText("");
    vence = "";
    venceManual = false;
    txtEstado.setText("");
    abrirLocal();
  }

  private void actualizarProductos() {
    if (bajando) return;
    bajando = true;
    final Local l = local();
    txtEstado.setTextColor(NEGRO);
    txtEstado.setText("Actualizando productos de " + l.nombre + "…");
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
          if (elegido != null) { Producto p = c.exacto(elegido.sku); if (p != null) elegido = p; }
          txtEstado.setText("Productos actualizados (" + miles(c.productos.size()) + ").");
          pintarTodo();
        });
      } catch (final Exception e) {
        ui.post(() -> {
          bajando = false;
          if (!l.id.equals(aj.local)) return;
          txtEstado.setTextColor(ROJO);
          if (cat.productos.isEmpty()) txtEstado.setText("Sin internet y sin lista guardada. Conéctate a internet y toca «Actualizar productos».");
          else txtEstado.setText("Sin internet: uso la lista guardada (" + horaTxt(cat.hora) + ").");
        });
      }
    }).start();
  }

  private static String miles(int n) { return String.format(Locale.ROOT, "%,d", n).replace(',', '.'); }

  private static String horaTxt(long t) {
    if (t <= 0) return "sin fecha";
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(t);
    return String.format(Locale.ROOT, "%02d/%02d %02d:%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1,
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
  }

  // ---------------------------------------------------------------- recientes (por local)
  private List<Producto> recientes() {
    List<Producto> out = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs.getString("recientes_" + aj.local, "[]"));
      for (int i = 0; i < a.length(); i++) { Producto p = cat.exacto(a.optString(i)); if (p != null) out.add(p); }
    } catch (Exception ignore) {}
    return out;
  }

  private void guardarReciente(String sku) {
    try {
      JSONArray a = new JSONArray(prefs.getString("recientes_" + aj.local, "[]"));
      JSONArray n = new JSONArray().put(sku);
      for (int i = 0; i < a.length() && n.length() < 8; i++) if (!sku.equals(a.optString(i))) n.put(a.optString(i));
      prefs.edit().putString("recientes_" + aj.local, n.toString()).apply();
    } catch (Exception ignore) {}
  }

  // ---------------------------------------------------------------- pintar
  private void pintarTodo() {
    for (int i = 0; i < btnLocal.length; i++) {
      boolean on = Local.TODOS[i].id.equals(aj.local);
      colorear(btnLocal[i], on ? VERDE : GRIS, on ? Color.WHITE : NEGRO);
    }
    txtImpresora.setText(aj.mac.isEmpty() ? "Impresora: sin elegir" : "Impresora: " + (aj.nombreImpresora.isEmpty() ? aj.mac : aj.nombreImpresora));
    txtLista.setText(cargandoLista ? "Abriendo lista guardada…"
        : (cat.productos.isEmpty() ? "Sin lista guardada de " + cat.local.nombre + "."
        : "Lista de " + cat.local.nombre + ": " + miles(cat.productos.size()) + " productos · actualizada " + horaTxt(cat.hora)));
    pintarResultados();
    pintarElegido();
    pintarCantidad();
    pintarVence();
    pintarVista();
  }

  private Button botonProducto(final Producto p) {
    String cod = p.codigo();
    Button b = boton(p.nombre + "\n" + (p.precio > 0 ? precioTxt(p.precio) : "sin precio") + "   ·   " + cod, GRIS, NEGRO);
    b.setTextSize(18);
    b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    b.setOnClickListener(v -> elegir(p));
    return b;
  }

  private void pintarResultados() {
    if (cajaResultados == null) return;
    cajaResultados.removeAllViews();
    String q = buscador.getText().toString().trim();
    if (cargandoLista) { cajaResultados.addView(texto("Abriendo lista guardada…", 18, false, PLOMO)); return; }
    if (cat.productos.isEmpty()) {
      cajaResultados.addView(texto(bajando ? "Bajando productos… (solo la primera vez demora)" : "No hay productos guardados. Toca «Actualizar productos».", 18, false, PLOMO));
      return;
    }
    if (q.isEmpty()) {
      if (elegido != null) return;
      List<Producto> rec = recientes();
      if (rec.isEmpty()) { cajaResultados.addView(texto("Escribe parte del nombre o pasa el lector por el código.", 18, false, PLOMO)); return; }
      cajaResultados.addView(texto("Últimos impresos:", 17, false, PLOMO));
      for (Producto p : rec) cajaResultados.addView(botonProducto(p), lp(-1, -2, 6));
      return;
    }
    // Código escaneado completo: se elige solo (el lector escribe todo de golpe).
    Producto ex = cat.exacto(q);
    if (ex != null && q.length() >= 8 && q.matches("[0-9]+") && ex != elegido) { elegir(ex); return; }
    List<Producto> r = cat.buscar(q, 30);
    if (r.isEmpty()) { cajaResultados.addView(texto("No encontré «" + q + "». Prueba con otra palabra o toca «Actualizar productos».", 18, false, ROJO)); return; }
    for (Producto p : r) cajaResultados.addView(botonProducto(p), lp(-1, -2, 6));
    if (r.size() >= 30) cajaResultados.addView(texto("Hay más resultados: escribe una palabra más.", 16, false, PLOMO), lp(-1, -2, 6));
  }

  private void pintarElegido() {
    cajaElegido.removeAllViews();
    if (elegido == null) return;
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(14), dp(10), dp(14), dp(12));
    GradientDrawable g = new GradientDrawable();
    g.setColor(VERDE_CLARO);
    g.setCornerRadius(dp(12));
    c.setBackground(g);
    c.addView(texto(elegido.nombre, 22, true, NEGRO));
    LinearLayout f = fila();
    f.addView(texto(elegido.precio > 0 ? precioTxt(elegido.precio) : "Sin precio", 30, true, elegido.precio > 0 ? NEGRO : ROJO),
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    Button cambiar = boton("Cambiar", Color.WHITE, NEGRO);
    cambiar.setTextSize(17);
    cambiar.setOnClickListener(v -> {
      elegido = null;
      buscador.setText("");
      buscador.requestFocus();
      InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (im != null) im.showSoftInput(buscador, 0);
      pintarTodo();
    });
    f.addView(cambiar);
    c.addView(f, lp(-1, -2, 2));
    c.addView(texto("Código: " + elegido.codigo() + (elegido.barcode.isEmpty() ? " (SKU, no tiene código de barras)" : ""), 16, false, NEGRO), lp(-1, -2, 4));
    if (confirmando > 0) c.addView(texto("Confirmando precio con el servidor…", 15, false, PLOMO), lp(-1, -2, 4));
    cajaElegido.addView(c);
  }

  private void pintarCantidad() {
    txtCant.setText(String.valueOf(cantidad));
    validar();
  }

  private void pintarVence() {
    boolean hay = !vence.isEmpty();
    colorear(btnSinVence, hay ? GRIS : VERDE, hay ? NEGRO : Color.WHITE);
    colorear(btnVence, hay ? VERDE : GRIS, hay ? Color.WHITE : NEGRO);
    btnVence.setText(hay ? "Vence " + vence : "Poner fecha");
  }

  private EtiquetaPrecio etiquetaDe(Producto p) {
    EtiquetaPrecio e = new EtiquetaPrecio();
    e.nombre = p.nombre;
    e.precio = precioTxt(p.precio);
    e.codigo = p.codigo();
    e.vence = vence;
    return e;
  }

  private LabelRenderer.Resultado render(EtiquetaPrecio e) {
    return LabelRenderer.dibujar(e, aj.anchoPuntos, aj.altoPuntos(), aj.texto);
  }

  private void pintarVista() {
    if (elegido == null) { vista.setImageBitmap(null); vista.setVisibility(View.GONE); validar(); return; }
    LabelRenderer.Resultado r = render(etiquetaDe(elegido));
    vista.setVisibility(View.VISIBLE);
    vista.setImageBitmap(r.vista);
    txtAviso.setTag(r.aviso);
    validar();
  }

  private String motivo() {
    if (elegido == null) return "Busca y elige un producto.";
    if (aj.mac.isEmpty()) return "Falta elegir la impresora (botón «Impresora»).";
    return "";
  }

  private void validar() {
    String m = motivo();
    Object av = txtAviso.getTag();
    String aviso = m.isEmpty() ? (av == null ? "" : av.toString()) : m;
    if (m.isEmpty() && elegido != null && elegido.precio <= 0) aviso = "Ojo: este producto no tiene precio en el sistema.";
    txtAviso.setText(aviso);
    boolean ok = m.isEmpty() && !imprimiendo && confirmando == 0;
    btnImprimir.setEnabled(ok);
    btnImprimir.setAlpha(ok ? 1f : 0.45f);
    btnImprimir.setText(imprimiendo ? "Imprimiendo…" : (confirmando > 0 ? "Confirmando precio…"
        : (elegido == null ? "Imprimir" : "Imprimir " + cantidad + " etiqueta" + (cantidad == 1 ? "" : "s"))));
  }

  // ---------------------------------------------------------------- elegir producto
  private void elegir(final Producto p) {
    elegido = p;
    cantidad = 1;
    vence = "";
    venceManual = false;
    txtVenceInfo.setText("");
    buscador.setText("");
    InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (im != null) im.hideSoftInputFromWindow(buscador.getWindowToken(), 0);
    buscador.clearFocus();
    guardarReciente(p.sku);
    pintarTodo();
    confirmarConServidor(p);
  }

  /**
   * Pide al servidor el precio y código actuales (la lista guardada puede tener horas) y la fecha
   * del lote más próximo a vencer (como la web). Si no responde en 6 s se imprime con la lista.
   */
  private void confirmarConServidor(final Producto p) {
    final int tk = ++fichaToken;
    final Local l = local();
    confirmando = 1;
    final Runnable rendirse = () -> {
      if (tk != fichaToken || confirmando == 0) return;
      confirmando = 0;
      txtEstado.setTextColor(PLOMO);
      txtEstado.setText("No pude confirmar el precio (sin internet): uso la lista guardada.");
      pintarElegido();
      validar();
    };
    ui.postDelayed(rendirse, 6000);
    new Thread(() -> {
      Api.Ficha f = null;
      try { f = Api.ficha(l, p.sku); } catch (Exception ignore) {}
      final Api.Ficha ff = f;
      ui.post(() -> {
        if (tk != fichaToken) return;
        ui.removeCallbacks(rendirse);
        boolean seRindio = confirmando == 0;
        confirmando = 0;
        if (ff != null) {
          List<String> cambios = new ArrayList<>();
          if (ff.precio != null && ff.precio != p.precio) {
            cambios.add("precio " + precioTxt(p.precio) + " → " + precioTxt(ff.precio));
            p.precio = ff.precio;
          }
          if (ff.barcode != null && !ff.barcode.equals(p.barcode)) {
            cambios.add("código " + p.codigo() + " → " + (ff.barcode.isEmpty() ? p.sku : ff.barcode));
            p.barcode = ff.barcode;
          }
          if (!cambios.isEmpty()) {
            cat.poner(new ArrayList<>(cat.productos), cat.hora);
            new Thread(() -> cat.guardar(getApplicationContext())).start();
            txtEstado.setTextColor(VERDE);
            txtEstado.setText("Actualizado desde el servidor: " + android.text.TextUtils.join(", ", cambios) + ".");
          } else if (!seRindio) {
            txtEstado.setText("");
          }
          if (!venceManual && ff.vence != null) {
            vence = ff.vence;
            txtVenceInfo.setText("Fecha tomada del lote más próximo a vencer. Puedes cambiarla.");
          }
        } else if (!seRindio) {
          txtEstado.setTextColor(PLOMO);
          txtEstado.setText("No pude confirmar el precio (sin internet): uso la lista guardada.");
        }
        pintarElegido();
        pintarVence();
        pintarVista();
      });
    }).start();
  }

  private void elegirFecha() {
    Calendar c = Calendar.getInstance();
    if (!vence.isEmpty()) {
      try {
        String[] d = vence.split("/");
        c.set(Integer.parseInt(d[2]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[0]));
      } catch (Exception ignore) {}
    }
    new DatePickerDialog(this, (v, y, m, d) -> {
      vence = String.format(Locale.ROOT, "%02d/%02d/%04d", d, m + 1, y);
      venceManual = true;
      txtVenceInfo.setText("");
      pintarVence();
      pintarVista();
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
  }

  // ---------------------------------------------------------------- impresión
  private byte[] bytesDe(LabelRenderer.Resultado r, int copias) {
    if (aj.modo == Ajustes.MODO_TSPL)
      return Raster.tspl(r.bits, r.w, r.h, aj.anchoMm, aj.altoMm, aj.gapMm, aj.invertir, copias);
    byte[] una = Raster.escpos(r.bits, r.w, r.h, aj.avance);
    byte[] out = new byte[una.length * copias];
    for (int i = 0; i < copias; i++) System.arraycopy(una, 0, out, i * una.length, una.length);
    return out;
  }

  private void imprimir() {
    if (imprimiendo || confirmando > 0 || !motivo().isEmpty()) return;
    final int n = cantidad;
    enviar(bytesDe(render(etiquetaDe(elegido)), n), "Listo: " + n + " etiqueta" + (n == 1 ? "" : "s") + " de " + elegido.nombre + ".");
  }

  private void imprimirPrueba() {
    if (aj.mac.isEmpty()) { Toast.makeText(this, "Primero elige la impresora (botón «Impresora»).", Toast.LENGTH_LONG).show(); return; }
    EtiquetaPrecio e = new EtiquetaPrecio();
    e.nombre = "PRUEBA Coca Cola 1,5 L";
    e.precio = "$1.990";
    e.codigo = "0412345678905";   // código interno (empieza en 0) → Code 128
    e.vence = "31/12/2026";
    enviar(bytesDe(render(e), 1), "Etiqueta de prueba enviada. Pasa el lector: debe leer 0412345678905.");
  }

  private void enviar(final byte[] datos, final String ok) {
    imprimiendo = true;
    validar();
    txtEstado.setTextColor(NEGRO);
    txtEstado.setText("Conectando con la impresora…");
    new Thread(() -> {
      String err = null;
      try (Impresora imp = new Impresora(aj.mac)) {
        ui.post(() -> txtEstado.setText("Imprimiendo…"));
        imp.enviar(datos);
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
    if (!ad.isEnabled()) {
      new AlertDialog.Builder(this).setTitle("Enciende el Bluetooth")
          .setMessage("El Bluetooth del equipo está apagado. Enciéndelo (desliza la barra de arriba hacia abajo y toca Bluetooth) y vuelve a tocar «Impresora».")
          .setPositiveButton("Entendido", null).show();
      return;
    }
    final List<BluetoothDevice> devs = new ArrayList<>(ad.getBondedDevices());
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
    cont.addView(texto(rotulo, 16, false, NEGRO), lp(-1, -2, 10));
    EditText e = new EditText(this);
    e.setInputType(InputType.TYPE_CLASS_NUMBER);
    e.setText(String.valueOf(valor));
    e.setTextSize(20);
    cont.addView(e);
    return e;
  }

  private Spinner lista(LinearLayout cont, String rotulo, String[] opciones, int sel) {
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
    final Spinner texto = lista(c, "Tamaño de la letra", new String[]{"Pequeña", "Normal", "Grande"}, aj.texto);
    final EditText alto = campoNum(c, "Alto de la etiqueta en mm (32 es lo normal; las barras deben medir 12 mm o más)", aj.altoMm);
    final Spinner modo = lista(c, "Lenguaje de la impresora", new String[]{"ESC/POS (la mayoría de 58 mm)", "TSPL (impresoras de etiquetas)"}, aj.modo);
    final EditText ancho = campoNum(c, "Ancho imprimible en puntos (384 es lo normal en 58 mm)", aj.anchoPuntos);
    final Spinner av = lista(c, "Al terminar cada etiqueta (ESC/POS)", new String[]{"No avanzar", "Avanzar a la siguiente etiqueta (FF)", "Avanzar a la siguiente etiqueta (GS FF)", "Avanzar 5 mm"}, aj.avance);
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
          aj.modo = modo.getSelectedItemPosition();
          aj.anchoPuntos = num(ancho, 384, 200, 832);
          aj.avance = av.getSelectedItemPosition();
          aj.anchoMm = num(anchoMm, 58, 20, 110);
          aj.gapMm = num(gap, 3, 0, 10);
          aj.invertir = inv.isChecked();
          aj.guardar(this);
          pintarTodo();
        }).setNegativeButton("Cancelar", null).show();
  }
}
