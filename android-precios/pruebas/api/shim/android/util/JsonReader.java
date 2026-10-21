package android.util;
import java.io.IOException;
import java.io.Reader;
/** Prueba en el PC: mismo API que android.util.JsonReader, apoyado en gson. */
public class JsonReader implements java.io.Closeable {
  private final com.google.gson.stream.JsonReader g;
  public JsonReader(Reader in) { g = new com.google.gson.stream.JsonReader(in); g.setLenient(true); }
  public void beginObject() throws IOException { g.beginObject(); }
  public void endObject() throws IOException { g.endObject(); }
  public void beginArray() throws IOException { g.beginArray(); }
  public void endArray() throws IOException { g.endArray(); }
  public boolean hasNext() throws IOException { return g.hasNext(); }
  public String nextName() throws IOException { return g.nextName(); }
  public String nextString() throws IOException { return g.nextString(); }
  public int nextInt() throws IOException { return g.nextInt(); }
  public long nextLong() throws IOException { return g.nextLong(); }
  public double nextDouble() throws IOException { return g.nextDouble(); }
  public boolean nextBoolean() throws IOException { return g.nextBoolean(); }
  public void nextNull() throws IOException { g.nextNull(); }
  public void skipValue() throws IOException { g.skipValue(); }
  public JsonToken peek() throws IOException { return JsonToken.valueOf(g.peek().name()); }
  public void close() throws IOException { g.close(); }
}
