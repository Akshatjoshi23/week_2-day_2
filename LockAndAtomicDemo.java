import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class LockAndAtomicDemo {

    // shared flag to stop thread
    private static volatile boolean isRunning = true;

    // 1. using synchronized keyword
    static class SimpleSyncCounter {
        private int value = 0;

        public synchronized void addOne() {
            value++;
        }

        public synchronized int getValue() {
            return value;
        }
    }

    // 2. using ReentrantLock manually
    static class MyLockCounter {
        private int value = 0;
        private ReentrantLock myLock = new ReentrantLock();

        public void addOne() {
            myLock.lock();
            try {
                value = value + 1;
            } finally {
                myLock.unlock();
            }
        }

        public int getValue() {
            myLock.lock();
            try {
                return value;
            } finally {
                myLock.unlock();
            }
        }
    }

    // 3. using atomic class
    static class MyAtomicCounter {
        private AtomicInteger count = new AtomicInteger(0);

        public void addOne() {
            count.incrementAndGet();
        }

        public int getValue() {
            return count.get();
        }
    }

    public static void main(String[] args) throws InterruptedException {

        System.out.println("--- Volatile flag test ---");
        Thread watchThread = new Thread(() -> {
            while (isRunning) {
                // just waiting
            }
            System.out.println("Got stop signal, exiting loop");
        });

        watchThread.start();
        Thread.sleep(200);
        isRunning = false; // change flag
        watchThread.join();

        System.out.println("\n--- Comparing 3 counters ---");

        SimpleSyncCounter counter1 = new SimpleSyncCounter();
        MyLockCounter counter2 = new MyLockCounter();
        MyAtomicCounter counter3 = new MyAtomicCounter();

        Runnable myJob = () -> {
            for (int i = 0; i < 100000; i++) {
                counter1.addOne();
                counter2.addOne();
                counter3.addOne();
            }
        };

        Thread worker1 = new Thread(myJob, "T1");
        Thread worker2 = new Thread(myJob, "T2");

        worker1.start();
        worker2.start();

        worker1.join();
        worker2.join();

        System.out.println("Sync counter result: " + counter1.getValue());
        System.out.println("Lock counter result: " + counter2.getValue());
        System.out.println("Atomic counter result: " + counter3.getValue());

        System.out.println("\nAll done");
    }
}