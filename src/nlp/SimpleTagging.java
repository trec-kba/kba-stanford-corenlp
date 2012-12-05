package nlp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;

import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NormalizedNamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.util.CoreMap;

public class SimpleTagging extends SimpleFunction {
	
	private static boolean extraCols = true;
	private static boolean doParse = false;
	private static boolean matchDate = false;
	private static boolean skipFirstLine = false;
    //private final Pattern patternDocHead = Pattern.compile("<DOC id=\"(.*?)\".*?>");
    private Pattern patternDocHead = Pattern.compile("<FILENAME (.*?)>");
    private Pattern patternDocDate = Pattern.compile("\\d{8}");
    private StanfordCoreNLP pipeline = null;
    
    public void setWikiDocHeader() {
    	patternDocHead = Pattern.compile("<DOC id=\"(.*?)\".*?>");
    }
    
    public void init() {
	    Properties props = new Properties();
	    if (doParse) {
	    	props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner, parse");
	    } else {
	    	props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner");
	    	props.put("clean.sentenceendingtags", "p|P|headline|HEADLINE|dateline|DATELINE|" +
	    			"text|TEXT|TURN|SPEAKER|BODY|DATETIME|DOCTYPE|DOCID|" +
	    			"DOC|ENDTIME|POST|POSTER|POSTDATE|DocID|h2|H2|STYLE|style");
	    	props.put("clean.allowflawedxml", true);
	    	props.put("pos.maxlen", 100);
	    	props.put("parser.maxlen", 100);
	    }
	    pipeline = new StanfordCoreNLP(props);
    }

	public void cleanUp() {
		pipeline = null;
		System.gc();
	}
	
	private static final String[] months = {
		"January", "February", "March", "April", "May", "June", 
		"July", "August", "September", "October", "November", "December"
	};
	
	public String natDate(String dn) {
		String year = dn.substring(0,4);
		int month = Integer.parseInt(dn.substring(4,6));
		int day = Integer.parseInt(dn.substring(6));
		if (month > 12 || month < 1) return "";
		return months[month-1] + " " + day + ", " + year;
	}
	
	public List<String> process(String doc){
	    ArrayList<String> parsedSentences = new ArrayList<String>();
    	Matcher m = patternDocHead.matcher(doc);
    	String docid = "NA";
    	String datetag = "";
    	int padding = 0;
    	if(m.find()){
    		docid = m.group(1);
    		if (matchDate) {
	    		Matcher dm = patternDocDate.matcher(docid);
	    		if (dm.find()) {
	    			String datenum = dm.group();
	    			datenum = natDate(datenum);
	    			datetag = "<DATE>" + datenum + ".</DATE>\n";
	    			int headend = doc.indexOf(">");
	    			doc = doc.substring(0, headend+1) + datetag + doc.substring(headend+1);
	    			padding = datetag.length();
	    		}
    		}
    	}
		//doc = doc.replaceAll(" [^<>]*?>", ">");
	    Annotation document = new Annotation(doc);
	    try {
	    	pipeline.annotate(document);
	    } catch (Error e) {
	    	setStatus("OOM");
	    	System.err.println("CRASHED ON DOC: " + docid);
	    	e.printStackTrace();
	    	return parsedSentences;
	    }
	    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
	    
	    int sentid = 1;
	    if (skipFirstLine) sentid = 0;
	    for(CoreMap sentence: sentences) {
	    	// sentid = sentid + 1;
		    StringBuffer sb = new StringBuffer();
	    	sb.append("<SENT docid=\"" + docid + "\" sentid=\"" + sentid + "\">\n");
	    	
	    	int wordid = 0;
	    	for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
	    		++wordid;
	    		String word = token.get(TextAnnotation.class);
	    		String pos = token.get(PartOfSpeechAnnotation.class);
	    		String lemma = token.get(LemmaAnnotation.class);
	    		String ne = token.get(NamedEntityTagAnnotation.class);
	    		int xxbegin = token.get(CharacterOffsetBeginAnnotation.class) - padding;
	    		int xxend = token.get(CharacterOffsetEndAnnotation.class) - padding;
	    		//String nenorm = token.get(NormalizedNamedEntityTagAnnotation.class);
	    		if (extraCols) {
		    		sb.append(wordid + "\t" + word + "\t" + lemma + "\t" + pos + 
		    				"\t" + ne + "\t" + xxbegin + "\t" + xxend + "\n");
	    		} else {
		    		sb.append(wordid + "\t" + word + "\t" + pos + 
		    				"\t" + ne + "\t" + lemma + "\n");
	    		}
	    	}
	    	sb.append("</SENT>");
	    	++ sentid;
	    	if (sentid == 1 && skipFirstLine) { // from date tag
	    		continue;
	    	}
	    	parsedSentences.add(sb.toString());
	    }
	    setStatus("OK");
	    //System.err.println("PROCESSED A DOC\t"+docid+"\t" + parsedSentences.size());
		return parsedSentences;
	}
	
	
	private static boolean silent = false;
	public static void test(SimpleTagging tagger, String fin) {
		try {
			BufferedReader reader = new BufferedReader(new FileReader(fin));
			StringBuffer sb = new StringBuffer();
			String txt = reader.readLine();
			while(txt != null) {
				sb.append(txt + "\n");
				txt = reader.readLine();
			}
			reader.close();
			List<String> lines = tagger.process(sb.toString());
			String fout = fin + ".ner";
			BufferedWriter writer = new BufferedWriter(new FileWriter(fout));
			boolean goodpart = false;
			for (String line : lines) {
				if (!silent) System.out.println(line);
				if (goodpart) {
					writer.append(line + "\n");
				} else if (line.contains("MAGICSTARTINGPOINT")) {
					goodpart = true;
				}
			}
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public static void nerAll(SimpleTagging tagger, String dir) {
		silent = true;
		System.out.println("traversing " + dir);
		Collection<File> files = FileUtils.listFiles(
				  new File(dir), 
				  new RegexFileFilter(".*\\.html"), 
				  DirectoryFileFilter.DIRECTORY
				);
		int i = 0;
		for (File f : files) {
			System.out.println(++i);
			//if (i > 10) break;
			System.out.println(f.getAbsolutePath());
			test(tagger, f.getAbsolutePath());
		}
	}
	
	public static void main(String[] args) {
		SimpleTagging tagger = new SimpleTagging();
		tagger.init();
		
		String t = "/Users/fniu/hazy/data/pdfhtml/MIT20_109S10_syll_self/" +
				"MIT20_109S10_syll_self-1.html";
		//test(tagger, t);
		t = "/p/hazy/data/pdfhtml/";
		nerAll(tagger, t);
		System.exit(0);
		
		/*
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
			SimpleTagging tagger = new SimpleTagging();
			tagger.init();
			int cnt = 0;
			while (line != null) {
				String tin = line;
				cnt ++;
				if (escape) tin = TextRunner.decode(line);
				System.err.println("Tagging Line #" + cnt);
				try{
				for (String sent : tagger.process(tin)) {
					writer.write(TextRunner.encode(sent));
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
		*/
	}
	
}
