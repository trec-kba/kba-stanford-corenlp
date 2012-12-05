package edu.stanford.nlp.parser.lexparser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatures;
import edu.stanford.nlp.parser.lexparser.Options.LexOptions;

/**
 * A lexicon that accommodates a separation between the surface form and
 * inflectional features, which are encoded in the POS tags.
 * <p>
 * TODO: Could do smoothing during training so that each word is counted with its base
 * category.
 * 
 * @author Spence Green
 *
 */
public class FactoredLexicon extends BaseLexicon {

  private static final long serialVersionUID = 8496467161563992530L;

  private static final boolean DEBUG = false;
  
  private MorphoFeatureSpecification morphoSpec;
  
  public FactoredLexicon(MorphoFeatureSpecification morphoSpec) {
    super();
    this.morphoSpec = morphoSpec;
  }

  public FactoredLexicon(LexOptions op, MorphoFeatureSpecification morphoSpec) {
    super(op);
    this.morphoSpec = morphoSpec;
  }

  
  /**
   * 
   */
  @Override
  public Iterator<IntTaggedWord> ruleIteratorByWord(int word, int loc, String featureSpec) {
    final MorphoFeatures feats = morphoSpec.strToFeatures(featureSpec);
    List<IntTaggedWord> wordTaggings = new ArrayList<IntTaggedWord>();
    
    if(DEBUG)
      System.err.println("Taggings for word: " + (String) wordNumberer.object(word));
    
    if(isKnown(word)) {
        wordTaggings = rulesForWord(word,feats);
    } else if(DEBUG) {
      System.err.println("Unknown word!");
    }
       
    // Unknown or some new word/feature combination.
    // Try open class tag sets
    if(wordTaggings.size() == 0){
      if(DEBUG)
        System.err.println("Adding UW tags:");
      wordTaggings = rulesForWord(wordNumberer.number(UNKNOWN_WORD),feats);

      if(wordTaggings.size() == 0) {  
        System.err.printf("%s: Unseen word/feature combination: %s %s%n",this.getClass().getName(),(String) wordNumberer.object(word), feats.toString());
        wordTaggings = new ArrayList<IntTaggedWord>(40);
        for (IntTaggedWord iTW : rulesWithWord[wordNumberer.number(UNKNOWN_WORD)]) {
          wordTaggings.add(new IntTaggedWord(word, iTW.tag));
        }
      }
    }
    
    if(wordTaggings.size() == 0)
      System.err.printf("%s: No lexical insertion rules for (%s): %s%n", this.getClass().getName(),(String) wordNumberer().object(word),feats.toString());

    if(DEBUG)
      System.err.println();
    
    return wordTaggings.iterator();
  }
  
  //TODO: This currently only requires one feature match. That kind of stinks
  //Maybe turn off flexiTag?
  private List<IntTaggedWord> rulesForWord(int word, MorphoFeatures wordFeats) {
    List<IntTaggedWord> tagRules = new ArrayList<IntTaggedWord>();
    for (IntTaggedWord iTW : rulesWithWord[word]) {
      String tagStr = (String) tagNumberer().object(iTW.tag());
      MorphoFeatures tagFeats = wordFeats.fromTagString(tagStr);
      int nMatches = wordFeats.numFeatureMatches(tagFeats);
      
      if(DEBUG)
        System.err.printf("match: %s\t%s\t%s\t%d\t%b%n", tagStr,tagFeats.toString(),wordFeats.toString(),nMatches,flexiTag);
      
      if(flexiTag && nMatches > 0) {//Soft feature match
        if(DEBUG) System.err.println(tagStr);
        tagRules.add(iTW);
      
      } else if(tagFeats.numActiveFeatures() == wordFeats.numActiveFeatures() && wordFeats.numActiveFeatures() == nMatches) {//Strict feature match
        if(DEBUG) System.err.println(tagStr);
        tagRules.add(iTW);
      
      }
    }
        
    return tagRules;
  }
  
}
