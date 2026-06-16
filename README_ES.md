# Wait-notify-ARSW

**Autor original:** Juan Carlos Bohórquez Monroy

Proyecto de computación concurrente en Java que implementa un buscador de números primos con capacidades de pausa/reanudación controladas, utilizando los mecanismos de sincronización de bajo nivel `wait()` / `notifyAll()`.

---

## ¿Qué es la concurrencia?

La **concurrencia** es la capacidad de un programa para ejecutar múltiples tareas en simultáneo, aprovechando los múltiples núcleos de los procesadores modernos. En Java, esto se logra principalmente a través de **hilos (threads)**, que son flujos de ejecución independientes que comparten el mismo espacio de memoria.

### Ejemplo de la vida real: Cocina con múltiples chefs

Imagina una cocina de restaurante con 3 chefs:
- **Chef 1**: Corta verduras
- **Chef 2**: Cocina la pasta
- **Chef 3**: Prepara la salsa

Trabajan **concurrentemente** (cada uno hace lo suyo al mismo tiempo), pero comparten recursos limitados como el fuego, la mesada y los utensilios. Si dos chefs quieren usar el mismo fuego al mismo tiempo, necesitan **sincronización** para no estrellarse. En este proyecto, los hilos son como esos chefs, y el monitor compartido es como la mesa de trabajo donde coordinan quién usa qué recurso.

---

## ¿Qué hace este programa?

El programa lanza **tres hilos** (`PrimeFinderThread`), cada uno encargado de buscar números primos en un rango específico:

- Hilo 1: busca del 0 al 9,999,999
- Hilo 2: busca del 10,000,000 al 19,999,999
- Hilo 3: busca del 20,000,000 al 30,000,000

Un hilo coordinador (`Control`) supervisa la ejecución y, **cada 5 segundos**:
1. **Pausa** todos los hilos de búsqueda
2. Muestra un reporte parcial de primos encontrados
3. Espera a que el usuario presione **ENTER** para reanudar

Cuando todos los hilos terminan su rango, muestra el total final de primos encontrados.

---

## Conceptos de Concurrencia Aplicados

### 1. Monitor Compartido

Un **monitor** es un mecanismo de sincronización que permite que solo un hilo ejecute una sección crítica a la vez. En Java, cualquier objeto puede actuar como monitor mediante la palabra clave `synchronized`.

En este proyecto, la instancia compartida de `Control` actúa como el monitor (`this`). Todas las operaciones `synchronized`, `wait()` y `notifyAll()` se ejecutan sobre este mismo objeto. Esto simplifica el razonamiento sobre el estado compartido y elimina el riesgo de *deadlocks* (bloqueos eternos) por adquirir múltiples candados.

#### Ejemplo de la vida real: Semáforo de una calle angosta

Imagina una calle angosta donde solo un carro puede pasar a la vez en cada dirección. El **monitor** es el **semáforo** mismo. Todos los conductores (hilos) miran el mismo semáforo (`synchronized(monitor)`). Si está en rojo (`paused = true`), esperan (`wait()`). El policía de tránsito (el hilo `Control`) cambia el semáforo y avisa a todos (`notifyAll()`) cuando pueden seguir. No importa cuántos conductores haya: todos coordinan mirando el mismo semáforo.

```java
// Semaforo.java — Monitor compartido
public class Semaforo {
    private boolean verde = false;

    public synchronized void esperarVerde() throws InterruptedException {
        while (!verde) {
            wait();  // Conductores esperan el mismo semáforo
        }
    }

    public synchronized void cambiarVerde() {
        verde = true;
        notifyAll();  // Policía avisa a todos los conductores
    }
}

// Conductor.java — Hilo que usa el monitor compartido
public class Conductor extends Thread {
    private final Semaforo semaforo;
    private final String nombre;

    public Conductor(String nombre, Semaforo semaforo) {
        this.nombre = nombre;
        this.semaforo = semaforo;
    }

    @Override
    public void run() {
        try {
            System.out.println(nombre + " espera en el semáforo...");
            semaforo.esperarVerde();
            System.out.println(nombre + " está cruzando!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Semaforo semaforo = new Semaforo();  // Monitor ÚNICO compartido
        new Conductor("Carro Rojo", semaforo).start();
        new Conductor("Carro Azul", semaforo).start();
        new Conductor("Carro Verde", semaforo).start();

        Thread.sleep(2000);  // Simula tiempo en rojo
        System.out.println("--- Policía cambia el semáforo a VERDE ---");
        semaforo.cambiarVerde();  // notifyAll() despierta a todos
    }
}
```

### 2. Patrón de Pausa Cooperativa

En lugar de suspender hilos externamente (mecanismo deprecado desde Java 1.2 por ser inseguro), se usa un **patrón de pausa cooperativa**: cada hilo trabajador, en cada iteración de su ciclo, invoca voluntariamente el método `checkPause()` del monitor. Si la bandera `paused` está activa, el hilo se bloquea dentro de `wait()` hasta que el coordinador lo despierte.

```java
// El hilo trabajador voluntariamente revisa si debe pausarse
public void run() {
    for (int i = a; i < b; i++) {
        control.checkPause();  // Pausa cooperativa
        if (isPrime(i)) {
            primes.add(i);
        }
    }
}
```

#### Ejemplo de la vida real: Trabajadores en una fábrica

En una línea de ensamblaje, cada trabajador (hilo) tiene una tarea específica. Cuando suena la sirena de pausa (el método `checkPause()`), cada trabajador **voluntariamente** deja lo que está haciendo y se detiene. Nadie los obliga externamente; ellos eligen cooperar. Cuando suena la sirena de reanudación (`notifyAll()`), vuelven a su labor exactamente donde la dejaron. Esto es mucho más seguro que un capataz que forcejea con los trabajadores para detenerlos a la fuerza.

```java
// Sirena.java — Monitor con pausa cooperativa
public class Sirena {
    private boolean pausada = false;

    public synchronized void checkPausa() throws InterruptedException {
        while (pausada) {
            wait();  // Trabajador se detiene voluntariamente
        }
    }

    public synchronized void pausar() {
        pausada = true;
    }

    public synchronized void reanudar() {
        pausada = false;
        notifyAll();  // Sirena de reanudación
    }
}

// Trabajador.java — Hilo con pausa cooperativa
public class Trabajador extends Thread {
    private final Sirena sirena;
    private final String nombre;

    public Trabajador(String nombre, Sirena sirena) {
        this.nombre = nombre;
        this.sirena = sirena;
    }

    @Override
    public void run() {
        for (int pieza = 1; pieza <= 10; pieza++) {
            try {
                sirena.checkPausa();  // Pausa cooperativa en cada iteración
                System.out.println(nombre + " ensambló pieza #" + pieza);
                Thread.sleep(500);  // Simula trabajo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println(nombre + " terminó su trabajo");
    }

    public static void main(String[] args) throws InterruptedException {
        Sirena sirena = new Sirena();
        Trabajador t1 = new Trabajador("Chef 1 - Corta verduras", sirena);
        Trabajador t2 = new Trabajador("Chef 2 - Cocina pasta", sirena);
        Trabajador t3 = new Trabajador("Chef 3 - Prepara salsa", sirena);

        t1.start(); t2.start(); t3.start();

        Thread.sleep(2000);
        sirena.pausar();
        System.out.println("\n--- SIRENA DE PAUSA 🚨 ---\n");
        Thread.sleep(3000);

        sirena.reanudar();
        System.out.println("\n--- SIRENA DE REANUDACIÓN 🔔 ---\n");
    }
}
```

### 3. Variables de Estado del Monitor

El monitor `Control` mantiene dos variables de estado:

| Variable | Propósito |
|---|---|
| `paused` | Bandera booleana que indica a los hilos trabajadores si deben bloquearse |
| `waitingCount` | Contador de cuántos hilos trabajadores están actualmente dentro de `wait()` |

`waitingCount` es esencial para que `Control` determine cuándo **todos** los hilos vivos están efectivamente pausados antes de leer los resultados, evitando condiciones de carrera.

#### Ejemplo de la vida real: Lista de asistencia en un examen

El profesor (hilo `Control`) anuncia "examen: todos deben guardar silencio". La bandera `paused` es como la orden de "silencio". Pero el profesor necesita asegurarse de que **todos** los estudiantes (hilos) realmente estén callados antes de repartir el examen. `waitingCount` es como pasar lista: cada estudiante levanta la mano cuando está en silencio. Solo cuando el conteo de manos levantadas coincide con el número de estudiantes presentes, el profesor procede a repartir el examen (leer resultados).

```java
// Aula.java — Monitor con variable de estado waitingCount
public class Aula {
    private boolean silencio = false;
    private int manosArriba = 0;

    public synchronized void pedirSilencio(int estudiantesVivos) throws InterruptedException {
        silencio = true;
        while (manosArriba < estudiantesVivos) {
            wait();  // Profesor espera que todos levanten la mano
        }
    }

    public synchronized void levantarMano() {
        manosArriba++;
        notifyAll();  // "¡Yo ya estoy en silencio, profe!"
    }

    public synchronized void bajarMano() {
        manosArriba--;
    }

    public synchronized void reanudar() {
        silencio = false;
        notifyAll();
    }

    public synchronized void checkSilencio() throws InterruptedException {
        if (silencio) {
            levantarMano();
            while (silencio) {
                wait();
            }
            bajarMano();
        }
    }
}

// Estudiante.java
public class Estudiante extends Thread {
    private final Aula aula;
    private final String nombre;

    public Estudiante(String nombre, Aula aula) {
        this.nombre = nombre;
        this.aula = aula;
    }

    @Override
    public void run() {
        for (int i = 1; i <= 5; i++) {
            try {
                aula.checkSilencio();
                System.out.println(nombre + " resolvió la pregunta #" + i);
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Aula aula = new Aula();
        Estudiante[] grupo = {
            new Estudiante("Ana", aula), new Estudiante("Bob", aula),
            new Estudiante("Carlos", aula), new Estudiante("Diana", aula)
        };
        for (Estudiante e : grupo) e.start();

        Thread.sleep(2000);
        int vivos = 0;
        for (Estudiante e : grupo) if (e.isAlive()) vivos++;
        aula.pedirSilencio(vivos);

        System.out.println("\n--- Todos en silencio. Repartiendo examen ---\n");
        Thread.sleep(2000);
        aula.reanudar();
    }
}
```

### 4. Condiciones de Guardia

Las **condiciones de guardia** son bucles `while` que protegen las llamadas a `wait()`. Se usan dos condiciones de guardia sobre el mismo monitor:

| Condición | Evaluada por | ¿Qué espera? |
|---|---|---|
| `while (paused)` | `PrimeFinderThread` en `checkPause()` | Espera que `Control` establezca `paused = false` |
| `while (waitingCount < alive)` | `Control` en `run()` | Espera que todos los hilos vivos estén en `wait()` |

Ambas condiciones forman un **protocolo de barrera bidireccional**: los hilos trabajadores esperan al coordinador, y el coordinador espera a los hilos trabajadores.

```java
// Condición de guardia en el hilo trabajador
while (paused) {
    wait();  // Solo wait() dentro del while garantiza seguridad
}

// Condición de guardia en el coordinador
while (waitingCount < alive) {
    wait();  // Espera a que todos estén pausados
}
```

#### Ejemplo de la vida real: Juego de "estatua"

En el juego infantil donde un niño grita "¡estatua!" y todos deben quedarse quietos:
- El niño que grita (hilo `Control`) es como el coordinador
- Los demás jugadores (hilos trabajadores) deben congelarse
- El coordinador necesita verificar que **todos** estén quietos (`while (waitingCount < alive)`) antes de voltearse y caminar entre ellos
- Los jugadores, aunque estén quietos, deben **seguir verificando** que el coordinador no haya dicho "¡movimiento!" (`while (paused)`) antes de reanudar

Si usáramos `if` en lugar de `while`, un jugador podría moverse en falso si recibe una señal equivocada.

```java
// JuegoEstatua.java — Condiciones de guardia bidireccionales
public class JuegoEstatua {
    private boolean estatua = false;
    private int congelados = 0;

    public synchronized void checkCongelar() throws InterruptedException {
        if (estatua) {
            congelados++;
            notifyAll();  // "Ya estoy congelado, coordinador"
            while (estatua) {        // ← while, no if
                wait();              // Sigue verificado mientras sea estatua
            }
            congelados--;
        }
    }

    public synchronized void gritarEstatua(int jugadoresVivos) throws InterruptedException {
        estatua = true;
        while (congelados < jugadoresVivos) {  // ← while, no if
            wait();  // Coordinador espera que TODOS se congelen
        }
    }

    public synchronized void gritarMovimiento() {
        estatua = false;
        notifyAll();  // "¡Pueden moverse!"
    }
}

// Jugador.java
public class Jugador extends Thread {
    private final JuegoEstatua juego;
    private final String nombre;

    public Jugador(String nombre, JuegoEstatua juego) {
        this.nombre = nombre;
        this.juego = juego;
    }

    @Override
    public void run() {
        for (int paso = 1; paso <= 8; paso++) {
            try {
                juego.checkCongelar();
                System.out.println(nombre + " dio " + paso + " pasos");
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        JuegoEstatua juego = new JuegoEstatua();
        Jugador[] jugadores = {
            new Jugador("Alice", juego), new Jugador("Bob", juego),
            new Jugador("Charlie", juego)
        };
        for (Jugador j : jugadores) j.start();

        Thread.sleep(1500);
        int vivos = 0;
        for (Jugador j : jugadores) if (j.isAlive()) vivos++;
        juego.gritarEstatua(vivos);
        System.out.println("\n--- 🗿 ESTATUA! Todos congelados ---\n");
        Thread.sleep(3000);
        juego.gritarMovimiento();
        System.out.println("--- 🏃 MOVIMIENTO! ---\n");
    }
}
```

### 5. Prevención de Lost Wakeup (Señal Perdida)

Un *lost wakeup* ocurre cuando un `notify()` o `notifyAll()` se emite **antes** de que el hilo receptor haya llegado a su `wait()`, causando que la señal se pierda y el receptor quede bloqueado indefinidamente.

#### ¿Por qué ocurre?

```
Tiempo  Hilo A (trabajador)           Hilo B (coordinador)
──────  ───────────────────────────    ──────────────────────────
  1     if (paused) {  ← true
  2                                     paused = false
  3                                     notifyAll()    ← señal perdida
  4         wait();  ← se queda esperando para siempre!
```

En este escenario, el hilo A verificó la condición en el paso 1 y vio `paused == true`. Antes de que llegara a `wait()` en el paso 4, el hilo B cambió `paused = false` y emitió `notifyAll()`. El hilo A nunca recibió esa notificación y se queda bloqueado para siempre.

Este diseño lo previene mediante tres mecanismos combinados:

1. **La condición siempre se verifica dentro del candado** antes de llamar `wait()`. Si `Control` estableciera `paused = false` + `notifyAll()` entre la verificación `if (paused)` y la llamada `wait()`, sería imposible porque ambas operaciones están dentro del mismo bloque `synchronized`.

2. **`Control` también verifica su condición antes de entrar en `wait()`**: si todos los hilos trabajadores ya llamaron `checkPause()` y ejecutaron `notifyAll()` antes de que `Control` llegue a su `while`, el valor de `waitingCount` ya refleja esa realidad y `Control` no entra en `wait()`.

3. **Se usa `notifyAll()` en lugar de `notify()`**: `notify()` despierta un solo hilo aleatorio, que podría despertar al hilo equivocado cuando múltiples hilos están esperando. `notifyAll()` despierta a todos, y cada uno reevalúa su condición de guardia con el `while`.

#### Ejemplo de la vida real: Mensajero en una oficina

Imagina una oficina donde los empleados esperan documentos:
- **Empleado A** va al buzón de mensajería (`wait()`)
- El mensajero deja un documento y toca un timbre (`notifyAll()`)
- Si el empleado A se acerca al buzón justo cuando el mensajero ya se fue (señal perdida), nadie le avisará que el documento llegó

La solución es que el empleado **revise el buzón antes de esperar** (la condición de guardia), y que el mensajero **verifique que alguien está esperando** antes de dejar el documento. En nuestro código, esto se logra con los bucles `while` que verifican el estado compartido.

```java
// Buzon.java — Prevención de lost wakeup con while + synchronized
public class Buzon {
    private boolean hayDocumento = false;
    private int empleadosEsperando = 0;

    public synchronized void esperarDocumento() throws InterruptedException {
        empleadosEsperando++;
        notifyAll();                       // Avisa al mensajero: "alguien espera"

        // SIN lost wakeup: la condición se verifica DENTRO del synchronized
        while (!hayDocumento) {            // ← while protege contra señal perdida
            wait();
            // Si nos despertaron espuriamente o antes de tiempo,
            // volvemos a verificar la condición
        }
        empleadosEsperando--;
        hayDocumento = false;
    }

    public synchronized void dejarDocumento() throws InterruptedException {
        while (empleadosEsperando == 0) {  // Mensajero espera que alguien esté listo
            wait();                        // ← también usa while
        }
        hayDocumento = true;
        notifyAll();                       // notifyAll(), no notify()
    }
}

// Empleado.java
public class Empleado extends Thread {
    private final Buzon buzon;
    private final String nombre;

    public Empleado(String nombre, Buzon buzon) {
        this.nombre = nombre;
        this.buzon = buzon;
    }

    @Override
    public void run() {
        try {
            System.out.println(nombre + " espera documento...");
            buzon.esperarDocumento();
            System.out.println(nombre + " recibió el documento!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Buzon buzon = new Buzon();
        new Empleado("Empleado A", buzon).start();
        new Empleado("Empleado B", buzon).start();

        Thread.sleep(1000);
        buzon.dejarDocumento();  // notifyAll() despierta a todos
    }
}
```

### 6. Protección contra Spurious Wakeup (Despertar Espurio)

La Especificación de la Máquina Virtual de Java (JVMS) permite los *spurious wakeups*: un hilo puede salir de `wait()` sin que nadie haya llamado `notify()`/`notifyAll()`. Por esta razón, todas las condiciones de guardia usan `while` en lugar de `if`:

```java
// Correcto — protegido contra despertares espurios
while (paused) { wait(); }

// Incorrecto — vulnerable
if (paused) { wait(); }
```

Con `while`, incluso si el hilo se despierta espuriamente, **vuelve a verificar la condición**. Si `paused` sigue siendo `true`, vuelve a bloquearese. Con `if`, el hilo asumiría incorrectamente que fue despertado por el coordinador y continuaría ejecutándose mientras el sistema debería estar pausado.

#### Ejemplo de la vida real: Despertador falso

Ponte en un hotel. Tu despertador está programado a las 7:00 AM. Pero a las 6:30 AM, hay un incendio falso y suena la alarma general. Te despiertas (spurious wakeup). Con `if (alarm == 7:00AM) { wait(); }`, al despertarte a las 6:30 asumirías que ya son las 7:00 y te levantarías. Con `while (alarm != 7:00AM) { wait(); }`, verificas la hora, ves que aún son las 6:30, y vuelves a dormir (`wait()`).

```java
// Despertador.java — Protección contra spurious wakeup
public class Despertador {
    private boolean sonando = true;  // ¿Ya son las 7:00 AM?

    public synchronized void esperarAlarma() throws InterruptedException {
        // VERSIÓN INCORRECTA — vulnerable a spurious wakeup
        // if (!sonando) { wait(); }

        // VERSIÓN CORRECTA — protegida con while
        while (!sonando) {   // ← while, no if: protege contra despertares falsos
            wait();
        }
        System.out.println("¡Son las 7:00 AM, hora de despertar!");
    }

    public synchronized void activarAlarma() {
        sonando = true;
        notifyAll();
    }
}

// Huesped.java
public class Huesped extends Thread {
    private final Despertador despertador;

    public Huesped(Despertador despertador) {
        this.despertador = despertador;
    }

    @Override
    public void run() {
        try {
            System.out.println("Huesped durmiendo... Zzz");
            despertador.esperarAlarma();
            System.out.println("Huesped se levanta 😊");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Despertador d = new Despertador();

        // Hilo que simula un SPURIOUS WAKEUP (falsa alarma de incendio)
        Thread falsaAlarma = new Thread(() -> {
            try {
                Thread.sleep(1500);
                synchronized (d) {
                    d.notifyAll();  // ← Despertar espurio: nadie activó la alarma real
                }
                System.out.println("🚨 Falsa alarma de incendio a las 6:30 AM");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        falsaAlarma.start();

        // Si usáramos if en lugar de while, el huésped se levantaría aquí
        new Huesped(d).start();

        Thread.sleep(3000);
        System.out.println("--- Son las 7:00 AM ---");
        d.activarAlarma();  // Alarma real
    }
}
```

### 7. Lectura Segura Sin Sincronización

Una vez que todos los hilos trabajadores están en `wait()`, ninguno está modificando sus listas de primos. Por lo tanto, `Control` puede leer `getPrimes().size()` **fuera** de un bloque `synchronized`, reduciendo la contención innecesaria del monitor.

```java
// Todos los hilos están pausados → lectura segura sin sincronización
int total = 0;
for (PrimeFinderThread t : pft) {
    total += t.getPrimes().size();
}
```

#### Ejemplo de la vida real: La bóveda del banco al cierre

Al final del día, todos los cajeros (hilos trabajadores) han terminado sus transacciones y están en pausa. El gerente (hilo `Control`) puede contar el dinero total de la bóveda sin necesidad de custodias adicionales, porque sabe con certeza que nadie está moviendo el dinero en ese momento. No necesita pedirles a los cajeros que "suélten la llave" (el candado `synchronized`) porque ya no la están usando.

```java
// Banco.java — Lectura segura sin sincronización
import java.util.*;

public class Banco {
    private final List<Cajero> cajeros = new ArrayList<>();
    private boolean pausado = false;
    private int enPausa = 0;

    public synchronized void pausar(int vivos) throws InterruptedException {
        pausado = true;
        while (enPausa < vivos) {
            wait();  // Espera que todos los cajeros se detengan
        }
    }

    public synchronized void checkPausa() throws InterruptedException {
        if (pausado) {
            enPausa++;
            notifyAll();
            while (pausado) wait();
            enPausa--;
        }
    }

    public synchronized void reanudar() {
        pausado = false;
        notifyAll();
    }

    // LECTURA SEGURA: los cajeros están pausados, nadie modifica los datos
    public int contarBoveda() {
        int total = 0;
        for (Cajero c : cajeros) {
            total += c.getTransacciones();  // Sin synchronized — lectura segura
        }
        return total;
    }

    public static void main(String[] args) throws InterruptedException {
        Banco banco = new Banco();
        Cajero c1 = new Cajero("Cajero 1", banco);
        Cajero c2 = new Cajero("Cajero 2", banco);
        banco.cajeros.add(c1);
        banco.cajeros.add(c2);
        c1.start();
        c2.start();

        Thread.sleep(2000);
        int vivos = 0;
        for (Cajero c : banco.cajeros) if (c.isAlive()) vivos++;
        banco.pausar(vivos);

        // Todos pausados → lectura SIN synchronized
        System.out.println("Recaudación total del día: $" + banco.contarBoveda());
        System.out.println("(Lectura realizada sin candados — nadie modificaba datos)");
        banco.reanudar();
    }
}

class Cajero extends Thread {
    private final Banco banco;
    private final String nombre;
    private int transacciones = 0;

    public Cajero(String nombre, Banco banco) {
        this.nombre = nombre;
        this.banco = banco;
    }

    public int getTransacciones() { return transacciones; }

    @Override
    public void run() {
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            try {
                banco.checkPausa();
                int monto = rand.nextInt(1000);
                transacciones += monto;
                System.out.println(nombre + " registró $" + monto + " (total: $" + transacciones + ")");
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
```

### 8. Detección Dinámica de Hilos Finalizados

El método `aliveCount()` se invoca justo antes de establecer la pausa. Si un hilo terminó su rango justo antes de la pausa, no estará en `wait()`. Comparar contra el número de hilos **vivos** en ese momento (en lugar de `NTHREADS`) evita que `Control` espere indefinidamente por un hilo que ya terminó.

```java
int alive = aliveCount();  // Cuenta solo los que siguen vivos
synchronized (this) {
    paused = true;
    while (waitingCount < alive) {  // No espera hilos muertos
        wait();
    }
}
```

#### Ejemplo de la vida real: Reunión con asistentes

En una reunión de equipo, el líder anuncia "pausa de 5 minutos". Pero no todos estaban presentes desde el inicio: algunos se fueron, otros llegaron tarde. El líder cuenta cuántas personas **siguen en la sala** en ese momento (`aliveCount()`), no cuántas personas hay en la lista oficial del equipo (`NTHREADS`). Así, si alguien ya se fue, el líder no espera a que esa persona regrese para continuar.

```java
// Reunion.java — Detección dinámica de asistentes presentes
public class Reunion {
    private boolean enPausa = false;
    private int enEspera = 0;

    public synchronized void pedirPausa(int presentes) throws InterruptedException {
        enPausa = true;
        while (enEspera < presentes) {  // Solo espera a los que ESTÁN en la sala
            wait();
        }
    }

    public synchronized void checkPausa() throws InterruptedException {
        if (enPausa) {
            enEspera++;
            notifyAll();
            while (enPausa) wait();
            enEspera--;
        }
    }

    public synchronized void reanudar() {
        enPausa = false;
        notifyAll();
    }
}

// Participante.java
public class Participante extends Thread {
    private final Reunion reunion;
    private final String nombre;
    private final int duracion;  // Simula cuándo se va de la reunión

    public Participante(String nombre, Reunion reunion, int duracion) {
        this.nombre = nombre;
        this.reunion = reunion;
        this.duracion = duracion;
    }

    @Override
    public void run() {
        try {
            for (int minuto = 1; minuto <= duracion; minuto++) {
                reunion.checkPausa();
                System.out.println(nombre + " participando (minuto " + minuto + ")");
                Thread.sleep(400);
            }
            System.out.println("👋 " + nombre + " se retiró de la reunión");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Reunion reunion = new Reunion();

        // Ana dura 10 minutos, Bob dura 4 (se va temprano), Carlos dura 8
        Participante ana = new Participante("Ana", reunion, 10);
        Participante bob = new Participante("Bob", reunion, 4);
        Participante carlos = new Participante("Carlos", reunion, 8);
        ana.start(); bob.start(); carlos.start();

        Thread.sleep(5000);  // Pausa en el minuto 5
        int vivos = 0;
        for (Participante p : new Participante[]{ana, bob, carlos}) {
            if (p.isAlive()) {
                vivos++;
                System.out.println("  ✔ " + p.getName() + " sigue en la sala");
            } else {
                System.out.println("  ✘ " + p.getName() + " ya se fue");
            }
        }
        System.out.println("\n--- LÍDER: Pausa de 5 minutos. Presentes: " + vivos + " ---\n");
        reunion.pedirPausa(vivos);  // Solo espera a los que siguen vivos
        // ... (lee reportes) ...
        reunion.reanudar();
    }
}
```

---

## Mecanismos de Concurrencia Utilizados

Este proyecto usa **exclusivamente `synchronized`** como mecanismo de control de concurrencia. No hay variables atómicas (`AtomicInteger`, `AtomicBoolean`, etc.), no hay la palabra clave `volatile`, y no hay candados explícitos (`ReentrantLock`, `ReadWriteLock`, etc.).

### ¿Por qué `synchronized` y no variables atómicas?

La decisión de usar `synchronized` sobre variables atómicas se debe a la naturaleza del problema de coordinación:

| Requisito | Por qué las variables atómicas no son suficientes |
|---|---|
| **Varias variables de estado deben cambiar atómicamente juntas** | Tanto `paused` como `waitingCount` se modifican en la misma sección crítica (ej. en `checkPause()`, donde `waitingCount++` y `notifyAll()` deben ocurrir juntos). Las variables atómicas solo protegen campos individuales, no operaciones compuestas entre múltiples campos. |
| **Se requiere bloqueo de hilos (`wait()`/`notify()`)** | La esencia de este diseño es que los hilos trabajadores deben **bloquearse** hasta que el coordinador decida reanudarlos. Las variables atómicas no pueden bloquear hilos; solo proveen lecturas/escrituras libres de candados. `synchronized` combinado con `wait()`/`notifyAll()` es el idiom estándar de Java para coordinación condicional de hilos. |
| **Las condiciones de guardia necesitan exclusión mutua** | Los bucles `while (paused)` y `while (waitingCount < alive)` requieren que la verificación y la llamada a `wait()` ocurran atómicamente con respecto a otros hilos que modifican esas variables. `synchronized` provee esta exclusión mutua naturalmente mediante el candado del monitor. |

En resumen: las variables atómicas sobresalen para operaciones **libres de candados y no bloqueantes** sobre campos individuales, pero este problema requiere **bloqueo condicional de hilos** y **actualizaciones atómicas compuestas entre múltiples campos**, que es precisamente el dominio de `synchronized` + `wait()`/`notifyAll()`.

### Comparativa: `synchronized` vs. `ReentrantLock`

Aunque `ReentrantLock` ofrece funcionalidades más avanzadas (como `tryLock()`, tiempo de espera, fairness policy), `synchronized` es suficiente para este problema porque:
- Solo necesitamos una estructura simple de bloqueo/espera/notificación
- `synchronized` tiene menor sobrecarga sintáctica y es menos propenso a errores (no hay riesgo de olvidar liberar el candado con `unlock()`)
- El `synchronized` bloquea y libera automáticamente, incluso si ocurre una excepción

---

## Diagrama de Flujo de Sincronización

```
Control.run()                           PrimeFinderThread.run()
──────────────────────────────────      ───────────────────────────────────
inicia 3 hilos →                          bucle: for i = a..b
                                           checkPause()   ← por cada número
sleep(5000ms)
                                             synchronized(control):
paused = true                                 if paused:
                                                waitingCount++
                                                notifyAll()  ──→ despierta a Control
wait() hasta que waitingCount==N  ←──────  wait()         (hilo bloqueado)

todos pausados → lee getPrimes()
muestra total
espera ENTER del usuario

paused = false
notifyAll()  ──────────────────────────→   sale del while(paused)
                                             waitingCount--
                                             continúa el bucle
```

---

## Cambios Realizados respecto a la versión original

| Archivo | Cambio | Razón |
|---|---|---|
| `pom.xml` | `1.7` → `21` en `maven.compiler.source/target` | Java 7 no es soportado por compiladores modernos |
| `PrimeFinderThread` | Nuevo campo `Control control` + constructor actualizado | Necesita referencia al monitor para llamar `checkPause()` |
| `PrimeFinderThread` | `checkPause()` llamado en cada iteración | Pausa cooperativa sin *busy-waiting* |
| `PrimeFinderThread` | Eliminado `System.out.println(i)` | Evita inundar la consola con 30M de líneas |
| `Control` | Campos `paused` y `waitingCount` | Estado compartido del monitor |
| `Control` | Método `checkPause()` con `wait()`/`notifyAll()` | Punto de bloqueo real para hilos trabajadores |
| `Control` | Bucle con `sleep` + pausa + ENTER + reanudación | Implementa el ciclo de pausa cada 5 segundos |
| `Control` | Métodos `anyAlive()` y `aliveCount()` | Detecta hilos terminados para evitar esperas infinitas |
| `Control` | `t.join()` + reporte final | Asegura que todos los hilos terminen antes de mostrar el total |

---

## Cómo ejecutar

```bash
cd Wait-notify
mvn compile exec:java
```

Cada 5 segundos el programa se pausa, muestra cuántos primos ha encontrado hasta el momento, y espera que presiones **ENTER** para continuar. Cuando termina, muestra el total final.

---

## Preguntas Frecuentes

### ¿Qué pasa si presiono ENTER antes de que todos los hilos estén pausados?

El método `checkPause()` ya fue diseñado para esto: cuando presionas ENTER, `Control` establece `paused = false` y llama `notifyAll()`. Cualquier hilo que aún no haya entrado en `checkPause()` simplemente seguirá ejecutándose sin bloquearse. En el próximo ciclo de pausa (5 segundos después), todos se pausarán nuevamente.

### ¿Por qué no usar `Thread.suspend()` y `Thread.resume()`?

Estos métodos fueron deprecados en Java 1.2 porque son inherentemente inseguros: si suspendes un hilo mientras sostiene un candado, puedes causar *deadlocks* fácilmente. El patrón de pausa cooperativa usado aquí es la alternativa segura y recomendada.

### ¿Qué sucede si un hilo encuentra una excepción dentro de `wait()`?

El `InterruptedException` se captura en ambos lados: en `checkPause()` (hilos trabajadores) y en el bucle principal de `Control`. En ambos casos, se restablece el estado de interrupción con `Thread.currentThread().interrupt()` y se sale del bucle, terminando el hilo de forma limpia.

### ¿Cuántos primos se encuentran aproximadamente?

Entre 0 y 30,000,000 hay aproximadamente 1,856,000 números primos. La ejecución completa puede tomar varios minutos dependiendo del hardware.
