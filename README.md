# Proyecto de PSP - Mini blockchain - Lucas Piacentini

Simulación de una cadena de bloques con minado por prueba de trabajo repartido entre procesos Java independientes.

El programa Main lee los datos de un fichero de texto y construye la cadena bloque a bloque. Para cada bloque lanza cuatro procesos Worker que buscan en paralelo un nonce válido. El Main comprueba la solución, incorpora el bloque y termina los workers antes de pasar al siguiente.

## Ficheros

- `Main.java`: construye la cadena, lanza los workers, valida las soluciones y los finaliza.
- `Worker.java`: proceso minero. Busca un nonce y lo envia al Main por la salida estándar.
- `datosDePrueba.txt`: fichero de ejemplo (una línea con contenido = un bloque; las líneas vacías serán ignoradas).


El programa pedirá por teclado:

1. La semilla inicial (texto no vacío). Su hash es la referencia del primer bloque.
2. La ruta del fichero de datos (por ejemplo `datos.txt`).

### Establecer la dificultad

La dificultad es el número de ceros que debe tener al principio cada hash válido. Hay dos formas de establecerla:

- Pasándola como argumento (tiene prioridad):

  ```
  java Main 5
  ```

- Cambiando el valor de la variable `DIFICULTAD` al principio de `Main.java` (por defecto 4) y volviendo a compilar.

El valor debe ser un entero entre 1 y 64. Con una dificultad mayor hacen falta mas intentos de media (con valores de 6 o más puede tardar bastante).

## Funcionamiento

- Hash de un bloque: `SHA-256(hash anterior + "|" + datos + "|" + nonce)`.
- El hash de la semilla es el punto de partida y no tiene que cumplir la dificultad.
- Reparto del trabajo: el worker `i` (de 4) prueba los nonces `i`, `i+4`, `i+8`, ... asi que ninguno repite candidatos.
- Comunicación: el main pasa por argumentos el ID del worker, el número de workers, la dificultad, el hash anterior y los datos. El worker responde por su salida estándar con `nonce hash`.
- Validación: el main recalcula el hash y comprueba que coincide con el recibido y que empieza por la cantidad de ceros exigida. Si no, rechaza la propuesta y sigue atendiendo a los demás workers.
- Finalización: al aceptar un bloque se terminan los workers que sigan activos y se espera a que acaben todos. Ante un error también se terminan todos los procesos hijos.
- Al final se muestra la cadena completa: datos, hash anterior, nonce y hash de cada bloque.

## Errores controlados

El programa avisa y termina sin lanzar workers si la semilla está vacía, el fichero no se puede leer o la dificultad no es válida. Si el fichero no tiene líneas con datos, informa de que no hay bloques que procesar.
