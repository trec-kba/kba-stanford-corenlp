//
// StanfordCoreNLP -- a suite of NLP tools
// Copyright (c) 2009-2010 The Board of Trustees of
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import edu.stanford.nlp.dcoref.Dictionaries.Animacy;
import edu.stanford.nlp.dcoref.Dictionaries.Gender;
import edu.stanford.nlp.dcoref.Dictionaries.MentionType;
import edu.stanford.nlp.dcoref.Dictionaries.Number;
import edu.stanford.nlp.ling.CorefCoreAnnotations.CorefClusterIdAnnotation;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.IntTuple;

/**
 * Output of coref system.  Each CorefChain represents a set of
 * entries in the text which should all correspond to the same actual
 * entity.  There is a representative mention, which stores the best
 * mention of an entity, and then there is a sequence of other
 * mentions which connect to that mention.
 * 
 * @author Heeyoung Lee
 */
public class CorefChain implements Serializable {

  private final int chainID;
  private final List<CorefMention> mentions;
  private final HashMap<IntPair, CorefMention> mentionMap;

  /** The most representative mention in this cluster */
  private CorefMention representative = null;

  @Override
  public boolean equals(Object aThat) {
    if (this == aThat)
      return true;
    if (!(aThat instanceof CorefChain))
      return false;
    CorefChain that = (CorefChain) aThat;
    if (chainID != that.chainID)
      return false;
    if (!mentions.equals(that.mentions))
      return false;
    if ((representative == null && that.representative != null) ||
        (representative != null && that.representative == null) ||
        (!representative.equals(that.representative))) {
      return false;
    }
    // mentionMap is another view of mentions, so no need to compare
    // that once we've compared mentions
    return true;
  }

  @Override
  public int hashCode() {
    return mentions.hashCode();
  }

  /** get List of CorefMentions */
  public List<CorefMention> getCorefMentions() { return mentions; }

  /** get CorefMention by position (sentence number, headIndex) */
  public CorefMention getMention(IntPair position) { return mentionMap.get(position); }

  /** get CorefMention by position */
  public CorefMention getMention(int sentenceNumber, int headIndex) {
    return mentionMap.get(new IntPair(sentenceNumber, headIndex));
  }

  /** Return the most representative mention in the chain.
   *  Proper mention and a mention with more pre-modifiers are preferred.
   */
  public CorefMention getRepresentativeMention() { return representative; }
  public int getChainID() { return chainID; }

  /** Mention for coref output.  This is one instance of the entity
   * referred to by a given CorefChain.  */
  public static class CorefMention implements Serializable {
    public final MentionType mentionType;
    public final Number number;
    public final Gender gender;
    public final Animacy animacy;

    /**
     * Starting word number, indexed from 1
     */
    public final int startIndex;
    /**
     * One past the end word number, indexed from 1
     */
    public final int endIndex;
    /**
     * Head word of the mention
     */
    public final int headIndex;
    public final int corefClusterID;
    public final int mentionID;
    /**
     * Sentence number in the document containing this mention,
     * indexed from 1.
     */
    public final int sentNum;
    public final IntTuple position;
    public final String mentionSpan;

    public CorefMention(Mention m, IntTuple pos){
      mentionType = m.mentionType;
      number = m.number;
      gender = m.gender;
      animacy = m.animacy;
      startIndex = m.startIndex + 1;
      endIndex = m.endIndex + 1;
      headIndex = m.headIndex + 1;
      corefClusterID = m.corefClusterID;
      sentNum = m.sentNum + 1;
      mentionID = m.mentionID;
      mentionSpan = m.spanToString();

      // index starts from 1
      position = new IntTuple(2);
      position.set(0, pos.get(0)+1);
      position.set(1, pos.get(1)+1);

      m.headWord.set(CorefClusterIdAnnotation.class, corefClusterID);
    }

    @Override
    public boolean equals(Object aThat) {
      if (this == aThat)
        return true;
      if (!(aThat instanceof CorefMention))
        return true;
      CorefMention that = (CorefMention) aThat;
      if (mentionType != that.mentionType)
        return false;
      if (number != that.number)
        return false;
      if (gender != that.gender)
        return false;
      if (animacy != that.animacy)
        return false;
      if (startIndex != that.startIndex)
        return false;
      if (endIndex != that.endIndex)
        return false;
      if (headIndex != that.headIndex)
        return false;
      if (corefClusterID != that.corefClusterID)
        return false;
      if (mentionID != that.mentionID)
        return false;
      if (sentNum != that.sentNum)
        return false;
      if (!position.equals(that.position))
        return false;
      // we ignore MentionSpan as it is constructed from the tokens
      // the mention is a span of, so if we know those spans are the
      // same, we should be able to ignore the actual text
      return true;
    }

    @Override
    public int hashCode() {
      return position.hashCode();
    }

    @Override
    public String toString(){
      StringBuilder s = new StringBuilder();
      s.append("\"").append(mentionSpan).append("\"").append(" in sentence ").append(sentNum);
      return s.toString();
      //      return "(sentence:" + sentNum + ", startIndex:" + startIndex + "-endIndex:" + endIndex + ")";
    }
    private boolean moreRepresentativeThan(CorefMention m){
      if(m==null) return true;
      if(mentionType!=m.mentionType) {
        if((mentionType==MentionType.PROPER && m.mentionType!=MentionType.PROPER)
            || (mentionType==MentionType.NOMINAL && m.mentionType==MentionType.PRONOMINAL)) return true;
        else return false;
      } else {
        if(headIndex-startIndex > m.headIndex - m.startIndex) return true;
        else if (sentNum < m.sentNum || (sentNum==m.sentNum && headIndex < m.headIndex)) return true;
        else return false;
      }
    }

    private static final long serialVersionUID = 3657691243504173L;
  }

  protected static class MentionComparator implements Comparator<CorefMention> {
    public int compare(CorefMention m1, CorefMention m2) {
      if(m1.sentNum < m2.sentNum) return -1;
      else if(m1.sentNum > m2.sentNum) return 1;
      else{
        if(m1.startIndex < m2.startIndex) return -1;
        else if(m1.startIndex > m2.startIndex) return 1;
        else {
          if(m1.endIndex > m2.endIndex) return -1;
          else if(m1.endIndex < m2.endIndex) return 1;
          else return 0;
        }
      }
    }
  }
  public CorefChain(CorefCluster c, HashMap<Mention, IntTuple> positions){
    chainID = c.clusterID;
    mentions = new ArrayList<CorefMention>();
    mentionMap = new HashMap<IntPair, CorefMention>();
    for (Mention m : c.getCorefMentions()) {
      CorefMention men = new CorefMention(m, positions.get(m));
      mentions.add(men);
      mentionMap.put(new IntPair(men.sentNum, men.headIndex), men);
      if(men.moreRepresentativeThan(representative)) representative = men;
    }
    Collections.sort(mentions, new MentionComparator());
  }
  public String toString(){
    return "CHAIN"+this.chainID+ "-" +mentions.toString();
  }

  private static final long serialVersionUID = 3657691243506528L;

}
