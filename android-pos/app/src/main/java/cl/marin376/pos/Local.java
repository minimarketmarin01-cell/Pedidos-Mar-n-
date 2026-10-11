package cl.marin376.pos;

/** Los dos locales: cada uno tiene su propia app web (GitHub Pages) y su propio servidor. */
public final class Local {
  public final String id, nombre, url;
  private Local(String id, String nombre, String url) { this.id = id; this.nombre = nombre; this.url = url; }

  public static final Local MARIN = new Local("marin", "Marín 376", "https://minimarketmarin01-cell.github.io/Pedidos-Mar-n-/index.html");
  public static final Local ARGOMEDO = new Local("argomedo", "Argomedo 455", "https://minimarketmarin01-cell.github.io/argomedo455/index.html");
  public static final Local[] TODOS = {MARIN, ARGOMEDO};

  public static Local de(String id) {
    for (Local l : TODOS) if (l.id.equals(id)) return l;
    return null;
  }

  /** ¿Esta dirección es de la propia app (se queda dentro) o es un enlace externo (WhatsApp, teléfono…)? */
  public static boolean esInterna(String url) {
    return url != null && url.startsWith("https://minimarketmarin01-cell.github.io/");
  }
}
