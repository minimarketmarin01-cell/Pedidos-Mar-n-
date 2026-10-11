package cl.marin376.precios;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/**
 * Busca una versión nueva publicada por GitHub Actions (descargas/precios-version.json), baja el
 * APK y abre el instalador: la persona solo toca "Instalar". Android no permite instalar en
 * silencio una app que no viene de Play Store.
 */
public final class Actualizador {
  static final String BASE = "https://minimarketmarin01-cell.github.io/Pedidos-Mar-n-/descargas/";
  private static final long CADA_MS = 3L * 3600 * 1000;   // revisa sola como máximo cada 3 h

  private final Activity act;
  private final Handler ui = new Handler(Looper.getMainLooper());

  public Actualizador(Activity a) { act = a; }

  static int versionInstalada(Activity a) {
    try {
      PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
      return pi.versionCode;
    } catch (Exception e) { return 0; }
  }

  static String nombreInstalado(Activity a) {
    try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
  }

  /** aMano = el usuario tocó "Buscar actualización": avisa también si no hay nada o no hay internet. */
  public void revisar(final boolean aMano) {
    final android.content.SharedPreferences p = act.getSharedPreferences("precios", Activity.MODE_PRIVATE);
    if (!aMano && System.currentTimeMillis() - p.getLong("actualizRevisada", 0) < CADA_MS) return;
    new Thread(() -> {
      try {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + "precios-version.json?t=" + System.currentTimeMillis()).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        if (c.getResponseCode() >= 400) throw new Exception("HTTP " + c.getResponseCode());
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) o.write(b, 0, n); }
        finally { c.disconnect(); }
        JSONObject j = new JSONObject(o.toString("UTF-8"));
        final int nueva = j.optInt("versionCode", 0);
        final String nombre = j.optString("versionName", "");
        final String apk = j.optString("apk", "precios-marin376.apk");
        p.edit().putLong("actualizRevisada", System.currentTimeMillis()).apply();
        ui.post(() -> {
          if (act.isFinishing()) return;
          if (nueva > versionInstalada(act)) ofrecer(nombre, apk);
          else if (aMano) Toast.makeText(act, "Ya tienes la última versión (" + nombreInstalado(act) + ").", Toast.LENGTH_LONG).show();
        });
      } catch (Exception e) {
        if (aMano) ui.post(() -> Toast.makeText(act, "No pude revisar si hay versión nueva (sin internet).", Toast.LENGTH_LONG).show());
      }
    }).start();
  }

  private void ofrecer(final String nombre, final String apk) {
    new AlertDialog.Builder(act).setTitle("Hay una versión nueva")
        .setMessage("Versión " + nombre + " de la app de etiquetas (tienes la " + nombreInstalado(act) + ").\n\n"
            + "Se baja sola y después solo tocas «Instalar». No se pierde la impresora ni los ajustes.")
        .setPositiveButton("Actualizar ahora", (d, i) -> bajar(apk))
        .setNegativeButton("Más tarde", null).show();
  }

  private void bajar(final String apk) {
    // Android 8: hay que permitir que ESTA app instale actualizaciones (una sola vez).
    if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
      new AlertDialog.Builder(act).setTitle("Permitir actualizaciones")
          .setMessage("Solo la primera vez: en la pantalla que se abre, activa «Permitir de esta fuente» y vuelve atrás. Después toca otra vez «Actualizar».")
          .setPositiveButton("Abrir permiso", (d, i) -> act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
              Uri.parse("package:" + act.getPackageName()))))
          .setNegativeButton("Cancelar", null).show();
      return;
    }
    final ProgressDialog pd = new ProgressDialog(act);
    pd.setMessage("Bajando la versión nueva…");
    pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    pd.setCancelable(false);
    pd.setMax(100);
    pd.show();
    new Thread(() -> {
      String err = null;
      File f = ApkProvider.archivo(act), tmp = new File(f.getPath() + ".parte");
      try {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + apk + "?t=" + System.currentTimeMillis()).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setUseCaches(false);
        if (c.getResponseCode() >= 400) throw new Exception("HTTP " + c.getResponseCode());
        final int total = c.getContentLength();
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
          byte[] b = new byte[16384];
          int n, leido = 0;
          while ((n = in.read(b)) > 0) {
            out.write(b, 0, n);
            leido += n;
            if (total > 0) { final int pct = (int) (leido * 100L / total); ui.post(() -> pd.setProgress(pct)); }
          }
        } finally { c.disconnect(); }
        if (tmp.length() < 10000) throw new Exception("archivo incompleto");
        if (f.exists()) f.delete();
        if (!tmp.renameTo(f)) throw new Exception("no se pudo guardar");
      } catch (Exception e) {
        tmp.delete();
        err = "No se pudo bajar la actualización. Revisa el internet e inténtalo de nuevo.";
      }
      final String fe = err;
      ui.post(() -> {
        try { pd.dismiss(); } catch (Exception ignore) {}
        if (fe != null) { Toast.makeText(act, fe, Toast.LENGTH_LONG).show(); return; }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(ApkProvider.uri(), "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try { act.startActivity(i); }
        catch (Exception e) { Toast.makeText(act, "No se pudo abrir el instalador.", Toast.LENGTH_LONG).show(); }
      });
    }).start();
  }
}
