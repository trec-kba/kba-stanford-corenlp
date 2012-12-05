package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.dcoref.Mention;
import edu.stanford.nlp.dcoref.MentionExtractor;
import edu.stanford.nlp.dcoref.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.dcoref.SieveCoreferenceSystem;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CorefCoreAnnotations.CorefClusterAnnotation;
import edu.stanford.nlp.ling.CorefCoreAnnotations.CorefGraphAnnotation;
import edu.stanford.nlp.ling.CyclicCoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntTuple;
import edu.stanford.nlp.util.Pair;

/**
 * Implements the Annotator for the new deterministic coreference resolution system.
 * This requires: ParsePLAnnotation and WordsPLAnnotation, where each word must have the POS tag and NER label set.
 * In other words, this depends on: POSTaggerAnnotator, OldNERCombinerAnnotator (or equivalent), and ParserAnnotator.
 *
 * @author Mihai Surdeanu, based on the CorefAnnotator written by Marie-Catherine de Marneffe
 */

public class DeterministicCorefAnnotator implements Annotator {

  private static final boolean VERBOSE = false;

  private final MentionExtractor mentionExtractor;
  private final SieveCoreferenceSystem corefSystem;

  // for backward compatibility
  private final boolean OLD_FORMAT;

  public DeterministicCorefAnnotator(Properties props) {
    try {
      corefSystem = new SieveCoreferenceSystem(props);
      mentionExtractor = new MentionExtractor(corefSystem.dictionaries(), corefSystem.semantics());
      OLD_FORMAT = Boolean.parseBoolean(props.getProperty("oldCorefFormat", "false"));
    } catch (Exception e) {
      System.err.println("ERROR: cannot create DeterministicCorefAnnotator!");
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public void annotate(Annotation annotation){
    List<Tree> trees = new ArrayList<Tree>();
    List<List<CoreLabel>> sentences = new ArrayList<List<CoreLabel>>();
    Annotation anno = new Annotation(annotation);

    // extract trees and sentence words
    // we are only supporting the new annotation standard for this Annotator!
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      int sentNum = 0;
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        CoreMap s = anno.get(CoreAnnotations.SentencesAnnotation.class).get(sentNum++);
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        // deep copy of the sentence. must do this because the Annotation tags are different!
        sentences.add(tokens);
        s.set(CoreAnnotations.TokensAnnotation.class, tokens);
        // deep copy of the tree. These are modified inside coref!
        Tree tree = sentence.get(TreeAnnotation.class);
        Tree treeCopy = tree.treeSkeletonCopy();
        trees.add(treeCopy);
        s.set(TreeAnnotation.class, treeCopy);
        // merge the new CoreLabels with the tree leaves
        MentionExtractor.mergeLabels(treeCopy, tokens);
        MentionExtractor.initializeUtterance(tokens);
      }
    } else {
      System.err.println("ERROR: this coreference resolution system requires SentencesAnnotation!");
      return;
    }

    // extract all possible mentions
    RuleBasedCorefMentionFinder finder = new RuleBasedCorefMentionFinder();
    List<List<Mention>> allUnprocessedMentions = finder.extractPredictedMentions(anno, 0, corefSystem.dictionaries());

    // add the relevant info to mentions and order them for coref
    Document document = mentionExtractor.arrange(annotation, sentences, trees, allUnprocessedMentions);
    List<List<Mention>> orderedMentions = document.getOrderedMentions();
    if(VERBOSE){
      for(int i = 0; i < orderedMentions.size(); i ++){
        System.err.printf("Mentions in sentence #%d:\n", i);
        for(int j = 0; j < orderedMentions.get(i).size(); j ++){
          System.err.println("\tMention #" + j + ": " + orderedMentions.get(i).get(j).spanToString());
        }
      }
    }

    Map<Integer, CorefChain> result = corefSystem.coref(document);
    annotation.set(CorefChainAnnotation.class, result);

    // for backward compatibility
    if(OLD_FORMAT) {
      List<Pair<IntTuple, IntTuple>> links = SieveCoreferenceSystem.getLinks(result);

      if(VERBOSE){
        System.err.printf("Found %d coreference links:\n", links.size());
        for(Pair<IntTuple, IntTuple> link: links){
          System.err.printf("LINK (%d, %d) -> (%d, %d)\n", link.first.get(0), link.first.get(1), link.second.get(0), link.second.get(1));
        }
      }

      //
      // save the coref output as CorefGraphAnnotation
      //
      List<List<CoreLabel>> sents = new ArrayList<List<CoreLabel>>();
      for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        sents.add(tokens);
      }
      // this graph is stored in CorefGraphAnnotation -- the raw links found by the coref system
      List<Pair<IntTuple, IntTuple>> graph = new ArrayList<Pair<IntTuple,IntTuple>>();

      for(Pair<IntTuple, IntTuple> link: links){
        //
        // Note: all offsets in the graph start at 1 (not at 0!)
        //       we do this for consistency reasons, as indices for syntactic dependencies start at 1
        //
        int srcSent = link.first.get(0);
        int srcTok = orderedMentions.get(srcSent - 1).get(link.first.get(1)-1).headIndex + 1;
        int dstSent = link.second.get(0);
        int dstTok = orderedMentions.get(dstSent - 1).get(link.second.get(1)-1).headIndex + 1;
        IntTuple dst = new IntTuple(2);
        dst.set(0, dstSent);
        dst.set(1, dstTok);
        IntTuple src = new IntTuple(2);
        src.set(0, srcSent);
        src.set(1, srcTok);
        graph.add(new Pair<IntTuple, IntTuple>(src, dst));
      }
      annotation.set(CorefGraphAnnotation.class, graph);

      for (CorefChain corefChain : result.values()) {
        if(corefChain.getCorefMentions().size() < 2) continue;
        Set<CoreLabel> coreferentTokens = new HashSet<CoreLabel>();
        Set<CoreLabel> cyclicCoreferentTokens = new HashSet<CoreLabel>();
        for (CorefMention mention : corefChain.getCorefMentions()) {
          CoreMap sentence = annotation.get(SentencesAnnotation.class).get(mention.sentNum - 1);
          CoreLabel token = sentence.get(TokensAnnotation.class).get(mention.headIndex - 1);

          // this stuff is so things will mostly work without us replacing all
          // tokens with CyclicCoreLabels whenever coref is run (which maybe
          // wouldn't be a terrible thing?)
          coreferentTokens.add(token);
          cyclicCoreferentTokens.add(new CyclicCoreLabel(token));
        }
        for (CoreLabel token : coreferentTokens) {
          token.set(CorefClusterAnnotation.class, cyclicCoreferentTokens);
        }
        for (CoreLabel token : cyclicCoreferentTokens) {
          token.set(CorefClusterAnnotation.class, cyclicCoreferentTokens);
        }
      }
    }
  }
}
