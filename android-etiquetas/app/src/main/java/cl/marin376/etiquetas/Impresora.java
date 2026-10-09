package cl.marin376.etiquetas;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/** Envío de bytes a una impresora Bluetooth (perfil serie SPP). */
public final class Impresora implements AutoCloseable {
  private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
  private BluetoothSocket socket;
  private OutputStream out;

  public Impresora(String mac) throws IOException {
    BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
    if (ad == null) throw new IOException("Este equipo no tiene Bluetooth");
    if (!ad.isEnabled()) throw new IOException("El Bluetooth está apagado. Enciéndelo e inténtalo de nuevo.");
    BluetoothDevice dev = ad.getRemoteDevice(mac);
    ad.cancelDiscovery();
    try {
      socket = dev.createRfcommSocketToServiceRecord(SPP);
      socket.connect();
    } catch (IOException e1) {
      try { if (socket != null) socket.close(); } catch (IOException ignore) {}
      try {   // método alternativo que necesitan algunas impresoras chinas
        socket = (BluetoothSocket) dev.getClass().getMethod("createRfcommSocket", int.class).invoke(dev, 1);
        socket.connect();
      } catch (Exception e2) {
        throw new IOException("No se pudo conectar con la impresora. ¿Está encendida y cerca?");
      }
    }
    out = socket.getOutputStream();
  }

  public void enviar(byte[] datos) throws IOException, InterruptedException {
    int off = 0;
    while (off < datos.length) {
      int n = Math.min(512, datos.length - off);
      out.write(datos, off, n);
      off += n;
      Thread.sleep(15);
    }
    out.flush();
  }

  @Override
  public void close() {
    try { if (out != null) out.flush(); } catch (IOException ignore) {}
    try { Thread.sleep(500); } catch (InterruptedException ignore) {}
    try { if (socket != null) socket.close(); } catch (IOException ignore) {}
  }
}
