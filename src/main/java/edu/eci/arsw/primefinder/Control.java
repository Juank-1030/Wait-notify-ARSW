package edu.eci.arsw.primefinder;

import java.util.Scanner;

public class Control extends Thread {

    private static final int NTHREADS = 3;
    private static final int MAXVALUE = 30000000;
    private static final int TMILISECONDS = 5000;

    private final int NDATA = MAXVALUE / NTHREADS;

    private final PrimeFinderThread[] pft;

    // --- monitor state variables ---
    private boolean paused = false;
    private int waitingCount = 0;

    private Control() {
        super();
        this.pft = new PrimeFinderThread[NTHREADS];
        int i;
        for (i = 0; i < NTHREADS - 1; i++) {
            pft[i] = new PrimeFinderThread(i * NDATA, (i + 1) * NDATA, this);
        }
        pft[i] = new PrimeFinderThread(i * NDATA, MAXVALUE + 1, this);
    }

    public static Control newControl() {
        return new Control();
    }

    /**
     * Called by each PrimeFinderThread in its loop.
     * If paused==true, the thread blocks here until resumed.
     */
    public synchronized void checkPause() throws InterruptedException {
        if (paused) {
            waitingCount++;
            notifyAll(); // notifies Control that another thread is waiting
            while (paused) {
                wait();
            }
            waitingCount--;
        }
    }

    @Override
    public void run() {
        // Start the worker threads
        for (PrimeFinderThread t : pft) {
            t.start();
        }

        Scanner scanner = new Scanner(System.in);

        while (anyAlive()) {
            try {
                Thread.sleep(TMILISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (!anyAlive())
                break;

            // 1. Pause all threads
            int alive = aliveCount();
            synchronized (this) {
                paused = true;
                // Wait without busy-wait until all alive threads are in wait()
                while (waitingCount < alive) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // 2. Show how many primes have been found (outside sync, threads paused)
            int total = 0;
            for (PrimeFinderThread t : pft) {
                total += t.getPrimes().size();
            }
            System.out.println("\n--- PAUSE ---");
            System.out.println("Primes found so far: " + total);
            System.out.print("Press ENTER to continue...");

            // 3. Wait for ENTER
            scanner.nextLine();

            // 4. Resume all threads
            synchronized (this) {
                paused = false;
                notifyAll();
            }

            System.out.println("--- RESUMING ---\n");
        }

        // Wait for all to finish before showing the final total
        for (PrimeFinderThread t : pft) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
            }
        }

        int total = 0;
        for (PrimeFinderThread t : pft) {
            total += t.getPrimes().size();
        }
        System.out.println("\n=== END: total primes found between 0 and " + MAXVALUE + ": " + total + " ===");
        scanner.close();
    }

    private boolean anyAlive() {
        for (PrimeFinderThread t : pft) {
            if (t.isAlive())
                return true;
        }
        return false;
    }

    private int aliveCount() {
        int count = 0;
        for (PrimeFinderThread t : pft) {
            if (t.isAlive())
                count++;
        }
        return count;
    }
}
