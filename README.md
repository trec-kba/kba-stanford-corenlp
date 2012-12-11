kba-stanford-corenlp
====================

Wrappers for generating one-word-per-line output representing all the goodies from Stanford CoreNLP, so we can include it in the KBA stream corpus.

## Dependency ##

This wrapper supports a varity version of CoreNLP. We use the version version 1.3.4 for testing.

## Installation ##

After checking out the repository, you need to get all dependencies by run the lib/get.sh script:

    $ cd lib
    $ sh get.sh

This script will download a lib.zip file, and unzip it to get all .jar files we need.

After that, you can compile the code using

    $ ant

### NER, Parsing, and Coref

To get the .jar file for NER (including parsing and coref), run

    $ ant ner

This will produce a .jar file called runNER.jar.

    $ java -jar runNER.jar <INPUT> <OUTPUT>


## NER and Parsing ##

The file src/nlp/runNER.java takes as input a corpus in format

    <FILENAME DOCUMENTID>
    content...
    </FILENAME>
    ...
    <FILENAME DOCUMENTID>
    content...
    </FILENAME>

and output the one-token-per-line TSV format as

    <FILENAME id="DOCUMENTID">
    <SENT id="SENT-NUM-IN-DOC">
    TOKEN-NUM-IN-SENTENCE    TOKEN    BEGIN_OFFSET:END_OFFSET    POS    NER    LEMMA    DEP-PATH-TO-PARENT    PARENT-ID    COREF-CLUSTER-ID
    ...
    </SENT>
    </FILENAME>

You can see the input.txt and ner-output.txt examples in the test/ folder.