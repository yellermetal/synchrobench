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
    	numThreads = 128,
    	range = 2048,
		size = 1024,
    	iterations = 100,
    	minTxOps = 10,
    	minNonTxOps = 10;
    
    public static double ROTxFrac = 0.5;
    
    public static boolean detailedStats = false;

    public static String benchClassName = new String("structures.tdslSkiplist");
    
    public static List<String> paramNames() {
    	return Arrays.asList( "numThreads", "range", "size", "minTxOps", "minNonTxOps", "ReadOnlyFrac" );
    }
    
    public static List<String> paramValues() {
    	return Arrays.asList( String.valueOf(numThreads), String.valueOf(range), String.valueOf(size), 
    						  String.valueOf(minTxOps), String.valueOf(minNonTxOps), String.valueOf(ROTxFrac) );
    }
}
