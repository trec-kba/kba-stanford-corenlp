package edu.stanford.nlp.time;

import java.util.Properties;

/**
 * Various options for using time expression extractor
 *
 * @author Angel Chang
 */
class Options {
  // Whether to mark time ranges like from 1991 to 1992 as one timex or leave it separate
  boolean markTimeRanges = false;
  // Heuristics for determining relative time
  // level 1 = no heuristics (default)
  // level 2 = basic heuristics taking into past tense
  // level 3 = more heuristics with since/until
  int teRelHeurLevel = 1;  // TODO: Rename to something better
  // Include nested time expressions
  boolean includeNested = false;
  // Convert times to ranges
  boolean includeRange = false;
  // TODO: Add default country for holidays and default time format
  // would want a per document default as well

  public Options()
  {
  }

  public Options(String name, Properties props)
  {
    includeRange = Boolean.parseBoolean(props.getProperty(name + ".includeRange",
          String.valueOf(includeRange)));
    markTimeRanges = Boolean.parseBoolean(props.getProperty(name + ".markTimeRanges",
          String.valueOf(markTimeRanges)));
    includeNested = Boolean.parseBoolean(props.getProperty(name + ".includeNested",
          String.valueOf(includeNested)));
    teRelHeurLevel = Integer.parseInt(props.getProperty(name + ".teRelHeurLevel",
          String.valueOf(teRelHeurLevel)));
  }
}
