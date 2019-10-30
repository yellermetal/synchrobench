package benchmark;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import benchmark.TxThread.TxType;
import structures.Skiplist;

public class RunnableFactory {
	
	private Random rand = new Random();
	
	public Runnable getInstance(int myThreadNum, Skiplist<Integer, Object> bench, CountDownLatch latch) {

		if (rand.nextDouble() < Parameters.ROTxFrac)
			return new TxThread(myThreadNum, bench, latch, TxType.ReadOnly);
		
		return new TxThread(myThreadNum, bench, latch, TxType.WriteOnly);	
	}

}
