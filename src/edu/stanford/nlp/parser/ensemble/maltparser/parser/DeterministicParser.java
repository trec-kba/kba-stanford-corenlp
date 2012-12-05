package edu.stanford.nlp.parser.ensemble.maltparser.parser;



import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.ClassifierGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.guide.SingleGuide;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideHistory;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideDecision;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;
/**
 * @author Johan Hall
 *
 */
public class DeterministicParser extends Parser {
	private int parseCount;
	
	public DeterministicParser(DependencyParserConfig manager) throws MaltChainedException {
		super(manager);
		setManager(manager);
		initParserState(1);
		((SingleMalt)manager).addRegistry(edu.stanford.nlp.parser.ensemble.maltparser.parser.Algorithm.class, this);
		setGuide(new SingleGuide(manager, (GuideHistory)parserState.getHistory(), ClassifierGuide.GuideMode.CLASSIFY));
	}
	
	public DependencyStructure parse(DependencyStructure parseDependencyGraph) throws MaltChainedException {
		parserState.clear();
		parserState.initialize(parseDependencyGraph);
		currentParserConfiguration = parserState.getConfiguration();
		if (diagnostics == true) {
			writeToDiaFile("ParseCount: " + ++parseCount + "\n");
		}
		while (!parserState.isTerminalState()) {
			GuideUserAction action = parserState.getTransitionSystem().getDeterministicAction(parserState.getHistory(), currentParserConfiguration);
			if (action == null) {
				action = predict();
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
		if (diagnostics == true) {
			writeToDiaFile("\n");
		}
		return parseDependencyGraph;
	}
	
	private GuideUserAction predict() throws MaltChainedException {
		GuideUserAction currentAction = parserState.getHistory().getEmptyGuideUserAction();
		try {
			classifierGuide.predict((GuideDecision)currentAction);
			while (!parserState.permissible(currentAction)) {
				if (classifierGuide.predictFromKBestList((GuideDecision)currentAction) == false) {
					currentAction = getParserState().getTransitionSystem().defaultAction(parserState.getHistory(), currentParserConfiguration);
					break;
				}
			}
		} catch (NullPointerException e) {
			throw new MaltChainedException("The guide cannot be found. ", e);
		}
		return currentAction;
	}
	
	public void terminate() throws MaltChainedException {
		if (diagnostics == true) {
			closeDiaWriter();
		}
	}
}
