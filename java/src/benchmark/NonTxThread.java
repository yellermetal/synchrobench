package benchmark;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import structures.Skiplist;

public class NonTxThread implements Runnable {


	/* The instance of the running benchmark */
	public Skiplist<Integer, Object> bench;
	/* The number of the current thread */
	protected final int myThreadNum;

	/* The counters of the thread successful operations */
	public long readOps = 0;
	public long writeOps = 0;
	/* The counter of aborts */
	public long aborts = 0;
	/* The random number */
	Random rand = new Random();
	private CountDownLatch latch;
	
	public NonTxThread(int myThreadNum, Skiplist<Integer, Object> bench, CountDownLatch latch) {
		this.myThreadNum = myThreadNum;
		this.bench = bench;
		this.latch = latch;
	}
	
	@Override
	public void run() {
		
		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		while(readOps < Parameters.numOps) {
			bench.containsKey(rand.nextInt(Parameters.range));
			readOps++;
		}
		
		while (writeOps < Parameters.numOps ) {
			bench.put(rand.nextInt(Parameters.range), String.valueOf(writeOps));
			writeOps++;
		}

		System.out.println("Thread #" + myThreadNum + " finished.");
		
	}

}
