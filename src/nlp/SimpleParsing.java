package nlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public class SimpleParsing extends SimpleFunction {

    private final Pattern patternSentHead = Pattern.compile("<SENT docid=\"(.*?)\" .*?>");
    private StanfordCoreNLP pipeline = null;
    
    public void init() {
	    Properties props = new Properties();
	    props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner");
	    pipeline = new StanfordCoreNLP(props);
    }
    
	public List<String> process(String doc){
    	Matcher m = patternSentHead.matcher(doc);
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
	    		sb.append(wordid + "\t" + word + "\t" + pos + 
	    				"\t" + ne + "\t" + lemma + "\n");
	    	}
	    	sb.append("</SENT>");
	    	parsedSentences.add(sb.toString());
	    }
	    
		return parsedSentences;
	}
	
}
