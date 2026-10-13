package cl.marin376.precios;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/**
 * Lector de códigos por la cámara trasera, sin librerías: muestra la imagen y, en un hilo aparte,
 * cada cuadro se pasa a Escaner (EAN-13/UPC-A, EAN-8, Code 128). Se acepta una lectura cuando sale
 * igual en dos cuadros seguidos. Pantalla completa; ✕/Atrás vuelve a la lista.
 */
public final class EscanerCamara implements SurfaceHolder.Callback, Camera.PreviewCallback {
  public interface AlLeer { void leido(String codigo); }

  private static EscanerCamara activo;

  private final Activity act;
  private final AlLeer alLeer;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private Dialog dlg;
  private Camera cam;
  private HandlerThread hilo;
  private Handler lector;
  private int pw, ph;
  private volatile boolean ocupado, cerrado;
  private String ultimo = "";
  private int repeticiones;
  private boolean autoEnfoque, luz;

  private EscanerCamara(Activity a, AlLeer l) { act = a; alLeer = l; }

  public static void abrir(Activity a, AlLeer l) {
    cerrarActivo();
    EscanerCamara e = new EscanerCamara(a, l);
    activo = e;
    e.mostrar();
  }

  public static void cerrarActivo() { if (activo != null) activo.cerrar(); }

  private int dp(int v) { return Math.round(v * act.getResources().getDisplayMetrics().density); }

  private void mostrar() {
    try {
      cam = Camera.open();   // trasera
    } catch (Exception e) {
      cam = null;
    }
    if (cam == null) {
      Toast.makeText(act, "No pude abrir la cámara (¿otra app la está usando, o el equipo no tiene?).", Toast.LENGTH_LONG).show();
      activo = null;
      return;
    }
    Camera.Parameters p = cam.getParameters();
    // Hasta 1280×720: lo bastante nítido para códigos chicos y rápido de analizar.
    Camera.Size mejor = null;
    List<Camera.Size> tamanos = p.getSupportedPreviewSizes();
    for (Camera.Size s : tamanos)
      if (s.width <= 1280 && s.height <= 720 && (mejor == null || s.width * s.height > mejor.width * mejor.height)) mejor = s;
    if (mejor == null) mejor = p.getPreviewSize();
    pw = mejor.width;
    ph = mejor.height;
    p.setPreviewSize(pw, ph);
    List<String> enfoques = p.getSupportedFocusModes();
    if (enfoques != null && enfoques.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
      p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    else if (enfoques != null && enfoques.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
      p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      autoEnfoque = true;
    }
    try { cam.setParameters(p); } catch (Exception ignore) {}

    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(0, info);
    int rot = act.getWindowManager().getDefaultDisplay().getRotation();
    int grados = rot == Surface.ROTATION_90 ? 90 : rot == Surface.ROTATION_180 ? 180 : rot == Surface.ROTATION_270 ? 270 : 0;
    int orient = (info.orientation - grados + 360) % 360;
    cam.setDisplayOrientation(orient);
    boolean vertical = orient == 90 || orient == 270;   // la imagen se ve girada respecto del sensor
    int vw = vertical ? ph : pw, vh = vertical ? pw : ph;

    dlg = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    dlg.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    FrameLayout raiz = new FrameLayout(act);
    raiz.setBackgroundColor(Color.BLACK);

    int ancho = act.getResources().getDisplayMetrics().widthPixels;
    int alto = act.getResources().getDisplayMetrics().heightPixels;
    int sw = ancho, sh = ancho * vh / vw;
    if (sh > alto) { sh = alto; sw = alto * vw / vh; }
    SurfaceView sv = new SurfaceView(act);
    sv.getHolder().addCallback(this);
    FrameLayout.LayoutParams lps = new FrameLayout.LayoutParams(sw, sh, Gravity.CENTER);
    raiz.addView(sv, lps);

    // Mira: línea roja al medio y esquinas.
    View mira = new View(act) {
      final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
      @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
        float ax = w * 0.42f, ay = h * 0.16f, l = dp(28);
        pt.setStyle(Paint.Style.STROKE);
        pt.setStrokeWidth(dp(4));
        pt.setColor(Color.WHITE);
        c.drawLine(cx - ax, cy - ay, cx - ax + l, cy - ay, pt); c.drawLine(cx - ax, cy - ay, cx - ax, cy - ay + l, pt);
        c.drawLine(cx + ax, cy - ay, cx + ax - l, cy - ay, pt); c.drawLine(cx + ax, cy - ay, cx + ax, cy - ay + l, pt);
        c.drawLine(cx - ax, cy + ay, cx - ax + l, cy + ay, pt); c.drawLine(cx - ax, cy + ay, cx - ax, cy + ay - l, pt);
        c.drawLine(cx + ax, cy + ay, cx + ax - l, cy + ay, pt); c.drawLine(cx + ax, cy + ay, cx + ax, cy + ay - l, pt);
        pt.setColor(Color.parseColor("#E53935"));
        pt.setStrokeWidth(dp(2));
        c.drawLine(cx - ax, cy, cx + ax, cy, pt);
      }
    };
    raiz.addView(mira, new FrameLayout.LayoutParams(-1, -1));

    TextView ayuda = new TextView(act);
    ayuda.setText("Apunta al código de barras\ny acércalo hasta que se vea nítido");
    ayuda.setTextColor(Color.WHITE);
    ayuda.setTextSize(18);
    ayuda.setGravity(Gravity.CENTER);
    ayuda.setBackgroundColor(Color.parseColor("#99000000"));
    ayuda.setPadding(dp(12), dp(10), dp(12), dp(10));
    raiz.addView(ayuda, new FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

    android.widget.LinearLayout abajo = new android.widget.LinearLayout(act);
    abajo.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    abajo.setPadding(dp(12), dp(12), dp(12), dp(16));
    abajo.setBackgroundColor(Color.parseColor("#99000000"));
    boolean hayLuz = p.getSupportedFlashModes() != null && p.getSupportedFlashModes().contains(Camera.Parameters.FLASH_MODE_TORCH);
    if (hayLuz) {
      final Button bLuz = new Button(act);
      bLuz.setText("Luz");
      bLuz.setTextSize(20);
      bLuz.setAllCaps(false);
      bLuz.setOnClickListener(v -> {
        if (cam == null) return;
        try {
          luz = !luz;
          Camera.Parameters q = cam.getParameters();
          q.setFlashMode(luz ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
          cam.setParameters(q);
          bLuz.setText(luz ? "Luz ✓" : "Luz");
        } catch (Exception ignore) {}
      });
      android.widget.LinearLayout.LayoutParams pl = new android.widget.LinearLayout.LayoutParams(0, dp(60), 1);
      pl.rightMargin = dp(10);
      abajo.addView(bLuz, pl);
    }
    Button bCerrar = new Button(act);
    bCerrar.setText("Cerrar");
    bCerrar.setTextSize(20);
    bCerrar.setAllCaps(false);
    bCerrar.setOnClickListener(v -> cerrar());
    abajo.addView(bCerrar, new android.widget.LinearLayout.LayoutParams(0, dp(60), 1));
    raiz.addView(abajo, new FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

    dlg.setContentView(raiz);
    dlg.setOnDismissListener(d -> liberar());
    hilo = new HandlerThread("lector-codigos");
    hilo.start();
    lector = new Handler(hilo.getLooper());
    dlg.show();
  }

  @Override public void surfaceCreated(SurfaceHolder h) {
    if (cam == null || cerrado) return;
    try {
      cam.setPreviewDisplay(h);
      cam.setPreviewCallback(this);
      cam.startPreview();
      if (autoEnfoque) enfocar();
    } catch (Exception e) {
      Toast.makeText(act, "No pude iniciar la cámara.", Toast.LENGTH_LONG).show();
      cerrar();
    }
  }

  @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}

  @Override public void surfaceDestroyed(SurfaceHolder h) {}

  /** Sin enfoque continuo: se pide enfocar cada 2 s. */
  private void enfocar() {
    if (cerrado || cam == null) return;
    try { cam.autoFocus(null); } catch (Exception ignore) {}
    ui.postDelayed(this::enfocar, 2000);
  }

  @Override public void onPreviewFrame(final byte[] data, Camera c) {
    if (ocupado || cerrado || data == null || data.length < pw * ph) return;
    ocupado = true;
    lector.post(() -> {
      String r = null;
      try { r = Escaner.leerImagen(data, pw, ph); } catch (Exception ignore) {}
      final String leido = r;
      ocupado = false;
      if (leido != null) ui.post(() -> confirmar(leido));
    });
  }

  private void confirmar(String r) {
    if (cerrado) return;
    if (r.equals(ultimo)) repeticiones++; else { ultimo = r; repeticiones = 1; }
    if (repeticiones < 2) return;   // dos cuadros seguidos iguales
    try { new ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150); } catch (Exception ignore) {}
    final String codigo = r;
    cerrar();
    alLeer.leido(codigo);
  }

  private void cerrar() {
    if (dlg != null && dlg.isShowing()) dlg.dismiss(); else liberar();
  }

  private void liberar() {
    if (cerrado) return;
    cerrado = true;
    ui.removeCallbacksAndMessages(null);
    if (cam != null) {
      try { cam.setPreviewCallback(null); cam.stopPreview(); } catch (Exception ignore) {}
      try { cam.release(); } catch (Exception ignore) {}
      cam = null;
    }
    if (hilo != null) hilo.quitSafely();
    if (activo == this) activo = null;
  }
}
