/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.io.PrintFile;
import edu.stanford.nlp.ling.HasOffset;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.sequences.BestSequenceFinder;
import edu.stanford.nlp.sequences.ExactBestSequenceFinder;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.tagger.common.TaggerConstants;
import edu.stanford.nlp.util.ArrayUtils;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.text.NumberFormat;
import java.text.DecimalFormat;


/**
 * @author Kristina Toutanova
 * @author Michel Galley
 * @version 1.0
 */
public class TestSentence implements SequenceModel {

  // TODO: use the VERBOSE in the config
  protected static final boolean VERBOSE = false;
  protected static final String naTag = "NA";
  protected static final boolean DBG = false;
  protected static final boolean doDeterministicTagExpansion = true;
  protected static final int kBestSize = 1;

  protected final String tagSeparator;
  protected final String encoding;
  protected final PairsHolder pairs = new PairsHolder();
  protected List<String> sent;
  // origWords is only set when run with a list of HasWords; when run
  // with a list of strings, this will be null
  protected List<HasWord> origWords; 
  protected int size; // TODO this always has the value of sent.size(). Remove it? [cdm 2008]
  // protected double[][][] probabilities;
  protected String[] correctTags;
  protected String[] finalTags;
  int numRight;
  int numWrong;
  int numUnknown;
  int numWrongUnknown;
  private int endSizePairs; // = 0;

  private volatile History history;
  protected volatile Map<String,double[]> localScores = new HashMap<String,double[]>();
  protected volatile double[][] localContextScores;

  protected final MaxentTagger maxentTagger;

  public TestSentence(MaxentTagger maxentTagger) {
    assert(maxentTagger != null);
    assert(maxentTagger.getLambdaSolve() != null);
    this.maxentTagger = maxentTagger;
    if (maxentTagger.config != null) {
      tagSeparator = maxentTagger.config.getTagSeparator();
      encoding = maxentTagger.config.getEncoding();
    } else {
      tagSeparator = TaggerConfig.getDefaultTagSeparator();
      encoding = "utf-8";
    }
    history = new History(pairs, maxentTagger.extractors);
  }

  public TestSentence(MaxentTagger maxentTagger, String[] s, String[] correctTags, 
                      PrintFile pf, Dictionary wrongWords, boolean verboseResults) {
    this(maxentTagger);
    if (DBG) {
      assert(s.length == correctTags.length);
      System.err.println("Entering TestSentence(); s.length is " + s.length + "; startSizePairs is " + endSizePairs + "; endSizePairs is " + endSizePairs);
    }
    this.origWords = null;
    this.sent = new ArrayList<String>(Arrays.asList(s));
    this.size = sent.size();
    this.correctTags = correctTags;
    init();
    testTagInference(pf, wrongWords, verboseResults);
    if (DBG) {
      System.err.println("Exiting TestSentence(); startSizePairs is " + endSizePairs + "; endSizePairs is " + endSizePairs);
    }
  }

  /**
   * Tags the sentence s by running maxent model.  Returns a Sentence of
   * TaggedWord objects.
   *
   * @param s Input sentence (List).  This isn't changed.
   * @return Tagged sentence
   */
  public ArrayList<TaggedWord> tagSentence(List<? extends HasWord> s) {
    this.origWords = new ArrayList<HasWord>(s);
    int sz = s.size();
    List<String> sentence = new ArrayList<String>(sz + 1);
    this.sent = sentence;
    for (int j = 0; j < sz; j++) {
      sentence.add(s.get(j).word());
    }
    sent.add(TaggerConstants.EOS_WORD);
    size = sz + 1;
    if (VERBOSE) {
      System.err.println("Sentence is " +
                         Sentence.listToString(sentence, false, tagSeparator));
    }
    init();
    return testTagInference();
  }


  protected void revert(int prevSize) {
    endSizePairs = prevSize;
  }

  protected void init() {
    //the eos are assumed already there
    localContextScores = new double[size][];
    for (int i = 0; i < size - 1; i++) {
      if (maxentTagger.dict.isUnknown(sent.get(i))) {
        numUnknown++;
      }
    }
  }

  /**
   * Returns a string representation of the sentence.
   * @return tagged sentence
   */
  String getTaggedNice() {
    StringBuilder sb = new StringBuilder();
    // size - 1 means to exclude the EOS (end of string) symbol
    for (int i = 0; i < size - 1; i++) {
      sb.append(toNice(sent.get(i))).append(tagSeparator).append(toNice(finalTags[i]));
      sb.append(' ');
    }
    return sb.toString();
  }


  ArrayList<TaggedWord> getTaggedSentence() {
    final boolean hasOffset;
    if (origWords != null && 
        (origWords.get(0) instanceof HasOffset)) {
      hasOffset = true;
    } else {
      hasOffset = false;
    }
    ArrayList<TaggedWord> taggedSentence = new ArrayList<TaggedWord>();
    for (int j = 0; j < size - 1; j++) {
      String tag = finalTags[j];
      TaggedWord w = new TaggedWord(sent.get(j), tag);
      if (hasOffset) {
        HasOffset offset = (HasOffset) origWords.get(j);
        w.setBeginPosition(offset.beginPosition());
        w.setEndPosition(offset.endPosition());
      }
      taggedSentence.add(w);
    }
    return taggedSentence;
  }

  static String toNice(String s) {
    if (s == null) {
      return naTag;
    } else {
      return s;
    }
  }

  /** calculateProbs puts log probs of taggings in the probabilities array.
   *
   *  @param probabilities Array with indices sent size, k best size, numTags
   */
  protected void calculateProbs(double[][][] probabilities) {
    ArrayUtils.fill(probabilities, Double.NEGATIVE_INFINITY);
    for (int hyp = 0; hyp < kBestSize; hyp++) {
      // put the whole thing in pairs, give its beginning and end
      pairs.setSize(size);
      for (int i = 0; i < size; i++) {
        pairs.setWord(i,sent.get(i));
        pairs.setTag(i,finalTags[i]);
        //pairs.add(new WordTag(sent.get(i),finalTags[i]));
        // TODO: if kBestSize > 1, use KBestSequenceFinder and save
        // k-best hypotheses into finalTags:
        //pairs.setTag(i,finalTags[i]);
      }
      int start = endSizePairs;
      int end = endSizePairs + size - 1;
      endSizePairs = endSizePairs + size;
      // iterate over the sentence
      for (int current = 0; current < size; current++) {
        History h = new History(start, end, current + start, pairs, maxentTagger.extractors);
        String[] tags = stringTagsAt(h.current - h.start + leftWindow());
        double[] probs = getHistories(tags, h);
        ArrayMath.logNormalize(probs);

        // System.err.println("word: " + pairs.getWord(current));
        // System.err.println("tags: " + Arrays.asList(tags));
        // System.err.println("probs: " + ArrayMath.toString(probs));

        for (int j = 0; j < tags.length; j++) {
          // score the j-th tag
          String tag = tags[j];
          boolean approximate = maxentTagger.defaultScore > 0.0;
          int tagindex = approximate ? maxentTagger.tags.getIndex(tag) : j;
          // System.err.println("Mapped from j="+ j + " " + tag + " to " + tagindex);
          probabilities[current][hyp][tagindex] = probs[j];
        }
      } // for current
    } // for hyp
    // clean up the stuff in PairsHolder (added by cdm in Aug 2008)
    revert(0);
  } // end calculateProbs()


  /** Write the tagging and note any errors (if pf != null) and accumulate
   *  global statistics.
   *
   *  @param finalTags Chosen tags for sentence
   *  @param pf File to write tagged output to (can be null, then no output;
   *               at present it is non-null iff the debug property is set)
   *  @param wrongWords Dictionary to accumulate wrong word counts in (cannot be null)
   */
  protected void writeTagsAndErrors(String[] finalTags, PrintFile pf, Dictionary wrongWords, boolean verboseResults) {
    StringWriter sw = new StringWriter(200);
    // don't write the EOS word
    for (int i = 0; i < correctTags.length - 1; i++) {
      sw.write(toNice(sent.get(i)));
      sw.write(tagSeparator);
      sw.write(finalTags[i]);
      sw.write(' ');
      if (pf != null) {
        pf.print(toNice(sent.get(i)));
        pf.print(tagSeparator);
        pf.print(finalTags[i]);
      }
      if ((correctTags[i]).equals(finalTags[i])) {
        numRight++;
      } else {
        numWrong++;
        if (pf != null) pf.print('|' + correctTags[i]);
        if (verboseResults) {
          EncodingPrintWriter.err.println("Word: " + sent.get(i) + "; correct: " + correctTags[i] + "; guessed: " + finalTags[i], encoding);
        }

        wrongWords.add(sent.get(i) + correctTags[i], finalTags[i]);
        if (maxentTagger.dict.isUnknown(sent.get(i))) {
          numWrongUnknown++;
          if (pf != null) pf.print("*");
        }// if
      }// else
      if (pf != null) pf.print(' ');
    }// for
    if (pf != null) pf.println();

    if (verboseResults) {
      PrintWriter pw;
      try { 
        pw = new PrintWriter(new OutputStreamWriter(System.out, encoding), 
                             true);
      } catch (UnsupportedEncodingException uee) {
        pw = new PrintWriter(new OutputStreamWriter(System.out), true);
      }
      pw.println(sw);
    }
  }


  // Test using (exact Viterbi) TagInference.
  private void testTagInference(PrintFile pf, Dictionary wrongWords, boolean verboseResults) {
    runTagInference();
    writeTagsAndErrors(finalTags, pf, wrongWords, verboseResults);
  }


  /**
   * Test using (exact Viterbi) TagInference.
   *
   * @return The tagged sentence
   */
  private ArrayList<TaggedWord> testTagInference() {
    runTagInference();
    return getTaggedSentence();
  }

  private void runTagInference() {
    this.initializeScorer();

    BestSequenceFinder ti = new ExactBestSequenceFinder();
      //new BeamBestSequenceFinder(50);
      //new KBestSequenceFinder()
    int[] bestTags = ti.bestSequence(this);
    finalTags = new String[bestTags.length];
    for (int j = 0; j < size; j++) {
      finalTags[j] = maxentTagger.tags.getTag(bestTags[j + leftWindow()]);
    }
    cleanUpScorer();
  }


  // This is used for Dan's tag inference methods.
  // current is the actual word number + leftW
  private void setHistory(int current, History h, int[] tags) {
    //writes over the tags in the last thing in pairs

    int left = leftWindow();
    int right = rightWindow();

    for (int j = current - left; j <= current + right; j++) {
      if (j < left) {
        continue;
      } //but shouldn't happen
      if (j >= size + left) {
        break;
      } //but shouldn't happen
      h.setTag(j - left, maxentTagger.tags.getTag(tags[j]));
    }
  }

  // do initializations for the TagScorer interface
  protected void initializeScorer() {
    pairs.setSize(size);
    for (int i = 0; i < size; i++)
      pairs.setWord(i,sent.get(i));
    endSizePairs += size;
  }


  /**
   * clean-up after the scorer
   */
  protected void cleanUpScorer() {
    revert(0);
  }

  protected String[] append(String[] tags, String word) {
    if (doDeterministicTagExpansion) {
      return maxentTagger.tags.deterministicallyExpandTags(tags, word);
    } else {
      return tags;
    }
  }

  // This scores the current assignment in PairsHolder at
  // current position h.current (returns normalized scores)
  private double[] getScores(History h) {
    if (maxentTagger.defaultScore > 0) {
      return getApproximateScores(h);
    }
    return getExactScores(h);
  }

  private double[] getExactScores(History h) {
    String[] tags = stringTagsAt(h.current - h.start + leftWindow());
    double[] histories = getHistories(tags, h); // log score for each tag
    ArrayMath.logNormalize(histories);
    double[] scores = new double[tags.length];
    for (int j = 0; j < tags.length; j++) {
      // score the j-th tag
      String tag = tags[j];
      int tagindex = maxentTagger.tags.getIndex(tag);
      scores[j] = histories[tagindex];
    }
    return scores;
  }

  // In this method, each tag that is incompatible with the current word
  // (e.g., apple_CC) gets a default (constant) score instead of its exact score.
  // The scores of all other tags are computed exactly.
  private double[] getApproximateScores(History h) {
    String[] tags = stringTagsAt(h.current - h.start + leftWindow());
    double[] scores = getHistories(tags, h); // log score for each active tag, unnormalized

    // Number of tags that get assigned a default score:
    double nDefault = maxentTagger.ySize - tags.length;
    double logScore = ArrayMath.logSum(scores);
    double logScoreInactiveTags = Math.log(nDefault*maxentTagger.defaultScore);
    double logTotal =
      ArrayMath.logSum(new double[] {logScore, logScoreInactiveTags});
    ArrayMath.addInPlace(scores, -logTotal);

    return scores;
  }

  // This precomputes scores of local features (localScores).
  protected double[] getHistories(String[] tags, History h) {
    boolean rare = maxentTagger.isRare(ExtractorFrames.cWord.extract(h));
    Extractors ex = maxentTagger.extractors, exR = maxentTagger.extractorsRare;
    String w = pairs.getWord(h.current);
    double[] lS, lcS;
    if((lS = localScores.get(w)) == null) {
      lS = getHistories(tags, h, ex.local, rare ? exR.local : null);
      localScores.put(w,lS);
    }
    if((lcS = localContextScores[h.current]) == null) {
      lcS = getHistories(tags, h, ex.localContext, rare ? exR.localContext : null);
      localContextScores[h.current] = lcS;
      ArrayMath.pairwiseAddInPlace(lcS,lS);
    }
    double[] totalS = getHistories(tags, h, ex.dynamic, rare ? exR.dynamic : null);
    ArrayMath.pairwiseAddInPlace(totalS,lcS);
    return totalS;
  }

  private double[] getHistories(String[] tags, History h, Map<Integer,Extractor> extractors, Map<Integer,Extractor> extractorsRare) {
    if(maxentTagger.defaultScore > 0)
      return getApproximateHistories(tags, h, extractors, extractorsRare);
    return getExactHistories(h, extractors, extractorsRare);
  }

  private double[] getExactHistories(History h, Map<Integer,Extractor> extractors, Map<Integer,Extractor> extractorsRare) {
    double[] scores = new double[maxentTagger.ySize];
    FeatureKey s = new FeatureKey();
    int szCommon = maxentTagger.extractors.getSize();

    for(Map.Entry<Integer,Extractor> e : extractors.entrySet()) {
      int kf = e.getKey();
      Extractor ex = e.getValue();
      for (int i = 0; i < maxentTagger.ySize; i++) {
        String tag = maxentTagger.tags.getTag(i);
        s.set(kf, ex.extract(h), tag);
        int fNum = maxentTagger.getNum(s);
        if (fNum > -1) {
          scores[i] += maxentTagger.getLambdaSolve().lambda[fNum];
        }
      }
    }
    if(extractorsRare != null) {
      for(Map.Entry<Integer,Extractor> e : extractorsRare.entrySet()) {
        int kf = e.getKey();
        Extractor ex = e.getValue();
        for (int i = 0; i < maxentTagger.ySize; i++) {
          String tag = maxentTagger.tags.getTag(i);
          s.set(szCommon+kf, ex.extract(h), tag);
          int fNum = maxentTagger.getNum(s);
          if (fNum > -1) {
            scores[i] += maxentTagger.getLambdaSolve().lambda[fNum];
          } // end for
        }
      }
    }
    return scores;
  }

  // Returns an unnormalized score (in log space) for each tag
  private double[] getApproximateHistories(String[] tags, History h, Map<Integer,Extractor> extractors, Map<Integer,Extractor> extractorsRare) {

    double[] scores = new double[tags.length];
    FeatureKey s = new FeatureKey();
    int szCommon = maxentTagger.extractors.getSize();

    for(Map.Entry<Integer,Extractor> e : extractors.entrySet()) {
      int kf = e.getKey();
      Extractor ex = e.getValue();
      for (int j = 0; j < tags.length; j++) {
        String tag = tags[j];
        s.set(kf, ex.extract(h), tag);
        int fNum = maxentTagger.getNum(s);
        if (fNum > -1) {
          scores[j] += maxentTagger.getLambdaSolve().lambda[fNum];
        }
      }
    }
    if(extractorsRare != null) {
      for(Map.Entry<Integer,Extractor> e : extractorsRare.entrySet()) {
        int kf = e.getKey();
        Extractor ex = e.getValue();
        for (int j = 0; j < tags.length; j++) {
          String tag = tags[j];
          s.set(szCommon+kf, ex.extract(h), tag);
          int fNum = maxentTagger.getNum(s);
          if (fNum > -1) {
            scores[j] += maxentTagger.getLambdaSolve().lambda[fNum];
          } // end for
        }
      }
    }
    return scores;
  }


  /**
   * This method should be called after the sentence has been tagged.
   * For every unknown word, this method prints the 3 most probable tags
   * to the file pfu.
   *
   * @param numSent The sentence number
   * @param pfu The file to print the probable tags to
   */
  void printUnknown(int numSent, PrintFile pfu) {
    NumberFormat nf = new DecimalFormat("0.0000");
    int numTags = maxentTagger.tags.getSize();
    double[][][] probabilities = new double[size][kBestSize][numTags];
    calculateProbs(probabilities);
    for (int current = 0; current < size; current++) {
      if (maxentTagger.dict.isUnknown(sent.get(current))) {
        pfu.print(sent.get(current));
        pfu.print(':');
        pfu.print(numSent);
        double[] probs = new double[3];
        String[] tag3 = new String[3];
        getTop3(probabilities, current, probs, tag3);
        for (int i = 0; i < 3; i++) {
          if (probs[i] > Double.NEGATIVE_INFINITY) {
            pfu.print('\t');
            pfu.print(tag3[i]);
            pfu.print(' ');
            pfu.print(nf.format(Math.exp(probs[i])));
          }
        }
        int rank;
        String correctTag = toNice(this.correctTags[current]);
        for (rank = 0; rank < 3; rank++) {
          if (correctTag.equals(tag3[rank])) {
            break;
          } //if
        }
        pfu.print('\t');
        switch (rank) {
          case 0:
            pfu.print("Correct");
            break;
          case 1:
            pfu.print("2nd");
            break;
          case 2:
            pfu.print("3rd");
            break;
          default:
            pfu.print("Not top 3");
        }
        pfu.println();
      }// if
    }// for
  }

  // This method should be called after a sentence has been tagged.
  // For every word token, this method prints the 3 most probable tags
  // to the file pfu except for
  void printTop(PrintFile pfu) {
    NumberFormat nf = new DecimalFormat("0.0000");
    int numTags = maxentTagger.tags.getSize();
    double[][][] probabilities = new double[size][kBestSize][numTags];
    calculateProbs(probabilities);
    for (int current = 0; current < size; current++) {
      pfu.print(sent.get(current));
      double[] probs = new double[3];
      String[] tag3 = new String[3];
      getTop3(probabilities, current, probs, tag3);
      for (int i = 0; i < 3; i++) {
        if (probs[i] > Double.NEGATIVE_INFINITY) {
          pfu.print('\t');
          pfu.print(tag3[i]);
          pfu.print(' ');
          pfu.print(nf.format(Math.exp(probs[i])));
        }
      }
      int rank;
      String correctTag = toNice(this.correctTags[current]);
      for (rank = 0; rank < 3; rank++) {
        if (correctTag.equals(tag3[rank])) {
          break;
        } //if
      }
      pfu.print('\t');
      switch (rank) {
      case 0:
        pfu.print("Correct");
        break;
      case 1:
        pfu.print("2nd");
        break;
      case 2:
        pfu.print("3rd");
        break;
      default:
        pfu.print("Not top 3");
      }
      pfu.println();
    } // for
  }

  /** probs and tags should be passed in as arrays of size 3!
   *  If probs[i] == Double.NEGATIVE_INFINITY, then the entry should be ignored.
   */
  private void getTop3(double[][][] probabilities, int current, double[] probs, String[] tags) {
    int[] topIds = new int[3];
    double[] probTags = probabilities[current][0];
    Arrays.fill(probs, Double.NEGATIVE_INFINITY);
    for (int i = 0; i < probTags.length; i++) {
      if (probTags[i] > probs[0]) {
        probs[2] = probs[1];
        probs[1] = probs[0];
        probs[0] = probTags[i];
        topIds[2] = topIds[1];
        topIds[1] = topIds[0];
        topIds[0] = i;
      } else if (probTags[i] > probs[1]) {
        probs[2] = probs[1];
        probs[1] = probTags[i];
        topIds[2] = topIds[1];
        topIds[1] = i;
      } else if (probTags[i] > probs[2]) {
        probs[2] = probTags[i];
        topIds[2] = i;
      }
    }
    for (int j = 0; j < 3; j++) {
      tags[j] = toNice(maxentTagger.tags.getTag(topIds[j]));
    }
  }

  /*
   * Implementation of the TagScorer interface follows
   */

  public int length() {
    return sent.size();
  }

  public int leftWindow() {
    return maxentTagger.leftContext; //hard-code for now
  }

  public int rightWindow() {
    return maxentTagger.rightContext; //hard code for now
  }


  public int[] getPossibleValues(int pos) {
    String[] arr1 = stringTagsAt(pos);
    int[] arr = new int[arr1.length];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = maxentTagger.tags.getIndex(arr1[i]);
    }

    return arr;
  }

  public double scoreOf(int[] tags, int pos) {
    double[] scores = scoresOf(tags, pos);
    double score = Double.NEGATIVE_INFINITY;
    int[] pv = getPossibleValues(pos);
    for (int i = 0; i < scores.length; i++) {
      if (pv[i] == tags[pos]) {
        score = scores[i];
      }
    }
    return score;
  }

  public double scoreOf(int[] sequence) {
    throw new UnsupportedOperationException();
  }

  public double[] scoresOf(int[] tags, int pos) {
    if (DBG) {
      System.err.println("scoresOf(): length of tags is " + tags.length + "; position is " + pos + "; endSizePairs = " + endSizePairs + "; size is " + size + "; leftWindow is " + leftWindow());
      System.err.println("  History h = new History(" + (endSizePairs - size) + ", " + (endSizePairs - 1) + ", " + (endSizePairs - size + pos - leftWindow()) + ")");
    }
    history.init(endSizePairs - size, endSizePairs - 1, endSizePairs - size + pos - leftWindow());
    setHistory(pos, history, tags);
    return getScores(history);
  }

  protected String[] stringTagsAt(int pos) {
    String[] arr1;
    if ((pos < leftWindow()) || (pos >= size + leftWindow())) {
      arr1 = new String[1];
      arr1[0] = naTag;
      return arr1;
    }
    if (maxentTagger.dict.isUnknown(sent.get(pos - leftWindow()))) {
      arr1 = maxentTagger.tags.getOpenTags().toArray(new String[maxentTagger.tags.getOpenTags().size()]);
    } else {
      arr1 = maxentTagger.dict.getTags(sent.get(pos - leftWindow()));
    }
    arr1 = append(arr1, sent.get(pos - leftWindow()));
    return arr1;
  }
}
