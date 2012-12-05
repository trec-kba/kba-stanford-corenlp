package edu.stanford.nlp.parser.ensemble.maltparser.core.helper;

import org.apache.log4j.Logger;

import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.io.dataformat.ColumnDescription;
import edu.stanford.nlp.parser.ensemble.maltparser.core.options.OptionManager;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.SymbolTableHandler;
/**
*
*
* @author Johan Hall
*/
public class Malt04 {
	public static void loadAllMalt04Tagset(OptionManager om, int containerIndex, SymbolTableHandler symbolTableHandler, Logger logger) throws MaltChainedException {
		String malt04Posset = om.getOptionValue(containerIndex, "malt0.4", "posset").toString();
		String malt04Cposset = om.getOptionValue(containerIndex, "malt0.4", "cposset").toString();
		String malt04Depset = om.getOptionValue(containerIndex, "malt0.4", "depset").toString();
		String nullValueStrategy = om.getOptionValue(containerIndex, "singlemalt", "null_value").toString();
		String rootLabels = om.getOptionValue(containerIndex, "graph", "root_label").toString();
		String inputCharSet = om.getOptionValue(containerIndex, "input", "charset").toString();
		loadMalt04Posset(malt04Posset, inputCharSet, nullValueStrategy, symbolTableHandler, logger);
		loadMalt04Cposset(malt04Cposset, inputCharSet, nullValueStrategy, symbolTableHandler, logger);
		loadMalt04Depset(malt04Depset, inputCharSet, nullValueStrategy, rootLabels, symbolTableHandler, logger);
	}
	
	public static void loadMalt04Posset(String fileName, String charSet, String nullValueStrategy, SymbolTableHandler symbolTableHandler, Logger logger) throws MaltChainedException {
		if (!fileName.toString().equalsIgnoreCase("")) {
			if (logger.isInfoEnabled()) {
				logger.info("Loading part-of-speech tagset '"+fileName+"'...\n");
			}
			symbolTableHandler.loadTagset(fileName, "POSTAG", charSet, ColumnDescription.INPUT, nullValueStrategy);
		}
	}
	
	public static void loadMalt04Cposset(String fileName, String charSet, String nullValueStrategy, SymbolTableHandler symbolTableHandler, Logger logger) throws MaltChainedException {
		if (!fileName.equalsIgnoreCase("")) {
			if (logger.isInfoEnabled()) {
				logger.info("Loading coarse-grained part-of-speech tagset '"+fileName+"'...\n");
			}
			symbolTableHandler.loadTagset(fileName, "CPOSTAG", charSet, ColumnDescription.INPUT, nullValueStrategy);
		}
	}
	
	public static void loadMalt04Depset(String fileName, String charSet, String nullValueStrategy, String rootLabels, SymbolTableHandler symbolTableHandler, Logger logger) throws MaltChainedException {
		if (!fileName.equalsIgnoreCase("")) {
			if (logger.isInfoEnabled()) {
				logger.info("Loading dependency type tagset '"+fileName+"'...\n");
			}
			symbolTableHandler.loadTagset(fileName, "DEPREL", charSet, ColumnDescription.DEPENDENCY_EDGE_LABEL, nullValueStrategy, rootLabels);
		}
	}
}
