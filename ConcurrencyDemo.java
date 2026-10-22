
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrencyDemo {

    // Shared application settings
    private static volatile boolean applicationRunning = true;

    // Shared counters and messages
    private static final AtomicInteger requestCount = new AtomicInteger(0);
    private static final AtomicReference<String> latestMessage =
            new AtomicReference<>("Application started");

    public static void main(String[] args) throws InterruptedException {

        System.out.println("==================================");
        System.out.println("   EVERYDAY CONCURRENCY DEMO");
        System.out.println("==================================");

        showThreadLifecycle();
        showVolatileInAction();
        showBankAccountSynchronization();
        showInventoryWithLock();
        showAtomicClasses();
        showBankAccountRaceCondition();

        System.out.println("\nAll examples finished successfully.");
    }

    // ---------------------------------------------------------
    // 1. THREAD LIFECYCLE
    // Example: A background worker doing a small task
    // ---------------------------------------------------------

    private static void showThreadLifecycle()
            throws InterruptedException {

        System.out.println("\n1. THREAD LIFECYCLE");

        CountDownLatch startSignal = new CountDownLatch(1);

        Thread backgroundWorker = new Thread(() -> {
            try {
                System.out.println("Background worker has started.");

                // The worker waits until the main thread releases it.
                startSignal.await();

                System.out.println("Worker received the signal.");

                // Simulate a task that takes some time.
                Thread.sleep(300);

                System.out.println("Background work is complete.");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Background worker was interrupted.");
            }
        }, "Background-Worker");

        System.out.println("Before start(): "
                + backgroundWorker.getState());

        backgroundWorker.start();

        // Wait briefly for the worker to enter WAITING.
        boolean waitingSeen = waitForWaitingState(backgroundWorker);

        System.out.println("Worker reached WAITING: " + waitingSeen);

        // Allow the worker to continue.
        startSignal.countDown();

        // Wait for the worker to finish.
        backgroundWorker.join();

        System.out.println("After join(): "
                + backgroundWorker.getState());

        // Thread states to remember:
        // NEW, RUNNABLE, BLOCKED, WAITING,
        // TIMED_WAITING and TERMINATED.
    }

    private static boolean waitForWaitingState(Thread thread)
            throws InterruptedException {

        long deadline = System.nanoTime() + 2_000_000_000L;

        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING) {
                return true;
            }

            if (thread.getState() == Thread.State.TERMINATED) {
                return false;
            }

            Thread.sleep(1);
        }

        return thread.getState() == Thread.State.WAITING;
    }

    // ---------------------------------------------------------
    // 2. VOLATILE AND JAVA MEMORY MODEL
    // Example: Shutting down an application safely
    // ---------------------------------------------------------

    private static void showVolatileInAction()
            throws InterruptedException {

        System.out.println("\n2. VOLATILE AND MEMORY VISIBILITY");

        applicationRunning = true;

        Thread applicationWorker = new Thread(() -> {
            System.out.println("Application worker is running.");

            while (applicationRunning) {
                // Keep checking whether the application should stop.
                Thread.yield();
            }

            System.out.println("Worker noticed the shutdown signal.");
        }, "Application-Worker");

        applicationWorker.start();

        // Give the worker a chance to start.
        Thread.sleep(200);

        // Because the variable is volatile, the worker can
        // observe this update without a synchronized block.
        applicationRunning = false;

        applicationWorker.join();

        System.out.println("Application shutdown completed.");

        // volatile provides visibility and ordering guarantees.
        // It does not make compound operations like count++
        // atomic.
    }

    // ---------------------------------------------------------
    // 3. SYNCHRONIZED
    // Example: Protecting a shared bank account
    // ---------------------------------------------------------

    private static class BankAccount {

        private int balance;

        public BankAccount(int startingBalance) {
            balance = startingBalance;
        }

        public synchronized void deposit(int amount) {
            balance += amount;

            System.out.println(
                    Thread.currentThread().getName()
                            + " deposited " + amount
                            + ". Balance: " + balance);
        }

        public synchronized boolean withdraw(int amount) {
            if (amount > balance) {
                System.out.println(
                        Thread.currentThread().getName()
                                + " could not withdraw "
                                + amount + ": insufficient funds.");

                return false;
            }

            balance -= amount;

            System.out.println(
                    Thread.currentThread().getName()
                            + " withdrew " + amount
                            + ". Balance: " + balance);

            return true;
        }

        public synchronized int getBalance() {
            return balance;
        }
    }

    private static void showBankAccountSynchronization()
            throws InterruptedException {

        System.out.println("\n3. SYNCHRONIZED BANK ACCOUNT");

        BankAccount account = new BankAccount(1000);

        Thread firstCustomer = new Thread(
                () -> account.withdraw(300), "Customer-A");

        Thread secondCustomer = new Thread(
                () -> account.deposit(200), "Customer-B");

        firstCustomer.start();
        secondCustomer.start();

        firstCustomer.join();
        secondCustomer.join();

        System.out.println("Final account balance: "
                + account.getBalance());

        // synchronized allows only one thread at a time
        // to execute a method on this same account object.
    }

    // ---------------------------------------------------------
    // 4. REENTRANTLOCK
    // Example: Reserving items from shared inventory
    // ---------------------------------------------------------

    private static class Inventory {

        private int availableItems = 5;

        private final ReentrantLock inventoryLock =
                new ReentrantLock();

        public boolean reserveItem() {

            // Do not wait indefinitely if another thread owns the lock.
            if (!inventoryLock.tryLock()) {
                System.out.println(
                        Thread.currentThread().getName()
                                + " could not get the inventory lock.");

                return false;
            }

            try {
                if (availableItems <= 0) {
                    System.out.println("No items left to reserve.");
                    return false;
                }

                availableItems--;

                System.out.println(
                        Thread.currentThread().getName()
                                + " reserved an item. Items left: "
                                + availableItems);

                return true;

            } finally {
                // Always release the lock.
                inventoryLock.unlock();
            }
        }

        public int getAvailableItems() {
            inventoryLock.lock();

            try {
                return availableItems;
            } finally {
                inventoryLock.unlock();
            }
        }
    }

    private static void showInventoryWithLock()
            throws InterruptedException {

        System.out.println("\n4. INVENTORY WITH REENTRANTLOCK");

        Inventory inventory = new Inventory();

        Thread customerOne = new Thread(
                inventory::reserveItem, "Shopper-A");

        Thread customerTwo = new Thread(
                inventory::reserveItem, "Shopper-B");

        customerOne.start();
        customerTwo.start();

        customerOne.join();
        customerTwo.join();

        System.out.println("Items remaining: "
                + inventory.getAvailableItems());

        // tryLock() attempts to acquire the lock immediately.
        // If it fails, the method returns without reserving.
    }

    // ---------------------------------------------------------
    // 5. ATOMIC CLASSES
    // Example: Counting application requests
    // ---------------------------------------------------------

    private static void showAtomicClasses()
            throws InterruptedException {

        System.out.println("\n5. ATOMIC CLASSES");

        requestCount.set(0);
        latestMessage.set("Waiting for requests");

        Runnable handleRequests = () -> {
            for (int i = 0; i < 1000; i++) {
                requestCount.incrementAndGet();
            }
        };

        Thread serverOne = new Thread(
                handleRequests, "Server-Worker-A");

        Thread serverTwo = new Thread(
                handleRequests, "Server-Worker-B");

        serverOne.start();
        serverTwo.start();

        serverOne.join();
        serverTwo.join();

        latestMessage.set("All requests processed");

        System.out.println("Total requests handled: "
                + requestCount.get());

        System.out.println("Latest status: "
                + latestMessage.get());

        // AtomicInteger provides thread-safe numeric updates.
        // AtomicReference provides atomic updates to a reference.
    }

    // ---------------------------------------------------------
    // 6. RACE CONDITION
    // Example: Two customers withdrawing from an unsafe account
    // ---------------------------------------------------------

    private static class UnsafeBankAccount {

        private int balance = 100;

        public boolean withdrawWithoutProtection(
                int amount,
                CountDownLatch bothCustomersChecked,
                CountDownLatch allowWithdrawals)
                throws InterruptedException {

            // Read the balance without synchronization.
            int balanceSeen = balance;

            // Both customers must check the balance first.
            bothCustomersChecked.countDown();
            allowWithdrawals.await();

            if (balanceSeen < amount) {
                return false;
            }

            // Both customers may write based on the same old balance.
            balance = balanceSeen - amount;

            System.out.println(
                    Thread.currentThread().getName()
                            + " believes the withdrawal succeeded.");

            return true;
        }

        public int getBalance() {
            return balance;
        }
    }

    private static void showBankAccountRaceCondition()
            throws InterruptedException {

        System.out.println("\n6. RACE CONDITION");

        UnsafeBankAccount account = new UnsafeBankAccount();

        CountDownLatch bothCustomersChecked =
                new CountDownLatch(2);

        CountDownLatch allowWithdrawals =
                new CountDownLatch(1);

        Runnable withdraw80 = () -> {
            try {
                account.withdrawWithoutProtection(
                        80, bothCustomersChecked, allowWithdrawals);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread customerOne = new Thread(
                withdraw80, "Customer-A");

        Thread customerTwo = new Thread(
                withdraw80, "Customer-B");

        customerOne.start();
        customerTwo.start();

        // Wait until both customers have read the balance of 100.
        bothCustomersChecked.await();

        // Let both continue, reproducing the lost-update problem.
        allowWithdrawals.countDown();

        customerOne.join();
        customerTwo.join();

        System.out.println("Starting balance: 100");
        System.out.println("Requested withdrawal per customer: 80");
        System.out.println("Actual stored balance: "
                + account.getBalance());

        System.out.println(
                "Problem: both withdrawals were accepted, "
                        + "although the account had only 100.");

        // This is deliberately unsafe demonstration code.
        // Real banking code must protect the check and update
        // as one synchronized or locked operation.
    }
}
