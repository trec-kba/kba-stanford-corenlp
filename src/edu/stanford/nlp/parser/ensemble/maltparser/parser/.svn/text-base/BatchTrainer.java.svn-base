package edu.stanford.nlp.parser.ensemble.maltparser.parser;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.ClassifierGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.OracleGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.SingleGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideHistory;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideDecision;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;
/**
 * @author Johan Hall
 *
 */
public class BatchTrainer extends Trainer {
	private OracleGuide oracleGuide;
	private int parseCount;
	
	public BatchTrainer(DependencyParserConfig manager) throws MaltChainedException {
		super(manager);
		((SingleMalt)manager).addRegistry(edu.stanford.nlp.parser.ensemble.maltparser.parser.Algorithm.class, this);
		setManager(manager);
		initParserState(1);
		setGuide(new SingleGuide(manager, (GuideHistory)parserState.getHistory(), ClassifierGuide.GuideMode.BATCH));
		oracleGuide = parserState.getFactory().makeOracleGuide(parserState.getHistory());
	}
	
	public DependencyStructure parse(DependencyStructure goldDependencyGraph, DependencyStructure parseDependencyGraph) throws MaltChainedException {
		parserState.clear();
		parserState.initialize(parseDependencyGraph);
		currentParserConfiguration = parserState.getConfiguration();
		if (diagnostics == true) {
			writeToDiaFile("ParseCount: " + ++parseCount + "\n");
		}
		while (!parserState.isTerminalState()) {
			GuideUserAction action = parserState.getTransitionSystem().getDeterministicAction(parserState.getHistory(), currentParserConfiguration);
			if (action == null) {
				action = oracleGuide.predict(goldDependencyGraph, currentParserConfiguration);
				try {
					classifierGuide.addInstance((GuideDecision)action);
				} catch (NullPointerException e) {
					throw new MaltChainedException("The guide cannot be found. ", e);
				}
			} else if (diagnostics == true) {
				writeToDiaFile("*");
			}
			if (diagnostics == true) {
				writeToDiaFile(parserState.getTransitionSystem().getActionString(action));
				writeToDiaFile("\n");
			}	
			parserState.apply(action);
		}
		copyEdges(currentParserConfiguration.getDependencyGraph(), parseDependencyGraph);
		parseDependencyGraph.linkAllTreesToRoot();
		oracleGuide.finalizeSentence(parseDependencyGraph);
		if (diagnostics == true) {
			writeToDiaFile("\n");
		}
		return parseDependencyGraph;
	}
	
	public OracleGuide getOracleGuide() {
		return oracleGuide;
	}
	
	public void train() throws MaltChainedException { }
	public void terminate() throws MaltChainedException {
		if (diagnostics == true) {
			closeDiaWriter();
		}
	}
}
