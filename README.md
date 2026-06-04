# Wait-notify-ARSW

**Author:** Juan Carlos Bohórquez Monroy

A concurrent computing project in Java that implements a prime number finder with controlled pause/resume capabilities, using the low-level synchronization mechanisms `wait()` / `notifyAll()`.

---

## Overview

The program launches three threads (`PrimeFinderThread`), each responsible for searching prime numbers in a specific range. A coordinator thread (`Control`) supervises execution and, every 5 seconds, **pauses** all search threads, displays a partial report of primes found, and waits for the user to press **ENTER** to resume execution.

---

## Applied Concurrency Concepts

### 1. Shared Monitor

The entire synchronization mechanism revolves around a **single shared instance** of `Control`, which acts as the monitor (`this`). All `synchronized` operations, `wait()`, and `notifyAll()` calls are executed on this same object. This simplifies reasoning about shared state and eliminates the risk of deadlocks from acquiring multiple locks.

### 2. Cooperative Pause Pattern

Instead of suspending threads externally (a mechanism deprecated since Java 1.2 for being unsafe), a **cooperative pause pattern** is used: each worker thread, on every iteration of its loop, voluntarily invokes the monitor's `checkPause()` method. If the `paused` flag is active, the thread blocks inside `wait()` until the coordinator wakes it up.

### 3. Monitor State Variables

| Variable | Purpose |
|---|---|
| `paused` | Boolean flag indicating to worker threads whether they should block |
| `waitingCount` | Counter of how many worker threads are currently inside `wait()` |

`waitingCount` is essential for `Control` to determine when **all** alive threads are effectively paused before reading results, preventing race conditions.

### 4. Guard Conditions

Two guard conditions are used on the same monitor:

| Condition | Evaluated by | What it waits for |
|---|---|---|
| `while (paused)` | `PrimeFinderThread` in `checkPause()` | Waits for `Control` to set `paused = false` |
| `while (waitingCount < alive)` | `Control` in `run()` | Waits for all alive threads to be in `wait()` |

Both conditions form a **bidirectional barrier protocol**: worker threads wait for the coordinator, and the coordinator waits for the worker threads.

### 5. Lost Wakeup Prevention

A *lost wakeup* occurs when a `notify()`/`notifyAll()` is emitted **before** the receiving thread has reached its `wait()`, causing the signal to be lost and the receiver to be blocked indefinitely. This design prevents them through three combined mechanisms:

1. **The condition is always checked inside the lock** before calling `wait()`. If `Control` were to set `paused = false` + `notifyAll()` between the `if (paused)` check and the `wait()` call, it would be impossible because both operations are within the same `synchronized` block.

2. **`Control` also checks its condition before entering `wait()`**: if all worker threads already called `checkPause()` and executed `notifyAll()` before `Control` reaches its `while`, the `waitingCount` value already reflects that reality and `Control` does not enter `wait()`.

3. **`notifyAll()` is used instead of `notify()`**: `notify()` wakes up a single random thread, which could wake up the wrong thread when multiple threads are waiting. `notifyAll()` wakes everyone up, and each one re-evaluates its guard condition with the `while`.

### 6. Spurious Wakeup Protection

The Java Virtual Machine Specification (JVMS) allows *spurious wakeups*: a thread may exit `wait()` without anyone having called `notify()`/`notifyAll()`. For this reason, all guard conditions use `while` instead of `if`:

```java
// Correct — protected against spurious wakeups
while (paused) { wait(); }

// Incorrect — vulnerable
if (paused) { wait(); }
```

### 7. Safe Reading Without Synchronization

Once all worker threads are in `wait()`, none of them are modifying their prime lists. Therefore, `Control` can read `getPrimes().size()` **outside** a `synchronized` block, reducing unnecessary monitor contention.

### 8. Dynamic Detection of Finished Threads

The `aliveCount()` method is invoked just before setting the pause. If a thread finished its range right before the pause, it will not be in `wait()`. Comparing against the number of **alive** threads at that moment (rather than `NTHREADS`) prevents `Control` from waiting indefinitely for a thread that has already finished.

---

## Concurrency Mechanisms Used

This project uses **`synchronized`** as its sole concurrency control mechanism. There are **no atomic variables** (`AtomicInteger`, `AtomicBoolean`, etc.), **no `volatile`** keyword, and **no explicit locks** (`ReentrantLock`, `ReadWriteLock`, etc.).

### Why `synchronized` and not atomic variables?

The decision to use `synchronized` over atomic variables is due to the nature of the coordination problem:

| Requirement | Why atomic variables don't suffice |
|---|---|
| **Multiple state variables must change atomically together** | Both `paused` and `waitingCount` are modified in the same critical section (e.g., in `checkPause()`, where `waitingCount++` and `notifyAll()` must happen together). Atomic variables only protect individual fields, not compound operations across multiple fields. |
| **Thread blocking (`wait()`/`notify()`)** is required | The very core of this design is that worker threads must **block** until the coordinator decides to resume them. Atomic variables cannot block threads; they only provide lock-free reads/writes. `synchronized` combined with `wait()`/`notifyAll()` is the standard Java idiom for condition-based thread coordination. |
| **Guard conditions need mutual exclusion** | The `while (paused)` and `while (waitingCount < alive)` loops require that the check and the `wait()` call happen atomically with respect to other threads modifying those variables. `synchronized` provides this mutual exclusion naturally via the monitor lock. |

In short: atomic variables excel for **lock-free, non-blocking** operations on single fields, but this problem requires **conditional thread blocking** and **compound atomic updates across multiple fields**, which is precisely the domain of `synchronized` + `wait()`/`notifyAll()`.

---

## Synchronization Flow Diagram

```
Control.run()                           PrimeFinderThread.run()
──────────────────────────────────      ───────────────────────────────────
starts 3 threads →                        loop: for i = a..b
                                           checkPause()   ← each number
sleep(5000ms)
                                             synchronized(control):
paused = true                                if paused:
                                                waitingCount++
                                                notifyAll()  ──→ wakes up Control
wait() until waitingCount==N  ←──────────  wait()         (thread blocked)

all paused → reads getPrimes()
shows total
waits for ENTER from user

paused = false
notifyAll()  ─────────────────────────→   exits while(paused)
                                            waitingCount--
                                            continues loop
```

---

## Changes Made

| File | Change | Reason |
|---|---|---|
| `pom.xml` | `1.7` → `21` in `maven.compiler.source/target` | Java 7 is not supported by modern compilers |
| `PrimeFinderThread` | New field `Control control` + updated constructor | Needs reference to the monitor to call `checkPause()` |
| `PrimeFinderThread` | `checkPause()` called on each iteration | Cooperative pause without busy-waiting |
| `PrimeFinderThread` | Removed `System.out.println(i)` | Avoids flooding the console with 30M lines |
| `Control` | Fields `paused` and `waitingCount` | Shared state of the monitor |
| `Control` | `checkPause()` method with `wait()`/`notifyAll()` | Actual blocking point for worker threads |
| `Control` | Loop with `sleep` + pause + ENTER + resume | Implements the pause cycle every 5 seconds |
| `Control` | Methods `anyAlive()` and `aliveCount()` | Detects finished threads to avoid infinite waits |
| `Control` | `t.join()` + final report | Ensures all threads finish before showing the total |

---

## How to run

```bash
cd Wait-notify
mvn compile exec:java
```

Every 5 seconds the program pauses, shows how many primes it has found so far, and waits for you to press **ENTER** to continue. When finished, it displays the final total.
