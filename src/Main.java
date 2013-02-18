import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
	public static void main(String[] args) throws InterruptedException {
		test("LinkedBlockingQueue", new BlockingQueueFactory<X>() {
			@Override
			public BlockingQueue<X> create() {
				return new LinkedBlockingQueue<>();
			}
		});

		test("LinkedTransferQueue", new BlockingQueueFactory<X>() {
			@Override
			public BlockingQueue<X> create() {
				return new LinkedTransferQueue<>();
			}
		});

		test("LinkedBlockingDeque", new BlockingQueueFactory<X>() {
			@Override
			public BlockingQueue<X> create() {
				return new LinkedBlockingDeque<>();
			}
		});
	}

	private static void test(String name, BlockingQueueFactory<X> queueFactory) throws InterruptedException {
		for (int numOperations = 1000000; numOperations <= 10000000; numOperations *= 10) {
			for (int numProducers = 1; numProducers <= 4; numProducers++) {
				for (int numConsumers = 1; numConsumers <= 4; numConsumers++) {
					int numTests = 100000000 / numOperations;
					double medianInNanos = execTimeInNanos(numTests, queueFactory, numOperations, numProducers,
							numConsumers);
					double medianInSeconds = medianInNanos / 1e9;
					double megaOpsPerSecond = numOperations / medianInSeconds / 1e6;
					System.out
							.printf("%s: {ops=%10d, producers=%2d, consumers=%2d}: %7.3fs -> %7.3f MegaOps/sec\n", name,
									numOperations, numProducers, numConsumers, medianInSeconds, megaOpsPerSecond);
				}
			}
		}
		System.out.println();
	}

	private static double execTimeInNanos(
			int numTests,
			BlockingQueueFactory<X> queueFactory,
			final int numOperations,
			int numProducers,
			int numConsumers
	) throws InterruptedException {
		if (numTests <= 0) {
			throw new IllegalArgumentException("numTests must be > 0");
		}

		long[] times = new long[numTests];
		for (int i = 0; i < numTests; i++) {
			times[i] = execTimeInNanos(queueFactory, numOperations, 100, numProducers, numConsumers);
		}

		return median(times);
	}

	private static long execTimeInNanos(
			BlockingQueueFactory<X> queueFactory,
			final int numOperations,
			final int workUnit,
			int numProducers,
			int numConsumers
	) throws InterruptedException {
		if (numOperations <= 0) {
			throw new IllegalArgumentException("numOperations must be > 0");
		}

		if (workUnit <= 0) {
			throw new IllegalArgumentException("workUnit must be > 0");
		}

		if (numOperations % workUnit != 0) {
			throw new IllegalArgumentException("numOperations must be a multiple of workUnit");
		}

		final BlockingQueue<X> queue = queueFactory.create();

		final AtomicInteger opsProduced = new AtomicInteger(0);
		final AtomicInteger opsConsumed = new AtomicInteger(0);
		final AtomicLong startInNanos = new AtomicLong(0);
		final AtomicLong endInNanos = new AtomicLong(0);

		final X[] x = new X[workUnit];
		for (int i = 0; i < x.length; i++) {
			x[i] = new X();
		}

		Thread[] producers = new Thread[numProducers];
		for (int i = 0; i < producers.length; i++) {
			producers[i] = new Thread(new Runnable() {
				@Override
				public void run() {
					while (true) {
						int opNumber = opsProduced.addAndGet(workUnit);
						if (opNumber == workUnit) {
							startInNanos.addAndGet(System.nanoTime());
						}
						if (opNumber > numOperations) {
							break;
						}
						for (int i = 0; i < workUnit; i++) {
							queue.add(x[i]);
						}
					}
				}
			});
		}

		Thread[] consumers = new Thread[numConsumers];
		for (int i = 0; i < consumers.length; i++) {
			consumers[i] = new Thread(new Runnable() {
				@Override
				public void run() {
					while (true) {
						int opNumber = opsConsumed.addAndGet(workUnit);
						if (opNumber > numOperations) {
							break;
						}

						for (int i = 0; i < workUnit; i++) {
							try {
								queue.take();
							} catch (InterruptedException e) {
								// Ignore
							}
						}

						if (opNumber == numOperations) {
							endInNanos.addAndGet(System.nanoTime());
						}
					}
				}
			});
		}

		for (Thread producer : producers) {
			producer.start();
		}

		for (Thread consumer : consumers) {
			consumer.start();
		}

		for (Thread producer : producers) {
			producer.join();
		}

		for (Thread consumer : consumers) {
			consumer.join();
		}

		long startLong = startInNanos.longValue();
		long endLong = endInNanos.longValue();
		if (startLong == 0 || endLong == 0) {
			throw new IllegalStateException("Something went wrong...");
		}

		return endLong - startLong;
	}

	private static double median(long... aTimes) {
		if (aTimes.length <= 0) {
			throw new IllegalArgumentException("array length must be > 0");
		}

		long[] times = aTimes.clone();
		Arrays.sort(times);

		if (times.length % 2 == 0) {
			return times[times.length / 2 - 1] / 2. + times[times.length / 2] / 2.;
		} else {
			return times[times.length / 2];
		}
	}

	private interface BlockingQueueFactory<T> {
		BlockingQueue<T> create();

	}

	private static final class X {

	}
}
