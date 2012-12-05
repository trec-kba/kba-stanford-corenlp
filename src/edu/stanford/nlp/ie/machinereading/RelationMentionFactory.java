package edu.stanford.nlp.ie.machinereading;

import java.io.Serializable;
import java.util.List;

import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;

public class RelationMentionFactory implements Serializable {
  private static final long serialVersionUID = -662846276208839290L;

  /**
   * Always use this method to construct RelationMentions
   * Other factories that inherit from this (e.g., NFLRelationFactory) may override this
   * @param objectId
   * @param sentence
   * @param extentSpan
   * @param headSpan
   * @param type
   * @param subtype
   * @param mentionType
   * @return
   */
  public RelationMention constructRelationMention(
      String objectId, 
      CoreMap sentence,
      Span span,
      String type,
      String subtype,
      List<ExtractionObject> args,
      Counter<String> probs) {
    RelationMention relation = new RelationMention(
        objectId,
        sentence,
        span,
        type,
        subtype,
        args);
    relation.setTypeProbabilities(probs);
    return relation;
  }
}
