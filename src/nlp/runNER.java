package nlp;
/**
 * This wrapper around Stanford's CoreNLP library produces a
 * one-word-per-line output that includes lemmatization, part of
 * speech tagging, dependency parsing, NER classification, and in-doc
 * coreference resolution.
 * 
 * This was created for the TREC KBA evaluation and is released under
 * the GPL, because Stanford CoreNLP is released under the GPL.
 */

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

/** 
 * Convenience class for organizing the data coming out of CoreNLP
 */
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

/** 
 * Convenience class for organizing the data coming out of CoreNLP
 */
class mySentence{
	ArrayList<myWord> words = new ArrayList<myWord>();
	
	public void pushWord(myWord word){
		words.add(word);
	}
}

/** 
 * Convenience class for organizing the data coming out of CoreNLP
 */
class myDocument{
	ArrayList<mySentence> sentences = new ArrayList<mySentence>();
	
	public void pushSentence(mySentence sent){
		sentences.add(sent);
	}
}


/** 
 * Adapted from the example wrapper provided by Stanford CoreNLP.
 * 
 * This generates one-word-per-line (OWPL) output by reading in a very
 * simple XML file containing blobs of text from files.  The OWPL
 * output is put into a text file with similarly simple XML structure.
 */
public class runNER extends SimpleFunction {
	private static boolean silent = true;
	private static boolean input_compressed = false;
	private static boolean compress_output = false;
	
	//public static String doNER(String )

    private Pattern patternDocHead = Pattern.compile("<FILENAME (.*?)>");
    
    private StanfordCoreNLP pipeline = null;

    /**
     * main function called when running java -jar runNER.jar <input> <output>
     */	
    static public void main(String[] args) throws IOException, TikaException{
	if (!silent) System.err.println("Starting NER...");
	Properties props = new Properties();
	props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner, parse, dcoref");
	
	// cause Part of Speech tagger and Dependency Parser to split
	// up sentences that are longer than 100 words.
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
	Pattern p = Pattern.compile("<FILENAME docid=\"(.*?)\">");

	// read in the <FILENAME ...>TEXT</FILENAME> input and
	// generate output with OWPL between SENT tags.
	while((line = is.readLine()) != null){	// for each line
	    Matcher m = p.matcher(line);	// is it a <FILENAME...> line
	    if(m.find()){
		if (!silent) System.err.println(line);
		content += "\n" + line;		// append this line to content
		currentDocid = m.group(1);	// parse docid
		continue;
	    }
	    
	    if(line.contains("</FILENAME>")){	// is it a </FILENAME> line, if it is, process the "content" variable
		content += line;		// append this line
		// Ce Zhang, what is this replaceAll doing?
		// To John: we want <AAA src=xxx style=yyy> to be <AAA>
		// Ce Zhang:  are we expecting any incoming tags other than <FILENAME ...>?
		content = content.replaceAll(" [^<>]*?>", ">");	
		String docid = currentDocid;	// set doc-id
		
		Annotation document = new Annotation(content);	
		pipeline.annotate(document);	// run Stanford CoreNLP
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);	// get set of sentences
		
		myDocument mydoc = new myDocument();	// object for a document object
		
		int sentid = 0;
		for(CoreMap sentence: sentences) {	// for each sentence
		    
		    mySentence mysent = new mySentence();	// object for a sentence output
		    
		    sentid = sentid + 1;			// sentence id
		    //os.write("<SENT id=\"" + docid + "_SENT_" + sentid + "\">\n");
		    
		    int wordid = 0;
		    for (CoreLabel token: sentence.get(TokensAnnotation.class)) {	// for each word
			++wordid;				// word id
			String word = token.get(TextAnnotation.class);
			String pos = token.get(PartOfSpeechAnnotation.class);
			String lemma = token.get(LemmaAnnotation.class);  
			String ne = token.get(NamedEntityTagAnnotation.class);	// get annotation   
			//os.write(wordid + "\t" + word + "\t" + pos + "\t" + ne + "\t" + lemma + "\n");
			
			myWord myword = new myWord(word, pos, lemma, ne, -1);	// object for a word output
			myword.offset1 = token.beginPosition();	// set start offset
			myword.offset2 = token.endPosition();	// set end offset
			
			mysent.pushWord(myword);		// add word output to sentence output
			
		    }
		    //os.write("</SENT>\n\n");
		    
		    Tree tree = sentence.get(TreeAnnotation.class);	
		    
		    SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);  //get parser result
		    
		    for(SemanticGraphEdge edge : dependencies.getEdgeSet()){
			
			myWord source = mysent.words.get(edge.getSource().index()-1);	// get start word of a path
			myWord target = mysent.words.get(edge.getTarget().index()-1);	// get end word of a path
			
			target.dep_class = edge.toString();	// set label of the path
			target.dep_partent = edge.getSource().index();	// set parent word of a word
			
		    }
		    
			    	
		    mydoc.pushSentence(mysent);	// add sentence to a document
		    
		    //System.out.println(depden)
		}
		
		Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);	// get co-reference result
		
		for(Integer clusterID : graph.keySet()){	// for each cluster
		    CorefChain chain = graph.get(clusterID);	
		    
		    for(CorefMention cm : chain.getMentionsInTextualOrder()){	// for each mention in the cluster
			//System.out.println("Coref" + clusterID + ":  SENT-" + cm.sentNum + " " + "WORD-" + cm.startIndex + " ~ WORD-" + cm.endIndex + " " + cm.mentionSpan); 
			for(int woffset =  cm.startIndex; woffset < cm.endIndex; woffset ++){	// for each word in the mention
			    mydoc.sentences.get(cm.sentNum-1).words.get(woffset-1).corefID = clusterID;		// update the word's cluster ID
			}
			
		    }
		}

		os.write("<FILENAME id=\"" + docid + "\">\n");	// output <FILENAME ...>
		sentid = 0;	// lets output!!
		for(mySentence mysent : mydoc.sentences){	// for each sentence
		    
		    sentid = sentid + 1;
		    os.write("<SENT id=\"" + docid + "_SENT_" + sentid + "\">\n");	// output <SENT>
		    
		    int wordid = 0;
		    for(myWord myword : mysent.words){	// for each word, output a line
			wordid = wordid + 1;
			os.write(wordid + "\t" + myword.word + 
				 "\t" + myword.offset1 + ":" + myword.offset2 + 
				 "\t" + myword.pos + 
				 "\t" + myword.ne + "\t" + myword.lemma + 
				 "\t" + myword.dep_class + "\t" + myword.dep_partent +
				 "\t" + myword.corefID + "\n");
		    }
		    
		    os.write("</SENT>\n");	// output </SENT>
		}
		os.write("</FILENAME>\n");
		
		content = "";	// clear "content" such that we can start a new document
		continue;	// continue, so that we will not get line324 (content += "\n" + line;).
	    }
	    content += "\n" + line;	// otherwise, append line to content

	}
	os.close();
    }
}
