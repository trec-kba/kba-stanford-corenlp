package nlp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class SimpleTextNormalizer extends SimpleFunction {

	public List<String> process(String inputRecord){
		ArrayList<String> outputRecords = new ArrayList<String>();
		outputRecords.add(inputRecord.toLowerCase());
		return outputRecords;
	}
	

	public static void main(String[] args) {
		String fin = args[0];
		String fout = args[1];
		boolean escape = true;
		if (args.length > 2 && args[2].equals("plain")) {
			escape = false;
		}
		try {
			BufferedReader reader = new BufferedReader(new FileReader(fin));
			BufferedWriter writer = new BufferedWriter(new FileWriter(fout));
			String line = reader.readLine();
			SimpleTextNormalizer tagger = new SimpleTextNormalizer();
			tagger.init();
			int cnt = 0;
			while (line != null) {
				String tin = line;
				cnt ++;
				if (escape) tin = TextRunner.decode(line);
				System.out.println("Tagging Line #" + cnt);
				try{
				for (String sent : tagger.process(tin)) {
					writer.write(sent);
					writer.write("\n");
				}
				} catch (OutOfMemoryError e) {
					System.err.println("OOOOOOOOOOOOOOOOUT OF MEMORY!!!!!");
					writer.write("Doc#" + cnt + " was NOT PROCESSED!!!!!!");
					System.gc();
				}
				line = reader.readLine();
			}
			reader.close();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
