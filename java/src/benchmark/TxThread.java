package benchmark;

import java.util.Random;

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

	public TxThread(int myThreadNum, Skiplist<Integer, Object> bench, TxType txType) {
		this.myThreadNum = myThreadNum;
		this.bench = bench;
		this.txType = txType;
	}

	@Override
	public void execute() throws AbortException {
		
		aborts++;
		
		if (txType == TxType.ReadOnly || txType == TxType.ReadWrite) {
			RangeIterator<Object> iter = bench.iterator();
			iter.init_upTo(rand.nextInt(Parameters.range));
			while(iter.hasNext()) {
				readOps++;
				iter.next();
			}
			
			for (int op = 0; op + readOps < Parameters.minTxOps; op++) {
				bench.containsKey(rand.nextInt(Parameters.range));
				readOps++;
			}
		}
			
		if (txType == TxType.WriteOnly || txType == TxType.ReadWrite) {
			
			for (int op = 0; op < Parameters.minTxOps; op++) {
				bench.put(rand.nextInt(Parameters.range), String.valueOf(op));
				writeOps++;
			}
		}
		System.out.println("Thread #" + myThreadNum + " finished.");
	}
}
