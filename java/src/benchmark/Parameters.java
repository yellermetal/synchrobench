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
    	numOps = 10;
    
    public static boolean AtomicIterator = false;

    public static String benchClassName = new String("structures.tdslSkiplist");
    
    public static List<String> paramNames() {
    	return Arrays.asList( "numThreads", "range", "size", "numOps");
    }
    
    public static List<String> paramValues() {
    	return Arrays.asList( String.valueOf(numThreads), String.valueOf(range), 
    						  String.valueOf(size), String.valueOf(numOps));
    }
}
