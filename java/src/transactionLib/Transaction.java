package transactionLib;

import transactionLib.TX;
import transactionLib.TXLibExceptions;

public abstract class Transaction implements Runnable {

    @Override
    public void run() {

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