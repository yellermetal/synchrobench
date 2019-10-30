package benchmark;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import structures.Skiplist;
import transactionLib.RangeIterator;

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

		RangeIterator<Object> iter = bench.iterator();
		iter.init_upTo(rand.nextInt(Parameters.range));
		while(iter.hasNext()) {
			readOps++;
			iter.next();
		}

		
		for (int op = 0; op + readOps < Parameters.minNonTxOps; op++) {
			bench.containsKey(rand.nextInt(Parameters.range));
			readOps++;
		}
		
		for (int op = 0; op < Parameters.minNonTxOps; op++) {
			bench.put(rand.nextInt(Parameters.range), String.valueOf(op));
			writeOps++;
		}

		System.out.println("Thread #" + myThreadNum + " finished.");
		
	}

}
