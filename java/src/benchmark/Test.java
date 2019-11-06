package benchmark;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import structures.Skiplist;

/**
 * SynchrobenchTDSL-java, a benchmark to evaluate the implementations of 
 * transactional and non-transactionl skiplists.
 * 
 * @author Ariel Livshits (based on "Syncrobench" by Vincent Gramoli)
 * 
 */
public class Test {

	public static final String VERSION = "29-10-2019";

	/* The array of threads executing the benchmark */
	private Thread[] threads = new Thread[Parameters.numThreads];
	/* The array of runnable thread codes */
	private Runnable[] runnables = new Runnable[Parameters.numThreads];
	/* The observed duration of the benchmark */
	private double elapsedTime = 0;
	/* The throughput */
	private long throughputOps = 0;
	private long throughputTxs = 0;
	/* The iteration */
	private int currentIteration = 0;

	/* The total number of operations for all threads */
	private long total = 0;
	/* The total number of successful operations for all threads */
	private long numReadOps = 0;
	private long numWriteOps = 0;
	/* The total number of aborts */
	private long aborts = 0;
	/* The instance of the benchmark */
	protected Skiplist<Integer, Object> skiplistBench = null;
	
	private RunnableFactory runnableFactory = new RunnableFactory();
	private CountDownLatch latch;

	
	/* ---------------------------- Thread-Local Section ---------------------------- */

	/* The thread-private PRNG */
	final private static ThreadLocal<Random> s_random = new ThreadLocal<Random>() {
		@Override
		protected synchronized Random initialValue() {
			return new Random();
		}
	};

	/* ---------------------------- Construction Section ---------------------------- */
	
	/**
	 * Constructor sets up the benchmark by reading parameters and creating
	 * threads
	 * 
	 * @param args
	 *            the arguments of the command-line
	 */
	public Test(String[] args) throws InterruptedException {
		printHeader();
		try {
			parseCommandLineParameters(args);
		} catch (Exception e) {
			System.err.println("Cannot parse parameters.");
			e.printStackTrace();
		}
	}
	
	/**
	 * Initialize the benchmark
	 * 
	 * @param benchName
	 *            the class name of the benchmark
	 * @return the instance of the initialized corresponding benchmark
	 */
	@SuppressWarnings("unchecked")
	public void instanciateAbstraction(String benchName) {
		
		try {
			Class<Skiplist<Integer, Object>> benchClass = 
					(Class<Skiplist<Integer, Object>>) Class.forName(benchName);
			Constructor<Skiplist<Integer, Object>> constructor = benchClass.getConstructor();

			if (Skiplist.class.isAssignableFrom((Class<?>) benchClass))
				skiplistBench = (Skiplist<Integer, Object>) constructor.newInstance();
			
			fill(Parameters.range, Parameters.size);
			
		} catch (Exception e) {
			System.err.println("Cannot find benchmark class: " + benchName);
			System.exit(-1);
		}
	}
	
	/* -------------------------------- Main Section -------------------------------- */

	public static void main(String[] args) throws InterruptedException, IOException {
		
		Test test = new Test(args);
		//test.printParams();

		// running the bench
		for (; test.currentIteration < Parameters.iterations; test.currentIteration++) {
			test.instanciateAbstraction(Parameters.benchClassName);
			
			System.out.println("Currently running experiment #" + String.valueOf(test.currentIteration));
			
			try {
				test.initThreads();
			} catch (Exception e) {
				System.err.println("Cannot launch operations.");
				e.printStackTrace();
			}
			test.execute();
			test.printBasicStats();
			test.resetStats();
		}
	}
	
	public void fill(final int range, final long size) {
		for (long i = size; i > 0;) {
			Integer v = s_random.get().nextInt(range);
			if (skiplistBench.putIfAbsent((Integer) v, (Integer) v) == null)
				i--;
		}
	}
			
	/**
	 * Creates as many threads as requested
	 * 
	 * @throws InterruptedException
	 *             if unable to launch them
	 */
	private void initThreads() throws InterruptedException {
		
		latch = new CountDownLatch(1);		
		for (int threadNum = 0; threadNum < Parameters.numThreads; threadNum++) {
			switch (Parameters.testType) {
				case "tdsl.tx":
					runnables[threadNum] = runnableFactory.getTxInstance(threadNum, skiplistBench, latch);
					break;
				case "tdsl.ntx":
					runnables[threadNum] = runnableFactory.getInstance(threadNum, skiplistBench, latch);
					break;
				default:
					System.err.println("Wrong test type");
					System.exit(0);
			}		
			threads[threadNum] = new Thread(runnables[threadNum]);
		}

	}

	/**
	 * Execute the main thread that starts and terminates the benchmark threads
	 * 
	 * @throws InterruptedException
	 */
	private void execute() throws InterruptedException {
		
		long startTime;
		startTime = System.currentTimeMillis();
		for (Thread thread : threads)
			thread.start();
		latch.countDown();
		for (Thread thread : threads)
			thread.join();

		long endTime = System.currentTimeMillis();
		elapsedTime = ((double) (endTime - startTime)) / 1000.0;
	}

	/* ----------------------------- Input/Output Section --------------------------- */

	/**
	 * Parse the parameters on the command line
	 */
	private void parseCommandLineParameters(String[] args) throws Exception {
		int argNumber = 0;

		while (argNumber < args.length) {
			String currentArg = args[argNumber++];

			try {
				/*
				if (currentArg.equals("--help") || currentArg.equals("-h")) {
					printUsage();
					System.exit(0); 
				}
				if (currentArg.equals("--verbose")
						|| currentArg.equals("-v")) {
					Parameters.detailedStats = true;
				} else {*/
				String optionValue = args[argNumber++];
				if (currentArg.equals("--thread-nums")
						|| currentArg.equals("-t"))
					Parameters.numThreads = Integer.parseInt(optionValue);
				else if (currentArg.equals("--size")
						|| currentArg.equals("-i"))
					Parameters.size = Integer.parseInt(optionValue);
				else if (currentArg.equals("--range")
						|| currentArg.equals("-r"))
					Parameters.range = Integer.parseInt(optionValue);
				else if (currentArg.equals("--benchmark")
						|| currentArg.equals("-b"))
					Parameters.benchClassName = "structures." + optionValue;
				else if (currentArg.equals("--type"))
					Parameters.testType = optionValue;
				else if (currentArg.equals("--iterations")
						|| currentArg.equals("-n"))
					Parameters.iterations = Integer.parseInt(optionValue);
				else if (currentArg.equals("--minTxOps"))
					Parameters.minTxOps = Integer.parseInt(optionValue);
				else if (currentArg.equals("--minNonTxOps"))
					Parameters.minNonTxOps = Integer.parseInt(optionValue);
				else if (currentArg.equals("--ReadOnlyFrac"))
					Parameters.ROTxFrac = Double.parseDouble(optionValue);
				
			} catch (IndexOutOfBoundsException e) {
				System.err.println("Missing value after option: " + currentArg
						+ ". Ignoring...");
			} catch (NumberFormatException e) {
				System.err.println("Number expected after option:  "
						+ currentArg + ". Ignoring...");
			}
		}
		assert (Parameters.range >= Parameters.size);
		if (Parameters.range != 2 * Parameters.size)
			System.err.println("Note that the value range is not twice " + 
							   "the initial size, thus the size expectation varies at runtime.");
	}

	/**
	 * Print a 80 character line filled with the same marker character
	 * 
	 * @param ch
	 *            the marker character
	 */
	private void printLine(char ch) {
		StringBuffer line = new StringBuffer(79);
		for (int i = 0; i < 79; i++)
			line.append(ch);
		System.out.println(line);
	}

	/**
	 * Print the header message on the standard output
	 */
	private void printHeader() {
		String header = "SynchrobenchTDSL-java\n" + 
						"A benchmark to evaluate the implementations of \n" + 
						"transactional and non-transactionl skiplists.";
		printLine('-');
		System.out.println(header);
		printLine('-');
		System.out.println();
	}

	/**
	 * Print the benchmark usage on the standard output
	 */
	/*
	private void printUsage() {
		String syntax = "Usage:\n"
				+ "java synchrobench.benchmark.Test [options] [-- stm-specific options]\n\n"
				+ "Options:\n"
				+ "\t-v            -- print detailed statistics (default: "
				+ Parameters.detailedStats
				+ ")\n"
				+ "\t-t thread-num -- set the number of threads (default: "
				+ Parameters.numThreads
				+ ")\n"
				+ "\t-u updates    -- set the number of threads (default: "
				+ Parameters.numWrites
				+ ")\n"
				+ "\t-a writeAll   -- set the percentage of composite updates (default: "
				+ Parameters.numWriteAlls
				+ ")\n"
				+ "\t-s snapshot   -- set the percentage of composite read-only operations (default: "
				+ Parameters.numSnapshots
				+ ")\n"
				+ "\t-r range      -- set the element range (default: "
				+ Parameters.range
				+ ")\n"
				+ "\t-b benchmark  -- set the benchmark (default: "
				+ Parameters.benchClassName
				+ ")\n"
				+ "\t-i size       -- set the datastructure initial size (default: "
				+ Parameters.size
				+ ")\n"
				+ "\t-n iterations -- set the bench iterations in the same JVM (default: "
				+ Parameters.iterations
				+ ")\n"
				+ "\t-W warmup     -- set the JVM warmup length, in seconds (default: "
				+ Parameters.warmUp + ").";
		System.err.println(syntax);
	}*/

	/**
	 * Print the parameters that have been given as an input to the benchmark
	 */
	/*
	private void printParams() {
		String params = "Benchmark parameters" + "\n" + "--------------------"
				+ "\n" + "  Detailed stats:          \t"
				+ (Parameters.detailedStats ? "enabled" : "disabled")
				+ "\n"
				+ "  Number of threads:       \t"
				+ Parameters.numThreads
				+ "\n"
				+ "  Write ratio:             \t"
				+ Parameters.numWrites
				+ " %\n"
				+ "  WriteAll ratio:          \t"
				+ Parameters.numWriteAlls
				+ " %\n"
				+ "  Snapshot ratio:          \t"
				+ Parameters.numSnapshots
				+ " %\n"
				+ "  Size:                    \t"
				+ Parameters.size
				+ " elts\n"
				+ "  Range:                   \t"
				+ Parameters.range
				+ " elts\n"
				+ "  WarmUp:                  \t"
				+ Parameters.warmUp
				+ " s\n"
				+ "  Iterations:              \t"
				+ Parameters.iterations
				+ "\n"
				+ "  Benchmark:               \t"
				+ Parameters.benchClassName;
		System.out.println(params);
	}*/

	private CsvWriter initCSV() throws IOException {
		
		String path = System.getProperty("user.dir") + File.separator + 
													   Parameters.benchClassName + 
													   '_' + Parameters.testType + ".csv";
		
		boolean exists = (new File(path)).exists();
			
		CsvWriter csvWriter = new CsvWriter(path, true);
		
		if (!exists) {
			
			ArrayList<String> header = new ArrayList<String> (Arrays.asList("Iteration", "throughputOps", "throughputTxs", 
					"aborts", "elapsedTime", "numReadOps", "numWriteOps", "operations"));
			
			List<String> paramNames = Parameters.paramNames();
			for (int i = 0; i < paramNames.size(); i++) 
				header.add(i + 1, paramNames.get(i));
			
			csvWriter.writeToCsv(header);
		}
		
		return csvWriter;

	}
	
	/**
	 * Print the statistics on the standard output
	 * @throws IOException 
	 */
	private void printBasicStats() throws IOException {
		for (short threadNum = 0; threadNum < Parameters.numThreads; threadNum++) {

			if (runnables[threadNum] instanceof  TxThread) {
				
				numReadOps += ((TxThread) runnables[threadNum]).readOps;
				numWriteOps += ((TxThread) runnables[threadNum]).writeOps;
				aborts += ((TxThread) runnables[threadNum]).aborts;
			}
			
			else {
				numReadOps += ((NonTxThread) runnables[threadNum]).readOps;
				numWriteOps += ((NonTxThread) runnables[threadNum]).writeOps;
				aborts += ((NonTxThread) runnables[threadNum]).aborts;
			}
		}
		total = numReadOps + numWriteOps;
		throughputOps = (long) ((double) total / elapsedTime);
		throughputTxs = (long) ((double) Parameters.numThreads / elapsedTime);
		printLine('-');
		System.out.println("Benchmark statistics");
		printLine('-');
		System.out.println("  Throughput (ops/s):      \t" + throughputOps);
		System.out.println("  Throughput (Tx/s):       \t" + throughputTxs);
		System.out.println("  Aborts:       		   \t" + aborts);
		System.out.println("  Elapsed time (s):        \t" + elapsedTime);
		System.out.println("  Number of ReadOps:       \t" + numReadOps);
		System.out.println("  Number of WriteOps:      \t" + numWriteOps);
		System.out.println("  Operations:              \t" + total + "\t( 100 %)");
		
		printLine('*');
		
		CsvWriter csvWriter = initCSV();
		List<String> paramValues = Parameters.paramValues();
		ArrayList<String> csvLine = new ArrayList<String>();
		
		csvLine.add(String.valueOf(currentIteration));
		for (int i = 0; i < paramValues.size(); i++) 
			csvLine.add(i + 1, paramValues.get(i));
		
		csvLine.add(String.valueOf(throughputOps));
		csvLine.add(String.valueOf(throughputTxs));
		csvLine.add(String.valueOf(aborts));
		csvLine.add(String.valueOf(elapsedTime));
		csvLine.add(String.valueOf(numReadOps));
		csvLine.add(String.valueOf(numWriteOps));
		csvLine.add(String.valueOf(total));
		
		csvWriter.writeToCsv(csvLine);		
		
		csvWriter.flush();
		csvWriter.close();		
	}
	
	private void resetStats() {
		elapsedTime = 0;
		throughputOps = 0;
		throughputTxs = 0;		
		total = 0;
		numReadOps = 0;
		numWriteOps = 0;
		aborts = 0;
	}

}