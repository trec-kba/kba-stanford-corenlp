package edu.stanford.nlp.parser.metrics;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import edu.stanford.nlp.international.Languages;
import edu.stanford.nlp.international.Languages.Language;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.Lexicon;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;

//TODO: wsg Add loading of serialized lexicon from the main method

/**
 * Computes POS tagging P/R/F1 from guess/gold trees. This version assumes that the yields match. For
 * trees with potentially different yields, use {@link #TsarfatyEval}. 
 * 
 * @author Spence Green
 *
 */
public class TaggingEval extends AbstractEval {

  private final Lexicon lex;

  private static boolean doCatLevelEval = false;
  private Counter<String> precisions;
  private Counter<String> recalls;
  private Counter<String> f1s;

  private Counter<String> precisions2;
  private Counter<String> recalls2;
  private Counter<String> pnums2;
  private Counter<String> rnums2;

  private Counter<String> percentOOV;
  private Counter<String> percentOOV2;

  public TaggingEval(String str) {
    this(str, true, null);
  }

  public TaggingEval(String str, boolean runningAverages, Lexicon lex) {
    super(str, runningAverages);
    this.lex = lex;

    if(doCatLevelEval) {
      precisions = new ClassicCounter<String>();
      recalls = new ClassicCounter<String>();
      f1s = new ClassicCounter<String>();

      precisions2 = new ClassicCounter<String>();
      recalls2 = new ClassicCounter<String>();
      pnums2 = new ClassicCounter<String>();
      rnums2 = new ClassicCounter<String>();

      percentOOV = new ClassicCounter<String>();
      percentOOV2 = new ClassicCounter<String>();
    }
  }

  @Override
  protected Set<HasTag> makeObjects(Tree tree) {
    return (tree == null) ? new HashSet<HasTag>() : new HashSet<HasTag>(tree.taggedLabeledYield());
  }

  private Map<String,Set<Label>> makeObjectsByCat(Tree t) {
    Map<String,Set<Label>> catMap = new HashMap<String,Set<Label>>();
    List<CoreLabel> tly = t.taggedLabeledYield();

    for(CoreLabel label : tly) {
      if(catMap.containsKey(label.value()))
        catMap.get(label.value()).add(label);
      else {
        Set<Label> catSet = new HashSet<Label>();
        catSet.add(label);
        catMap.put(label.value(), catSet);
      }
    }
    return catMap;
  }

  @Override
  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    if(gold == null || guess == null) {
      System.err.printf("%s: Cannot compare against a null gold or guess tree!\n",this.getClass().getName());
      return;
    }

    //Do regular evaluation
    super.evaluate(guess, gold, pw);

    if(doCatLevelEval) {
      final Map<String,Set<Label>> guessCats = makeObjectsByCat(guess);
      final Map<String,Set<Label>> goldCats = makeObjectsByCat(gold);
      final Set<String> allCats = new HashSet<String>();
      allCats.addAll(guessCats.keySet());
      allCats.addAll(goldCats.keySet());

      for(final String cat : allCats) {
        Set<Label> thisGuessCats = guessCats.get(cat);
        Set<Label> thisGoldCats = goldCats.get(cat);

        if (thisGuessCats == null)
          thisGuessCats = new HashSet<Label>();
        if (thisGoldCats == null)
          thisGoldCats = new HashSet<Label>();

        double currentPrecision = precision(thisGuessCats, thisGoldCats);
        double currentRecall = precision(thisGoldCats, thisGuessCats);

        double currentF1 = (currentPrecision > 0.0 && currentRecall > 0.0 ? 2.0 / (1.0 / currentPrecision + 1.0 / currentRecall) : 0.0);

        precisions.incrementCount(cat, currentPrecision);
        recalls.incrementCount(cat, currentRecall);
        f1s.incrementCount(cat, currentF1);

        precisions2.incrementCount(cat, thisGuessCats.size() * currentPrecision);
        pnums2.incrementCount(cat, thisGuessCats.size());

        recalls2.incrementCount(cat, thisGoldCats.size() * currentRecall);
        rnums2.incrementCount(cat, thisGoldCats.size());

        if(lex != null) measureOOV(guess,gold);

        if (pw != null && runningAverages) {
          pw.println(cat + "\tP: " + ((int) (currentPrecision * 10000)) / 100.0 + " (sent ave " + ((int) (precisions.getCount(cat) * 10000 / num)) / 100.0 + ") (evalb " + ((int) (precisions2.getCount(cat) * 10000 / pnums2.getCount(cat))) / 100.0 + ")");
          pw.println("\tR: " + ((int) (currentRecall * 10000)) / 100.0 + " (sent ave " + ((int) (recalls.getCount(cat) * 10000 / num)) / 100.0 + ") (evalb " + ((int) (recalls2.getCount(cat) * 10000 / rnums2.getCount(cat))) / 100.0 + ")");
          double cF1 = 2.0 / (rnums2.getCount(cat) / recalls2.getCount(cat) + pnums2.getCount(cat) / precisions2.getCount(cat));
          String emit = str + " F1: " + ((int) (currentF1 * 10000)) / 100.0 + " (sent ave " + ((int) (10000 * f1s.getCount(cat) / num)) / 100.0 + ", evalb " + ((int) (10000 * cF1)) / 100.0 + ")";
          pw.println(emit);
        }
      }
      if (pw != null && runningAverages) {
        pw.println("========================================");
      }
    }
  }

  /**
   * Measures the percentage of incorrect taggings that can be attributed to OOV words.
   * 
   * @param guess
   * @param gold
   */
  private void measureOOV(Tree guess, Tree gold) {
    List<CoreLabel> goldTagging = gold.taggedLabeledYield();
    List<CoreLabel> guessTagging = guess.taggedLabeledYield();

    assert goldTagging.size() == guessTagging.size();

    for(int i = 0; i < goldTagging.size(); i++) {
      if(!(goldTagging.get(i) == guessTagging.get(i))) {
        percentOOV2.incrementCount(goldTagging.get(i).tag());
        if(!lex.isKnown(goldTagging.get(i).word()))
          percentOOV.incrementCount(goldTagging.get(i).tag());
      }
    }
  }

  @Override
  public void display(boolean verbose, PrintWriter pw) {
    super.display(verbose, pw);

    if(doCatLevelEval) {
      final NumberFormat nf = new DecimalFormat("0.00");
      final Set<String> cats = new HashSet<String>();
      final Random rand = new Random();
      cats.addAll(precisions.keySet());
      cats.addAll(recalls.keySet());

      Map<Double,String> f1Map = new TreeMap<Double,String>();
      for (String cat : cats) {
        double pnum2 = pnums2.getCount(cat);
        double rnum2 = rnums2.getCount(cat);
        double prec = precisions2.getCount(cat) / pnum2;
        double rec = recalls2.getCount(cat) / rnum2;
        double f1 = 2.0 / (1.0 / prec + 1.0 / rec);

        if(new Double(f1).equals(Double.NaN)) f1 = -1.0;
        if(f1Map.containsKey(f1))
          f1Map.put(f1 + (rand.nextDouble()/1000.0), cat);
        else
          f1Map.put(f1, cat);
      }

      pw.println("============================================================");
      pw.println("Tagging Performance by Category -- final statistics");
      pw.println("============================================================");

      for (String cat : f1Map.values()) {
        double pnum2 = pnums2.getCount(cat);
        double rnum2 = rnums2.getCount(cat);
        double prec = precisions2.getCount(cat) / pnum2;
        prec *= 100.0;
        double rec = recalls2.getCount(cat) / rnum2;
        rec *= 100.0;
        double f1 = 2.0 / (1.0 / prec + 1.0 / rec);

        double oovRate = (lex == null) ? -1.0 : percentOOV.getCount(cat) / percentOOV2.getCount(cat);

        pw.println(cat + "\tLP: " + ((pnum2 == 0.0) ? " N/A": nf.format(prec)) + "\tguessed: " + (int) pnum2 +
            "\tLR: " + ((rnum2 == 0.0) ? " N/A": nf.format(rec)) + "\tgold:  " + (int) rnum2 +
            "\tF1: " + ((pnum2 == 0.0 || rnum2 == 0.0) ? " N/A": nf.format(f1)) +
            "\tOOV: " + ((lex == null) ? " N/A" : nf.format(oovRate)));
      }

      pw.println("============================================================");
    }
  }

  private static final int minArgs = 2;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] gold guess\n\n",TaggingEval.class.getName()));
    usage.append("Options:\n");
    usage.append("  -v         : Verbose mode.\n");
    usage.append("  -l lang    : Select language settings from " + Languages.listOfLanguages() + "\n");
    usage.append("  -y num     : Skip gold trees with yields longer than num.\n");
    usage.append("  -g num     : Skip guess trees with yields longer than num.\n");
    usage.append("  -c         : Compute LP/LR/F1 by category.\n");
  }

  /**
   * Run the scoring metric on guess/gold input. This method performs "Collinization." 
   * The default language is English.
   * 
   * @param args
   */
  public static void main(String[] args) {

    if(args.length < minArgs) {
      System.out.println(usage.toString());
      System.exit(-1);
    }

    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    int maxGoldYield = Integer.MAX_VALUE;
    int maxGuessYield = Integer.MAX_VALUE;
    boolean VERBOSE = false;
    boolean skipGuess = false;

    String guessFile = null;
    String goldFile = null;

    for(int i = 0; i < args.length; i++) {

      if(args[i].startsWith("-")) {

        if(args[i].equals("-l")) {
          Language lang = Language.valueOf(args[++i].trim());
          tlpp = Languages.getLanguageParams(lang);

        } else if(args[i].equals("-y")) {
          maxGoldYield = Integer.parseInt(args[++i].trim());

        } else if(args[i].equals("-v")) {
          VERBOSE = true;

        } else if(args[i].equals("-c")) {
          doCatLevelEval = true;

        } else if(args[i].equals("-g")) {
          maxGuessYield = Integer.parseInt(args[++i].trim());
          skipGuess = true;

        }	else {
          System.out.println(usage.toString());
          System.exit(-1);
        }

      } else {
        //Required parameters
        goldFile = args[i++];
        guessFile = args[i];
        break;
      }
    }

    final PrintWriter pwOut = tlpp.pw();

    final Treebank guessTreebank = tlpp.diskTreebank();
    guessTreebank.loadPath(guessFile);
    pwOut.println("GUESS TREEBANK:");
    pwOut.println(guessTreebank.textualSummary());

    final Treebank goldTreebank = tlpp.diskTreebank();
    goldTreebank.loadPath(goldFile);
    pwOut.println("GOLD TREEBANK:");
    pwOut.println(goldTreebank.textualSummary());

    final TaggingEval taggingEval = new TaggingEval("Tagging LP/LR");

    final TreeTransformer tc = tlpp.collinizer();

    //PennTreeReader skips over null/malformed parses. So when the yields of the gold/guess trees
    //don't match, we need to keep looking for the next gold tree that matches.
    //The evalb ref implementation differs slightly as it expects one tree per line. It assigns
    //status as follows:
    //
    //   0 - Ok (yields match)
    //   1 - length mismatch
    //   2 - null parse e.g. (()).
    //
    //In the cases of 1,2, evalb does not include the tree pair in the LP/LR computation.

    final Iterator<Tree> goldItr = goldTreebank.iterator();
    int goldLineId = 0;
    int skippedGuessTrees = 0;

    for(final Tree guess : guessTreebank) {
      final Tree evalGuess = tc.transformTree(guess);
      if(guess.yield().size() > maxGuessYield) {
        skippedGuessTrees++;
        continue;
      }

      boolean doneEval = false;
      while(goldItr.hasNext() && !doneEval) {
        final Tree gold = goldItr.next();
        final Tree evalGold = tc.transformTree(gold);
        goldLineId++;

        if(gold.yield().size() > maxGoldYield) {
          continue;

        } else if(gold.yield().size() != guess.yield().size()) { 
          skippedGuessTrees++;
          pwOut.println("Yield mismatch at gold line " + goldLineId);
          break; //Default evalb behavior -- skip this guess tree
        }

        taggingEval.evaluate(evalGuess, evalGold, ((VERBOSE) ? pwOut : null));

        doneEval = true; //Move to the next guess parse
      }
    }

    pwOut.println("================================================================================");
    if(skippedGuessTrees != 0) pwOut.printf("%s %d guess trees\n", ((skipGuess) ? "Skipped" : "Unable to evaluate"), skippedGuessTrees);
    taggingEval.display(true, pwOut);
    pwOut.println();
    pwOut.close();
  }
}