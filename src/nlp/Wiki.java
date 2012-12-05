package nlp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class Wiki {

    private static final Pattern patternDocHead = Pattern.compile("<DocID>(.*?)</DocID>");
	
    private String tidy(ArrayList<String> toks, String docid, int sentid) {
    	StringBuilder sb = new StringBuilder();
    
    	return sb.toString();
    }
    
 
	public void transform(String fin, String fout) {
		try {
	    BufferedReader is = null;
	    if (!fin.endsWith(".gz")) {
	    	is = new BufferedReader(new FileReader(fin));
	    } else {
		    FileInputStream fis = new FileInputStream(fin);
		    GZIPInputStream gzis = new GZIPInputStream(fis);
		    InputStreamReader xover = new InputStreamReader(gzis);
		    is = new BufferedReader(xover);
	    }

		BufferedWriter os = new BufferedWriter(new FileWriter(fout));
	    
	    String line = is.readLine();
	    ArrayList<String> toks = new ArrayList<String>();
	    String docid = "NA";
	    int sentid = 0;
	    while (line != null) {
	    	if (line.startsWith("1\t<DOC id=")) {
	    		Matcher m = patternDocHead.matcher(line);
	    		if (m.find()) {
	    			docid = m.group(1);
	    		}
	    		sentid = 0;
	    		toks = new ArrayList<String>();
	    	} else if (line.length() == 0) {
	    		sentid ++;
	    		if (!toks.isEmpty()) {
	    			String s = tidy(toks, docid, sentid);
	    			os.write(s + "\n");
	    		}
	    	} else {
	    		toks.add(line);
	    	}
	    	line = is.readLine();
	    }
		is.close();
		os.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	
	public static void main(String[] args) {
	}

}
