package cl.marin376.precios;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fotos chicas de la lista (como Loyverse): se bajan una vez, se guardan en el equipo y en memoria. */
public final class Imagenes {
  private final File dir;
  private final int lado;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final ExecutorService hilos = Executors.newFixedThreadPool(3);
  private final LruCache<String, Bitmap> memoria = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
    @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
  };

  public Imagenes(Context c, int ladoPx) {
    dir = new File(c.getCacheDir(), "fotos");
    dir.mkdirs();
    lado = ladoPx;
  }

  /** Pone la foto en v (o deja el cuadro gris si no hay / no carga). */
  public void poner(final ImageView v, final String url) {
    v.setTag(url);
    v.setImageBitmap(null);
    if (url == null || url.isEmpty()) return;
    Bitmap b = memoria.get(url);
    if (b != null) { v.setImageBitmap(b); return; }
    hilos.execute(() -> {
      final Bitmap bmp = cargar(url);
      if (bmp == null) return;
      memoria.put(url, bmp);
      ui.post(() -> { if (url.equals(v.getTag())) v.setImageBitmap(bmp); });
    });
  }

  private Bitmap cargar(String url) {
    File f = new File(dir, Integer.toHexString(url.hashCode()) + "_" + url.length());
    try {
      if (!f.exists()) {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        if (c.getResponseCode() >= 400) { c.disconnect(); return null; }
        byte[] datos;
        try (InputStream in = c.getInputStream()) {
          ByteArrayOutputStream o = new ByteArrayOutputStream();
          byte[] buf = new byte[8192];
          int n;
          while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
          datos = o.toByteArray();
        } finally {
          c.disconnect();
        }
        Bitmap chica = reducir(datos);
        if (chica == null) return null;
        try (FileOutputStream out = new FileOutputStream(f)) { chica.compress(Bitmap.CompressFormat.JPEG, 85, out); }
        return chica;
      }
      try (InputStream in = new FileInputStream(f)) { return BitmapFactory.decodeStream(in); }
    } catch (Exception e) {
      return null;
    }
  }

  private Bitmap reducir(byte[] d) {
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(d, 0, d.length, o);
    int s = 1;
    while (o.outWidth / (s * 2) >= lado && o.outHeight / (s * 2) >= lado) s *= 2;
    BitmapFactory.Options o2 = new BitmapFactory.Options();
    o2.inSampleSize = s;
    Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length, o2);
    if (b == null) return null;
    int min = Math.min(b.getWidth(), b.getHeight());
    Bitmap cuadro = Bitmap.createBitmap(b, (b.getWidth() - min) / 2, (b.getHeight() - min) / 2, min, min);
    return Bitmap.createScaledBitmap(cuadro, lado, lado, true);
  }
}
