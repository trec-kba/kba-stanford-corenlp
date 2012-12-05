package edu.stanford.nlp.parser.ensemble.maltparser.core.feature.spec.reader;

import java.net.URL;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.spec.SpecificationModels;
/**
*
*
* @author Johan Hall
*/
public interface FeatureSpecReader {
	public void load(URL specModelURL, SpecificationModels featureSpecModels) throws MaltChainedException;
}
