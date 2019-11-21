package benchmark;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

import structures.Skiplist;
import transactionLib.RangeIterator;
import transactionLib.TXLibExceptions.AbortException;
import transactionLib.Transaction;
/**
 * A transactional thread of the skiplist benchmark.
 * 
 * @author Ariel Livshits (based on "Syncrobench" by Vincent Gramoli)
 * 
 */
public class TxThread extends Transaction {

	public enum TxType {
		ReadOnly, ReadWrite, WriteOnly
	}
	
	/* The instance of the running benchmark */
	public Skiplist<Integer, Object> bench;
	/* The number of the current thread */
	protected final int myThreadNum;

	/* The counters of the thread successful operations */
	public long readOps = 0;
	public long writeOps = 0;
	/* The counter of aborts */
	public long aborts = -1;
	/* The random number */
	Random rand = new Random();
	public TxType txType;

	public TxThread(int myThreadNum, Skiplist<Integer, Object> bench, CountDownLatch latch, TxType txType) {
		super(latch);
		this.myThreadNum = myThreadNum;
		this.bench = bench;
		this.txType = txType;
	}

	@Override
	public void execute() throws AbortException {
		
		reboot();
		RangeIterator<Object> iter = bench.iterator(Parameters.AtomicIterator);
		iter.init();
			
		if (txType == TxType.ReadOnly) {
			while (iter.hasNext() && readOps < Parameters.numOps) {
				readOps++;
				iter.hasNext();

			}
			
		}
			
		if (txType == TxType.WriteOnly) {
			
			while (writeOps < Parameters.numOps) {
				bench.put(rand.nextInt(Parameters.range), String.valueOf(writeOps));
				writeOps++;
				bench.remove(rand.nextInt(Parameters.range));
				writeOps++;
			}
		}
	}
	
	private void reboot() {
		aborts++;
		readOps = 0;
		writeOps = 0;
	}
}
