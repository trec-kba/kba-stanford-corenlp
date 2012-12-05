/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */
package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.WordTag;
import edu.stanford.nlp.tagger.common.TaggerConstants;
import edu.stanford.nlp.tagger.io.TaggedFileReader;
import edu.stanford.nlp.tagger.io.TaggedFileRecord;
import edu.stanford.nlp.trees.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;


/**
 * Reads tagged data from a file and creates a dictionary.
 * The tagged data has to be whitespace-separated items, with the word and
 * tag split off by a delimiter character, which is found as the last instance
 * of the delimiter character in the item.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class ReadDataTagged {

  private final List<TaggedFileRecord> fileRecords;
  private ArrayList<DataWordTag> v = new ArrayList<DataWordTag>();
  private int numElements = 0;
  private int totalSentences = 0;
  private int totalWords = 0;
  private final PairsHolder pairs;
  private final MaxentTagger maxentTagger;

  //TODO: make a class DataHolder that holds the dict, tags, pairs, etc, for tagger
  // and pass it around

  protected ReadDataTagged(TaggerConfig config, MaxentTagger maxentTagger, 
                           PairsHolder pairs) 
    throws IOException
  {
    this.maxentTagger = maxentTagger;
    this.pairs = pairs;
    fileRecords = TaggedFileRecord.createRecords(config, config.getFile());
    for (TaggedFileRecord record : fileRecords)
      loadFile(record.reader());
  }


  /** Frees the memory that is stored in this object by dropping the word-tag data.
   */
  void release() {
    v = null;
  }


  DataWordTag get(int index) {
    return v.get(index);
  }

  private void loadFile(TaggedFileReader reader) {
    System.err.println("Loading tagged words from " + reader.filename());

    ArrayList<String> words = new ArrayList<String>();
    ArrayList<String> tags = new ArrayList<String>();
    int numSentences = 0;
    int numWords = 0;
    int maxLen = Integer.MIN_VALUE;
    int minLen = Integer.MAX_VALUE;

    for (List<TaggedWord> sentence : reader) {
      for (TaggedWord tw : sentence) {
        if(tw != null) {
          words.add(tw.word());
          tags.add(tw.tag());
          if (!maxentTagger.tagTokens.containsKey(tw.tag())) {
            maxentTagger.tagTokens.put(tw.tag(), new HashSet<String>());
          }
          maxentTagger.tagTokens.get(tw.tag()).add(tw.word());
        }
      }
      maxLen = (sentence.size() > maxLen ? sentence.size() : maxLen);
      minLen = (sentence.size() < minLen ? sentence.size() : minLen);
      words.add(TaggerConstants.EOS_WORD);
      tags.add(TaggerConstants.EOS_TAG);
      numElements = numElements + sentence.size() + 1;
      // iterate over the words in the sentence
      for (int i = 0; i < sentence.size() + 1; i++) {
        History h = new History(totalWords + totalSentences, 
                                totalWords + totalSentences + sentence.size(), 
                                totalWords + totalSentences + i, 
                                pairs, maxentTagger.extractors);
        String tag = tags.get(i);
        String word = words.get(i);
        pairs.add(new WordTag(word,tag));
        int y = maxentTagger.tags.add(tag);
        DataWordTag dat = new DataWordTag(h, y, maxentTagger.tags);
        v.add(dat);
        maxentTagger.dict.add(word, tag);

      }
      totalSentences++;
      totalWords += sentence.size();
      numSentences++;
      numWords += sentence.size();
      words.clear();
      tags.clear();
      if ((numSentences % 100000) == 0) System.err.println("Read " + numSentences + " sentences, min " + minLen + " words, max " + maxLen + " words ... [still reading]");
    }

    System.err.println("Read " + numWords + " words from " + reader.filename() + " [done].");
    System.err.println("Read " + numSentences + " sentences, min " + minLen + " words, max " + maxLen + " words.");
  }


  /** Returns the number of tokens in the data read, which is the number of words
   *  plus one end sentence token per sentence.
   *  @return The number of tokens in the data
   */
  public int getSize() {
    return numElements;
  }
}
