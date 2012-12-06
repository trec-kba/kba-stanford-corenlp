kba-stanford-corenlp
====================

Wrappers for generating one-word-per-line output representing all the goodies from Stanford CoreNLP, so we can include it in the KBA stream corpus.

====Dependency====

This wrapper supports a varity version of CoreNLP. We use the version version 1.3.4 for testing.

====NER and Parsing====

The file src/nlp/runNER.java takes as input a corpus in format

  <FILENAME DOCUMENTID>
  content...
  </FILENAME>
  ...
  <FILENAME DOCUMENTID>
  content...
  </FILENAME>

and output the one-token-per-line TSV format as

  <SENT id="SENTID">
  WORDID    WORD    POS    NER    LEMMA    DEP-PATH-TO-PARENT    PARENT-ID    COREF-CLUSTER-ID
  ..
  </SENT>

You can see the input.txt and ner-output.txt examples in the test/ folder.