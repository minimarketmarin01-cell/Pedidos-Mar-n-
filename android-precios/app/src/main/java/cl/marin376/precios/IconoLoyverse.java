package cl.marin376.precios;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Íconos de la barra de búsqueda, dibujados a mano (no dependen de emojis ni fuentes del equipo) con el mismo estilo
 * de Loyverse: lector de código de barras (esquinas + barras), lupa y cerrar (✕).
 */
public class IconoLoyverse extends View {
  public static final int ESCANER = 0, LUPA = 1, CERRAR = 2;
  private final Paint trazo = new Paint(Paint.ANTI_ALIAS_FLAG), relleno = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int tipo;

  public IconoLoyverse(Context c, int tipo, int color) {
    super(c);
    this.tipo = tipo;
    float d = c.getResources().getDisplayMetrics().density;
    trazo.setStyle(Paint.Style.STROKE);
    trazo.setColor(color);
    trazo.setStrokeWidth(2.4f * d);
    trazo.setStrokeCap(Paint.Cap.ROUND);
    trazo.setStrokeJoin(Paint.Join.ROUND);
    relleno.setStyle(Paint.Style.FILL);
    relleno.setColor(color);
    setClickable(true);
  }

  public void setTipo(int t) { if (t != tipo) { tipo = t; invalidate(); } }

  @Override protected void onDraw(Canvas c) {
    float s = Math.min(getWidth(), getHeight()) * 0.42f;   // lado del dibujo
    float x0 = (getWidth() - s) / 2f, y0 = (getHeight() - s) / 2f;
    if (tipo == ESCANER) {
      float l = s * 0.30f;   // largo de cada esquina
      float x1 = x0 + s, y1 = y0 + s;
      c.drawLine(x0, y0 + l, x0, y0, trazo); c.drawLine(x0, y0, x0 + l, y0, trazo);
      c.drawLine(x1 - l, y0, x1, y0, trazo); c.drawLine(x1, y0, x1, y0 + l, trazo);
      c.drawLine(x0, y1 - l, x0, y1, trazo); c.drawLine(x0, y1, x0 + l, y1, trazo);
      c.drawLine(x1 - l, y1, x1, y1, trazo); c.drawLine(x1, y1, x1, y1 - l, trazo);
      // Barras del código (ancho en fracciones del lado): finas y gruesas como un código real.
      float[][] barras = {{0.17f, 0.045f}, {0.27f, 0.09f}, {0.42f, 0.045f}, {0.52f, 0.11f}, {0.69f, 0.045f}, {0.78f, 0.045f}};
      for (float[] b : barras) c.drawRect(x0 + s * b[0], y0 + s * 0.20f, x0 + s * (b[0] + b[1]), y1 - s * 0.20f, relleno);
    } else if (tipo == LUPA) {
      float r = s * 0.34f;
      c.drawCircle(x0 + s * 0.42f, y0 + s * 0.42f, r, trazo);
      c.drawLine(x0 + s * 0.42f + r * 0.72f, y0 + s * 0.42f + r * 0.72f, x0 + s * 0.98f, y0 + s * 0.98f, trazo);
    } else {
      c.drawLine(x0 + s * 0.12f, y0 + s * 0.12f, x0 + s * 0.88f, y0 + s * 0.88f, trazo);
      c.drawLine(x0 + s * 0.88f, y0 + s * 0.12f, x0 + s * 0.12f, y0 + s * 0.88f, trazo);
    }
  }
}
