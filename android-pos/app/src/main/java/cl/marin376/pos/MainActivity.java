package cl.marin376.pos;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Marín POS: abre la app web del local en pantalla completa dentro de la app (WebView), para POS
 * viejos donde el navegador no deja instalar la PWA. La app web se actualiza sola (carga siempre la
 * versión publicada); esta app además busca versiones nuevas de sí misma (Actualizador).
 */
public class MainActivity extends Activity {
  private static final int PEDIDO_CAMARA = 11, PEDIDO_ARCHIVO = 12;

  private final Handler ui = new Handler(Looper.getMainLooper());
  private SharedPreferences prefs;
  private WebView web;
  private TextView cargando;
  private FrameLayout raiz;
  private Local local;
  private PermissionRequest permisoPendiente;
  private ValueCallback<Uri[]> selectorArchivos;
  private boolean errorPagina = false;

  @Override
  protected void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // el POS no debe apagar la pantalla
    prefs = getSharedPreferences("pos", MODE_PRIVATE);
    raiz = new FrameLayout(this);
    raiz.setBackgroundColor(Color.WHITE);
    setContentView(raiz);

    web = new WebView(this);
    raiz.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    cargando = new TextView(this);
    cargando.setGravity(Gravity.CENTER);
    cargando.setTextSize(20);
    cargando.setTextColor(Color.parseColor("#16395F"));
    raiz.addView(cargando, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    configurarWeb();

    local = Local.de(prefs.getString("local", ""));
    if (local == null) elegirLocal(true);
    else abrir();
    new Actualizador(this).revisar(false);   // si hay versión nueva de la app, ofrece actualizar
  }

  // ---------------------------------------------------------------- WebView
  private void configurarWeb() {
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);
    s.setMediaPlaybackRequiresUserGesture(false);   // sonidos de confirmación
    s.setAllowFileAccess(false);
    s.setLoadWithOverviewMode(false);
    s.setUseWideViewPort(false);
    s.setBuiltInZoomControls(false);
    s.setSupportZoom(false);
    s.setTextZoom(100);
    web.setOverScrollMode(View.OVER_SCROLL_NEVER);
    web.addJavascriptInterface(new Puente(), "MarinPOS");

    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return manejarUrl(r.getUrl().toString()); }
      @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return manejarUrl(url); }
      @Override public void onPageStarted(WebView v, String url, Bitmap f) { errorPagina = false; }
      @Override public void onPageFinished(WebView v, String url) { if (!errorPagina) cargando.setVisibility(View.GONE); }
      @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
        if (r.isForMainFrame()) { errorPagina = true; mostrarSinConexion(); }
      }
      @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
        // El motor web se cerró (poca memoria): se vuelve a crear en vez de cerrar la app.
        ui.post(() -> { if (local != null) abrir(); });
        return true;
      }
    });

    web.setWebChromeClient(new WebChromeClient() {
      @Override public void onPermissionRequest(final PermissionRequest req) {
        // La cámara del escáner de códigos de barras: se pide el permiso de Android una vez.
        boolean quiereCamara = false;
        for (String r : req.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) quiereCamara = true;
        if (!quiereCamara) { req.grant(req.getResources()); return; }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) req.grant(req.getResources());
        else { permisoPendiente = req; requestPermissions(new String[]{Manifest.permission.CAMERA}, PEDIDO_CAMARA); }
      }
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
        if (selectorArchivos != null) selectorArchivos.onReceiveValue(null);
        selectorArchivos = cb;
        try { startActivityForResult(p.createIntent(), PEDIDO_ARCHIVO); return true; }
        catch (ActivityNotFoundException e) { selectorArchivos = null; return false; }
      }
      @Override public boolean onConsoleMessage(ConsoleMessage m) { return true; }
    });
  }

  private boolean manejarUrl(String url) {
    if (Local.esInterna(url)) return false;
    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }   // WhatsApp, teléfono, correo, otros sitios
    catch (Exception e) { Toast.makeText(this, "No hay una app para abrir ese enlace.", Toast.LENGTH_SHORT).show(); }
    return true;
  }

  private void abrir() {
    errorPagina = false;
    cargando.setText("Abriendo " + local.nombre + "…");
    cargando.setVisibility(View.VISIBLE);
    web.loadUrl(local.url);
  }

  private void mostrarSinConexion() {
    cargando.setVisibility(View.GONE);
    String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
        + "<body style='font-family:sans-serif;text-align:center;padding:40px 20px;color:#16395F'>"
        + "<h2>No se pudo abrir la app</h2><p style='font-size:18px'>Revisa que el POS tenga internet.</p>"
        + "<p><button style='font-size:20px;padding:16px 28px;border-radius:12px;border:0;background:#16395F;color:#fff' onclick='MarinPOS.recargar()'>Reintentar</button></p>"
        + "<p><button style='font-size:16px;padding:12px 20px;border-radius:12px;border:1px solid #16395F;background:#fff;color:#16395F' onclick='MarinPOS.cambiarLocal()'>Cambiar de local</button></p>"
        + "<p><button style='font-size:16px;padding:12px 20px;border-radius:12px;border:1px solid #16395F;background:#fff;color:#16395F' onclick='MarinPOS.buscarActualizacion()'>Buscar actualización de la app</button></p>"
        + "</body></html>";
    web.loadDataWithBaseURL("https://minimarketmarin01-cell.github.io/offline", html, "text/html", "UTF-8", null);
  }

  // ---------------------------------------------------------------- local, menú y actualización
  private void elegirLocal(final boolean obligatorio) {
    String[] nombres = new String[Local.TODOS.length];
    for (int i = 0; i < nombres.length; i++) nombres[i] = Local.TODOS[i].nombre;
    new AlertDialog.Builder(this).setTitle("¿De qué local es este POS?")
        .setCancelable(!obligatorio)
        .setItems(nombres, (d, i) -> {
          local = Local.TODOS[i];
          prefs.edit().putString("local", local.id).apply();
          abrir();
        })
        .setNegativeButton(obligatorio ? null : "Cancelar", null).show();
  }

  private String versionMotorWeb() {
    String ua = web.getSettings().getUserAgentString();
    int i = ua == null ? -1 : ua.indexOf("Chrome/");
    if (i < 0) return "desconocido";
    int j = ua.indexOf('.', i);
    return ua.substring(i + 7, j > 0 ? j : Math.min(ua.length(), i + 10));
  }

  /** Funciones que la app web puede llamar (menú "Más" → App del POS). */
  public class Puente {
    @JavascriptInterface public String versionApp() { return Actualizador.nombreInstalado(MainActivity.this); }
    @JavascriptInterface public String motorWeb() { return versionMotorWeb(); }
    @JavascriptInterface public String nombreLocal() { return local == null ? "" : local.nombre; }
    @JavascriptInterface public void buscarActualizacion() {
      ui.post(() -> {
        Toast.makeText(MainActivity.this, "Buscando actualización…", Toast.LENGTH_SHORT).show();   // que se note que respondió
        new Actualizador(MainActivity.this).revisar(true);
      });
    }
    @JavascriptInterface public void cambiarLocal() { ui.post(() -> elegirLocal(false)); }
    @JavascriptInterface public void recargar() {
      ui.post(() -> {
        Toast.makeText(MainActivity.this, "Recargando la pantalla…", Toast.LENGTH_SHORT).show();
        // Borra la copia de la página que guarda el motor web: si no, "recargar" volvía a mostrar la versión vieja.
        // (No toca los datos de la app: pedidos pendientes, preferencias, etc. quedan intactos.)
        web.clearCache(true);
        if (local != null) abrir();
      });
    }
  }

  // ---------------------------------------------------------------- ciclo de vida
  @Override
  public void onBackPressed() {
    // El "Atrás" lo maneja la propia app web (cierra primero la ventana abierta, etc.).
    if (web.canGoBack()) web.goBack();
    else super.onBackPressed();
  }

  private long pausadoEn = 0;
  @Override protected void onResume() {
    super.onResume();
    web.onResume();
    // Si el POS estuvo un rato en segundo plano, busca sola una versión nueva de la app (sin tocar nada).
    if (pausadoEn > 0 && System.currentTimeMillis() - pausadoEn > 10L * 60 * 1000) new Actualizador(this).revisar(false);
  }
  @Override protected void onPause() { pausadoEn = System.currentTimeMillis(); web.onPause(); super.onPause(); }

  @Override
  public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
    if (code != PEDIDO_CAMARA || permisoPendiente == null) return;
    PermissionRequest r = permisoPendiente;
    permisoPendiente = null;
    if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) r.grant(r.getResources());
    else { r.deny(); Toast.makeText(this, "Sin permiso de cámara no se pueden escanear códigos.", Toast.LENGTH_LONG).show(); }
  }

  @Override
  protected void onActivityResult(int code, int result, Intent data) {
    if (code != PEDIDO_ARCHIVO || selectorArchivos == null) { super.onActivityResult(code, result, data); return; }
    selectorArchivos.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
    selectorArchivos = null;
  }
}
