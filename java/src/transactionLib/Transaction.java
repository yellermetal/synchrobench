
package transactionLib;

import java.util.concurrent.CountDownLatch;

import transactionLib.TX;
import transactionLib.TXLibExceptions;

public abstract class Transaction implements Runnable {

	private long elapsedTime = 0;
	private long startTime = 0;
	private CountDownLatch latch = null;
	
	public Transaction(CountDownLatch latch) {
		startTime = System.currentTimeMillis();
		this.latch = latch;
	}
	
	public Transaction() {}
	
    @Override
    public void run() {
    	
    	if (latch != null) {
	    	try {
				latch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    	
        while (true) {
            try {
                try {
                    TX.TXbegin();                   
                    execute();                   
                } finally { TX.TXend(); }
            } 
            catch (TXLibExceptions.AbortException exp) {
                continue;
            } 
            break; 
        }
        
        elapsedTime = System.currentTimeMillis() - startTime;
    }
    
    public long getElapsedTime() {
    	return elapsedTime;
    }
    
    public abstract void execute() throws TXLibExceptions.AbortException;
}