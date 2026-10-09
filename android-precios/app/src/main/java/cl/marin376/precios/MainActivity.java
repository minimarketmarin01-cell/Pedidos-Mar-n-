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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
  private Spinner spCategoria;
  private EditText buscador;
  private Button btnBuscar;
  private ListView lista;
  private final Adaptador adaptador = new Adaptador();
  private final Runnable buscarLuego = this::filtrar;
  private boolean pintandoCategorias = false;

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
    raiz.addView(barra, new LinearLayout.LayoutParams(-1, dp(56)));

    // Fila: categoría (o buscador) + lupa
    LinearLayout filtro = fila();
    filtro.setPadding(dp(8), 0, 0, 0);
    spCategoria = new Spinner(this, Spinner.MODE_DROPDOWN);
    spCategoria.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
        if (pintandoCategorias) return;
        prefs.edit().putString("categoria_" + aj.local, pos == 0 ? "" : (String) a.getItemAtPosition(pos)).apply();
        filtrar();
        lista.setSelection(0);
      }
      public void onNothingSelected(AdapterView<?> a) {}
    });
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

  private void pintarCategorias() {
    pintandoCategorias = true;
    List<String> ops = new ArrayList<>();
    ops.add(TODOS);
    ops.addAll(cat.categorias);
    ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, ops) {
      @Override public View getView(int pos, View v, ViewGroup p) {
        TextView t = (TextView) super.getView(pos, v, p);
        t.setTextSize(20);
        t.setTextColor(NEGRO);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
      }
      @Override public View getDropDownView(int pos, View v, ViewGroup p) {
        TextView t = (TextView) super.getDropDownView(pos, v, p);
        t.setTextSize(20);
        t.setTextColor(NEGRO);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        return t;
      }
    };
    ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spCategoria.setAdapter(ad);
    String guardada = prefs.getString("categoria_" + aj.local, "");
    int sel = 0;
    for (int i = 1; i < ops.size(); i++) if (ops.get(i).equalsIgnoreCase(guardada)) sel = i;
    spCategoria.setSelection(sel, false);
    ui.post(() -> pintandoCategorias = false);
  }

  private String categoriaElegida() {
    int pos = spCategoria.getSelectedItemPosition();
    return pos <= 0 ? null : (String) spCategoria.getItemAtPosition(pos);
  }

  private void filtrar() {
    if (buscando) {
      String q = buscador.getText().toString().trim();
      visibles = q.isEmpty() ? new ArrayList<>() : cat.buscar(q, 200);
      // Código escaneado completo: abre directo (el lector escribe todo de golpe).
      Producto ex = cat.exacto(q);
      if (ex != null && q.length() >= 8 && q.matches("[0-9]+")) { buscador.setText(""); abrirEtiqueta(ex); }
    } else {
      visibles = cat.deCategoria(categoriaElegida());
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
      "Impresora: " + (aj.mac.isEmpty() ? "sin elegir" : (aj.nombreImpresora.isEmpty() ? aj.mac : aj.nombreImpresora)),
      "Imprimir etiqueta de prueba",
      "Actualizar productos",
      "Ajustes de impresión",
    };
    String lista = cat.productos.isEmpty() ? "Sin lista guardada" : miles(cat.productos.size()) + " productos · actualizada " + horaTxt(cat.hora);
    new AlertDialog.Builder(this).setTitle(lista).setItems(ops, (d, i) -> {
      switch (i) {
        case 0: elegirLocal(); break;
        case 1: elegirImpresora(null); break;
        case 2: imprimirPrueba(); break;
        case 3: actualizarProductos(); break;
        default: abrirAjustes(); break;
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
    String vence = "";
    boolean venceManual = false;
    AlertDialog dlg;
    TextView txtPrecio, txtCodigo, txtCant, txtAviso, txtEstado, txtVenceInfo;
    ImageView vista;
    Button btnImprimir, btnVence, btnSinVence;
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

    c.addView(texto(p.nombre, 22, true, NEGRO));
    w.txtPrecio = texto("", 30, true, NEGRO);
    c.addView(w.txtPrecio, lp(-1, -2, 2));
    w.txtCodigo = texto("", 15, false, PLOMO);
    c.addView(w.txtCodigo, lp(-1, -2, 2));

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
    w.txtCant.setText(String.valueOf(w.cantidad));
    boolean hay = !w.vence.isEmpty();
    colorear(w.btnSinVence, hay ? GRIS : VERDE, hay ? NEGRO : Color.WHITE);
    colorear(w.btnVence, hay ? VERDE : GRIS, hay ? Color.WHITE : NEGRO);
    w.btnVence.setText(hay ? "Vence " + w.vence : "Poner fecha");
    LabelRenderer.Resultado r = render(etiquetaDe(w));
    w.vista.setImageBitmap(r.vista);
    String aviso = r.aviso;
    if (w.p.precio <= 0) aviso = "Ojo: este producto no tiene precio en el sistema.";
    if (aj.mac.isEmpty()) aviso = "Falta elegir la impresora: al tocar Imprimir te la pido.";
    w.txtAviso.setText(aviso);
    w.txtAviso.setVisibility(aviso.isEmpty() ? View.GONE : View.VISIBLE);
    boolean ok = !imprimiendo && w.confirmando == 0;
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
          if (ff.barcode != null && !ff.barcode.equals(p.barcode)) {
            cambios.add("código " + p.codigo() + " → " + (ff.barcode.isEmpty() ? p.sku : ff.barcode));
            p.barcode = ff.barcode;
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

  private void elegirFecha(final Ventana w) {
    Calendar c = Calendar.getInstance();
    if (!w.vence.isEmpty()) {
      try {
        String[] d = w.vence.split("/");
        c.set(Integer.parseInt(d[2]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[0]));
      } catch (Exception ignore) {}
    }
    new DatePickerDialog(this, (v, y, m, d) -> {
      w.vence = String.format(Locale.ROOT, "%02d/%02d/%04d", d, m + 1, y);
      w.venceManual = true;
      w.txtVenceInfo.setText("");
      pintarVentana(w);
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
  }

  // ---------------------------------------------------------------- impresión
  /** Bytes de N copias + avance final para que la última etiqueta salga entera por el borde de corte. */
  private byte[] bytesDe(LabelRenderer.Resultado r, int copias) {
    if (aj.modo == Ajustes.MODO_TSPL)
      return Raster.tspl(r.bits, r.w, r.h, aj.anchoMm, aj.altoMm, aj.gapMm, aj.invertir, copias);
    byte[] una = Raster.escpos(r.bits, r.w, r.h, aj.avance);
    byte[] fin = Raster.avanzar(aj.finalMm);
    byte[] out = new byte[una.length * copias + fin.length];
    for (int i = 0; i < copias; i++) System.arraycopy(una, 0, out, i * una.length, una.length);
    System.arraycopy(fin, 0, out, una.length * copias, fin.length);
    return out;
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
