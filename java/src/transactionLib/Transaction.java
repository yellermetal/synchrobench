
package transactionLib;

import java.util.concurrent.CountDownLatch;

import transactionLib.TX;
import transactionLib.TXLibExceptions;

public abstract class Transaction implements Runnable {

	CountDownLatch latch = null;
	
	public Transaction(CountDownLatch latch) {
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
    }
    
    public abstract void execute() throws TXLibExceptions.AbortException;
}