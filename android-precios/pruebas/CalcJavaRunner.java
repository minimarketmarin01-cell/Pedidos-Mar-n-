import cl.marin376.precios.CalcMotor;
import java.nio.file.*; import java.util.*;
public class CalcJavaRunner { public static void main(String[] a) throws Exception {
  StringBuilder sb=new StringBuilder();
  for(String line: Files.readAllLines(Paths.get("seqs.txt"))){ if(line.trim().isEmpty())continue;
    CalcMotor m=new CalcMotor(); for(String k: line.trim().split(" ")) m.tecla(k);
    sb.append(m.pantalla()).append(" | ").append(m.abajo()).append('\n'); }
  Files.write(Paths.get("java.out"), sb.toString().getBytes("UTF-8"));
}}
