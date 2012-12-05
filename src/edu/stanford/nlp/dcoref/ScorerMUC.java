package edu.stanford.nlp.dcoref;

import java.util.*;

public class ScorerMUC extends CorefScorer {

  public ScorerMUC(){
    super();
    scoreType = ScoreType.MUC;
  }
  
  @Override
  protected void calculateRecall(Document doc) {
    int rDen = 0;
    int rNum = 0;
    
    Map<Integer, Mention> predictedMentions = doc.allPredictedMentions;
    for(CorefCluster g : doc.goldCorefClusters.values()){
      if(g.corefMentions.size()==0) continue;
      rDen += g.corefMentions.size()-1;
      rNum += g.corefMentions.size();
      
      Set<CorefCluster> partitions = new HashSet<CorefCluster>();
      for (Mention goldMention : g.corefMentions){
        if(!predictedMentions.containsKey(goldMention.mentionID)) {  // twinless goldmention
          rNum--;
        } else {
          partitions.add(doc.corefClusters.get(predictedMentions.get(goldMention.mentionID).corefClusterID));
        }
      }
      rNum -= partitions.size();
    }
    assert(rDen == (doc.allGoldMentions.size()-doc.goldCorefClusters.values().size()));
    
    recallNumSum += rNum;
    recallDenSum += rDen;
  }
  
  @Override
  protected void calculatePrecision(Document doc) {
    int pDen = 0;
    int pNum = 0;    
    Map<Integer, Mention> goldMentions = doc.allGoldMentions;

    for(CorefCluster c : doc.corefClusters.values()){
      if(c.corefMentions.size()==0) continue;
      pDen += c.corefMentions.size()-1;
      pNum += c.corefMentions.size();
      Set<CorefCluster> partitions = new HashSet<CorefCluster>();
      for (Mention predictedMention : c.corefMentions){
        if(!goldMentions.containsKey(predictedMention.mentionID)) {  // twinless goldmention
          pNum--;
        } else {
          partitions.add(doc.goldCorefClusters.get(goldMentions.get(predictedMention.mentionID).goldCorefClusterID));
        }
      }
      pNum -= partitions.size();
    }
    assert(pDen == (doc.allPredictedMentions.size()-doc.corefClusters.values().size()));
    
    precisionDenSum += pDen;
    precisionNumSum += pNum;
  }
}
