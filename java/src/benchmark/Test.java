package benchmark;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import benchmark.TxThread.TxType;
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
	private Thread[] threads;
	/* The array of runnable thread codes */
	private TxThread[] runnables;
	/* The throughput */
	private long throughputOps = 0;
	/* The iteration */
	private int currentIteration = 0;
	/* The total number of aborts */
	private long aborts = 0;
	/* The instance of the benchmark */
	protected Skiplist<Integer, Object> skiplistBench = null;
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
		threads = new Thread[Parameters.numThreads];
		runnables = new TxThread[Parameters.numThreads];
		
		
		int threadNum = 0;
		runnables[threadNum] = new TxThread(threadNum, skiplistBench, latch, TxType.ReadOnly);
		threads[threadNum] = new Thread(runnables[threadNum]);
		threadNum++;
		
		for (; threadNum < Parameters.numThreads; threadNum++) {
		
			runnables[threadNum] = new TxThread(threadNum, skiplistBench, latch, TxType.WriteOnly);			
			threads[threadNum] = new Thread(runnables[threadNum]);
		}

	}

	/**
	 * Execute the main thread that starts and terminates the benchmark threads
	 * 
	 * @throws InterruptedException
	 */
	private void execute() throws InterruptedException {
		
		for (Thread thread : threads)
			thread.start();
		latch.countDown();
		for (Thread thread : threads)
			thread.join();

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
						|| currentArg.equals("-s"))
					Parameters.size = Integer.parseInt(optionValue);
				else if (currentArg.equals("--range")
						|| currentArg.equals("-r"))
					Parameters.range = Integer.parseInt(optionValue);
				else if (currentArg.equals("--benchmark")
						|| currentArg.equals("-b"))
					Parameters.benchClassName = "structures." + optionValue;
				else if (currentArg.equals("--atomic")
					    || currentArg.equals("-a"))
					Parameters.AtomicIterator = Boolean.parseBoolean(optionValue);
				else if (currentArg.equals("--iterations")
						|| currentArg.equals("-n"))
					Parameters.iterations = Integer.parseInt(optionValue);
				else if (currentArg.equals("--numOps")
						|| currentArg.equals("-o"))
					Parameters.numOps = Integer.parseInt(optionValue);
				
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


	private CsvWriter initCSV() throws IOException {
		
		String filename = Parameters.benchClassName;
		if (Parameters.AtomicIterator)
			filename += "_AtomicIter";
		else
			filename += "_NonAtomicIter";
		
		filename += ".csv";
		
		String path = System.getProperty("user.dir") + File.separator + filename;
		
		boolean exists = (new File(path)).exists();
			
		CsvWriter csvWriter = new CsvWriter(path, true);
		
		if (!exists) {
			
			ArrayList<String> header = new ArrayList<String> (Arrays.asList("Iteration", "throughputOps", "aborts", "elapsedTime"));
			
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
		
		for (int threadNum = 1; threadNum < Parameters.numThreads; threadNum++)
			aborts += runnables[threadNum].aborts;
		
		long elapsedTime = runnables[0].getElapsedTime();

		throughputOps = (long) ((double) Parameters.numOps / elapsedTime);
		printLine('-');
		System.out.println("Benchmark statistics");
		printLine('-');
		System.out.println("  Throughput (ops/s):      \t" + throughputOps);
		System.out.println("  Aborts:       		   \t" + aborts);
		System.out.println("  Elapsed time (s):        \t" + elapsedTime);
		
		printLine('*');
		
		CsvWriter csvWriter = initCSV();
		List<String> paramValues = Parameters.paramValues();
		ArrayList<String> csvLine = new ArrayList<String>();
		
		csvLine.add(String.valueOf(currentIteration));
		for (int i = 0; i < paramValues.size(); i++) 
			csvLine.add(i + 1, paramValues.get(i));
		
		csvLine.add(String.valueOf(throughputOps));
		csvLine.add(String.valueOf(aborts));		
		csvLine.add(String.valueOf(elapsedTime));
		csvWriter.writeToCsv(csvLine);		
		
		csvWriter.flush();
		csvWriter.close();		
	}
	
	private void resetStats() {
		throughputOps = 0;
		aborts = 0;
	}

}