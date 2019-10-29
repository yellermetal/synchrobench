package benchmark;

/**
 * Parameters of the Java version of the 
 * SynchrobenchTDSL benchmark.
 *
 * @author Ariel Livshits (based on "Syncrobench" by Vincent Gramoli)
 */
public class Parameters {
    
    public static int numThreads = 1, 
    	numMilliseconds = 5000,
    	numWrites = 40,
    	numWriteAlls = 0,
    	numSnapshots = 0,
    	range = 2048,
		size = 1024,
		warmUp = 5,
    	iterations = 1,
    	minTxOps = 10;
    
    public static boolean detailedStats = true;

    public static String benchClassName = new String("structures.tdslSkiplist");
}
