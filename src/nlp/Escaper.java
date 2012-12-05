package nlp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import util.Timer;

public class Escaper {

	public void escape(String fin, String fout) {
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
	    StringBuffer sb = new StringBuffer();
	    while (line != null) {
	    	if (line.startsWith("<DOC id=")) {
	    		sb = new StringBuffer();
	    		sb.append(line + "\n");
	    	} else if (line.startsWith("</DOC>")) {
	    		sb.append(line);
	    		String esc = escapeDoc(sb.toString());
	    		os.write(esc + "\n");
	    	} else {
	    		sb.append(line + "\n");
	    	}
	    	line = is.readLine();
	    }
		is.close();
		os.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String escapeDoc(String doc) {
		return TextRunner.encode(doc);
	}
	
	public void escapeDir(String din, String dout) {
		File dir = new File(din);
		String[] kids = dir.list();
		if(kids == null){
			System.out.println("Specified dir doesn't exist: " + din);
			System.exit(0);
		}else{
			Timer.start("escape");
			System.out.println("Processing dir: " + din);
			for (String f : kids) {
				String path = din + "/" + f;
				System.out.println(path);
				String pout = dout + "/" + (f.split("\\."))[0] + ".txt";
				escape(path, pout);
			}
		    System.out.println("done.");
			Timer.printElapsed("escape");
		}
		
	}
	
	public static void main(String[] args) {
		String din = "/scratch.1/mr/English_gigaword_4th_ed/disk_2/data/xin_eng";
		String dout = "/scratch.1/mr/GigawordEscaped";
		Escaper esc = new Escaper();
		esc.escapeDir(din, dout);
	}

}
