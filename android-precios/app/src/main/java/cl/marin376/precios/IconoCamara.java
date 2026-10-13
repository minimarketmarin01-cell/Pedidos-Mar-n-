package cl.marin376.precios;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Ícono de cámara dibujado a mano (no depende de las fuentes/emojis del equipo). */
public class IconoCamara extends View {
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

  public IconoCamara(Context c, int color) {
    super(c);
    p.setStyle(Paint.Style.STROKE);
    p.setColor(color);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setStrokeWidth(2.6f * c.getResources().getDisplayMetrics().density);
  }

  @Override protected void onDraw(Canvas c) {
    float s = Math.min(getWidth(), getHeight()) * 0.56f;
    float cx = getWidth() / 2f, cy = getHeight() / 2f;
    float x0 = cx - s / 2, y0 = cy - s * 0.38f, x1 = cx + s / 2, y1 = cy + s * 0.42f;
    Path tapa = new Path();   // cuerpo con la "joroba" del visor arriba
    tapa.moveTo(x0, y0 + s * 0.12f);
    tapa.lineTo(x0 + s * 0.28f, y0 + s * 0.12f);
    tapa.lineTo(x0 + s * 0.36f, y0 - s * 0.02f);
    tapa.lineTo(x1 - s * 0.36f, y0 - s * 0.02f);
    tapa.lineTo(x1 - s * 0.28f, y0 + s * 0.12f);
    tapa.lineTo(x1, y0 + s * 0.12f);
    tapa.lineTo(x1, y1);
    tapa.lineTo(x0, y1);
    tapa.close();
    c.drawPath(tapa, p);
    c.drawCircle(cx, (y0 + y1) / 2f + s * 0.06f, s * 0.2f, p);
  }
}
