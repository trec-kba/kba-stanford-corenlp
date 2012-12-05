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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.dcoref.SieveCoreferenceSystem.Semantics;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.BeginIndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.UtteranceAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ValueAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

/**
 * Generic mention extractor from a corpus.
 *
 * @author Jenny Finkel
 * @author Mihai Surdeanu
 * @author Karthik Raghunathan
 * @author Heeyoung Lee
 * @author Sudarshan Rangarajan
 */
public class MentionExtractor {

  protected HeadFinder headFinder;

  protected String currentDocumentID;

  protected Dictionaries dictionaries;
  protected Semantics semantics;

  public CorefMentionFinder mentionFinder;
  protected StanfordCoreNLP stanfordProcessor;

  /** The maximum mention ID: for preventing duplicated mention ID assignment */
  protected int maxID = -1;

  public static final boolean VERBOSE = false;

  public MentionExtractor(Dictionaries dict, Semantics semantics) {
    this.headFinder = new SemanticHeadFinder();
    this.dictionaries = dict;
    this.semantics = semantics;
    this.mentionFinder = new RuleBasedCorefMentionFinder();  // Default
  }

  public void setMentionFinder(CorefMentionFinder mentionFinder)
  {
    this.mentionFinder = mentionFinder;
  }

  /**
   * Extracts the info relevant for coref from the next document in the corpus
   * @return List of mentions found in each sentence ordered according to the tree traversal.
   * @throws ClassNotFoundException
   */
  public Document nextDoc() throws ClassNotFoundException { return null; }

  public Document arrange(
      Annotation anno,
      List<List<CoreLabel>> words,
      List<Tree> trees,
      List<List<Mention>> unorderedMentions) {
    return arrange(anno, words, trees, unorderedMentions, null, false);
  }

  private int getHeadIndex(Tree t) {
    Tree ht = t.headTerminal(headFinder);
    if(ht==null) return -1;  // temporary: a key which is matched to nothing
    CoreLabel l = (CoreLabel) ht.label();
    return l.get(IndexAnnotation.class);
  }
  private String treeToKey(Tree t){
    int idx = getHeadIndex(t);
    String key = Integer.toString(idx) + ":" + t.toString();
    return key;
  }

  public Document arrange(
      Annotation anno,
      List<List<CoreLabel>> words,
      List<Tree> trees,
      List<List<Mention>> unorderedMentions,
      List<List<Mention>> unorderedGoldMentions,
      boolean doMergeLabels) {
    List<List<Mention>> predictedOrderedMentionsBySentence = arrange(anno, words, trees, unorderedMentions, doMergeLabels);
    List<List<Mention>> goldOrderedMentionsBySentence = null;
    if(unorderedGoldMentions != null) {
      goldOrderedMentionsBySentence = arrange(anno, words, trees, unorderedGoldMentions, doMergeLabels);
    }
    return new Document(anno, predictedOrderedMentionsBySentence, goldOrderedMentionsBySentence, dictionaries);
  }

  /**
   * Post-processes the extracted mentions. Here we set the Mention fields required for coref and order mentions by tree-traversal order.
   * @param words List of words in each sentence, in textual order
   * @param trees List of trees, one per sentence
   * @param unorderedMentions List of unordered, unprocessed mentions
   *                 Each mention MUST have startIndex and endIndex set!
   *                 Optionally, if scoring is desired, mentions must have mentionID and originalRef set.
   *                 All the other Mention fields are set here.
   * @return List of mentions ordered according to the tree traversal
   */
  public List<List<Mention>> arrange(
      Annotation anno,
      List<List<CoreLabel>> words,
      List<Tree> trees,
      List<List<Mention>> unorderedMentions,
      boolean doMergeLabels) {

    List<List<Mention>> orderedMentionsBySentence = new ArrayList<List<Mention>>();

    //
    // traverse all sentences and process each individual one
    //
    for(int sent = 0; sent < words.size(); sent ++){
      List<CoreLabel> sentence = words.get(sent);
      Tree tree = trees.get(sent);
      List<Mention> mentions = unorderedMentions.get(sent);
      HashMap<String, List<Mention>> mentionsToTrees = new HashMap<String, List<Mention>>();

      // merge the parse tree of the entire sentence with the sentence words
      if(doMergeLabels) mergeLabels(tree, sentence);

      //
      // set the surface information and the syntactic info in each mention
      // startIndex and endIndex MUST be set before!
      //
      for(Mention mention: mentions){
        mention.contextParseTree = tree;
        mention.sentenceWords = sentence;
        mention.originalSpan = new ArrayList<CoreLabel>(mention.sentenceWords.subList(mention.startIndex, mention.endIndex));
        if(!((CoreLabel)tree.label()).has(BeginIndexAnnotation.class)) tree.indexSpans(0);
        if(mention.headWord==null) {
          Tree headTree = ((RuleBasedCorefMentionFinder) mentionFinder).findSyntacticHead(mention, tree, sentence);
          mention.headWord = (CoreLabel)headTree.label();
          mention.headIndex = mention.headWord.get(IndexAnnotation.class) - 1;
        }
        if(mention.mentionSubTree==null) {
          // mentionSubTree = highest NP that has the same head
          Tree headTree = tree.getLeaves().get(mention.headIndex);
          if (headTree == null) { throw new RuntimeException("Missing head tree for a mention!"); }
          Tree t = headTree;
          while ((t = t.parent(tree)) != null) {
            if (t.headTerminal(headFinder) == headTree && t.value().equals("NP")) {
              mention.mentionSubTree = t;
            } else if(mention.mentionSubTree != null){
              break;
            }
          }
          if (mention.mentionSubTree == null) {
            mention.mentionSubTree = headTree;
          }
        }

        List<Mention> mentionsForTree = mentionsToTrees.get(treeToKey(mention.mentionSubTree));
        if(mentionsForTree == null){
          mentionsForTree = new ArrayList<Mention>();
          mentionsToTrees.put(treeToKey(mention.mentionSubTree), mentionsForTree);
        }
        mentionsForTree.add(mention);

        // generates all fields required for coref, such as gender, number, etc.
        mention.process(dictionaries, semantics, this);
      }

      //
      // Order all mentions in tree-traversal order
      //
      List<Mention> orderedMentions = new ArrayList<Mention>();
      orderedMentionsBySentence.add(orderedMentions);

      // extract all mentions in tree traversal order (alternative: tree.postOrderNodeList())
      for (Tree t : tree.preOrderNodeList()) {
        List<Mention> lm = mentionsToTrees.get(treeToKey(t));
        if(lm != null){
          for(Mention m: lm){
            orderedMentions.add(m);
          }
        }
      }

      //
      // find appositions, predicate nominatives, relative pronouns in this sentence
      //
      findSyntacticRelations(tree, orderedMentions);
    }
    return orderedMentionsBySentence;
  }

  /**
   * Sets the label of the leaf nodes to be the CoreLabels in the given sentence
   * The original value() of the Tree nodes is preserved
   */
  public static void mergeLabels(Tree tree, List<CoreLabel> sentence) {
    int idx = 0;
    for (Tree t : tree.getLeaves()) {
      CoreLabel cl = sentence.get(idx ++);
      String value = t.value();
      cl.set(ValueAnnotation.class, value);
      t.setLabel(cl);
    }
    tree.indexLeaves();
  }

  static boolean inside(int i, Mention m) {
    return (i >= m.startIndex && i < m.endIndex);
  }

  /** Find syntactic relations (e.g., appositives) in a sentence */
  private void findSyntacticRelations(Tree tree, List<Mention> orderedMentions) {
    Set<Pair<Integer, Integer>> appos = new HashSet<Pair<Integer, Integer>>();
    findAppositions(tree, appos);
    markMentionRelation(orderedMentions, appos, "APPOSITION");

    Set<Pair<Integer, Integer>> preNomi = new HashSet<Pair<Integer, Integer>>();
    findPredicateNominatives(tree, preNomi);
    markMentionRelation(orderedMentions, preNomi, "PREDICATE_NOMINATIVE");

    Set<Pair<Integer, Integer>> relativePronounPairs = new HashSet<Pair<Integer, Integer>>();
    findRelativePronouns(tree, relativePronounPairs);
    markMentionRelation(orderedMentions, relativePronounPairs, "RELATIVE_PRONOUN");
  }

  /** Find syntactic pattern in a sentence by tregex */
  private void findTreePattern(Tree tree, String pattern, Set<Pair<Integer, Integer>> foundPairs) {
    try {
      TregexPattern tgrepPattern = TregexPattern.compile(pattern);
      TregexMatcher m = tgrepPattern.matcher(tree);
      while (m.find()) {
        Tree t = m.getMatch();
        Tree np1 = m.getNode("m1");
        Tree np2 = m.getNode("m2");
        Tree np3 = null;
        if(pattern.contains("m3")) np3 = m.getNode("m3");
        addFoundPair(np1, np2, t, foundPairs);
        if(np3!=null) addFoundPair(np2, np3, t, foundPairs);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(0);
    }
  }

  private void addFoundPair(Tree np1, Tree np2, Tree t,
      Set<Pair<Integer, Integer>> foundPairs) {
    Tree head1 = np1.headTerminal(headFinder);
    Tree head2 = np2.headTerminal(headFinder);
    int h1 = ((CoreMap) head1.label()).get(IndexAnnotation.class) - 1;
    int h2 = ((CoreMap) head2.label()).get(IndexAnnotation.class) - 1;
    Pair<Integer, Integer> p = new Pair<Integer, Integer>(h1, h2);
    foundPairs.add(p);
  }

  private void findAppositions(Tree tree, Set<Pair<Integer, Integer>> appos) {
    String appositionPattern = "NP=m1 < (NP=m2 $.. (/,/ $.. NP=m3))";
    String appositionPattern2 = "NP=m1 < (NP=m2 $.. (/,/ $.. (SBAR < (WHNP < WP|WDT=m3))))";
    String appositionPattern3 = "/^NP(?:-TMP|-ADV)?$/=m1 < (NP=m2 $- /^,$/ $-- NP=m3 !$ CC|CONJP)";
    String appositionPattern4 = "/^NP(?:-TMP|-ADV)?$/=m1 < (PRN=m2 < (NP < /^NNS?|CD$/ $-- /^-LRB-$/ $+ /^-RRB-$/))";
    findTreePattern(tree, appositionPattern, appos);
    findTreePattern(tree, appositionPattern2, appos);
    findTreePattern(tree, appositionPattern3, appos);
    findTreePattern(tree, appositionPattern4, appos);
  }
  private void findPredicateNominatives(Tree tree, Set<Pair<Integer, Integer>> preNomi) {
    String predicateNominativePattern = "S < (NP=m1 $.. (VP < ((/VB/ < /^(am|are|is|was|were|'m|'re|'s|be)$/) $.. NP=m2)))";
    String predicateNominativePattern2 = "S < (NP=m1 $.. (VP < (VP < ((/VB/ < /^(be|been|being)$/) $.. NP=m2))))";
    //    String predicateNominativePattern2 = "NP=m1 $.. (VP < ((/VB/ < /^(am|are|is|was|were|'m|'re|'s|be)$/) $.. NP=m2))";
    findTreePattern(tree, predicateNominativePattern, preNomi);
    findTreePattern(tree, predicateNominativePattern2, preNomi);
  }
  private void findRelativePronouns(Tree tree, Set<Pair<Integer, Integer>> relativePronounPairs) {
    String relativePronounPattern = "NP < (NP=m1 $.. (SBAR < (WHNP < WP|WDT=m2)))";
    findTreePattern(tree, relativePronounPattern, relativePronounPairs);
  }
  private static void markMentionRelation(List<Mention> orderedMentions, Set<Pair<Integer, Integer>> foundPairs, String flag) {
    for(Mention m1 : orderedMentions){
      for(Mention m2 : orderedMentions){
        for(Pair<Integer, Integer> foundPair: foundPairs){
          if((foundPair.first == m1.headIndex && foundPair.second == m2.headIndex)){
            if(flag.equals("APPOSITION")) m2.addApposition(m1);
            else if(flag.equals("PREDICATE_NOMINATIVE")) m2.addPredicateNominatives(m1);
            else if(flag.equals("RELATIVE_PRONOUN")) m2.addRelativePronoun(m1);
            else throw new RuntimeException("check flag in markMentionRelation (dcoref/MentionExtractor.java)");
          }
        }
      }
    }
  }
  /**
   * Finds the tree the matches this span exactly
   * @param tree Leaves must be indexed!
   * @param first First element in the span (first position has offset 1)
   * @param last Last element included in the span (first position has offset 1)
   */
  public static Tree findExactMatch(Tree tree, int first, int last) {
    List<Tree> leaves = tree.getLeaves();
    int thisFirst = ((CoreMap) leaves.get(0).label()).get(IndexAnnotation.class);
    int thisLast = ((CoreMap) leaves.get(leaves.size() - 1).label()).get(IndexAnnotation.class);
    if(thisFirst == first && thisLast == last) {
      return tree;
    } else {
      Tree [] kids = tree.children();
      for(Tree k: kids){
        Tree t = findExactMatch(k, first, last);
        if(t != null) return t;
      }
    }
    return null;
  }

  /** Load Stanford Processor: skip unnecessary annotator */
  protected StanfordCoreNLP loadStanfordProcessor(Properties props) {
    boolean replicateCoNLL = Boolean.parseBoolean(props.getProperty(Constants.REPLICATECONLL_PROP, "false"));

    Properties pipelineProps = new Properties(props);
    StringBuilder annoSb = new StringBuilder("");
    if (!Constants.USE_GOLD_POS && !replicateCoNLL)  {
      annoSb.append("pos, lemma");
    } else {
      annoSb.append("lemma");
    }
    if(Constants.USE_TRUECASE) {
      annoSb.append(", truecase");
    }
    if (!Constants.USE_GOLD_NE && !replicateCoNLL)  {
      annoSb.append(", ner");
    }
    if (!Constants.USE_GOLD_PARSES && !replicateCoNLL)  {
      annoSb.append(", parse");
    }
    String annoStr = annoSb.toString();
    SieveCoreferenceSystem.logger.info("Ignoring specified annotators, using annotators=" + annoStr);
    pipelineProps.put("annotators", annoStr);
    return new StanfordCoreNLP(pipelineProps, false);
  }

  public static void initializeUtterance(List<CoreLabel> tokens) {
    for(CoreLabel l : tokens){
      l.set(UtteranceAnnotation.class, 0);
    }
  }
}
