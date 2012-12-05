package edu.stanford.nlp.ie.regexp;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NumericCompositeTypeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NumericCompositeValueAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NumerizedTokensAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.time.TimeAnnotations.TimexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokenEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter;
import edu.stanford.nlp.time.TimeExpressionExtractor;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.StringUtils;

import java.io.ObjectInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * A set of deterministic rules for marking certain entities, to add
 * categories and to correct for failures of statistical NER taggers.
 * This is an extremely simple and ungeneralized implementation of
 * AbstractSequenceClassifier that was written for PASCAL RTE.
 * It could profitably be extended and generalized.
 * It marks a NUMBER category based on part-of-speech tags in a
 * deterministic manner.
 * It marks an ORDINAL category based on word form in a deterministic manner.
 * It tags as MONEY currency signs and things tagged CD after a currency sign.
 * It marks a number before a month name as a DATE.
 * It marks as a DATE a word of the form xx/xx/xxxx
 * (where x is a digit from a suitable range).
 * It marks as a TIME a word of the form x(x):xx (where x is a digit).
 * It marks everything else tagged "CD" as a NUMBER, and instances
 * of "and" appearing between CD tags in contexts suggestive of a number.
 * It requires text to be POS-tagged (have the getString(TagAnnotation.class) attribute).
 * Effectively these rules assume that
 * this classifier will be used as a secondary classifier by
 * code such as ClassifierCombiner: it will mark most CD as NUMBER, and it
 * is assumed that something else with higher priority is marking ones that
 * are PERCENT, ADDRESS, etc.
 *
 * @author Christopher Manning
 * @author Mihai (integrated with NumberNormalizer, SUTime)
 */
public class NumberSequenceClassifier extends AbstractSequenceClassifier<CoreLabel> {

  private static final boolean DEBUG = false;

  private final boolean useSUTime;

  public static final boolean USE_SUTIME_DEFAULT = true;
  public static final String USE_SUTIME_PROPERTY = "ner.useSUTime";

  private final TimeExpressionExtractor timexExtractor;

  public NumberSequenceClassifier() {
    this(new Properties(), USE_SUTIME_DEFAULT);
    if (! CURRENCY_WORD_PATTERN.matcher("pounds").matches()) {
      System.err.println("NumberSequence: Currency pattern broken");
    }
  }

  public NumberSequenceClassifier(boolean useSUTime) {
    this(new Properties(), useSUTime);
  }

  public NumberSequenceClassifier(Properties props, boolean useSUTime) {
    super(props);
    this.useSUTime = useSUTime;
    if(this.useSUTime) {
      this.timexExtractor = new TimeExpressionExtractor("sutime", props);
    } else {
      this.timexExtractor = null;
    }
  }

  /**
   * Classify a {@link List} of {@link CoreLabel}s.
   *
   * @param document A {@link List} of {@link CoreLabel}s.
   * @return the same {@link List}, but with the elements annotated
   *         with their answers.
   */
  @Override
  public List<CoreLabel> classify(List<CoreLabel> document) {
    return classifyWithGlobalInformation(document, null, null);
  }

  @Override
  public List<CoreLabel> classifyWithGlobalInformation(List<CoreLabel> tokens, final CoreMap document, final CoreMap sentence) {
    if(useSUTime) return classifyWithSUTime(tokens, document, sentence);
    return classifyOld(tokens);
  }

  /**
   * Modular classification using NumberNormalizer for numbers, SUTime for date/time.
   * Note: this is slower than classifyOld because it runs multiple passes
   *   over the tokens (one for numbers and dates, and others for money and ordinals).
   *   However, the slowdown is not substantial since the passes are fast. Plus,
   *   the code is much cleaner than before...
   * @param tokenSequence
   * @return
   */
  private List<CoreLabel> classifyWithSUTime(List<CoreLabel> tokenSequence, final CoreMap document, final CoreMap sentence) {
    //
    // set everything to "O" by default
    //
    for(CoreLabel token: tokenSequence) {
      if(token.get(AnswerAnnotation.class) == null)
        token.set(AnswerAnnotation.class, flags.backgroundSymbol);
    }

    //
    // run SUTime
    // note: SUTime requires TextAnnotation to be set at document/sent level and
    //   that the Character*Offset annotations be aligned with the token words.
    //   This is guaranteed because here we work on a copy generated by copyTokens()
    //
    CoreMap timeSentence = (sentence != null ?
        alignSentence(sentence) :
        buildSentenceFromTokens(tokenSequence));
    List<CoreMap> timeExpressions = runSUTime(timeSentence, document);
    List<CoreMap> numbers = timeSentence.get(NumerizedTokensAnnotation.class);

    //
    // store DATE and TIME
    //
    if(timeExpressions != null){
      for(CoreMap timeExpression: timeExpressions) {
        int start = timeExpression.get(TokenBeginAnnotation.class);
        int end = timeExpression.get(TokenEndAnnotation.class);
        int offset = 0;
        if(sentence != null && sentence.containsKey(TokenBeginAnnotation.class)) {
          offset = sentence.get(TokenBeginAnnotation.class);
        }
        Timex timex = timeExpression.get(TimexAnnotation.class);
        if(timex != null){
          if(DEBUG){
            System.err.println("FOUND DATE/TIME \"" + timeExpression +
                "\" with offsets " + start + " " + end +
                " and value " + timex);
            System.err.println("The above CoreMap has the following fields:");
            // for(Class key: timeExpression.keySet()) System.err.println("\t" + key + ": " + timeExpression.get(key));
          }
          String label = timex.timexType();
          for(int i = start; i < end; i ++){
            CoreLabel token = tokenSequence.get(i - offset);
            if(token.get(AnswerAnnotation.class).equals(flags.backgroundSymbol)){
              token.set(AnswerAnnotation.class, label);
              token.set(TimexAnnotation.class, timex);
            }
          }
        }
      }
    }

    //
    // store the numbers found by SUTime as NUMBER if they are not part of anything else
    //
    if(numbers != null){
      for(CoreMap number: numbers) {
        if(number.containsKey(NumericCompositeValueAnnotation.class)){
          int start = number.get(TokenBeginAnnotation.class);
          int end = number.get(TokenEndAnnotation.class);
          int offset = 0;
          if(sentence != null && sentence.containsKey(TokenBeginAnnotation.class)) {
            offset = sentence.get(TokenBeginAnnotation.class);
          }
          String type = number.get(NumericCompositeTypeAnnotation.class);
          Number value = number.get(NumericCompositeValueAnnotation.class);
          if(type != null){
            if(DEBUG) System.err.println("FOUND NUMBER \"" + number + "\" with offsets " + start + " " + end + " and value " + value + " and type " + type);
            for(int i = start; i < end; i ++){
              CoreLabel token = tokenSequence.get(i - offset);
              if(token.get(AnswerAnnotation.class).equals(flags.backgroundSymbol)){
                token.set(AnswerAnnotation.class, type);
                if(value != null){
                  token.set(NumericCompositeValueAnnotation.class, value);
                }
              }
            }
          }
        }
      }
    }
    // everything tagged as CD is also a number
    // NumberNormalizer probably catches these but let's be safe
    for(CoreLabel token: tokenSequence) {
      if(token.tag().equals("CD") &&
         token.get(AnswerAnnotation.class).equals(flags.backgroundSymbol)){
        token.set(AnswerAnnotation.class, "NUMBER");
      }
    }

    // extract money and percents
    moneyAndPercentRecognizer(tokenSequence);

    // ordinals
    // NumberNormalizer probably catches these but let's be safe
    ordinalRecognizer(tokenSequence);

    return tokenSequence;
  }

  /**
   * Copies one sentence replicating only information necessary for SUTime
   * @param sentence
   * @return
   */
  public static CoreMap alignSentence(CoreMap sentence) {

    String text = sentence.get(CoreAnnotations.TextAnnotation.class);
    if(text != null){
      // original text is preserved; no need to align anything
      return sentence;
    }

    CoreMap newSentence = buildSentenceFromTokens(
        sentence.get(TokensAnnotation.class),
        sentence.get(CharacterOffsetBeginAnnotation.class),
        sentence.get(CharacterOffsetEndAnnotation.class));

    newSentence.set(TokenBeginAnnotation.class,
        sentence.get(TokenBeginAnnotation.class));
    newSentence.set(TokenEndAnnotation.class,
        sentence.get(TokenEndAnnotation.class));

    return newSentence;
  }

  private static CoreMap buildSentenceFromTokens(List<CoreLabel> tokens) {
    return buildSentenceFromTokens(tokens, null, null);
  }

  private static CoreMap buildSentenceFromTokens(
      List<CoreLabel> tokens,
      Integer characterOffsetStart,
      Integer characterOffsetEnd) {

    //
    // Recover the sentence text:
    // a) try to get it from TextAnnotation
    // b) if not present, build it from the OriginalTextAnnotation of each token
    // c) if not present, build it from the TextAnnotation of each token
    //
    boolean adjustCharacterOffsets = false;
    // try to recover the text from the original tokens
    String text = buildText(tokens, OriginalTextAnnotation.class);
    if(text == null){
      text = buildText(tokens, TextAnnotation.class);
      // character offset will point to the original tokens
      //   so we need to align them to the text built from normalized tokens
      adjustCharacterOffsets = true;
      if(text == null){
        throw new RuntimeException("ERROR: to use SUTime, sentences must have TextAnnotation set, or the individual tokens must have OriginalTextAnnotation or TextAnnotation set!");
      }
    }

    // make sure token character offsets are aligned with text
    List<CoreLabel> tokenSequence = copyTokens(tokens, adjustCharacterOffsets, false);

    Annotation newSentence = new Annotation(text);
    newSentence.set(TokensAnnotation.class, tokenSequence);
    if (! adjustCharacterOffsets &&
        characterOffsetStart != null &&
        characterOffsetEnd != null){
      newSentence.set(CharacterOffsetBeginAnnotation.class, characterOffsetStart);
      newSentence.set(CharacterOffsetEndAnnotation.class, characterOffsetEnd);
    } else {
      int tokenCharStart = tokenSequence.get(0).get(CharacterOffsetBeginAnnotation.class);
      int tokenCharEnd = tokenSequence.get(tokenSequence.size() - 1).get(CharacterOffsetEndAnnotation.class);
      newSentence.set(CharacterOffsetBeginAnnotation.class, tokenCharStart);
      newSentence.set(CharacterOffsetEndAnnotation.class, tokenCharEnd);
    }

    // some default token offsets
    newSentence.set(TokenBeginAnnotation.class, 0);
    newSentence.set(TokenEndAnnotation.class, tokenSequence.size());

    return newSentence;
  }

  @SuppressWarnings("unchecked")
  private static String buildText(List<CoreLabel> tokens, Class textAnnotation) {
    StringBuilder os = new StringBuilder();
    for (int i = 0, sz = tokens.size(); i < sz; i ++) {
      CoreLabel crt = tokens.get(i);
      // System.out.println("\t" + crt.word() + "\t" + crt.get(CharacterOffsetBeginAnnotation.class) + "\t" + crt.get(CharacterOffsetEndAnnotation.class));
      if (i > 0) {
        CoreLabel prev = tokens.get(i - 1);
        int spaces = 1;
        if (crt.containsKey(CharacterOffsetBeginAnnotation.class)) {
          spaces = crt.get(CharacterOffsetBeginAnnotation.class) -
              prev.get(CharacterOffsetEndAnnotation.class);
        }
        while (spaces > 0) {
          os.append(' ');
          spaces--;
        }
      }
      String word = (String) crt.get(textAnnotation);
      if (word == null) {
        // this annotation does not exist; bail out
        return null;
      }
      os.append(word);
    }
    return os.toString();
  }

  /**
   * Runs SUTime and converts its output into NamedEntityTagAnnotations
   * @param sentence
   * @param document Contains document-level annotations such as DocDateAnnotation
   */
  private List<CoreMap> runSUTime(CoreMap sentence, final CoreMap document) {
    timexExtractor.resetTimeIndex();
    // docDate can be null. In such situations we do not disambiguate relative dates
    String docDate = (document != null ? document.get(CoreAnnotations.DocDateAnnotation.class) : null);

    /*
    System.err.println("PARSING SENTENCE: " + sentence.get(TextAnnotation.class));
    for(CoreLabel t: sentence.get(TokensAnnotation.class)){
      System.err.println("TOKEN: \"" + t.word() + "\" \"" + t.get(OriginalTextAnnotation.class) + "\" " + t.get(CharacterOffsetBeginAnnotation.class) + " " + t.get(CharacterOffsetEndAnnotation.class));
    }
    */

    List<CoreMap> timeExpressions = timexExtractor.extractTimeExpressionCoreMaps(sentence, docDate);
    if(timeExpressions != null){
      if(DEBUG) System.out.println("FOUND TEMPORALS: " + timeExpressions);
    }

    return timeExpressions;
  }

  /**
   * Recognizes money and percents
   * This accepts currency symbols (e.g., $) both before and after numbers; but it accepts units (e.g., "dollar") only after
   * @param tokenSequence
   */
  private void moneyAndPercentRecognizer(List<CoreLabel> tokenSequence) {
    for(int i = 0; i < tokenSequence.size(); i ++){
      CoreLabel crt = tokenSequence.get(i);
      CoreLabel next = (i < tokenSequence.size() - 1 ? tokenSequence.get(i + 1) : null);
      CoreLabel prev = (i > 0 ? tokenSequence.get(i - 1) : null);

      // $5
      if(CURRENCY_SYMBOL_PATTERN.matcher(crt.word()).matches() && next != null &&
         (next.get(AnswerAnnotation.class).equals("NUMBER") || next.tag().equals("CD"))){
        crt.set(AnswerAnnotation.class, "MONEY");
        i = changeLeftToRight(tokenSequence, i + 1,
            next.get(AnswerAnnotation.class),
            next.tag(), "MONEY") - 1;
      }

      // 5$, 5 dollars
      else if((CURRENCY_WORD_PATTERN.matcher(crt.word()).matches() ||
               CURRENCY_SYMBOL_PATTERN.matcher(crt.word()).matches()) &&
               prev != null &&
               (prev.get(AnswerAnnotation.class).equals("NUMBER") ||
                prev.tag().equals("CD")) &&
               ! leftScanFindsWeightWord(tokenSequence, i)){
        crt.set(AnswerAnnotation.class, "MONEY");
        changeRightToLeft(tokenSequence, i - 1,
            prev.get(AnswerAnnotation.class),
            prev.tag(), "MONEY");
      }

      // 5%, 5 percent
      else if((PERCENT_WORD_PATTERN.matcher(crt.word()).matches() ||
               PERCENT_SYMBOL_PATTERN.matcher(crt.word()).matches()) &&
               prev != null &&
               (prev.get(AnswerAnnotation.class).equals("NUMBER") ||
                prev.tag().equals("CD"))){
        crt.set(AnswerAnnotation.class, "PERCENT");
        changeRightToLeft(tokenSequence, i - 1,
            prev.get(AnswerAnnotation.class),
            prev.tag(), "PERCENT");
      }
    }
  }

  /**
   * Recognizes ordinal numbers
   * @param tokenSequence
   */
  private void ordinalRecognizer(List<CoreLabel> tokenSequence) {
    for (CoreLabel crt : tokenSequence) {
      if ((crt.get(AnswerAnnotation.class).equals(flags.backgroundSymbol) ||
              crt.get(AnswerAnnotation.class).equals("NUMBER")) &&
              ORDINAL_PATTERN.matcher(crt.word()).matches()) {
        crt.set(AnswerAnnotation.class, "ORDINAL");
      }
    }
  }

  private int changeLeftToRight(List<CoreLabel> tokens,
      int start,
      String oldTag,
      String posTag,
      String newTag) {
    while(start < tokens.size()) {
      CoreLabel crt = tokens.get(start);
      // we are scanning for a NER tag and found something different
      if(! oldTag.equals(flags.backgroundSymbol) && ! crt.get(AnswerAnnotation.class).equals(oldTag)) {
        break;
      }
      // the NER tag is not set, so we scan for similar POS tags
      if(oldTag.equals(flags.backgroundSymbol) && ! crt.tag().equals(posTag)) {
        break;
      }

      crt.set(AnswerAnnotation.class, newTag);
      start ++;
    }
    return start;
  }

  private int changeRightToLeft(List<CoreLabel> tokens,
      int start,
      String oldTag,
      String posTag,
      String newTag) {
    while(start >= 0) {
      CoreLabel crt = tokens.get(start);
      // we are scanning for a NER tag and found something different
      if(! oldTag.equals(flags.backgroundSymbol) && ! crt.get(AnswerAnnotation.class).equals(oldTag)) {
        break;
      }
      // the NER tag is not set, so we scan for similar POS tags
      if(oldTag.equals(flags.backgroundSymbol) && ! crt.tag().equals(posTag)) {
        break;
      }

      crt.set(AnswerAnnotation.class, newTag);
      start --;
    }
    return start;
  }

  /**
   * Aligns the character offsets of these tokens with the actual text stored in each token
   * Note that this copies the list ONLY when we need to adjust the character offsets. Otherwise, it keeps the original list.
   * Note that this looks first at OriginalTextAnnotation and only when null at TextAnnotation.
   * @param srcList
   * @param adjustCharacterOffsets If true, it adjust the character offsets to match exactly with the token lengths
   * @return
   */
  private static List<CoreLabel> copyTokens(List<CoreLabel> srcList,
      boolean adjustCharacterOffsets,
      boolean forceCopy) {
    // no need to adjust anything; use the original list
    if(! adjustCharacterOffsets && ! forceCopy) return srcList;

    List<CoreLabel> dstList = new ArrayList<CoreLabel>();
    int adjustment = 0;
    int offset = 0; // for when offsets are not available
    for(CoreLabel src: srcList) {
      if(adjustCharacterOffsets) {
        int wordLength = (src.containsKey(OriginalTextAnnotation.class))?
          src.get(OriginalTextAnnotation.class).length():src.word().length();

        // We try to preserve the old character offsets but they just don't work well for normalized token text
        // Also, in some cases, these offsets are not set
        if(src.containsKey(CharacterOffsetBeginAnnotation.class) &&
           src.containsKey(CharacterOffsetEndAnnotation.class)){
          int start = src.get(CharacterOffsetBeginAnnotation.class);
          int end = src.get(CharacterOffsetEndAnnotation.class);
          int origLength = end - start;
          start += adjustment;
          end = start + wordLength;
          dstList.add(copyCoreLabel(src, start, end));
          adjustment += wordLength - origLength;
        } else {
          int start = offset;
          int end = start + wordLength;
          offset = end + 1; // allow for one space character
          dstList.add(copyCoreLabel(src, start, end));
        }
      } else {
        dstList.add(copyCoreLabel(src, null, null));
      }
    }

    return dstList;
  }

  /**
   * Transfer from src to dst all annotations generated bu SUTime and NumberNormalizer
   * @param src
   * @param dst
   */
  public static void transferAnnotations(CoreLabel src, CoreLabel dst) {
    //
    // annotations potentially set by NumberNormalizer
    //
    if(src.containsKey(CoreAnnotations.NumericCompositeValueAnnotation.class)){
      dst.set(CoreAnnotations.NumericCompositeValueAnnotation.class,
          src.get(CoreAnnotations.NumericCompositeValueAnnotation.class));
    }

    if(src.containsKey(CoreAnnotations.NumericCompositeTypeAnnotation.class))
      dst.set(CoreAnnotations.NumericCompositeTypeAnnotation.class,
          src.get(CoreAnnotations.NumericCompositeTypeAnnotation.class));

    //
    // annotations set by SUTime
    //
    if(src.containsKey(TimexAnnotation.class))
      dst.set(TimexAnnotation.class,
          src.get(TimexAnnotation.class));
  }

  /**
   * Create a copy of srcTokens, detecting on the fly if character offsets need adjusting
   * @param srcTokens
   * @param srcSentence
   * @return
   */
  public static List<CoreLabel> copyTokens(List<CoreLabel> srcTokens, CoreMap srcSentence) {
    boolean adjustCharacterOffsets = false;
    if(srcSentence == null ||
        srcSentence.get(TextAnnotation.class) == null ||
        srcTokens.size() == 0 ||
        srcTokens.get(0).get(OriginalTextAnnotation.class) == null){
      adjustCharacterOffsets = true;
    }

    return copyTokens(srcTokens, adjustCharacterOffsets, true);
  }

  /**
   * Copies only the fields required for numeric entity extraction in the new CoreLabel
   * @param src
   * @return
   */
  private static CoreLabel copyCoreLabel(CoreLabel src, Integer startOffset, Integer endOffset) {
    CoreLabel dst = new CoreLabel();
    dst.setWord(src.word());
    dst.setTag(src.tag());
    if (src.containsKey(OriginalTextAnnotation.class)) {
      dst.set(CoreAnnotations.OriginalTextAnnotation.class, src.get(CoreAnnotations.OriginalTextAnnotation.class));
    }
    if(startOffset == null){
      dst.set(CharacterOffsetBeginAnnotation.class, src.get(CharacterOffsetBeginAnnotation.class));
    } else {
      dst.set(CharacterOffsetBeginAnnotation.class, startOffset);
    }
    if(endOffset == null){
      dst.set(CharacterOffsetEndAnnotation.class, src.get(CharacterOffsetEndAnnotation.class));
    } else {
      dst.set(CharacterOffsetEndAnnotation.class, endOffset);
    }

    transferAnnotations(src, dst);

    return dst;
  }

  public static final Pattern MONTH_PATTERN = Pattern.compile("January|Jan\\.?|February|Feb\\.?|March|Mar\\.?|April|Apr\\.?|May|June|Jun\\.?|July|Jul\\.?|August|Aug\\.?|September|Sept?\\.?|October|Oct\\.?|November|Nov\\.?|December|Dec\\.");

  public static final Pattern YEAR_PATTERN = Pattern.compile("[1-3][0-9]{3}|'?[0-9]{2}");

  public static final Pattern DAY_PATTERN = Pattern.compile("(?:[1-9]|[12][0-9]|3[01])(?:st|nd|rd)?");

  public static final Pattern DATE_PATTERN = Pattern.compile("(?:[1-9]|[0-3][0-9])\\\\?/(?:[1-9]|[0-3][0-9])\\\\?/[1-3][0-9]{3}");

  public static final Pattern DATE_PATTERN2 = Pattern.compile("[12][0-9]{3}[-/](?:0?[1-9]|1[0-2])[-/][0-3][0-9]");

  public static final Pattern TIME_PATTERN = Pattern.compile("[0-2]?[0-9]:[0-5][0-9]");

  public static final Pattern TIME_PATTERN2 = Pattern.compile("[0-2][0-9]:[0-5][0-9]:[0-5][0-9]");

  public static final Pattern AM_PM = Pattern.compile("(a\\.?m\\.?)|(p\\.?m\\.?)", Pattern.CASE_INSENSITIVE);

  public static final Pattern CURRENCY_WORD_PATTERN = Pattern.compile("(?:dollar|cent|euro|pound)s?|penny|pence|yen|yuan|won", Pattern.CASE_INSENSITIVE);
  public static final Pattern CURRENCY_SYMBOL_PATTERN = Pattern.compile("\\$|&#163|\u00A3|\u00A5|#|\u20AC|US\\$|HK\\$|A\\$", Pattern.CASE_INSENSITIVE);

  public static final Pattern ORDINAL_PATTERN = Pattern.compile("(?i)[2-9]?1st|[2-9]?2nd|[2-9]?3rd|1[0-9]th|[2-9]?[04-9]th|100+th|zeroth|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|twenty-first|twenty-second|twenty-third|twenty-fourth|twenty-fifth|twenty-sixth|twenty-seventh|twenty-eighth|twenty-ninth|thirtieth|thirty-first|fortieth|fiftieth|sixtieth|seventieth|eightieth|ninetieth|hundredth|thousandth|millionth");

  public static final Pattern ARMY_TIME_MORNING = Pattern.compile("0([0-9])([0-9]){2}");

  public static final Pattern GENERIC_TIME_WORDS = Pattern.compile("(morning|evening|night|noon|midnight|teatime|lunchtime|dinnertime|suppertime|afternoon|midday|dusk|dawn|sunup|sundown|daybreak|day)");

  public static final Pattern PERCENT_WORD_PATTERN = Pattern.compile("percent", Pattern.CASE_INSENSITIVE);
  public static final Pattern PERCENT_SYMBOL_PATTERN = Pattern.compile("%");

  private List<CoreLabel> classifyOld(List<CoreLabel> document) {
    // if (DEBUG) { System.err.println("NumberSequenceClassifier tagging"); }
    PaddedList<CoreLabel> pl = new PaddedList<CoreLabel>(document, pad);
    for (int i = 0, sz = pl.size(); i < sz; i++) {
      CoreLabel me = pl.get(i);
      CoreLabel prev = pl.get(i - 1);
      CoreLabel next = pl.get(i + 1);
      CoreLabel next2 = pl.get(i + 2);
      //if (DEBUG) { System.err.println("Tagging:" + me.word()); }
      me.set(AnswerAnnotation.class, flags.backgroundSymbol);
      if (CURRENCY_SYMBOL_PATTERN.matcher(me.word()).matches() &&
              (prev.getString(PartOfSpeechAnnotation.class).equals("CD") ||
               next.getString(PartOfSpeechAnnotation.class).equals("CD"))) {
        // dollar, pound, pound, yen,
        // Penn Treebank ancient # as pound, euro,
        if (DEBUG) {
          System.err.println("Found currency sign:" + me.word());
        }
        me.set(AnswerAnnotation.class, "MONEY");
      } else if (me.getString(PartOfSpeechAnnotation.class).equals("CD")) {
        if (DEBUG) {
          System.err.println("Tagging CD:" + me.word());
        }

        if (TIME_PATTERN.matcher(me.word()).matches()) {
          me.set(AnswerAnnotation.class, "TIME");
        } else if (TIME_PATTERN2.matcher(me.word()).matches()) {
            me.set(AnswerAnnotation.class, "TIME");
        } else if (DATE_PATTERN.matcher(me.word()).matches()) {
          me.set(AnswerAnnotation.class, "DATE");
        } else if (DATE_PATTERN2.matcher(me.word()).matches()) {
          me.set(AnswerAnnotation.class, "DATE");

        } else if (next.get(TextAnnotation.class) != null &&
            me.get(TextAnnotation.class) != null &&
            DAY_PATTERN.matcher(me.get(TextAnnotation.class)).matches() &&
            MONTH_PATTERN.matcher(next.get(TextAnnotation.class)).matches()) {
          // deterministically make DATE for British-style number before month
          me.set(AnswerAnnotation.class, "DATE");
        } else if (prev.get(TextAnnotation.class) != null &&
            MONTH_PATTERN.matcher(prev.get(TextAnnotation.class)).matches() &&
            me.get(TextAnnotation.class) != null &&
            DAY_PATTERN.matcher(me.get(TextAnnotation.class)).matches()) {
          // deterministically make DATE for number after month
          me.set(AnswerAnnotation.class, "DATE");
        } else if (rightScanFindsMoneyWord(pl, i) && ! leftScanFindsWeightWord(pl, i)) {
          me.set(AnswerAnnotation.class, "MONEY");
        } else if(ARMY_TIME_MORNING.matcher(me.word()).matches()) {
          me.set(AnswerAnnotation.class, "TIME");
        } else
        if (YEAR_PATTERN.matcher(me.word()).matches() &&
            prev.getString(AnswerAnnotation.class).equals("DATE") &&
            (MONTH_PATTERN.matcher(prev.word()).matches() ||
             pl.get(i - 2).get(AnswerAnnotation.class).equals("DATE")))
        {
          me.set(AnswerAnnotation.class, "DATE");
        } else {
          if (DEBUG) {
            System.err.println("Found number:" + me.word());
          }
          if (prev.getString(AnswerAnnotation.class).equals("MONEY")) {
            me.set(AnswerAnnotation.class, "MONEY");
          } else {
            me.set(AnswerAnnotation.class, "NUMBER");
          }
        }
      } else if(AM_PM.matcher(me.word()).matches() &&
          prev.get(AnswerAnnotation.class).equals("TIME")){
        me.set(AnswerAnnotation.class, "TIME");
      } else if (me.getString(PartOfSpeechAnnotation.class) != null &&
          me.getString(PartOfSpeechAnnotation.class).equals(",") &&
          prev.getString(AnswerAnnotation.class).equals("DATE") &&
          next.word() != null && YEAR_PATTERN.matcher(next.word()).matches()) {
        me.set(AnswerAnnotation.class, "DATE");
      } else if (me.getString(PartOfSpeechAnnotation.class).equals("NNP") &&
          MONTH_PATTERN.matcher(me.word()).matches()) {
        if (prev.getString(AnswerAnnotation.class).equals("DATE") ||
            next.getString(PartOfSpeechAnnotation.class).equals("CD")) {
          me.set(AnswerAnnotation.class, "DATE");
        }
      } else if (me.getString(PartOfSpeechAnnotation.class) != null &&
          me.getString(PartOfSpeechAnnotation.class).equals("CC")) {
        if (prev.tag() != null && prev.tag().equals("CD") &&
            next.tag() != null && next.tag().equals("CD") &&
            me.get(TextAnnotation.class) != null &&
            me.get(TextAnnotation.class).equalsIgnoreCase("and")) {
          if (DEBUG) {
            System.err.println("Found number and:" + me.word());
          }
          String wd = prev.word();
          if (wd.equalsIgnoreCase("hundred") ||
              wd.equalsIgnoreCase("thousand") ||
              wd.equalsIgnoreCase("million") ||
              wd.equalsIgnoreCase("billion") ||
              wd.equalsIgnoreCase("trillion"))
          {
            me.set(AnswerAnnotation.class, "NUMBER");
          }
        }
      } else if (me.getString(PartOfSpeechAnnotation.class) != null &&
          (me.getString(PartOfSpeechAnnotation.class).equals("NN") ||
           me.getString(PartOfSpeechAnnotation.class).equals("NNS"))) {
        if (CURRENCY_WORD_PATTERN.matcher(me.word()).matches()) {
          if (prev.getString(PartOfSpeechAnnotation.class).equals("CD") &&
              prev.getString(AnswerAnnotation.class).equals("MONEY")) {
            me.set(AnswerAnnotation.class, "MONEY");
          }
        } else if (me.word().equals("m") || me.word().equals("b")) {
          // could be metres, but it's probably million or billion in our
          // applications
          if (prev.getString(AnswerAnnotation.class).equals("MONEY")) {
            me.set(AnswerAnnotation.class, "MONEY");
          } else {
            me.set(AnswerAnnotation.class, "NUMBER");
          }
        } else if (ORDINAL_PATTERN.matcher(me.word()).matches()) {
          if ((next.word() != null && MONTH_PATTERN.matcher(next.word()).matches()) ||
              (next.word() != null && next.word().equalsIgnoreCase("of") &&
               next2.word() != null && MONTH_PATTERN.matcher(next2.word()).matches())) {
            me.set(AnswerAnnotation.class, "DATE");
          }
        } else if(GENERIC_TIME_WORDS.matcher(me.word()).matches()){
          me.set(AnswerAnnotation.class, "TIME");
        }
      } else if (me.getString(PartOfSpeechAnnotation.class).equals("JJ")) {
        if ((next.word() != null && MONTH_PATTERN.matcher(next.word()).matches()) ||
            next.word() != null && next.word().equalsIgnoreCase("of") &&
            next2.word() != null && MONTH_PATTERN.matcher(next2.word()).matches()) {
          me.set(AnswerAnnotation.class, "DATE");
        } else if (ORDINAL_PATTERN.matcher(me.word()).matches()) {
          // don't do other tags: don't want 'second' as noun, or 'first' as adverb
          // introducing reasons
          me.set(AnswerAnnotation.class, "ORDINAL");
        }
      } else if (me.getString(PartOfSpeechAnnotation.class).equals("IN") &&
          me.word().equalsIgnoreCase("of")) {
        if (prev.get(TextAnnotation.class) != null &&
            ORDINAL_PATTERN.matcher(prev.get(TextAnnotation.class)).matches() &&
            next.get(TextAnnotation.class) != null &&
            MONTH_PATTERN.matcher(next.get(TextAnnotation.class)).matches()) {
          me.set(AnswerAnnotation.class, "DATE");
        }
      }
    }
    return document;
  }


  /**
   * Look for a distance of up to 3 for something that indicates weight not
   * money.
   *
   * @param pl The list of CoreLabel
   * @param i The position to scan right from
   * @return whether a weight word is found
   */
  private static boolean leftScanFindsWeightWord(List<CoreLabel> pl, int i) {
    if (DEBUG) {
      System.err.println("leftScan from: " + pl.get(i).word());
    }
    for (int j = i - 1; j >= 0 && j >= i - 3; j--) {
      CoreLabel fl = pl.get(j);
      if (fl.word().startsWith("weigh")) {
        if (DEBUG) {
          System.err.println("leftScan found weight: " + fl.word());
        }
        return true;
      }
    }
    return false;
  }


  /**
   * Look along CD words and see if next thing is a money word
   * like cents or pounds.
   *
   * @param pl The list of CoreLabel
   * @param i The position to scan right from
   * @return Whether a money word is found
   */
  private static boolean rightScanFindsMoneyWord(List<CoreLabel> pl, int i) {
    int j = i;
    if (DEBUG) {
      System.err.println("rightScan from: " + pl.get(j).word());
    }
    int sz = pl.size();
    while (j < sz && pl.get(j).getString(PartOfSpeechAnnotation.class).equals("CD")) {
      j++;
    }
    if (j >= sz) {
      return false;
    }
    String tag = pl.get(j).getString(PartOfSpeechAnnotation.class);
    String word = pl.get(j).word();
    if (DEBUG) {
      System.err.println("rightScan testing: " + word + '/' + tag + "; answer is: " + Boolean.toString((tag.equals("NN") || tag.equals("NNS")) && CURRENCY_WORD_PATTERN.matcher(word).matches()));
    }
    return (tag.equals("NN") || tag.equals("NNS")) && CURRENCY_WORD_PATTERN.matcher(word).matches();
  }

  // Implement other methods of AbstractSequenceClassifier interface

  @SuppressWarnings("unchecked")
  @Override
  public void train(Collection<List<CoreLabel>> docs,
                    DocumentReaderAndWriter readerAndWriter) {
  }

  @Override
  public void printProbsDocument(List<CoreLabel> document) {
  }

  @Override
  public void serializeClassifier(String serializePath) {
    System.err.print("Serializing classifier to " + serializePath + "...");
    System.err.println("done.");
  }

  @Override
  public void loadClassifier(ObjectInputStream in, Properties props) throws IOException, ClassCastException, ClassNotFoundException {
  }

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    NumberSequenceClassifier nsc = new NumberSequenceClassifier(props, true);
    String trainFile = nsc.flags.trainFile;
    String testFile = nsc.flags.testFile;
    String textFile = nsc.flags.textFile;
    String loadPath = nsc.flags.loadClassifier;
    String serializeTo = nsc.flags.serializeTo;

    if (loadPath != null) {
      nsc.loadClassifierNoExceptions(loadPath);
      nsc.flags.setProperties(props);
    } else if (trainFile != null) {
      nsc.train(trainFile);
    }

    if (serializeTo != null) {
      nsc.serializeClassifier(serializeTo);
    }

    if (testFile != null) {
      nsc.classifyAndWriteAnswers(testFile, nsc.makeReaderAndWriter());
    }

    if (textFile != null) {
      DocumentReaderAndWriter readerAndWriter =
        new PlainTextDocumentReaderAndWriter();
      nsc.classifyAndWriteAnswers(textFile, readerAndWriter);
    }
  } // end main

}
