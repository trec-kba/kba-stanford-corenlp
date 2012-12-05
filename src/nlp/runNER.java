package nlp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.tika.exception.TikaException;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class runNER extends SimpleFunction {
	private static boolean silent = true;
	private static boolean input_compressed = false;
	private static boolean compress_output = true;
	
	//public static String doNER(String )

    private Pattern patternDocHead = Pattern.compile("<FILENAME (.*?)>");
    
    private StanfordCoreNLP pipeline = null;
    
    public void init() {
	    Properties props = new Properties();
	    props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner");
	    pipeline = new StanfordCoreNLP(props);
	    
    }
    
	public List<String> process(String doc){
    	Matcher m = patternDocHead.matcher(doc);
    	String docid = "NA";
    	if(m.find()){
    		docid = m.group(1);
    	}
		doc = doc.replaceAll(" [^<>]*?>", ">");
	    Annotation document = new Annotation(doc);
	    pipeline.annotate(document);
	    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
	    ArrayList<String> parsedSentences = new ArrayList<String>();
	    
	    int sentid = 0;
	    for(CoreMap sentence: sentences) {
	    	sentid = sentid + 1;
		    StringBuffer sb = new StringBuffer();
	    	sb.append("<SENT docid=\"" + docid + "\" sentid=\"" + sentid + "\">\n");
	    	
	    	int wordid = 0;
	    	for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
	    		++wordid;
	    		String word = token.get(TextAnnotation.class);
	    		String pos = token.get(PartOfSpeechAnnotation.class);
	    		String lemma = token.get(LemmaAnnotation.class);  
	    		String ne = token.get(NamedEntityTagAnnotation.class);   
	    		sb.append(wordid + "\t" + word + "\t" + pos + "\t" + ne + "\t" + lemma + "\n");
	    	}
	    	sb.append("</SENT>");
	    	parsedSentences.add(sb.toString());
	    }
	    
		return null;
	}
	
	static public void main(String[] args) throws IOException, TikaException{
		if (!silent) System.err.println("Starting NER...");
	    Properties props = new Properties();
	    props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner");
	    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
	    
	    String filename = args[0];
	    String outfilename = args[1];
	    
	    BufferedReader is = null;
	    if (!input_compressed) {
	    	is = new BufferedReader(new FileReader(filename));
	    } else {
		    FileInputStream fin = new FileInputStream(filename);
		    GZIPInputStream gzis = new GZIPInputStream(fin);
		    InputStreamReader xover = new InputStreamReader(gzis);
		    is = new BufferedReader(xover);
	    }
	    
	    BufferedWriter os = null;
	    if (!compress_output) {
	    	os = new BufferedWriter(new FileWriter(outfilename));
	    } else {
		    FileOutputStream fout = new FileOutputStream(outfilename);
		    GZIPOutputStream gzos = new GZIPOutputStream(fout);
		    OutputStreamWriter xover2 = new OutputStreamWriter(gzos);
		    os = new BufferedWriter(xover2);
	    }
	    
	    String content = "";
	    String currentDocid = null;
	    String line;
	    Pattern p = Pattern.compile("<FILENAME (.*?)>");
	    
	    while((line = is.readLine()) != null){
	    	Matcher m = p.matcher(line);
	    	if(m.find()){
		    	if (!silent) System.err.println(line);
	    		content += "\n" + line;
	    		currentDocid = m.group(1);
	    		continue;
	    	}
	    	
	    	if(line.contains("</FILENAME>")){
	    		content += line;
	    		content = content.replaceAll(" [^<>]*?>", ">");
	    		String docid = currentDocid;
				
			    Annotation document = new Annotation(content);
			    pipeline.annotate(document);
			    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
			    
			    int sentid = 0;
			    for(CoreMap sentence: sentences) {
			    	sentid = sentid + 1;
			    	os.write("<SENT id=\"" + docid + "_SENT_" + sentid + "\">\n");
			    	
			    	int wordid = 0;
			    	for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
			    		++wordid;
			    		String word = token.get(TextAnnotation.class);
			    		String pos = token.get(PartOfSpeechAnnotation.class);
			    		String lemma = token.get(LemmaAnnotation.class);  
			    		String ne = token.get(NamedEntityTagAnnotation.class);   
			    		os.write(wordid + "\t" + word + "\t" + pos + "\t" + ne + "\t" + lemma + "\n");
			    	}
			    	os.write("</SENT>\n\n");
			    }
			    
			    content = "";
			    continue;
	    	}
	    	content += "\n" + line;
	    }
	    os.close();
	}
}
