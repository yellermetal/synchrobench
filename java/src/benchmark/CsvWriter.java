package benchmark;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class CsvWriter extends FileWriter {

	public CsvWriter(String filename) throws IOException {
		super(filename);
	}
	
	public CsvWriter(String filename, boolean append) throws IOException {
		super(filename, append);
	}
	
	private String convertToCSV(ArrayList<String> data) {
	    String csvStr = "";
	    for (String entry : data)
	    	csvStr += "," + entry;
	    
	    csvStr += "\n";
	    return csvStr.substring(1);
	}
	
	public void writeToCsv(ArrayList<String> csvLine) throws IOException {
		append(convertToCSV(csvLine));
	}

}
