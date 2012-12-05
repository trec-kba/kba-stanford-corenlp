package edu.stanford.nlp.parser.metrics;

import edu.stanford.nlp.international.Languages;
import edu.stanford.nlp.international.Languages.Language;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Triple;

import java.util.*;
import java.io.*;

/**
 * A Java re-implementation of the evalb bracket scoring metric (Collins, 1997) that accepts Unicode input.
 * "Collinization" should be performed on input trees prior to invoking the package programmatically.
 * "Collinization" refers to normalization of trees for things not counted in evaluation,
 * such as equivalencing PRT and ADVP, which has standardly been done in English evaluation.
 * A main method is provided that performs Collinization according to language specific settings.
 * <p>
 * This implementation was last validated against EVALB20080701 (http://nlp.cs.nyu.edu/evalb/)
 * by Spence Green on 22 Jan. 2010.  Notwithstanding this, Sekine and collins' EVALB script has been
 * the common standard for constituency evaluation of parsers for the last decade.  We always validate
 * any numbers we report with it, and we suggest that you do the same.
 *
 * @author Dan Klein
 * @author Spence Green
 */
public class Evalb extends AbstractEval {

  private final boolean atCharLevel;
  private final ConstituentFactory cf;

  public Evalb(String str, boolean runningAverages) {
    this(str, runningAverages, false);
  }

  public Evalb(String str, boolean runningAverages, boolean charLevel) {
    super(str, runningAverages);

    atCharLevel = charLevel;
    cf = new LabeledScoredConstituentFactory();
  }

  /**
   * evalb only evaluates phrasal categories, thus constituents() does not
   * return objects for terminals and pre-terminals.
   */
  @Override
  protected Set<Constituent> makeObjects(Tree tree) {
    Set<Constituent> set = new HashSet<Constituent>();
    if(tree != null) set.addAll(tree.constituents(cf, atCharLevel));
    return set;
  }

  @Override
  public void evaluate(Tree guess, Tree gold, PrintWriter pw) {
    if(gold == null || guess == null) {
      System.err.printf("%s: Cannot compare against a null gold or guess tree!\n",this.getClass().getName());
      return;

    } else if (!atCharLevel && guess.yield().size() != gold.yield().size()) {
      System.err.println("Warning: yield differs:");
      System.err.println("Guess: " + Sentence.listToString(guess.yield()));
      System.err.println("Gold:  " + Sentence.listToString(gold.yield()));
    }

    super.evaluate(guess, gold, pw);
  }


  public static class CBEval extends Evalb {

    private double cb = 0.0;
    private double num = 0.0;
    private double zeroCB = 0.0;

    protected void checkCrossing(Set<Constituent> s1, Set<Constituent> s2) {
      double c = 0.0;
      for (Constituent constit : s1) {
        if (constit.crosses(s2)) {
          c += 1.0;
        }
      }
      if (c == 0.0) {
        zeroCB += 1.0;
      }
      cb += c;
      num += 1.0;
    }

    @Override
    public void evaluate(Tree t1, Tree t2, PrintWriter pw) {
      Set<Constituent> b1 = makeObjects(t1);
      Set<Constituent> b2 = makeObjects(t2);
      checkCrossing(b1, b2);
      if (pw != null && runningAverages) {
        pw.println("AvgCB: " + ((int) (10000.0 * cb / num)) / 100.0 +
            " ZeroCB: " + ((int) (10000.0 * zeroCB / num)) / 100.0 + " N: " + getNum());
      }
    }

    @Override
    public void display(boolean verbose, PrintWriter pw) {
      pw.println(str + " AvgCB: " + ((int) (10000.0 * cb / num)) / 100.0 +
          " ZeroCB: " + ((int) (10000.0 * zeroCB / num)) / 100.0);
    }

    public CBEval(String str, boolean runningAverages) {
      super(str, runningAverages);
    }
  }


  private static final int minArgs = 2;
  private static final StringBuilder usage = new StringBuilder();
  static {
    usage.append(String.format("Usage: java %s [OPTS] gold guess\n\n",Evalb.class.getName()));
    usage.append("Options:\n");
    usage.append("  -v         : Verbose mode.\n");
    usage.append("  -l lang    : Select language settings from ").append(Languages.listOfLanguages()).append('\n');
    usage.append("  -y num     : Skip gold trees with yields longer than num.\n");
    usage.append("  -g num     : Skip guess trees with yields longer than num.\n");
    usage.append("  -t         : Evaluate bracketings at character level per Tsarfaty (2006).\n");
    usage.append("  -s num     : Sort the trees by F1 and output the num lowest F1 trees.\n");
    usage.append("  -c         : Compute LP/LR/F1 by category.\n");
    usage.append("  -e         : Input encoding.\n");
  }

  /**
   * Run the scoring metric on guess/gold input. This method performs "Collinization."
   * The default language is English.
   *
   * @param args Usage: Evalb [-y|-l|lt|-v|-s|-c|-g|-e] goldFile guessFile
   */
  public static void main(String[] args) {

    if(args.length < minArgs) {
      System.out.println(usage.toString());
      System.exit(-1);
    }

    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    int maxGuessYield = Integer.MAX_VALUE;
    int maxGoldYield = Integer.MAX_VALUE;
    boolean VERBOSE = false;
    boolean charLevel = false;
    boolean sortByF1 = false;
    int worstKTreesToEmit = 0;
    PriorityQueue<Triple<Double,Tree,Tree>> queue = null;
    boolean doCatLevel = false;
    boolean skipGuess = false;
    String encoding = "UTF-8";

    String guessFile = null;
    String goldFile = null;

    for(int i = 0; i < args.length; i++) {

      if(args[i].startsWith("-")) {

        if(args[i].equals("-l")) {
          Language lang = Language.valueOf(args[++i].trim());
          tlpp = Languages.getLanguageParams(lang);

        } else if(args[i].equals("-y")) {
          maxGoldYield = Integer.parseInt(args[++i].trim());

        } else if(args[i].equals("-t")) {
          charLevel = true;

        } else if(args[i].equals("-v")) {
          VERBOSE = true;

        } else if(args[i].equals("-s")) {
          sortByF1 = true;
          worstKTreesToEmit = Integer.parseInt(args[++i].trim());
          queue = new PriorityQueue<Triple<Double,Tree,Tree>>(2000, new F1Comparator());

        } else if(args[i].equals("-c")) {
          doCatLevel = true;

        } else if(args[i].equals("-g")) {
          maxGuessYield = Integer.parseInt(args[++i].trim());
          skipGuess = true;

        } else if(args[i].equals("-e")) {
          encoding = args[++i];

        } else {
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

    tlpp.setInputEncoding(encoding);

    final PrintWriter pwOut = tlpp.pw();

    final Treebank guessTreebank = tlpp.diskTreebank();
    guessTreebank.loadPath(guessFile);
    pwOut.println("GUESS TREEBANK:");
    pwOut.println(guessTreebank.textualSummary());

    final Treebank goldTreebank = tlpp.diskTreebank();
    goldTreebank.loadPath(goldFile);
    pwOut.println("GOLD TREEBANK:");
    pwOut.println(goldTreebank.textualSummary());

    final Evalb evalb = new Evalb("Evalb LP/LR", true, charLevel);
    final EvalbByCat evalbCat = (doCatLevel) ? new EvalbByCat("EvalbByCat LP/LR",true) : null;

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
      final ArrayList<Label> guessSent = guess.yield();
      if(guessSent.size() > maxGuessYield) {
        skippedGuessTrees++;
        continue;
      }

      boolean doneEval = false;
      while(goldItr.hasNext() && !doneEval) {
        final Tree gold = goldItr.next();
        final Tree evalGold = tc.transformTree(gold);
        goldLineId++;

        final ArrayList<Label> goldSent = gold.yield();

        if(goldSent.size() > maxGoldYield) {
          continue;

        } else if(goldSent.size() != guessSent.size() && !charLevel) {
          pwOut.println("Yield mismatch at gold line " + goldLineId);
          skippedGuessTrees++;
          break; //Default evalb behavior -- skip this guess tree

        } else if(charLevel) {
          String goldChars = Sentence.listToString(goldSent).replaceAll("\\s+","");
          String guessChars = Sentence.listToString(guessSent).replaceAll("\\s+","");
          if(goldChars.length() != guessChars.length()) {
            pwOut.printf("Char level yield mismatch at line %d (guess: %d gold: %d)\n",goldLineId,guessChars.length(),goldChars.length());
            skippedGuessTrees++;
            break; //Default evalb behavior -- skip this guess tree
          }
        }

        evalb.evaluate(evalGuess, evalGold, ((VERBOSE) ? pwOut : null));

        if(doCatLevel) evalbCat.evaluate(evalGuess, evalGold, ((VERBOSE) ? pwOut : null));
        if(sortByF1) storeTrees(queue,guess,gold,evalb.getLastF1());

        doneEval = true; //Move to the next guess parse
      }
    }

    pwOut.println("================================================================================");
    if(skippedGuessTrees != 0) pwOut.printf("%s %d guess trees\n", ((skipGuess) ? "Skipped" : "Unable to evaluate"), skippedGuessTrees);
    evalb.display(true, pwOut);
    pwOut.println();
    if(doCatLevel) {
      evalbCat.display(true, pwOut);
      pwOut.println();
    }
    if(sortByF1) emitSortedTrees(queue,worstKTreesToEmit,guessFile);
    pwOut.close();
  }


  private static void emitSortedTrees(PriorityQueue<Triple<Double, Tree, Tree>> queue, int worstKTreesToEmit,
      String filePrefix) {

    if(queue == null) System.err.println("Queue was not initialized properly");

    try {
      final PrintWriter guessPw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePrefix + ".kworst.guess"),"UTF-8")));
      final PrintWriter goldPw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePrefix + ".kworst.gold"),"UTF-8")));

      final ConstituentFactory cFact = new LabeledScoredConstituentFactory();
      final PrintWriter guessDepPw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePrefix + ".kworst.guess.deps"),"UTF-8")));
      final PrintWriter goldDepPw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePrefix + ".kworst.gold.deps"),"UTF-8")));

      System.out.printf("F1s of %d worst trees:\n",worstKTreesToEmit);

      for(int i = 0; queue.peek() != null && i < worstKTreesToEmit; i++) {
        final Triple<Double, Tree, Tree> trees = queue.poll();

        System.out.println(trees.first());

        //Output the trees
        goldPw.println(trees.second().toString());
        guessPw.println(trees.third().toString());

        //Output the set differences
        Set<Constituent> goldDeps = new HashSet<Constituent>();
        goldDeps.addAll(trees.second().constituents(cFact));
        goldDeps.removeAll(trees.third().constituents(cFact));
        for(Constituent c : goldDeps)
          goldDepPw.print(c.toString() + "  ");
        goldDepPw.println();

        Set<Constituent> guessDeps = new HashSet<Constituent>();
        guessDeps.addAll(trees.third().constituents(cFact));
        guessDeps.removeAll(trees.second().constituents(cFact));
        for(Constituent c : guessDeps)
          guessDepPw.print(c.toString() + "  ");
        guessDepPw.println();
      }

      guessPw.close();
      goldPw.close();
      goldDepPw.close();
      guessDepPw.close();

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  private static void storeTrees(PriorityQueue<Triple<Double, Tree, Tree>> queue, Tree guess, Tree gold, double curF1) {
    if(queue == null) return;

    queue.add(new Triple<Double,Tree,Tree>(curF1,gold,guess));
  }

  private static class F1Comparator implements Comparator<Triple<Double, Tree, Tree>> {

    public int compare(Triple<Double, Tree, Tree> o1, Triple<Double, Tree, Tree> o2) {
      final double firstF1 = o1.first();
      final double secondF1 = o2.first();

      if(firstF1 < secondF1)
        return -1;
      else if(firstF1 == secondF1)
        return 0;

      return 1;
    }
  }

}
