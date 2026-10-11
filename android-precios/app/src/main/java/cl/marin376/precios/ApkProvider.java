package cl.marin376.precios;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Entrega el APK descargado al instalador de Android (content://, que es lo que exige Android 7+;
 * sin librerías como FileProvider). Solo sirve el archivo de la actualización, solo lectura.
 */
public class ApkProvider extends ContentProvider {
  public static final String AUTORIDAD = "cl.marin376.precios.apk";
  public static final String NOMBRE = "actualizacion.apk";

  public static Uri uri() { return Uri.parse("content://" + AUTORIDAD + "/" + NOMBRE); }

  static File archivo(android.content.Context c) { return new File(c.getFilesDir(), NOMBRE); }

  @Override public boolean onCreate() { return true; }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!NOMBRE.equals(uri.getLastPathSegment())) throw new FileNotFoundException(uri.toString());
    return ParcelFileDescriptor.open(archivo(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

  @Override
  public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
    File f = archivo(getContext());
    MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
    c.addRow(new Object[]{NOMBRE, f.length()});
    return c;
  }

  @Override public Uri insert(Uri uri, ContentValues v) { return null; }
  @Override public int delete(Uri uri, String s, String[] a) { return 0; }
  @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
