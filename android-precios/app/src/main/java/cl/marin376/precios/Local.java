package cl.marin376.precios;

/** Los dos locales, cada uno con su servidor y su propio catálogo. */
public final class Local {
  public final String id, nombre, url;
  private Local(String id, String nombre, String url) { this.id = id; this.nombre = nombre; this.url = url; }

  public static final Local MARIN = new Local("marin", "Marín 376", "https://znywepjclzhgkctzrjxb.supabase.co/functions/v1/marin-api");
  public static final Local ARGOMEDO = new Local("argomedo", "Argomedo 455", "https://znywepjclzhgkctzrjxb.supabase.co/functions/v1/argomedo-api");
  public static final Local[] TODOS = {MARIN, ARGOMEDO};

  public static Local de(String id) {
    for (Local l : TODOS) if (l.id.equals(id)) return l;
    return MARIN;
  }
}
