package edu.stanford.nlp.pipeline;

import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.charniak.CharniakParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;

/**
 * This class will add parse information to an Annotation from the BLLIP parser.
 * It allows you to use the Charniak parser or Charniak and Johnson reranking parser
 * along with any existing parser and reranking model.
 * 
 * It assumes that the Annotation already contains the tokenized words
 * as a {@code List<List<CoreLabel>>} under
 * {@code DeprecatedAnnotations.WordsPLAnnotation.class}.
 * If the words have POS tags, they will not be used.
 */
public class CharniakParserAnnotator implements Annotator {
  private final boolean VERBOSE;

  CharniakParser parser;

  public CharniakParserAnnotator(String parserModel, String parserExecutable, boolean verbose, int maxSentenceLength) {
    VERBOSE = verbose;
    parser = new CharniakParser(parserExecutable, parserModel);
    parser.setMaxSentenceLength(maxSentenceLength);
  }
  
  public CharniakParserAnnotator() {
    VERBOSE = false;
    parser = new CharniakParser();
  }

  public void annotate(Annotation annotation) {
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      // parse a tree for each sentence
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        Tree tree = null;
        List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
        if (VERBOSE) {
          System.err.println("Parsing: " + words);
        }
        int maxSentenceLength = parser.getMaxSentenceLength();
        // generate the constituent tree
        if (maxSentenceLength <= 0 || words.size() < maxSentenceLength) {
          tree = parser.getBestParse(words);
        }
        else {
          tree = ParserAnnotatorUtils.xTree(words);
        }

        ParserAnnotatorUtils.fillInParseAnnotations(VERBOSE, sentence, tree);
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }
}
