package benchmark;

import java.util.Arrays;
import java.util.List;

/**
 * Parameters of the Java version of the 
 * SynchrobenchTDSL benchmark.
 *
 * @author Ariel Livshits (based on "Syncrobench" by Vincent Gramoli)
 */
public class Parameters {
    
    public static int
    	numThreads = 16,
    	range = 200,
		size = 100,
    	iterations = 100,
    	numOps = 100;
    
    public static double 
    	ROTxFrac = 0.3,
    	ReadWriteRatio = 0.7,
    	TxNum = 0.8;
    
    public static boolean AtomicIterator = false;

    public static String benchClassName = new String("structures.tdslSkiplist");
    
    public static List<String> paramNames() {
    	return Arrays.asList( "numThreads", "range", "size", "numOps", "ReadOnlyFrac", "ReadWriteRatio", "TxNum" );
    }
    
    public static List<String> paramValues() {
    	return Arrays.asList( String.valueOf(numThreads), String.valueOf(range), 
    						  String.valueOf(size), String.valueOf(numOps),
    						  String.valueOf(ROTxFrac), String.valueOf(ReadWriteRatio), String.valueOf(TxNum));
    }
}
