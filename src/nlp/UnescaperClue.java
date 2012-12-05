package nlp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import util.Timer;

public class UnescaperClue {

	private static boolean silent = true;
	private static int max_sent_len = 100;
	private static final boolean wikiCols = true;
    private static final Pattern patternSentHead = 
    		Pattern.compile("<SENT docid=\"(.*?)\" sentid=\"(.*?)\">");
	 
	private String sanitize(String s) {
		return s.replace(' ', '_');
	}
	
	public ArrayList<String> lineToConll(String block) {
		ArrayList<String> lines = new ArrayList<String>();
		String[] parts = block.split("\n");
		if (parts.length <= 2) return lines;
		String header = parts[0];
		if (!header.startsWith("<SENT")) return lines;
		Matcher m = patternSentHead.matcher(header);
		String docid = null, sentid = null;
		if (m.find()) {
			docid = m.group(1);
			sentid = m.group(2);
		} else {
			return lines;
		}
		if (parts.length > max_sent_len + 1) {
			if (!silent) System.err.println("skipped long sent of size " + (parts.length-2));
			return lines;
		}
		for (int i = 1; i < parts.length - 1; i++) {
			String[] cols = parts[i].split("\t");
			int goodlen = 5;
			if (wikiCols) goodlen = 7;
			if (cols.length != goodlen) {
				System.err.println("SHOULD BE 7 COLUMNS:\n" + parts[i]);
				break;
			}
			if (wikiCols) {
				String id = cols[0];
				String word = sanitize(cols[1]);
				String lemma = sanitize(cols[2]);
				String pos = cols[3];
				String mtype = cols[4];
				String begin = cols[5];
				String end = cols[6];
				String[] ncols = {id, word, lemma, pos, pos, docid + ":" + 
				sentid + ":" + mtype + ":" + begin + ":" + end};
				lines.add(StringUtils.join(ncols, "\t"));
			} else {
				lines.add(parts[i] + "\t" + docid + ":" + sentid);
			}
		}
		return lines;
	}

	public void unescape(String fin, String fout) {
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
	    while (line != null) {
	    	String palin = TextRunner.decode(line);
	    	ArrayList<String> toklines = lineToConll(palin);
	    	line = is.readLine();
	    	if (toklines == null || toklines.size() == 0) {
	    		continue;
	    	}
	    	for (String tok : toklines) {
	    		os.write(tok + "\n");
	    	}
	    	os.write("\n");
	    }
		is.close();
		os.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void unescapeDir(String din, String dout) {
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
				String pout = dout + "/" + (f.split("\\."))[0] + ".conll";
				unescape(path, pout);
			}
		    System.out.println("done.");
			Timer.printElapsed("escape");
		}
		
	}
	
	

	public static void mass(String[] args) {
		final String HDFS_URI = "hdfs://d-101.cs.wisc.edu:9000/";
		final String HDFS_DIR_OUT = "/GigawordConll/";
		if (args.length == 0) {
			System.err.println("args: file [workspace_dir]");
			System.exit(0);
		}
		String fin = args[0];
		String workDir = "./";
		if (args.length > 1) {
			workDir = args[1] + "/";
		}
		String[] parts = fin.split("/");
		String fname = parts[parts.length-1].split("\\.")[0] + ".conll";
		String fout = workDir + fname;
		
		Configuration conf = new Configuration();
		conf.set("fs.default.name", HDFS_URI);
		conf.set("dfs.replication", "2");
		try {
			FileSystem dfs = FileSystem.get(conf);
			UnescaperClue unesc = new UnescaperClue();
			System.out.println("Processing " + fin);
			unesc.unescape(fin, fout);
			new File(fout).deleteOnExit();
			System.out.println("Sending to " + new Path(HDFS_DIR_OUT, fname));
			dfs.copyFromLocalFile(new Path(fout), new Path(HDFS_DIR_OUT, fname));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		System.out.println("DONE!");
	}
	
	
	public static void main(String[] args) {
		UnescaperClue unesc = new UnescaperClue();
		//mass(args);
		
		/*
		String din = "/p/hazy/condor_2/ner/";
		String dout = "/scratch.1/mr/GigawordConll";
		unesc.unescapeDir(din, dout);
		*/
		
		
		String fin = args[0];
		String fout = args[1];
		unesc.unescape(fin, fout);
		
	}

}
