package benchmark;

import java.io.FileWriter;
import java.io.IOException;

public class CsvWriter extends FileWriter {

	public CsvWriter(String filename) throws IOException {
		super(filename);
	}
	
	public CsvWriter(String filename, boolean append) throws IOException {
		super(filename, append);
	}
	
	private String convertToCSV(String[] data) {
	    String csvStr = "";
	    for (String entry : data)
	    	csvStr += "," + entry;
	    
	    csvStr += "\n";
	    return csvStr.substring(1);
	}
	
	public void writeToCsv(String[] data) throws IOException {
		append(convertToCSV(data));
	}

}
