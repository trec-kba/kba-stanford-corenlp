package edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph;

import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
/**
*
*
* @author Johan Hall
*/
public interface Structure {
	/**
	 * Resets the structure.
	 * 
	 * @throws MaltChainedException
	 */
	public void clear() throws MaltChainedException;
}
