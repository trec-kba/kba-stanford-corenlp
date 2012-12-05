//
// StanfordCoreNLP -- a suite of NLP tools
// Copyright (c) 2009-2011 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//

package edu.stanford.nlp.dcoref;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefChain.MentionComparator;
import edu.stanford.nlp.dcoref.ScorerBCubed.BCubedType;
import edu.stanford.nlp.dcoref.sievepasses.DeterministicCorefSieve;
import edu.stanford.nlp.dcoref.sievepasses.ExactStringMatch;
import edu.stanford.nlp.io.StringOutputStream;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.DocIDAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SpeakerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.UtteranceAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntTuple;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.SystemUtils;

/**
 * Multi-pass Sieve coreference resolution system (see EMNLP 2010 paper).
 * <p>
 * The main entry point for API is coref(Document document).
 * The output is a map from CorefChain ID to corresponding CorefChain.
 *
 * @author Jenny Finkel
 * @author Mihai Surdeanu
 * @author Karthik Raghunathan
 * @author Heeyoung Lee
 * @author Sudarshan Rangarajan
 */
public class SieveCoreferenceSystem {

  public static final Logger logger = Logger.getLogger(SieveCoreferenceSystem.class.getName());

  /**
   * If true, we score the output of the given test document
   * Assumes gold annotations are available
   */
  private final boolean doScore;

  /**
   * If true, we do post processing.
   */
  private final boolean doPostProcessing;

  /**
   * maximum sentence distance between two mentions for resolution (-1: no constraint on distance)
   */
  private final int maxSentDist;

  /**
   * automatically set by looking at sieves
   */
  private final boolean useSemantics;

  /** flag for replicating conllst result */
  private final boolean replicateCoNLL;

  /** Path for the official CoNLL scorer  */
  public final String conllMentionEvalScript;

  /**
   * Array of sieve passes to be used in the system
   * Ordered from highest precision to lowest!
   */
  private final DeterministicCorefSieve [] sieves;
  private final String [] sieveClassNames;

  /**
   * Dictionaries of all the useful goodies (gender, animacy, number etc. lists)
   */
  private final Dictionaries dictionaries;

  /**
   * Semantic knowledge: WordNet
   */
  private final Semantics semantics;

  /** Current sieve index */
  public int currentSieve;

  /** counter for links in passes (Pair<correct links, total links>)  */
  public List<Pair<Integer, Integer>> linksCountInPass;


  /** Scores for each pass */
  public List<CorefScorer> scorePairwise;
  public List<CorefScorer> scoreBcubed;
  public List<CorefScorer> scoreMUC;

  private List<CorefScorer> scoreSingleDoc;

  /** Additional scoring stats */
  int additionalCorrectLinksCount;
  int additionalLinksCount;

  /** Semantic knowledge: currently WordNet is available */
  public class Semantics {
    public WordNet wordnet;

    public Semantics(Dictionaries dict) throws Exception{
      wordnet = new WordNet();
    }
  }

  public static class LogFormatter extends Formatter {
    @Override
    public String format(LogRecord rec) {
      StringBuilder buf = new StringBuilder(1000);
      buf.append(formatMessage(rec));
      buf.append('\n');
      return buf.toString();
    }
  }

  public SieveCoreferenceSystem(Properties props) throws Exception {
    // initialize required fields
    currentSieve = -1;

    linksCountInPass = new ArrayList<Pair<Integer, Integer>>();
    scorePairwise = new ArrayList<CorefScorer>();
    scoreBcubed = new ArrayList<CorefScorer>();
    scoreMUC = new ArrayList<CorefScorer>();

    //
    // construct the sieve passes
    //
    String sievePasses = props.getProperty(Constants.SIEVES_PROP, Constants.SIEVEPASSES);
    sieveClassNames = sievePasses.trim().split(",\\s*");
    sieves = new DeterministicCorefSieve[sieveClassNames.length];
    for(int i = 0; i < sieveClassNames.length; i ++){
      sieves[i] = (DeterministicCorefSieve) Class.forName("edu.stanford.nlp.dcoref.sievepasses."+sieveClassNames[i]).getConstructor().newInstance();
      sieves[i].init(props);
    }

    //
    // create scoring framework
    //
    doScore = Boolean.parseBoolean(props.getProperty(Constants.SCORE_PROP, "false"));

    //
    // setting post processing
    //
    doPostProcessing = Boolean.parseBoolean(props.getProperty(Constants.POSTPROCESSING_PROP, "false"));

    //
    // setting maximum sentence distance between two mentions for resolution (-1: no constraint on distance)
    //
    maxSentDist = Integer.parseInt(props.getProperty(Constants.MAXDIST_PROP, "-1"));

    //
    // set useWordNet
    //
    useSemantics = sievePasses.contains("AliasMatch") || sievePasses.contains("LexicalChainMatch");

    // flag for replicating conllst result
    replicateCoNLL = Boolean.parseBoolean(props.getProperty(Constants.REPLICATECONLL_PROP, "false"));
    conllMentionEvalScript = props.getProperty(Constants.CONLL_SCORER, Constants.conllMentionEvalScript);

    if(doScore){
      for(int i = 0 ; i < sieveClassNames.length ; i++){
        scorePairwise.add(new ScorerPairwise());
        scoreBcubed.add(new ScorerBCubed(BCubedType.Bconll));
        scoreMUC.add(new ScorerMUC());
        linksCountInPass.add(new Pair<Integer, Integer>(0, 0));
      }
    }

    //
    // load all dictionaries
    //
    dictionaries = new Dictionaries(props);
    semantics = (useSemantics)? new Semantics(dictionaries) : null;
  }

  public boolean doScore() { return doScore; }
  public Dictionaries dictionaries() { return dictionaries; }
  public Semantics semantics() { return semantics; }

  private static LexicalizedParser makeParser(Properties props) {
    LexicalizedParser parser = new LexicalizedParser(props.getProperty(Constants.PARSER_MODEL_PROP, DefaultPaths.DEFAULT_PARSER_MODEL));
    int maxLen = Integer.parseInt(props.getProperty(Constants.PARSER_MAXLEN_PROP, "100"));
    parser.setOptionFlags("-maxLength", Integer.toString(maxLen));
    return parser;
  }

  /**
   * Needs the following properties:
   *  -props 'Location of coref.properties'
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    String timeStamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-");

    //
    // initialize logger
    //
    FileHandler fh;
    try {
      String logFileName = props.getProperty(Constants.LOG_PROP, "log.txt");
      if(logFileName.endsWith(".txt")) {
        logFileName = logFileName.substring(0, logFileName.length()-4) +"_"+ timeStamp+".txt";
      } else {
        logFileName = logFileName + "_"+ timeStamp+".txt";
      }
      fh = new FileHandler(logFileName, false);
      logger.addHandler(fh);
      logger.setLevel(Level.FINE);
      fh.setFormatter(new LogFormatter());
    } catch (SecurityException e) {
      System.err.println("ERROR: cannot initialize logger!");
      throw e;
    } catch (IOException e) {
      System.err.println("ERROR: cannot initialize logger!");
      throw e;
    }

    logger.fine(timeStamp);
    logger.fine(props.toString());
    Constants.printConstants(logger);

    // initialize coref system
    SieveCoreferenceSystem corefSystem = new SieveCoreferenceSystem(props);
    LexicalizedParser parser = makeParser(props); // Load the Stanford Parser

    // prepare conll output
    PrintWriter writerGold = null;
    PrintWriter writerPredicted = null;
    PrintWriter writerPredictedCoref = null;

    String conllOutputMentionGoldFile = null;
    String conllOutputMentionPredictedFile = null;
    String conllOutputMentionCorefPredictedFile = null;
    String conllMentionEvalFile = null;
    String conllMentionEvalErrFile = null;
    String conllMentionCorefEvalFile = null;
    String conllMentionCorefEvalErrFile = null;

    if(Constants.PRINT_CONLL_OUTPUT || corefSystem.replicateCoNLL) {
      String conllOutput = props.getProperty(Constants.CONLL_OUTPUT_PROP, "conlloutput");
      conllOutputMentionGoldFile = conllOutput + "-"+timeStamp+".gold.txt";
      conllOutputMentionPredictedFile = conllOutput +"-"+timeStamp+ ".predicted.txt";
      conllOutputMentionCorefPredictedFile = conllOutput +"-"+timeStamp+ ".coref.predicted.txt";
      conllMentionEvalFile = conllOutput +"-"+timeStamp+ ".eval.txt";
      conllMentionEvalErrFile = conllOutput +"-"+timeStamp+ ".eval.err.txt";
      conllMentionCorefEvalFile = conllOutput +"-"+timeStamp+ ".coref.eval.txt";
      conllMentionCorefEvalErrFile = conllOutput +"-"+timeStamp+ ".coref.eval.err.txt";
      logger.info("CONLL MENTION GOLD FILE: " + conllOutputMentionGoldFile);
      logger.info("CONLL MENTION PREDICTED FILE: " + conllOutputMentionPredictedFile);
      logger.info("CONLL MENTION EVAL FILE: " + conllMentionEvalFile);
      if (!Constants.SKIP_COREF) {
        logger.info("CONLL MENTION PREDICTED WITH COREF FILE: " + conllOutputMentionCorefPredictedFile);
        logger.info("CONLL MENTION WITH COREF EVAL FILE: " + conllMentionCorefEvalFile);
      }
      writerGold = new PrintWriter(new FileOutputStream(conllOutputMentionGoldFile));
      writerPredicted = new PrintWriter(new FileOutputStream(conllOutputMentionPredictedFile));
      writerPredictedCoref = new PrintWriter(new FileOutputStream(conllOutputMentionCorefPredictedFile));
    }

    // MentionExtractor extracts MUC, ACE, or CoNLL documents
    MentionExtractor mentionExtractor = null;
    if(props.containsKey(Constants.MUC_PROP)){
      mentionExtractor = new MUCMentionExtractor(parser, corefSystem.dictionaries, props, corefSystem.semantics);
    } else if(props.containsKey(Constants.ACE2004_PROP) || props.containsKey(Constants.ACE2005_PROP)) {
      mentionExtractor = new ACEMentionExtractor(parser, corefSystem.dictionaries, props, corefSystem.semantics);
    } else if (props.containsKey(Constants.CONLL2011_PROP)) {
      mentionExtractor = new CoNLLMentionExtractor(parser, corefSystem.dictionaries, props, corefSystem.semantics);
    }
    if(mentionExtractor == null){
      throw new RuntimeException("No input file specified!");
    }
    if (!Constants.USE_GOLD_MENTIONS) {
      // Set mention finder
      String mentionFinderClass = props.getProperty(Constants.MENTION_FINDER_PROP);
      if (mentionFinderClass != null) {
        String mentionFinderPropFilename = props.getProperty(Constants.MENTION_FINDER_PROPFILE_PROP);
        CorefMentionFinder mentionFinder;
        if (mentionFinderPropFilename != null) {
          Properties mentionFinderProps = new Properties();
          mentionFinderProps.load(new FileInputStream(mentionFinderPropFilename));
          mentionFinder = (CorefMentionFinder) Class.forName(mentionFinderClass).getConstructor(Properties.class).newInstance(mentionFinderProps);
        } else {
          mentionFinder = (CorefMentionFinder) Class.forName(mentionFinderClass).newInstance();
        }
        mentionExtractor.setMentionFinder(mentionFinder);
      }
      if (mentionExtractor.mentionFinder == null) {
        logger.warning("No mention finder specified, but not using gold mentions");
      }
    }

    //
    // Parse one document at a time, and do single-doc coreference resolution in each
    //
    Document document;

    //
    // In one iteration, orderedMentionsBySentence contains a list of all
    // mentions in one document. Each mention has properties (annotations):
    // its surface form (Word), NER Tag, POS Tag, Index, etc.
    //

    while(true) {

      document = mentionExtractor.nextDoc();
      if(document==null) break;

      if(!props.containsKey(Constants.MUC_PROP)) {
        printRawDoc(document, true);
        printRawDoc(document, false);
      }
      printDiscourseStructure(document);

      if(corefSystem.doScore()){
        document.extractGoldCorefClusters();
      }

      if(Constants.PRINT_CONLL_OUTPUT || corefSystem.replicateCoNLL) {
        // Not doing coref - print conll output here
        printConllOutput(document, writerGold, true);
        printConllOutput(document, writerPredicted, false);
      }

      // run mention detection only
      if(Constants.SKIP_COREF) {
        continue;
      }

      corefSystem.coref(document);  // Do Coreference Resolution

      if(corefSystem.doScore()){
        //Identifying possible coreferring mentions in the corpus along with any recall/precision errors with gold corpus
        corefSystem.printTopK(logger, document, corefSystem.semantics);

        logger.fine("pairwise score for this doc: ");
        corefSystem.scoreSingleDoc.get(corefSystem.sieves.length-1).printF1(logger);
        logger.fine("accumulated score: ");
        corefSystem.printF1(true);
        logger.fine("\n");
      }
      if(Constants.PRINT_CONLL_OUTPUT || corefSystem.replicateCoNLL){
        printConllOutput(document, writerPredictedCoref, false, true);
      }
    }

    if(Constants.PRINT_CONLL_OUTPUT || corefSystem.replicateCoNLL) {
      writerGold.close();
      writerPredicted.close();
      writerPredictedCoref.close();

      if(props.containsKey(Constants.CONLL_SCORER)) {
        runConllEval(corefSystem.conllMentionEvalScript, conllOutputMentionGoldFile, conllOutputMentionPredictedFile, conllMentionEvalFile, conllMentionEvalErrFile);

        String summary = getConllEvalSummary(corefSystem.conllMentionEvalScript, conllOutputMentionGoldFile, conllOutputMentionPredictedFile);
        logger.info("CONLL EVAL SUMMARY (Before COREF)\n" + summary);

        if (!Constants.SKIP_COREF) {
          runConllEval(corefSystem.conllMentionEvalScript, conllOutputMentionGoldFile, conllOutputMentionCorefPredictedFile, conllMentionCorefEvalFile, conllMentionCorefEvalErrFile);
          summary = getConllEvalSummary(corefSystem.conllMentionEvalScript, conllOutputMentionGoldFile, conllOutputMentionCorefPredictedFile);
          logger.info("CONLL EVAL SUMMARY (After COREF)\n" + summary);
          printFinalScore(summary);
        }
      }
    }
    logger.info("done");
  }

  /**
   * Extracts coreference clusters.
   * This is the main API entry point for coreference resolution.
   * Return a map from CorefChain ID to corresponding CorefChain.
   */
  public Map<Integer, CorefChain> coref(Document document) {

    // Multi-pass sieve coreference resolution
    for (int i = 0; i < sieves.length ; i++){
      currentSieve = i;
      DeterministicCorefSieve sieve = sieves[i];
      // Do coreference resolution using this pass
      coreference(document, sieve);
    }

    // post processing (e.g., removing singletons, appositions for conll)
    if((!Constants.USE_GOLD_MENTIONS && doPostProcessing) || replicateCoNLL) postProcessing(document);

    // coref system output: CorefChain
    Map<Integer, CorefChain> result = new HashMap<Integer, CorefChain>();
    for(CorefCluster c : document.corefClusters.values()) {
      result.put(c.clusterID, new CorefChain(c, document.positions));
    }

    return result;
  }

  /**
   * Do coreference resolution using one sieve pass
   * @param document - an extracted document
   */
  private void coreference(
      Document document,
      DeterministicCorefSieve sieve) {

    List<List<Mention>> orderedMentionsBySentence = document.getOrderedMentions();
    Map<Integer, CorefCluster> corefClusters = document.corefClusters;
    Set<Mention> roleSet = document.roleSet;

    logger.finest("ROLE SET (Skip exact string match): ------------------");
    for(Mention m : roleSet){
      logger.finest("\t"+m.spanToString());
    }
    logger.finest("-------------------------------------------------------");

    additionalCorrectLinksCount = 0;
    additionalLinksCount = 0;

    for (int sentI = 0; sentI < orderedMentionsBySentence.size(); sentI++) {
      List<Mention> orderedMentions = orderedMentionsBySentence.get(sentI);

      for (int mentionI = 0; mentionI < orderedMentions.size(); mentionI++) {

        Mention m1 = orderedMentions.get(mentionI);

        // check for skip: first mention only, discourse salience
        if(sieve.skipThisMention(document, m1, corefClusters.get(m1.corefClusterID), dictionaries)) {
          continue;
        }

        LOOP:
          for (int sentJ = sentI; sentJ >= 0; sentJ--) {
            List<Mention> l = sieve.getOrderedAntecedents(sentJ, sentI, orderedMentions, orderedMentionsBySentence, m1, mentionI, corefClusters, dictionaries);
            if(maxSentDist != -1 && sentJ - sentI > maxSentDist) continue;

            // Sort mentions by length whenever we have two mentions beginning at the same position and having the same head
            for(int i = 0; i < l.size(); i++) {
              for(int j = 0; j < l.size(); j++) {
                if(l.get(i).headString.equals(l.get(j).headString) &&
                    l.get(i).startIndex == l.get(j).startIndex &&
                    l.get(i).sameSentence(l.get(j)) && j > i &&
                    l.get(i).spanToString().length() > l.get(j).spanToString().length()) {
                  logger.finest("FLIPPED: "+l.get(i).spanToString()+"("+i+"), "+l.get(j).spanToString()+"("+j+")");
                  l.set(j, l.set(i, l.get(j)));
                }
              }
            }

            for (Mention m2 : l) {
              // m2 - antecedent of m1

              if (m1.corefClusterID == m2.corefClusterID) continue;
              CorefCluster c1 = corefClusters.get(m1.corefClusterID);
              CorefCluster c2 = corefClusters.get(m2.corefClusterID);

              if (sieve.useRoleSkip()) {
                if (m1.isRoleAppositive(m2, dictionaries)) {
                  roleSet.add(m1);
                } else if (m2.isRoleAppositive(m1, dictionaries)) {
                  roleSet.add(m2);
                }
                continue;
              }

              if (sieve.coreferent(document, c1, c2, m1, m2, dictionaries, roleSet, semantics)) {

                // print logs for analysis
                if (doScore()) {
                  printLogs(c1, c2, m1, m2, document, currentSieve);
                }

                int removeID = c1.clusterID;
                CorefCluster.mergeClusters(c2, c1);
                corefClusters.remove(removeID);
                break LOOP;
              }
            }
          } // End of "LOOP"
      }
    }

    // scoring
    if(doScore()){
      scoreMUC.get(currentSieve).calculateScore(document);
      scoreBcubed.get(currentSieve).calculateScore(document);
      scorePairwise.get(currentSieve).calculateScore(document);
      if(currentSieve==0) {
        scoreSingleDoc = new ArrayList<CorefScorer>();
        scoreSingleDoc.add(new ScorerPairwise());
        scoreSingleDoc.get(currentSieve).calculateScore(document);
        additionalCorrectLinksCount = (int) scoreSingleDoc.get(currentSieve).precisionNumSum;
        additionalLinksCount = (int) scoreSingleDoc.get(currentSieve).precisionDenSum;
      } else {
        scoreSingleDoc.add(new ScorerPairwise());
        scoreSingleDoc.get(currentSieve).calculateScore(document);
        additionalCorrectLinksCount = (int) (scoreSingleDoc.get(currentSieve).precisionNumSum - scoreSingleDoc.get(currentSieve-1).precisionNumSum);
        additionalLinksCount = (int) (scoreSingleDoc.get(currentSieve).precisionDenSum - scoreSingleDoc.get(currentSieve-1).precisionDenSum);
      }
      linksCountInPass.get(currentSieve).setFirst(linksCountInPass.get(currentSieve).first() + additionalCorrectLinksCount);
      linksCountInPass.get(currentSieve).setSecond(linksCountInPass.get(currentSieve).second() + additionalLinksCount);

      printSieveScore(document, sieve);
    }
  }

  /** Remove singletons, appositive, predicate nominatives, relative pronouns */
  private void postProcessing(Document document) {
    Set<IntTuple> removeSet = new HashSet<IntTuple>();
    Set<Integer> removeClusterSet = new HashSet<Integer>();

    for(CorefCluster c : document.corefClusters.values()){
      Set<Mention> removeMentions = new HashSet<Mention>();
      for(Mention m : c.getCorefMentions()) {
        if(Constants.REMOVE_APPOSITION_PREDICATENOMINATIVES
            && ((m.appositions!=null && m.appositions.size() > 0)
                || (m.predicateNominatives!=null && m.predicateNominatives.size() > 0)
                || (m.relativePronouns!=null && m.relativePronouns.size() > 0))){
          removeMentions.add(m);
          removeSet.add(document.positions.get(m));
          m.corefClusterID = m.mentionID;
        }
      }
      c.corefMentions.removeAll(removeMentions);
      if(Constants.REMOVE_SINGLETONS && c.getCorefMentions().size()==1) {
        removeClusterSet.add(c.clusterID);
      }
    }
    for(int removeId : removeClusterSet){
      document.corefClusters.remove(removeId);
    }
    for(IntTuple pos : removeSet){
      document.positions.remove(pos);
    }
  }
  /** Remove singleton clusters */
  public static List<List<Mention>> filterMentionsWithSingletonClusters(Document document, List<List<Mention>> mentions)
  {

    List<List<Mention>> res = new ArrayList<List<Mention>>(mentions.size());
    for (List<Mention> ml:mentions) {
      List<Mention> filtered = new ArrayList<Mention>();
      for (Mention m:ml) {
        CorefCluster cluster = document.corefClusters.get(m.corefClusterID);
        if (cluster != null && cluster.getCorefMentions().size() > 1) {
          filtered.add(m);
        }
      }
      res.add(filtered);
    }
    return res;
  }
  public static void runConllEval(String conllMentionEvalScript,
      String goldFile, String predictFile, String evalFile, String errFile) throws IOException
      {
    ProcessBuilder process = new ProcessBuilder(conllMentionEvalScript, "all", goldFile, predictFile);
    PrintWriter out = new PrintWriter(new FileOutputStream(evalFile));
    PrintWriter err = new PrintWriter(new FileOutputStream(errFile));
    SystemUtils.run(process, out, err);
    out.close();
    err.close();
      }

  public static String getConllEvalSummary(String conllMentionEvalScript,
      String goldFile, String predictFile) throws IOException
      {
    ProcessBuilder process = new ProcessBuilder(conllMentionEvalScript, "all", goldFile, predictFile, "none");
    StringOutputStream errSos = new StringOutputStream();
    StringOutputStream outSos = new StringOutputStream();
    PrintWriter out = new PrintWriter(outSos);
    PrintWriter err = new PrintWriter(errSos);
    SystemUtils.run(process, out, err);
    out.close();
    err.close();
    String summary = outSos.toString();
    String errStr = errSos.toString();
    if (errStr.length() > 0) {
      summary += "\nERROR: " + errStr;
    }
    return summary;
      }

  /** Print logs for error analysis */
  public void printTopK(Logger logger, Document document, Semantics semantics) {

    List<List<Mention>> orderedMentionsBySentence = document.getOrderedMentions();
    Map<Integer, CorefCluster> corefClusters = document.corefClusters;
    HashMap<Mention, IntTuple> positions = document.positions;
    Map<Integer, Mention> golds = document.allGoldMentions;

    logger.fine("=======ERROR ANALYSIS=========================================================");

    boolean correct;
    boolean chosen;
    for(int i = 0 ; i < orderedMentionsBySentence.size(); i++){
      for(int j =0 ; j < orderedMentionsBySentence.get(i).size(); j++){
        Mention m = orderedMentionsBySentence.get(i).get(j);
        List<Mention> orderedMentions = orderedMentionsBySentence.get(i);
        logger.fine("=========Line: "+i+"\tmention: "+j+"=======================================================");
        logger.fine(m.spanToString()+"\tmentionID: "+m.mentionID+"\tcorefClusterID: "+m.corefClusterID+"\tgoldCorefClusterID: "+m.goldCorefClusterID);
        CorefCluster corefCluster = corefClusters.get(m.corefClusterID);
        if (corefCluster != null) {
          corefCluster.printCorefCluster(logger);
        } else {
          logger.finer("CANNOT find coref cluster for cluster " + m.corefClusterID);
        }
        logger.fine("-------------------------------------------------------");

        boolean oneRecallErrorPrinted = false;
        boolean onePrecisionErrorPrinted = false;
        boolean alreadyChoose = false;

        for (int sentJ = i; sentJ >= 0; sentJ--) {
          List<Mention> l = (new ExactStringMatch()).getOrderedAntecedents(sentJ, i, orderedMentions, orderedMentionsBySentence, m, j, corefClusters, dictionaries);

          // Sort mentions by length whenever we have two mentions beginning at the same position and having the same head
          for(int ii = 0; ii < l.size(); ii++) {
            for(int jj = 0; jj < l.size(); jj++) {
              if(l.get(ii).headString.equals(l.get(jj).headString) &&
                  l.get(ii).startIndex == l.get(jj).startIndex &&
                  l.get(ii).sameSentence(l.get(jj)) && jj > ii &&
                  l.get(ii).spanToString().length() > l.get(jj).spanToString().length()) {
                logger.finest("FLIPPED: "+l.get(ii).spanToString()+"("+ii+"), "+l.get(jj).spanToString()+"("+jj+")");
                l.set(jj, l.set(ii, l.get(jj)));
              }
            }
          }

          logger.finest("Candidates in sentence #"+sentJ+" for mention: "+m.spanToString());
          for(int ii = 0; ii < l.size(); ii ++){
            logger.finest("\tCandidate #"+ii+": "+l.get(ii).spanToString());
          }

          for (Mention antecedent : l) {
            chosen=(m.corefClusterID==antecedent.corefClusterID);
            IntTuple src = new IntTuple(2);
            src.set(0,i);
            src.set(1,j);

            IntTuple ant = positions.get(antecedent);
            //correct=(chosen==goldLinks.contains(new Pair<IntTuple, IntTuple>(src,ant)));
            boolean coreferent = golds.containsKey(m.mentionID)
            && golds.containsKey(antecedent.mentionID)
            && (golds.get(m.mentionID).goldCorefClusterID == golds.get(antecedent.mentionID).goldCorefClusterID);
            correct=(chosen==coreferent);

            String chosenness = chosen ? "Chosen" : "Not Chosen";
            String correctness = correct ? "Correct" : "Incorrect";
            logger.fine("\t" + correctness +"\t\t" + chosenness + "\t"+antecedent.spanToString());
            CorefCluster mC = corefClusters.get(m.corefClusterID);
            CorefCluster aC = corefClusters.get(antecedent.corefClusterID);

            if(chosen && !correct && !onePrecisionErrorPrinted && !alreadyChoose)  {
              onePrecisionErrorPrinted = true;
              printLinkWithContext(logger, "\nPRECISION ERROR ", src, ant, document, semantics);
              logger.fine("END of PRECISION ERROR LOG");
            }

            if(!chosen && !correct && !oneRecallErrorPrinted && (!alreadyChoose || (alreadyChoose && onePrecisionErrorPrinted))) {
              oneRecallErrorPrinted = true;
              printLinkWithContext(logger, "\nRECALL ERROR ", src, ant, document, semantics);

              logger.finer("cluster info: ");
              if (mC != null) {
                mC.printCorefCluster(logger);
              } else {
                logger.finer("CANNOT find coref cluster for cluster " + m.corefClusterID);
              }
              logger.finer("----------------------------------------------------------");
              if (aC != null) {
                aC.printCorefCluster(logger);
              } else {
                logger.finer("CANNOT find coref cluster for cluster " + m.corefClusterID);
              }
              logger.finer("");
              logger.fine("END of RECALL ERROR LOG");
            }
            if(chosen) alreadyChoose = true;
          }
        }
        logger.fine("\n");
      }
    }
    logger.fine("===============================================================================");
  }

  public void printF1(boolean printF1First) {
    scoreMUC.get(sieveClassNames.length - 1).printF1(logger, printF1First);
    scoreBcubed.get(sieveClassNames.length - 1).printF1(logger, printF1First);
    scorePairwise.get(sieveClassNames.length - 1).printF1(logger, printF1First);
  }

  private void printSieveScore(Document document, DeterministicCorefSieve sieve) {
    logger.fine("===========================================");
    logger.fine("pass"+currentSieve+": "+ sieve.flagsToString());
    scoreMUC.get(currentSieve).printF1(logger);
    scoreBcubed.get(currentSieve).printF1(logger);
    scorePairwise.get(currentSieve).printF1(logger);
    logger.fine("# of Clusters: "+document.corefClusters.size() + ",\t# of additional links: "+additionalLinksCount
        +",\t# of additional correct links: "+additionalCorrectLinksCount
        +",\tprecision of new links: "+1.0*additionalCorrectLinksCount/additionalLinksCount);
    logger.fine("# of total additional links: "+linksCountInPass.get(currentSieve).second()
        +",\t# of total additional correct links: "+linksCountInPass.get(currentSieve).first()
        +",\taccumulated precision of this pass: "+1.0*linksCountInPass.get(currentSieve).first()/linksCountInPass.get(currentSieve).second());
    logger.fine("--------------------------------------");
  }
  /** Print coref link info */
  private static void printLink(Logger logger, String header, IntTuple src, IntTuple dst, List<List<Mention>> orderedMentionsBySentence) {
    Mention srcMention = orderedMentionsBySentence.get(src.get(0)).get(src.get(1));
    Mention dstMention = orderedMentionsBySentence.get(dst.get(0)).get(dst.get(1));
    if(src.get(0)==dst.get(0)) {
      logger.fine(header + ": ["+srcMention.spanToString()+"](id="+srcMention.mentionID
          +") in sent #"+src.get(0)+" => ["+dstMention.spanToString()+"](id="+dstMention.mentionID+") in sent #"+dst.get(0) + " Same Sentence");
    } else {
      logger.fine(header + ": ["+srcMention.spanToString()+"](id="+srcMention.mentionID
          +") in sent #"+src.get(0)+" => ["+dstMention.spanToString()+"](id="+dstMention.mentionID+") in sent #"+dst.get(0));
    }
  }

  protected static void printList(Logger logger, String... args)  {
    String p = "";
    for(String arg : args)
      p += arg+"\t";
    logger.fine(p);
  }

  /** print a coref link information including context and parse tree */
  private static void printLinkWithContext(Logger logger,
      String header,
      IntTuple src,
      IntTuple dst,
      Document document, Semantics semantics
  ) {
    List<List<Mention>> orderedMentionsBySentence = document.getOrderedMentions();
    List<List<Mention>> goldOrderedMentionsBySentence = document.goldOrderedMentionsBySentence;

    Mention srcMention = orderedMentionsBySentence.get(src.get(0)).get(src.get(1));
    Mention dstMention = orderedMentionsBySentence.get(dst.get(0)).get(dst.get(1));
    List<CoreLabel> srcSentence = srcMention.sentenceWords;
    List<CoreLabel> dstSentence = dstMention.sentenceWords;

    printLink(logger, header, src, dst, orderedMentionsBySentence);

    printList(logger, "Mention:" + srcMention.spanToString(),
        "Gender:" + srcMention.gender.toString(),
        "Number:" + srcMention.number.toString(),
        "Animacy:" + srcMention.animacy.toString(),
        "Person:" + srcMention.person.toString(),
        "NER:" + srcMention.nerString,
        "Head:" + srcMention.headString,
        "Type:" + srcMention.mentionType.toString(),
        "utter: "+srcMention.headWord.get(UtteranceAnnotation.class),
        "speakerID: "+srcMention.headWord.get(SpeakerAnnotation.class),
        "twinless:" + srcMention.twinless);
    logger.fine("Context:");

    String p = "";
    for(int i = 0; i < srcSentence.size(); i++) {
      if(i == srcMention.startIndex)
        p += "[";
      if(i == srcMention.endIndex)
        p += "]";
      p += srcSentence.get(i).word() + " ";
    }
    logger.fine(p);

    StringBuilder golds = new StringBuilder();
    golds.append("Gold mentions in the sentence:\n");
    Counter<Integer> mBegin = new ClassicCounter<Integer>();
    Counter<Integer> mEnd = new ClassicCounter<Integer>();

    for(Mention m : goldOrderedMentionsBySentence.get(src.get(0))){
      mBegin.incrementCount(m.startIndex);
      mEnd.incrementCount(m.endIndex);
    }
    List<CoreLabel> l = document.annotation.get(SentencesAnnotation.class).get(src.get(0)).get(TokensAnnotation.class);
    for(int i = 0 ; i < l.size() ; i++){
      for(int j = 0; j < mEnd.getCount(i); j++){
        golds.append("]");
      }
      for(int j = 0; j < mBegin.getCount(i); j++){
        golds.append("[");
      }
      golds.append(l.get(i).get(TextAnnotation.class));
      golds.append(" ");
    }
    logger.fine(golds.toString());

    printList(logger, "\nAntecedent:" + dstMention.spanToString(),
        "Gender:" + dstMention.gender.toString(),
        "Number:" + dstMention.number.toString(),
        "Animacy:" + dstMention.animacy.toString(),
        "Person:" + dstMention.person.toString(),
        "NER:" + dstMention.nerString,
        "Head:" + dstMention.headString,
        "Type:" + dstMention.mentionType.toString(),
        "utter: "+dstMention.headWord.get(UtteranceAnnotation.class),
        "speakerID: "+dstMention.headWord.get(SpeakerAnnotation.class),
        "twinless:" + dstMention.twinless);
    logger.fine("Context:");

    p = "";
    for(int i = 0; i < dstSentence.size(); i++) {
      if(i == dstMention.startIndex)
        p += "[";
      if(i == dstMention.endIndex)
        p += "]";
      p += dstSentence.get(i).word() + " ";
    }
    logger.fine(p);

    golds = new StringBuilder();
    golds.append("Gold mentions in the sentence:\n");
    mBegin = new ClassicCounter<Integer>();
    mEnd = new ClassicCounter<Integer>();

    for(Mention m : goldOrderedMentionsBySentence.get(dst.get(0))){
      mBegin.incrementCount(m.startIndex);
      mEnd.incrementCount(m.endIndex);
    }
    l = document.annotation.get(SentencesAnnotation.class).get(dst.get(0)).get(TokensAnnotation.class);
    for(int i = 0 ; i < l.size() ; i++){
      for(int j = 0; j < mEnd.getCount(i); j++){
        golds.append("]");
      }
      for(int j = 0; j < mBegin.getCount(i); j++){
        golds.append("[");
      }
      golds.append(l.get(i).get(TextAnnotation.class));
      golds.append(" ");
    }
    logger.fine(golds.toString());

    logger.finer("\nMention:: --------------------------------------------------------");
    try {
      logger.finer(srcMention.dependency.toString());
    } catch (Exception e){} //throw new RuntimeException(e);}
    logger.finer("Parse:");
    logger.finer(formatPennTree(srcMention.contextParseTree));
    logger.finer("\nAntecedent:: -----------------------------------------------------");
    try {
      logger.finer(dstMention.dependency.toString());
    } catch (Exception e){} //throw new RuntimeException(e);}
    logger.finer("Parse:");
    logger.finer(formatPennTree(dstMention.contextParseTree));
  }
  /** For printing tree in a better format */
  public static String formatPennTree(Tree parseTree)	{
    String treeString = parseTree.pennString();
    treeString = treeString.replaceAll("\\[TextAnnotation=", "");
    treeString = treeString.replaceAll("(NamedEntityTag|Value|Index|PartOfSpeech)Annotation.+?\\)", ")");
    treeString = treeString.replaceAll("\\[.+?\\]", "");
    return treeString;
  }

  /** Print pass results */
  private static void printLogs(CorefCluster c1, CorefCluster c2, Mention m1,
      Mention m2, Document document, int sieveIndex) {
    HashMap<Mention, IntTuple> positions = document.positions;
    List<List<Mention>> orderedMentionsBySentence = document.getOrderedMentions();
    List<Pair<IntTuple, IntTuple>> goldLinks = document.getGoldLinks();

    IntTuple p1 = positions.get(m1);
    assert(p1 != null);
    IntTuple p2 = positions.get(m2);
    assert(p2 != null);

    int menDist = 0;
    for (int i = p2.get(0) ; i<= p1.get(0) ; i++){
      if(p1.get(0)==p2.get(0)) {
        menDist = p1.get(1)-p2.get(1);
        break;
      }
      if(i==p2.get(0)) {
        menDist += orderedMentionsBySentence.get(p2.get(0)).size()-p2.get(1);
        continue;
      }
      if(i==p1.get(0)) {
        menDist += p1.get(1);
        continue;
      }
      if(p2.get(0)<i && i < p1.get(0)) menDist += orderedMentionsBySentence.get(i).size();
    }
    String correct = (goldLinks.contains(new Pair<IntTuple, IntTuple>(p1,p2)))? "\tCorrect" : "\tIncorrect";
    logger.finest("\nsentence distance: "+(p1.get(0)-p2.get(0))+"\tmention distance: "+menDist + correct);

    if(!goldLinks.contains(new Pair<IntTuple,IntTuple>(p1,p2))){
      logger.finer("-------Incorrect merge in pass"+sieveIndex+"::--------------------");
      c1.printCorefCluster(logger);
      logger.finer("--------------------------------------------");
      c2.printCorefCluster(logger);
      logger.finer("--------------------------------------------");
    }
    logger.finer("antecedent: "+m2.spanToString()+"("+m2.mentionID+")\tmention: "+m1.spanToString()+"("+m1.mentionID+")\tsentDistance: "+Math.abs(m1.sentNum-m2.sentNum)+"\t"+correct+" Pass"+sieveIndex+":");
  }

  private static void printDiscourseStructure(Document document) {
    logger.finer("DISCOURSE STRUCTURE==============================");
    logger.finer("doc type: "+document.docType);
    int previousUtterIndex = -1;
    String previousSpeaker = "";
    StringBuilder sb = new StringBuilder();
    for(CoreMap s : document.annotation.get(SentencesAnnotation.class)) {
      for(CoreLabel l : s.get(TokensAnnotation.class)) {
        int utterIndex = l.get(UtteranceAnnotation.class);
        String speaker = l.get(SpeakerAnnotation.class);
        String word = l.get(TextAnnotation.class);
        if(previousUtterIndex!=utterIndex) {
          try {
            int previousSpeakerID = Integer.parseInt(previousSpeaker);
            logger.finer("\n<utter>: "+previousUtterIndex + " <speaker>: "+document.allPredictedMentions.get(previousSpeakerID).spanToString());
          } catch (Exception e) {
            logger.finer("\n<utter>: "+previousUtterIndex + " <speaker>: "+previousSpeaker);
          }

          logger.finer(sb.toString());
          sb.setLength(0);
          previousUtterIndex = utterIndex;
          previousSpeaker = speaker;
        }
        sb.append(" ").append(word);
      }
      sb.append("\n");
    }
    try {
      int previousSpeakerID = Integer.parseInt(previousSpeaker);
      logger.finer("\n<utter>: "+previousUtterIndex + " <speaker>: "+document.allPredictedMentions.get(previousSpeakerID).spanToString());
    } catch (Exception e) {
      logger.finer("\n<utter>: "+previousUtterIndex + " <speaker>: "+previousSpeaker);
    }
    logger.finer(sb.toString());
    logger.finer("END OF DISCOURSE STRUCTURE==============================");
  }

  /** Print average F1 of MUC, B^3, CEAF_E */
  private static void printFinalScore(String summary) {
    Pattern f1 = Pattern.compile("Coreference:.*F1: (.*)%");
    Matcher f1Matcher = f1.matcher(summary);
    double[] F1s = new double[5];
    int i = 0;
    while (f1Matcher.find()) {
      F1s[i++] = Double.parseDouble(f1Matcher.group(1));
    }
    logger.info("Final score ((muc+bcub+ceafe)/3) = "+(F1s[0]+F1s[1]+F1s[3])/3);
  }

  public static void printConllOutput(Document document, PrintWriter writer, boolean gold) {
    printConllOutput(document, writer, gold, false);
  }

  public static void printConllOutput(Document document, PrintWriter writer, boolean gold, boolean filterSingletons) {
    List<List<Mention>> orderedMentions;
    if(gold) orderedMentions = document.goldOrderedMentionsBySentence;
    else orderedMentions = document.predictedOrderedMentionsBySentence;
    if (filterSingletons) {
      orderedMentions = filterMentionsWithSingletonClusters(document, orderedMentions);
    }
    printConllOutput(document, writer, orderedMentions, gold);
  }

  public static void printConllOutput(Document document, PrintWriter writer, List<List<Mention>> orderedMentions, boolean gold)
  {
    Annotation anno = document.annotation;
    List<List<String[]>> conllDocSentences = document.conllDoc.sentenceWordLists;
    String docID = anno.get(DocIDAnnotation.class);
    StringBuilder sb = new StringBuilder();
    sb.append("#begin document ").append(docID).append("\n");
    List<CoreMap> sentences = anno.get(SentencesAnnotation.class);
    for(int sentNum = 0 ; sentNum < sentences.size() ; sentNum++){
      List<CoreLabel> sentence = sentences.get(sentNum).get(TokensAnnotation.class);
      List<String[]> conllSentence = conllDocSentences.get(sentNum);
      Map<Integer,Set<Mention>> mentionBeginOnly = new HashMap<Integer,Set<Mention>>();
      Map<Integer,Set<Mention>> mentionEndOnly = new HashMap<Integer,Set<Mention>>();
      Map<Integer,Set<Mention>> mentionBeginEnd = new HashMap<Integer,Set<Mention>>();

      for(int i=0 ; i<sentence.size(); i++){
        mentionBeginOnly.put(i, new LinkedHashSet<Mention>());
        mentionEndOnly.put(i, new LinkedHashSet<Mention>());
        mentionBeginEnd.put(i, new LinkedHashSet<Mention>());
      }

      for(Mention m : orderedMentions.get(sentNum)) {
        if(m.startIndex==m.endIndex-1) {
          mentionBeginEnd.get(m.startIndex).add(m);
        } else {
          mentionBeginOnly.get(m.startIndex).add(m);
          mentionEndOnly.get(m.endIndex-1).add(m);
        }
      }

      for(int i=0 ; i<sentence.size(); i++){
        StringBuilder sb2 = new StringBuilder();
        for(Mention m : mentionBeginOnly.get(i)){
          if (sb2.length() > 0) {
            sb2.append("|");
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append("(").append(corefClusterId);
        }
        for(Mention m : mentionBeginEnd.get(i)){
          if (sb2.length() > 0) {
            sb2.append("|");
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append("(").append(corefClusterId).append(")");
        }
        for(Mention m : mentionEndOnly.get(i)){
          if (sb2.length() > 0) {
            sb2.append("|");
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append(corefClusterId).append(")");
        }
        if(sb2.length() == 0) sb2.append("-");

        String[] columns = conllSentence.get(i);
        for(int j = 0 ; j < columns.length-1 ; j++){
          String column = columns[j];
          sb.append(column).append("\t");
        }
        sb.append(sb2).append("\n");
      }
      sb.append("\n");
    }

    sb.append("#end document").append("\n");
    //    sb.append("#end document ").append(docID).append("\n");

    writer.print(sb.toString());
    writer.flush();
  }

  /** Print raw document for analysis */
  private static void printRawDoc(Document document, boolean gold) throws FileNotFoundException {
    List<CoreMap> sentences = document.annotation.get(SentencesAnnotation.class);
    List<List<Mention>> allMentions;
    if(gold) allMentions = document.goldOrderedMentionsBySentence;
    else allMentions = document.predictedOrderedMentionsBySentence;
    //    String filename = document.annotation.get()

    StringBuilder doc = new StringBuilder();
    int previousOffset = 0;
    Counter<Integer> mentionCount = new ClassicCounter<Integer>();
    for(List<Mention> l : allMentions) {
      for(Mention m : l){
        mentionCount.incrementCount(m.goldCorefClusterID);
      }
    }

    for(int i = 0 ; i<sentences.size(); i++) {
      CoreMap sentence = sentences.get(i);
      List<Mention> mentions = allMentions.get(i);

      String[] tokens = sentence.get(TextAnnotation.class).split(" ");
      List<CoreLabel> t = sentence.get(TokensAnnotation.class);
      if(previousOffset+2 < t.get(0).get(CharacterOffsetBeginAnnotation.class)) {
        doc.append("\n");
      }
      previousOffset = t.get(t.size()-1).get(CharacterOffsetEndAnnotation.class);
      Counter<Integer> startCounts = new ClassicCounter<Integer>();
      Counter<Integer> endCounts = new ClassicCounter<Integer>();
      HashMap<Integer, Set<Mention>> endMentions = new HashMap<Integer, Set<Mention>>();
      for (Mention m : mentions) {
        startCounts.incrementCount(m.startIndex);
        endCounts.incrementCount(m.endIndex);
        if(!endMentions.containsKey(m.endIndex)) endMentions.put(m.endIndex, new HashSet<Mention>());
        endMentions.get(m.endIndex).add(m);
      }
      for (int j = 0 ; j < tokens.length; j++){
        if(endMentions.containsKey(j)) {
          for(Mention m : endMentions.get(j)){
            int corefChainId =  (gold)? m.goldCorefClusterID: m.corefClusterID;
            doc.append("]_").append(corefChainId);
          }
        }
        for (int k = 0 ; k < startCounts.getCount(j) ; k++) {
          char lastChar = (doc.length() > 0)? doc.charAt(doc.length()-1):' ';
          if (lastChar != '[') doc.append(" ");
          doc.append("[");
        }
        doc.append(" ");
        doc.append(tokens[j]);
      }
      if(endMentions.containsKey(tokens.length)) {
        for(Mention m : endMentions.get(tokens.length)){
          int corefChainId =  (gold)? m.goldCorefClusterID: m.corefClusterID;
          doc.append("]_").append(corefChainId); //append("_").append(m.mentionID);
        }
      }

      doc.append("\n");
    }
    logger.fine(document.annotation.get(DocIDAnnotation.class));
    if(gold) logger.fine("New DOC: (GOLD MENTIONS) ==================================================");
    else logger.fine("New DOC: (Predicted Mentions) ==================================================");
    logger.fine(doc.toString());
  }
  public static List<Pair<IntTuple, IntTuple>> getLinks(
      Map<Integer, CorefChain> result) {
    List<Pair<IntTuple, IntTuple>> links = new ArrayList<Pair<IntTuple, IntTuple>>();
    MentionComparator comparator = new MentionComparator();

    for(CorefChain c : result.values()) {
      List<CorefMention> s = c.getCorefMentions();
      for(CorefMention m1 : s){
        for(CorefMention m2 : s){
          if(comparator.compare(m1, m2)==1) links.add(new Pair<IntTuple, IntTuple>(m1.position, m2.position));
        }
      }
    }
    return links;
  }
}
