package nlp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import util.Timer;

public class TextRunner {

	public static String decode(String coded) {
		if (coded == null) return null;
		StringBuffer plain = new StringBuffer();
		for (int i = 0; i < coded.length(); ++i) {
			char ch = coded.charAt(i);
			if (ch == '\\') {
				if (i == coded.length() - 1) break;
				char ch2 = coded.charAt(i + 1);
				switch(ch2) {
				case 'n': { plain.append('\n'); break; }
				case 'r': { plain.append('\r'); break; }
				case 't': { plain.append('\t'); break; }
				default: { plain.append(ch2); break; }
				}
				++ i;
			} else {
				plain.append(ch);
			}
		}
		return plain.toString();
	}

	public static String encode(String plain) {    
		if (plain == null) return null;
		StringBuffer coded = new StringBuffer();
		for (int i = 0; i < plain.length(); i++) {
			char ch = plain.charAt(i);
			switch (ch) {
			case '\\':  { coded.append("\\\\"); break; }
			case '\n':  { coded.append("\\n"); break; }
			case '\r':  { coded.append("\\r"); break; }
			case '\t':  { coded.append("\\t"); break; }
			default: { coded.append(ch); break; }
			}
		}
		return coded.toString();
	}

	static class InputFile {
		private String name = null;
		private String dir = null;
		private long curLine = 0;
		private long readLines = 0;
		private File file = null;
		private BufferedReader reader = null;

		public InputFile(String parent, String fname) {
			dir = parent;
			name = fname;
			file = new File(dir, name);
		}

		public String name() {
			return name;
		}

		public long length() {
			return file.length();
		}

		public void close() {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Read in next record
		 * @return
		 */
		public String nextRecord() {
			try {
				String line = reader.readLine();
				if (line == null) {
					return null;
				} else {
					++ curLine;
					++ readLines;
					return decode(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}

		/**
		 * Seek to the given position.
		 * @param lineOffset Line offset
		 * @return
		 */
		public boolean seek(long lineOffset) {
			try {
				reader = new BufferedReader(new FileReader(file));
				curLine = lineOffset;
				if (lineOffset == 0) return true;
				String line = reader.readLine();
				long numLines = 1;
				while (line != null && numLines < lineOffset) {
					line = reader.readLine();
					++ numLines;
				}
				System.err.println("Cursor[" + name + "] = " + numLines);
				return lineOffset == numLines;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}
	}

	static class OutputFile {
		private String name = null;
		private String basename = null;
		private String dir = null;
		private long curLine = 0;
		private File file = null;
		private BufferedWriter writer = null;
		private int idx = -1;
		private long numChars = 0;

		public OutputFile(String parent, String fname, int index) {
			dir = parent;
			name = fname;
			basename = fname;
			if (index >= 0) {
				idx = index;
				name += String.format(".%03d", index);
			}
			file = new File(dir, name);
		}
		
		public File getFile() {
			return new File(dir, name);
		}
		
		public int getIndex() {
			return idx;
		}

		public String name() {
			return name;
		}

		public long length() {
			return file.length();
		}

		public void flush() {
			try {
				writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void close() {
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public void suicide() {
			try {
				if (writer != null) writer.close();
				writer = null;
				file.delete();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Write a list of records out
		 * @param outputRecords
		 */
		public void writeRecords(List<String> outputRecords) {
			StringBuffer sb = new StringBuffer();
			for (String rec : outputRecords) {
				sb.append(encode(rec));
				sb.append("\n");
			}
			try {
				numChars += sb.length();
				writer.write(sb.toString());
				curLine += outputRecords.size();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Seek to the given line and truncate the tail.
		 * Currently implementation isn't super efficient: it simply
		 * copies the wanted lines to a new file.
		 * RandomAccessFile can use byte offset, but byte offset doesn't
		 * go along well with BufferedReader.
		 * @param lineOffset
		 */
		public boolean seekAndTruncate(long lineOffset) {
			try {
				String tname = name + ".tmp";
				if (writer != null) {
					writer.close();
					writer = null;
				}
				// Start from zero
				if (lineOffset == 0) {
					writer = new BufferedWriter(new FileWriter(file, false));
					//file.deleteOnExit();
					curLine = 0;
					return true;
				}
				// Copy over lines to new file
				BufferedWriter twriter = new BufferedWriter(new FileWriter(new File(dir, tname)));
				long numLines = 0;
				long numChars = 0;
				if (new File(dir, name).exists()) {
					BufferedReader reader = new BufferedReader(new FileReader(new File(dir, name)));
					String line = reader.readLine();
					while (line != null && numLines < lineOffset) {
						twriter.append(line);
						twriter.append('\n');
						numChars += line.length() + 1;
						++ numLines;
						line = reader.readLine();
					}
					reader.close();
				}
				if (numLines < lineOffset) {
					System.err.println("Requested " + lineOffset + " lines but only found " +
							numLines + " lines!");
				}
				twriter.close();
				curLine = numLines;
				// Rename new file
				file = new File(dir, name);
				File tfile = new File(dir, tname);
				file.renameTo(new File(dir, name + ".stale"));
				tfile.renameTo(new File(dir, name));
				new File(dir, name + ".stale").delete();
				// Refresh file handlers
				file = new File(dir, name);
				writer = new BufferedWriter(new FileWriter(file, true));
				this.numChars = numChars;
				if (numLines > 0) {
					System.err.println("Cursor[" + name + "] = " + numLines);
				}
				return true;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}

	}

	class LogFile {
		String name;

		public LogFile(String fname) {
			name = fname;
		}

		public boolean recover() {
			try {
				BufferedReader reader = new BufferedReader(new FileReader(new File(local_output_dir, name)));
				String line = reader.readLine();
				reader.close();
				String[] specs = line.split("\t");
				boolean wellFormed = true;
				for (String spec : specs) {
					String[] parts = spec.split(":");
					if (parts.length != 3) {
						System.err.println("Bad log file!!!!");
						return false;
					}
					String ftype = parts[0];
					String fname = parts[1];
					long offset = Long.parseLong(parts[2]);
					if (ftype.equals("in")) {
						InputFile fin = inputFiles.get(fname);
						boolean ok = fin.seek(offset);
						if (!ok) {
							wellFormed = false;
							break;
						}
					} else if (ftype.equals("out")) {
						if (split_output) {
							int lastdot = fname.lastIndexOf('.');
							int idx = Integer.parseInt(fname.substring(lastdot + 1));
							fname = fname.substring(0, lastdot);
							OutputFile sfout = new OutputFile(local_output_dir, fname, idx);
							outputFiles.put(fname, sfout);
						}
						OutputFile fout = outputFiles.get(fname);
						boolean ok = fout.seekAndTruncate(offset);
						if (!ok) {
							wellFormed = false;
							break;
						}
					} else {
						wellFormed = false;
						break;
					}
				}
				if (!wellFormed) {
					resetAllFiles();
					return false;
				}
				return true;
			} catch (FileNotFoundException e) {
				// Probably a fresh run, nothing to recover
				resetAllFiles();
				return true;
			} catch (IOException e) {
				// Couldn't read the log file
				resetAllFiles();
				return false;
			}
		}

		public void commit() {
			try {
				ArrayList<String> specs = new ArrayList<String>();
				for (InputFile in : inputFiles.values()) {
					String spec = "in:" + in.name + ":" + in.curLine;
					specs.add(spec);
				}
				HashSet<String> outkeys = new HashSet<String>(outputFiles.keySet());
				for (String fnout : outkeys){
					OutputFile out = outputFiles.get(fnout);
					out.flush();
					String spec = "out:" + out.name + ":" + out.curLine;
					specs.add(spec);
				}
				String line = StringUtils.join(specs, "\t");
				System.err.println("TextRunner::Commit");
				BufferedWriter writer = new BufferedWriter(new FileWriter(new File(local_output_dir, name)));
				writer.write(line);
				writer.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public void remove() {
			File flog = new File(local_output_dir, name);
			if (flog.exists()) {
				flog.delete();
			}
		}
	}

	
	private OutputFile splitOutputFile(OutputFile out) {
		out.close();
		// Upload to the HDFS
		if (upload_output_to_hdfs) {
			try {
				System.out.println("uploading " + out.getFile().getPath());
				hdfs.delete(new Path(hdfs_output_dir, out.name()), false);
				hdfs.copyFromLocalFile(new Path(out.getFile().getPath()), hdfs_output_dir);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			out.suicide();
		}
		// Create a new output file
		int idx = out.getIndex() + 1;
		OutputFile out2 = new OutputFile(local_output_dir, out.basename, idx);
		out2.seekAndTruncate(0);
		outputFiles.put(out.basename, out2);
		logger.commit();
		return out2;
	}
	
	private void resetAllFiles() {
		for (InputFile fin : inputFiles.values()) {
			fin.seek(0);
		}
		for (OutputFile fout : outputFiles.values()) {
			fout.seekAndTruncate(0);
		}
		logger.commit();
	}

	// Name of task
	private String task_name = null;
	// Processor
	private SimpleFunction func = null;
	// Number of seconds between checkpoints
	private int checkpoint_interval = 30;
	// HDFS driver
	private FileSystem hdfs = null;
	private Path hdfs_output_dir = null;
	private String local_input_dir = "./";
	private String local_output_dir = "./";
	private boolean upload_output_to_hdfs = false;
	// Whether to split output file
	private boolean split_output = false;
	// Output split size
	private long output_split_size = 30 * (1 << 20);

	private HashMap<String, InputFile> inputFiles = new HashMap<String, InputFile>();
	private HashMap<String, OutputFile> outputFiles = new HashMap<String, OutputFile>();
	private LogFile logger = null;

	private void addInputFile(String fname) {
		InputFile fin = new InputFile(local_input_dir, fname);
		inputFiles.put(fname, fin);
	}

	private void addOutputFile(String fname) {
		OutputFile fout; 
		if (split_output) {
			fout = new OutputFile(local_output_dir, fname, 0);
		} else {
			fout = new OutputFile(local_output_dir, fname, -1);
		}
		outputFiles.put(fname, fout);
	}

	public void setLocalInputDir(String dir) {
		local_input_dir = dir;
	}
	
	public void setLocalOutputDir(String dir) {
		local_output_dir = dir;
	}
	
	public void setHDFS(FileSystem dfs) {
		hdfs = dfs;
	}
	
	public void setHdfsOutputDir(Path dir) {
		hdfs_output_dir = dir;
	}
	
	public void setSplitOutput(boolean val) {
		split_output = val;
	}
	
	public void setUploadToHdfs(boolean val) {
		upload_output_to_hdfs = val;
	}
	
	public TextRunner(String name, 
			SimpleFunction function) {
		task_name = name;
		func = function;
		logger = new LogFile(task_name + ".textrunner.log");
	}

	public void run() {
		func.init();
		Timer.resetClock();
		System.err.println("Recovering...");
		logger.recover();
		Timer.printElapsed();

		System.err.println("Processing...");
		// TODO support multiple input/output files
		if (inputFiles.size() != 1 || outputFiles.size() != 1) {
			System.err.println("Currently only supporting 1 in and 1 out!");
			return;
		}
		InputFile fin = (InputFile)inputFiles.values().toArray()[0];
		OutputFile fout = (OutputFile)outputFiles.values().toArray()[0];
		int numRecordsProcessed = 0;
		long numBadRecords = 0;
		Timer.start("runner");
		String recIn = fin.nextRecord();
		while (recIn != null) {
			List<String> recsOut = func.process(recIn);
			fout.writeRecords(recsOut);
			if (func.getStatus().equals("OOM")) {
				recsOut	= new ArrayList<String>();
				recsOut.add(recIn);
				fout.writeRecords(recsOut);
				numBadRecords ++;
			}
			++ numRecordsProcessed;
			if (split_output && fout.numChars >= output_split_size) {
				fout = splitOutputFile(fout);
			}
			double elapsed = Timer.elapsedSeconds("runner");
			if (elapsed >= checkpoint_interval) {
				logger.commit();
				Timer.start("runner");
			}
			recIn = fin.nextRecord();
		}
		fin.close();
		fout.close();
		if (upload_output_to_hdfs) {
			try {
				if (fout.length() > 0) {
					System.out.println("uploading " + fout.getFile().getPath());
					hdfs.copyFromLocalFile(new Path(fout.getFile().getPath()), hdfs_output_dir);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			fout.suicide();
		}
		logger.remove();
		func.cleanUp();
		System.err.println("#BAD_RECORDS = " + numBadRecords);
		Timer.printElapsed();
	}
	
	public static void gzipFile(String from, String to) throws IOException {
		FileInputStream in = new FileInputStream(from);
		GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(to));
		byte[] buffer = new byte[409600];
		int bytesRead;
		while ((bytesRead = in.read(buffer)) != -1)
			out.write(buffer, 0, bytesRead);
		in.close();
		out.close();
	}
	
	
	
	
	public static void heavy(String[] args) throws Exception {
		if (args.length < 3) {
			System.err.println("args: hdfs_din hdfs_dout file_name [workspace_dir [\"wiki\"]]");
			System.exit(0);
		}
		final String HDFS_URI = "hdfs://hdfs-nn.chtc.wisc.edu:9000/";
		final String HDFS_DIR_IN = args[0] + "/";
		final String HDFS_DIR_OUT = args[1] + "/";
		String fname = args[2];
		String workDir = "./";
		if (args.length > 3) {
			workDir = args[3] + "/";
		}
		boolean iswiki = false;
		if (args.length > 4 && args[4].equals("wiki")) iswiki = true;
		
		Configuration conf = new Configuration();
		conf.set("fs.default.name", HDFS_URI);
		conf.set("dfs.replication", "2");
		conf.set("dfs.blocksize", "" + (1 << 25));
		FileSystem dfs = FileSystem.get(conf);
		
		String local_indir = workDir;
		String local_outdir = workDir;
		
		// Download data
		if (!new File(local_indir, fname).exists()) {
			System.err.println("Downloading " + fname);
			dfs.copyToLocalFile(new Path(HDFS_DIR_IN, fname), 
					new Path(local_indir, fname));
			new File(local_indir, fname).deleteOnExit();
		}
		
		// Run it
		System.err.println("Tagging...");
		SimpleTextNormalizer fnorm = new SimpleTextNormalizer();
		SimpleTagging stn = new SimpleTagging();
		if (iswiki) {
			stn.setWikiDocHeader();
		}
		
		TextRunner tr = new TextRunner(fname, stn);
		tr.setHDFS(dfs);
		tr.setHdfsOutputDir(new Path(HDFS_DIR_OUT));
		tr.setUploadToHdfs(true);
		tr.setSplitOutput(true);
		tr.setLocalInputDir(local_indir);
		tr.setLocalOutputDir(local_outdir);
		tr.addInputFile(fname);
		tr.addOutputFile(fname + ".ner");
		
		try{
			tr.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Clean up
		System.err.println("Removing " + fname);
		new File(local_indir, fname).delete();
		
		System.err.println("Done!");
	}

	public static void giga(String[] args) throws Exception {
		String din = args[0] + "/";
		String fin = args[1];
		String dout = args[2] + "/";
		String fout = args[3];
		
		// Run it
		System.err.println("Tagging...");
		SimpleTagging stn = new SimpleTagging();
		TextRunner tr = new TextRunner(fin, stn);
		tr.setLocalInputDir(din);
		tr.setLocalOutputDir(dout);
		tr.addInputFile(fin);
		tr.addOutputFile(fout);
		try{
			tr.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.err.println("Done!");
	}

	public static void main(String[] args) throws Exception {

		giga(args);
		//heavy(args, "/ClueWebPlainTextEscaped/", "/ClueWebTagged/");
		
		/*
		String din = args[0] + "/";
		String fin = args[1];
		String dout = args[2] + "/";
		String fout = args[3];
		
		// Run it
		System.err.println("Tagging...");
		SimpleTagging stn = new SimpleTagging();
		TextRunner tr = new TextRunner(fin, stn);
		tr.setLocalInputDir(din);
		tr.setLocalOutputDir(dout);
		tr.addInputFile(fin);
		tr.addOutputFile(fout);
		try{
			tr.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.err.println("Done!");
		*/
	}

}
