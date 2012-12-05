package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PositionAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ShapeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.process.Americanize;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.util.CoreMap;

import java.util.*;
import java.util.regex.Pattern;


/**
 * This class is used to wrap the ObjectBank used by the sequence
 * models and is where any sort of general processing, like the IOB mapping
 * stuff and wordshape stuff, should go.
 * It checks the SeqClassifierFlags to decide what to do.
 * <p>
 * TODO: We should rearchitect this so that the FeatureFactory-specific
 * stuff is done by a callback to the relevant FeatureFactory.
 *
 * @author Jenny Finkel
 */

public class ObjectBankWrapper<IN extends CoreMap> extends ObjectBank<List<IN>> {

  private static final long serialVersionUID = -3838331732026362075L;

  private SeqClassifierFlags flags;
  private ObjectBank<List<IN>> wrapped;
  private Set<String> knownLCWords;


  public ObjectBankWrapper(SeqClassifierFlags flags, ObjectBank<List<IN>> wrapped, Set<String> knownLCWords) {
    super(null,null);
    this.flags = flags;
    this.wrapped = wrapped;
    this.knownLCWords = knownLCWords;
  }


  @Override
  public Iterator<List<IN>> iterator() {
    Iterator<List<IN>> iter = new WrappedIterator(wrapped.iterator());

    // If using WordShapeClassifier, we have to make an extra pass through the
    // data before we really process it, so that we can build up the
    // database of known lower case words in the data.  We do that here.
    if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) && (!flags.useShapeStrings)) {
      while (iter.hasNext()) {
        List<IN> doc = iter.next();
        for (IN fl : doc) {
          String word = fl.get(TextAnnotation.class);
          if (word.length() > 0) {
            char ch = word.charAt(0);
            if (Character.isLowerCase(ch)) {
              knownLCWords.add(word);
            }
          }
        }
      }
      iter = new WrappedIterator(wrapped.iterator());
    }
    return iter;
  }

  private class WrappedIterator extends AbstractIterator<List<IN>> {
    Iterator<List<IN>> wrappedIter;
    Iterator<List<IN>> spilloverIter;

    public WrappedIterator(Iterator<List<IN>> wrappedIter) {
      this.wrappedIter = wrappedIter;
    }

    @Override
    public boolean hasNext() {
      while ((spilloverIter == null || !spilloverIter.hasNext()) &&
             wrappedIter.hasNext()) {
        List<IN> doc = wrappedIter.next();
        List<List<IN>> docs = new ArrayList<List<IN>>();
        docs.add(doc);
        fixDocLengths(docs);
        spilloverIter = docs.iterator();
      }
      return wrappedIter.hasNext() ||
        (spilloverIter != null && spilloverIter.hasNext());
    }

    @Override
    public List<IN> next() {
      // this while loop now is redundant because it should
      // have already been done in "hasNext".
      // I'm keeping it so that the diff is minimal.
      // -pichuan
      while (spilloverIter == null || !spilloverIter.hasNext()) {
        List<IN> doc = wrappedIter.next();
        List<List<IN>> docs = new ArrayList<List<IN>>();
        docs.add(doc);
        fixDocLengths(docs);
        spilloverIter = docs.iterator();
      }

      return processDocument(spilloverIter.next());
    }
  }

  public List<IN> processDocument(List<IN> doc) {
    if (flags.mergeTags) { mergeTags(doc); }
    if (flags.iobTags) { iobTags(doc); }
    doBasicStuff(doc);

    return doc;
  }

  private String intern(String s) {
    if (flags.intern) {
      return s.intern();
    } else {
      return s;
    }
  }


  private final Pattern monthDayPattern = Pattern.compile("Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|January|February|March|April|May|June|July|August|September|October|November|December", Pattern.CASE_INSENSITIVE);

  private String fix(String word) {
    if (flags.normalizeTerms || flags.normalizeTimex) {
      // Same case for days/months: map to lowercase
      if (monthDayPattern.matcher(word).matches()) {
        return word.toLowerCase();
      }
    }
    if (flags.normalizeTerms) {
      return Americanize.americanize(word, false);
    }
    return word;
  }


  private void doBasicStuff(List<IN> doc) {
    int position = 0;
    for (IN fl : doc) {

      // position in document
      fl.set(PositionAnnotation.class, Integer.toString((position++)));

      // word shape
      if ((flags.wordShape > WordShapeClassifier.NOWORDSHAPE) && (!flags.useShapeStrings)) {
        String s = intern(WordShapeClassifier.wordShape(fl.get(TextAnnotation.class), flags.wordShape, knownLCWords));
        fl.set(ShapeAnnotation.class, s);
      }

      // normalizing and interning
      // was the following; should presumably now be
      // if ("CTBSegDocumentReader".equalsIgnoreCase(flags.documentReader)) {
      if ("edu.stanford.nlp.wordseg.Sighan2005DocumentReaderAndWriter".equalsIgnoreCase(flags.readerAndWriter)) {
        // for Chinese segmentation, "word" is no use and ignore goldAnswer for memory efficiency.
        fl.set(CharAnnotation.class,intern(fix(fl.get(CharAnnotation.class))));
      } else {
        fl.set(TextAnnotation.class, intern(fix(fl.get(TextAnnotation.class))));
        fl.set(GoldAnswerAnnotation.class, fl.get(AnswerAnnotation.class));
      }
    }
  }

  /**
   * Take a {@link List} of documents (which are themselves {@link List}s
   * of something that extends {@link CoreMap}, CoreLabel by default),
   * and if any are longer than the length
   * specified by flags.maxDocSize split them up.  If maxDocSize is negative,
   * nothing is changed.  In practice, documents need to be not too long or
   * else the CRF inference will fail due to numerical problems.
   * This method tries to be smart
   * and split on sentence boundaries, but this is hard-coded to English.
   * 
   * @param docs The list of documents whose length might be adjusted.
   */
  private void fixDocLengths(List<List<IN>> docs) {
    final int maxDocSize = flags.maxDocSize;

    WordToSentenceProcessor<IN> wts = new WordToSentenceProcessor<IN>();
    List<List<IN>> newDocuments = new ArrayList<List<IN>>();
    for (List<IN> document : docs) {
      if (maxDocSize <= 0 || document.size() <= maxDocSize) {
        if (!document.isEmpty()) {
          newDocuments.add(document);
        }
        continue;
      }
      List<List<IN>> sentences = wts.process(document);
      List<IN> newDocument = new ArrayList<IN>();
      for (List<IN> sentence : sentences) {
        if (newDocument.size() + sentence.size() > maxDocSize) {
          if (!newDocument.isEmpty()) {
            newDocuments.add(newDocument);
          }
          newDocument = new ArrayList<IN>();
        }
        newDocument.addAll(sentence);
      }
      if (!newDocument.isEmpty()) {
        newDocuments.add(newDocument);
      }
    }

    docs.clear();
    docs.addAll(newDocuments);
  }

  private void iobTags(List<IN> doc) {
    String lastTag = "";
    for (IN wi : doc) {
      String answer = wi.get(AnswerAnnotation.class);
      if (!answer.equals(flags.backgroundSymbol)) {
        int index = answer.indexOf('-');
        String prefix;
        String label;
        if (index < 0) {
          prefix = "";
          label = answer;
        } else {
          prefix = answer.substring(0,1);
          label = answer.substring(2);
        }

        if (!prefix.equals("B")) {
          if (!lastTag.equals(label)) {
            wi.set(AnswerAnnotation.class, "B-" + label);
          } else {
            wi.set(AnswerAnnotation.class, "I-" + label);
          }
        }
        lastTag = label;
      } else {
        lastTag = answer;
      }
    }
  }


  private void mergeTags(List<IN> doc) {
    for (IN wi : doc) {
      String answer = wi.get(AnswerAnnotation.class);
      if (answer == null) {
        continue;
      }
      if (!answer.equals(flags.backgroundSymbol) && answer.indexOf('-') >= 0) {
        answer = answer.substring(2);
      }
      wi.set(AnswerAnnotation.class, answer);
    }
  }


  // all the other the crap from ObjectBank
  @Override
  public boolean add(List<IN> o) { return wrapped.add(o); }
  @Override
  public boolean addAll(Collection<? extends List<IN>> c) { return wrapped.addAll(c); }
  @Override
  public void clear() { wrapped.clear(); }
  @Override
  public void clearMemory() { wrapped.clearMemory(); }
  public boolean contains(List<IN> o) { return wrapped.contains(o); }
  @Override
  public boolean containsAll(Collection<?> c) { return wrapped.containsAll(c); }
  @Override
  public boolean isEmpty() { return wrapped.isEmpty(); }
  @Override
  public void keepInMemory(boolean keep) { wrapped.keepInMemory(keep); }
  public boolean remove(List<IN> o) { return wrapped.remove(o); }
  @Override
  public boolean removeAll(Collection<?> c) { return wrapped.removeAll(c); }
  @Override
  public boolean retainAll(Collection<?> c) { return wrapped.retainAll(c); }
  @Override
  public int size() { return wrapped.size(); }
  @Override
  public Object[] toArray() { return wrapped.toArray(); }
  public List<IN>[] toArray(List<IN>[] o) { return wrapped.toArray(o); }

} // end class ObjectBankWrapper
