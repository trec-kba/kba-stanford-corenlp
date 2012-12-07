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
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.tika.exception.TikaException;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefGraphAnnotation;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;


class myWord{

	public String word;
	public String pos;
	public String lemma;
	public String ne;
	
	public int offset1 = 0;
	public int offset2 = 0;
	
	public int corefID;
	
	public int dep_partent = 0;
	public String dep_class = "_";
	
	public myWord(String _word, String _pos, String _lemma, String _ne, int _corefID){
		word = _word;
		pos = _pos;
		lemma = _lemma;
		ne = _ne;
		
		corefID = _corefID;
	}
	
}


class mySentence{
	ArrayList<myWord> words = new ArrayList<myWord>();
	
	public void pushWord(myWord word){
		words.add(word);
	}
}

class myDocument{
	ArrayList<mySentence> sentences = new ArrayList<mySentence>();
	
	public void pushSentence(mySentence sent){
		sentences.add(sent);
	}
}

public class runNER extends SimpleFunction {
	private static boolean silent = true;
	private static boolean input_compressed = false;
	private static boolean compress_output = false;
	
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
	    
	    myDocument mydoc = new myDocument();
	    
	    int sentid = 0;
	    for(CoreMap sentence: sentences) {
	    	
	    	mySentence mysent = new mySentence();
	    	
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
	    		
	    		mysent.pushWord(new myWord(word, pos, lemma, ne, -1));
	    		
	    	}
	    	
	    	mydoc.pushSentence(mysent);
	    	
	    	sb.append("</SENT>");
	    	parsedSentences.add(sb.toString());
	    }
	    
		return null;
	}
	
	static public void main(String[] args) throws IOException, TikaException{
		if (!silent) System.err.println("Starting NER...");
	    Properties props = new Properties();
	    props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner, parse, dcoref");
	    
    	props.setProperty("pos.maxlen", "100");
    	props.setProperty("parse.maxlen", "100");
    	
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
			    
			    myDocument mydoc = new myDocument();
			    
			    int sentid = 0;
			    for(CoreMap sentence: sentences) {
			    	
			    	mySentence mysent = new mySentence();
			    	
			    	sentid = sentid + 1;
			    	//os.write("<SENT id=\"" + docid + "_SENT_" + sentid + "\">\n");
			    	
			    	int wordid = 0;
			    	for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
			    		++wordid;
			    		String word = token.get(TextAnnotation.class);
			    		String pos = token.get(PartOfSpeechAnnotation.class);
			    		String lemma = token.get(LemmaAnnotation.class);  
			    		String ne = token.get(NamedEntityTagAnnotation.class);   
			    		//os.write(wordid + "\t" + word + "\t" + pos + "\t" + ne + "\t" + lemma + "\n");
			    	
			    		myWord myword = new myWord(word, pos, lemma, ne, -1);
			    		myword.offset1 = token.beginPosition();
			    		myword.offset2 = token.endPosition();
			    		
			    		mysent.pushWord(myword);
			    	
			    	}
			    	//os.write("</SENT>\n\n");
			    	
			    	Tree tree = sentence.get(TreeAnnotation.class);
			    	
			    	SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
			    	
			    	for(SemanticGraphEdge edge : dependencies.getEdgeSet()){
			    		
			    		myWord source = mysent.words.get(edge.getSource().index()-1);
			    		myWord target = mysent.words.get(edge.getTarget().index()-1);
			    		
			    		target.dep_class = edge.toString();
			    		target.dep_partent = edge.getSource().index();
			    		
			    	}
			    	
			    	
			    	mydoc.pushSentence(mysent);
			    	
			    	//System.out.println(depden)
			    }
			    
			    Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);
			    
			    for(Integer clusterID : graph.keySet()){
			    	CorefChain chain = graph.get(clusterID);
			    	
			    	for(CorefMention cm : chain.getMentionsInTextualOrder()){
			    		//System.out.println("Coref" + clusterID + ":  SENT-" + cm.sentNum + " " + "WORD-" + cm.startIndex + " ~ WORD-" + cm.endIndex + " " + cm.mentionSpan); 
			    		for(int woffset =  cm.startIndex; woffset < cm.endIndex; woffset ++){
			    			mydoc.sentences.get(cm.sentNum-1).words.get(woffset-1).corefID = clusterID;
			    		}
			    	
			    	}
			    }
			    
			    
			    sentid = 0;
			    for(mySentence mysent : mydoc.sentences){
			    	
			    	sentid = sentid + 1;
			    	os.write("<SENT id=\"" + docid + "_SENT_" + sentid + "\">\n");
			    	
			    	int wordid = 0;
			    	for(myWord myword : mysent.words){
			    		wordid = wordid + 1;
			    		os.write(wordid + "\t" + myword.word + 
			    								  "\t" + myword.offset1 + ":" + myword.offset2 + 
			    								  "\t" + myword.pos + 
			    				                  "\t" + myword.ne + "\t" + myword.lemma + 
			    				                  "\t" + myword.dep_class + "\t" + myword.dep_partent +
			    				                  "\t" + myword.corefID + "\n");
			    	}
			    	
			    	os.write("</SENT>\n");
			    }
			    
			    content = "";
			    continue;
	    	}
	    	content += "\n" + line;
	    }
	    os.close();
	}
}
