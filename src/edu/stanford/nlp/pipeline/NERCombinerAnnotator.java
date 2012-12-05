package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NormalizedNamedEntityTagAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Timing;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

/**
 * This class will add NER information to an
 * Annotation using a combination of NER models.
 * It assumes that the Annotation
 * already contains the tokenized words as a
 * List&lt;? extends CoreLabel&gt; or a
 * List&lt;List&lt;? extends CoreLabel&gt;&gt; under Annotation.WORDS_KEY
 * and adds NER information to each CoreLabel,
 * in the CoreLabel.NER_KEY field.  It uses
 * the NERClassifierCombiner class in the ie package.
 *
 * @author Jenny Finkel
 * @author Mihai Surdeanu (modified it to work with the new NERClassifierCombiner)
 */
public class NERCombinerAnnotator implements Annotator {

  private NERClassifierCombiner ner;

  private Timing timer = new Timing();
  private boolean VERBOSE = true;

  public NERCombinerAnnotator() throws IOException, ClassNotFoundException {
    this(true);
  }

  private void timerStart(String msg) {
    if(VERBOSE){
      timer.start();
      System.err.println(msg);
    }
  }
  private void timerStop() {
    if(VERBOSE){
      timer.stop("done.");
    }
  }

  public NERCombinerAnnotator(boolean verbose) throws IOException, ClassNotFoundException {
    VERBOSE = verbose;
    timerStart("Loading NER combiner model...");
    ner = new NERClassifierCombiner(new Properties());
    timerStop();
  }

  public NERCombinerAnnotator(boolean verbose, String... classifiers)
  throws IOException, ClassNotFoundException {
    VERBOSE = verbose;
    timerStart("Loading NER combiner model...");
    ner = new NERClassifierCombiner(classifiers);
    timerStop();
  }

  public NERCombinerAnnotator(NERClassifierCombiner ner, boolean verbose) {
    VERBOSE = verbose;
    this.ner = ner;
  }

  public void annotate(Annotation annotation) {
    timerStart("Adding NER Combiner annotation...");
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      // classify tokens for each sentence
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        List<CoreLabel> output = this.ner.classifySentenceWithGlobalInformation(tokens, annotation, sentence);
        if (VERBOSE) {
          boolean first = true;
          System.err.print("NERCombinerAnnotator direct output: [");
          for (CoreLabel w : output) {
            if (first) { first = false; } else { System.err.print(", "); }
            System.err.print(w.toString());
          }
          System.err.println(']');
        }

        for (int i = 0; i < tokens.size(); ++i) {

          // add the named entity tag to each token
          String neTag = output.get(i).get(NamedEntityTagAnnotation.class);
          String normNeTag = output.get(i).get(NormalizedNamedEntityTagAnnotation.class);
          tokens.get(i).setNER(neTag);
          if(normNeTag != null) tokens.get(i).set(NormalizedNamedEntityTagAnnotation.class, normNeTag);
          NumberSequenceClassifier.transferAnnotations(output.get(i), tokens.get(i));
        }

        if (VERBOSE) {
          boolean first = true;
          System.err.print("NERCombinerAnnotator output: [");
          for (CoreLabel w : tokens) {
            if (first) { first = false; } else { System.err.print(", "); }
            System.err.print(w.toShorterString("Word", "NamedEntityTag", "NormalizedNamedEntityTag"));
          }
          System.err.println(']');
        }
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
    //timerStop("done.");
  }
}
