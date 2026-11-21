import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Worker {
    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("Uso: java Worker id totalWorkers dificultad hashAnterior datos");
            System.exit(1);
        }

        try {
            int id = Integer.parseInt(args[0]);
            int totalWorkers = Integer.parseInt(args[1]);
            int dificultad = Integer.parseInt(args[2]);
            String hashAnterior = args[3];
            String datos = args[4];

            if (id < 0 || totalWorkers < 1 || id >= totalWorkers || dificultad < 1) {
                System.err.println("Worker " + id + ": argumentos no validos.");
                System.exit(1);
            }

            String prefijo = "";
            for (int i = 0; i < dificultad; i++) prefijo += "0";

            long nonce = id;
            String hash = calcularHash(hashAnterior + "|" + datos + "|" + nonce);
            while (!hash.startsWith(prefijo)) {
                nonce += totalWorkers;
                hash = calcularHash(hashAnterior + "|" + datos + "|" + nonce);
            }

            // El resultado se envia al Main por la salida estandar:
            System.out.println(nonce + " " + hash);

        } catch (NumberFormatException | NoSuchAlgorithmException e) {
            System.err.println("Error en el worker: " + e.getMessage());
            System.exit(1);
        }
    }

    static String calcularHash(String texto) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }
}
