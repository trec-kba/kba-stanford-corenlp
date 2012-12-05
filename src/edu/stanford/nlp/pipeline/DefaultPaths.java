package edu.stanford.nlp.pipeline;

/**
 * Default model paths for StanfordCoreNLP
 * All these paths point to files distributed with the model jar file (stanford-corenlp-models-*.jar)
 */
public class DefaultPaths {

  public static final String DEFAULT_POS_MODEL = "edu/stanford/nlp/models/pos-tagger/wsj3t0-18-left3words/left3words-distsim-wsj-0-18.tagger";

  public static final String DEFAULT_PARSER_MODEL = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";

  public static final String DEFAULT_NER_THREECLASS_MODEL = "edu/stanford/nlp/models/ner/all.3class.distsim.crf.ser.gz";
  public static final String DEFAULT_NER_CONLL_MODEL = "edu/stanford/nlp/models/ner/conll.distsim.crf.ser.gz";
  public static final String DEFAULT_NER_MUC_MODEL = "edu/stanford/nlp/models/ner/muc.distsim.crf.ser.gz";

  public static final String DEFAULT_REGEXNER_RULES = "edu/stanford/nlp/models/regexner/type_map_clean";
  public static final String DEFAULT_GENDER_FIRST_NAMES = "edu/stanford/nlp/models/gender/first_name_map_small";

  public static final String DEFAULT_TRUECASE_MODEL = "edu/stanford/nlp/models/truecase/noUN.ser.gz";
  public static final String DEFAULT_TRUECASE_DISAMBIGUATION_LIST = "edu/stanford/nlp/models/truecase/MixDisambiguation.list";

  public static final String DEFAULT_DCOREF_ANIMATE = "edu/stanford/nlp/models/dcoref/animate.unigrams.txt";
  public static final String DEFAULT_DCOREF_DEMONYM = "edu/stanford/nlp/models/dcoref/demonyms.txt";
  public static final String DEFAULT_DCOREF_FEMALE = "edu/stanford/nlp/models/dcoref/female.unigrams.txt";
  public static final String DEFAULT_DCOREF_INANIMATE = "edu/stanford/nlp/models/dcoref/inanimate.unigrams.txt";
  public static final String DEFAULT_DCOREF_MALE = "edu/stanford/nlp/models/dcoref/male.unigrams.txt";
  public static final String DEFAULT_DCOREF_NEUTRAL = "edu/stanford/nlp/models/dcoref/neutral.unigrams.txt";
  public static final String DEFAULT_DCOREF_PLURAL = "edu/stanford/nlp/models/dcoref/plural.unigrams.txt";
  public static final String DEFAULT_DCOREF_SINGULAR = "edu/stanford/nlp/models/dcoref/singular.unigrams.txt";
  public static final String DEFAULT_DCOREF_STATES = "edu/stanford/nlp/models/dcoref/state-abbreviations.txt";

  public static final String DEFAULT_DCOREF_COUNTRIES = "edu/stanford/nlp/models/dcoref/countries";
  public static final String DEFAULT_DCOREF_STATES_AND_PROVINCES = "edu/stanford/nlp/models/dcoref/statesandprovinces";
  public static final String DEFAULT_DCOREF_GENDER_NUMBER = "edu/stanford/nlp/models/dcoref/gender.data.gz";
  public static final String DEFAULT_DCOREF_EXTRA_GENDER = "edu/stanford/nlp/models/dcoref/namegender.combine.txt";

  public static final String DEFAULT_NFL_ENTITY_MODEL = "edu/stanford/nlp/models/machinereading/nfl/nfl_entity_model.ser";
  public static final String DEFAULT_NFL_RELATION_MODEL = "edu/stanford/nlp/models/machinereading/nfl/nfl_relation_model.ser";
  public static final String DEFAULT_NFL_GAZETTEER = "edu/stanford/nlp/models/machinereading/nfl/NFLgazetteer.txt";


  private DefaultPaths() {
  }
  
}
