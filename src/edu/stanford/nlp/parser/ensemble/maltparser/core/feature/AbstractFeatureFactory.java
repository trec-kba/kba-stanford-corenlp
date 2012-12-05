package edu.stanford.nlp.parser.ensemble.maltparser.core.feature;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.function.Function;

public interface AbstractFeatureFactory {
	public Function makeFunction(String subFunctionName) throws MaltChainedException;
}
