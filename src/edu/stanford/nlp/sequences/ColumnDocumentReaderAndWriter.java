package edu.stanford.nlp.sequences;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.StringUtils;


/**
 * DocumentReader for column format
 *
 * @author Jenny Finkel
 */
public class ColumnDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 3806263423697973704L;

//  private SeqClassifierFlags flags; // = null;
  private String[] map; // = null;
  private IteratorFromReaderFactory<List<CoreLabel>> factory;

//  public void init(SeqClassifierFlags flags) {
//    this.flags = flags;
//    this.map = StringUtils.mapStringToArray(flags.map);
//    factory = DelimitRegExIterator.getFactory("\n(\\s*\n)+", new ColumnDocParser());
//  }

  public void init(SeqClassifierFlags flags) {
    this.map = StringUtils.mapStringToArray(flags.map);
    factory = DelimitRegExIterator.getFactory("\n(?:\\s*\n)+", new ColumnDocParser());
  }


  public void init(String map) {
//    this.flags = null;
    this.map = StringUtils.mapStringToArray(map);
    factory = DelimitRegExIterator.getFactory("\n(\\s*\n)+", new ColumnDocParser());
  }

  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  private int num; // = 0;


  private class ColumnDocParser implements Serializable, Function<String,List<CoreLabel>> {

    private static final long serialVersionUID = -6266332661459630572L;
    private final Pattern whitePattern = Pattern.compile("\\s+");

    public List<CoreLabel> apply(String doc) {
      if (num > 0 && num % 1000 == 0) { System.err.print("["+num+"]"); }
      num++;

      List<CoreLabel> words = new ArrayList<CoreLabel>();

      String[] lines = doc.split("\n");

      for (String line : lines) {
        if (line.trim().length() == 0) {
          continue;
        }
        String[] info = whitePattern.split(line);
        // todo: We could speed things up here by having one time only having converted map into an array of CoreLabel keys (Class<? extends CoreAnnotation<?>>) and then instantiating them. Need new constructor.
        CoreLabel wi;
        try {
          wi = new CoreLabel(map, info);
        } catch (RuntimeException e) {
          System.err.println("Error on line: " + line);
          throw e;
        }
        words.add(wi);
      }
      return words;
    }

  } // end class ColumnDocParser


  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
    for (CoreLabel wi : doc) {
      String answer = wi.get(AnswerAnnotation.class);
      String goldAnswer = wi.get(GoldAnswerAnnotation.class);
      out.println(wi.word() + "\t" + goldAnswer + "\t" + answer);
    }
    out.println();
  }

}
