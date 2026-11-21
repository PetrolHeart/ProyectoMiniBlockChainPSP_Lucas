import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {

    //Número de hash:
    static int DIFICULTAD = 4;

    public static void main(String[] args) throws InterruptedException {
        Process[] workers = new Process[4];
        boolean error = false;

        try {
            if (args.length > 1) throw new IllegalArgumentException("Uso: java Principal [DIFICULTAD]");
            if (args.length == 1) {
                try {
                    DIFICULTAD = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("La dificultad debe ser un numero entero.");
                }
            }
            if (DIFICULTAD < 1 || DIFICULTAD > 64) {
                throw new IllegalArgumentException("La dificultad debe estar entre 1 y 64.");
            }

            Scanner teclado = new Scanner(System.in);
            System.out.print("Introduce la semilla inicial: ");
            String seed = teclado.nextLine().trim();
            if (seed.isEmpty()) throw new IllegalArgumentException("La semilla no puede estar vacia.");

            System.out.print("Introduce la ruta del fichero con los datos: ");
            File fichero = new File(teclado.nextLine().trim());
            if (!fichero.isFile() || !fichero.canRead()) {
                throw new IllegalArgumentException("No se puede leer el fichero: " + fichero);
            }

            ArrayList<String> datosBloques = new ArrayList<>();
            for (String linea : Files.readAllLines(fichero.toPath())) {
                if (!linea.trim().isEmpty()) datosBloques.add(linea.trim());
            }
            if (datosBloques.isEmpty()) {
                System.out.println("El fichero no contiene datos: no hay bloques que procesar.");
                return;
            }

            String prefijo = "";
            for (int i = 0; i < DIFICULTAD; i++) prefijo += "0";
            String classpath = System.getProperty("java.class.path");

            // La cadena: junto a datosBloques guardamos hash anterior, nonce y hash de cada bloque
            ArrayList<String> cadenaAnterior = new ArrayList<>();
            ArrayList<String> cadenaNonce = new ArrayList<>();
            ArrayList<String> cadenaHash = new ArrayList<>();

            String hashAnterior = calcularHash(seed);
            System.out.println("Hash inicial (semilla): " + hashAnterior + "\n");

            for (int b = 0; b < datosBloques.size(); b++) {
                String datos = datosBloques.get(b);
                System.out.println("\nPreparando el bloque " + (b + 1) + "...");

                for (int i = 0; i < workers.length; i++) {
                    workers[i] = new ProcessBuilder(
                            "java", "-cp", classpath, "Worker", String.valueOf(i),
                            String.valueOf(workers.length), String.valueOf(DIFICULTAD), hashAnterior, datos
                    ).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                }

                boolean[] terminado = new boolean[workers.length];
                int terminados = 0;
                int ganador = -1;
                String nonce = "", hashRecibido = "", hashRecalculado = "";

                while (ganador == -1) {
                    if (terminados == workers.length) {
                        throw new IOException("Ningún worker entregó una solucion valida para el bloque " + (b + 1));
                    }
                    Thread.sleep(10);

                    for (int i = 0; i < workers.length && ganador == -1; i++) {
                        if (terminado[i]) continue;
                        boolean vivo = workers[i].isAlive();

                        if (workers[i].getInputStream().available() > 0) {
                            terminado[i] = true;
                            terminados++;
                            String[] p = new String(workers[i].getInputStream().readAllBytes()).trim().split(" ");

                            // Validación: formato, hash recalculado y dificultad.
                            String motivo = "";
                            String recalculado = "";
                            if (p.length != 2 || !p[0].matches("\\d+")) {
                                motivo = "Formato incorrecto";
                            } else {
                                recalculado = calcularHash(hashAnterior + "|" + datos + "|" + p[0]);
                                if (!recalculado.equals(p[1])) motivo = "el hash no coincide";
                                else if (!recalculado.startsWith(prefijo)) motivo = "no cumple la dificultad";
                            }

                            if (motivo.isEmpty()) {
                                ganador = i;
                                nonce = p[0];
                                hashRecibido = p[1];
                                hashRecalculado = recalculado;
                            } else {
                                System.out.println("Propuesta del worker " + i + " rechazada: " + motivo + ".");
                            }
                        } else if (!vivo) {
                            terminado[i] = true;
                            terminados++;
                        }
                    }
                }

                // Una vez que porfin acepta el bloque: se guarda y hago un destroy() a los workers
                cadenaAnterior.add(hashAnterior);
                cadenaNonce.add(nonce);
                cadenaHash.add(hashRecalculado);

                for (Process w : workers) {
                    if (w.isAlive()) w.destroy();
                }
                for (Process w : workers) {
                    w.waitFor();
                    //Si uno no acabó por lo que sea:
                    if (w.isAlive()) throw new IOException("Un worker no ha finalizado.");
                }

                System.out.println("\nBLOQUE " + (b + 1) + " VALIDADO\n");
                System.out.println("Datos: " + datos);
                System.out.println("Hash anterior: " + hashAnterior);
                System.out.println("Dificultad: " + DIFICULTAD);
                System.out.println("Worker ganador: " + ganador + " - PID: " + workers[ganador].pid());
                System.out.println("Nonce encontrado: " + nonce);
                System.out.println("\nHash recibido: " + hashRecibido);
                System.out.println("\nHash recalculado por el principal: " + hashRecalculado);
                System.out.println("\nValidacion: hashes coincidentes y prefijo de " + DIFICULTAD + " ceros correcto.");
                System.out.println("Bloque incorporado a la cadena.");
                System.out.println("Workers de esta búsqueda: todos finalizados.");

                hashAnterior = hashRecalculado;
            }

            System.out.println("CADENA FINAL:");
            for (int k = 0; k < cadenaHash.size(); k++) {
                System.out.println("Bloque " + (k + 1) + ": " + datosBloques.get(k));
                System.out.println("  Anterior: " + cadenaAnterior.get(k));
                System.out.println("  Nonce:    " + cadenaNonce.get(k));
                System.out.println("  Hash:     " + cadenaHash.get(k));
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            error = true;
        } finally {
            // Pase lo que pase, no dejamos procesos hijos activos
            for (Process w : workers) {
                if (w != null && w.isAlive()) w.destroy();
            }
            for (Process w : workers) {
                if (w != null) w.waitFor();
            }
        }

        if (error) System.exit(1);
    }

    static String calcularHash(String texto) throws NoSuchAlgorithmException {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
