package edu.stanford.nlp.parser.ensemble.maltparser.parser;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.symbol.SymbolTableHandler;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.DependencyStructure;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideUserHistory;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.History;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.HistoryList;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.HistoryStructure;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;
/**
 * @author Johan Hall
 *
 */
public class ParserState {
	private AbstractParserFactory factory;
	private Algorithm algorithm;
	private SymbolTableHandler symboltables;
	private GuideUserHistory history;
	private TransitionSystem transitionSystem;
	private HistoryStructure historyStructure;
	private ParserConfiguration config;
	
	public ParserState(Algorithm algorithm, AbstractParserFactory factory) throws MaltChainedException {
		this(algorithm, factory, 1);
	}
	
	public ParserState(Algorithm algorithm, AbstractParserFactory factory, int k) throws MaltChainedException {
		setAlgorithm(algorithm);
		setFactory(factory);
		setSymboltables(algorithm.getManager().getSymbolTables());
		setHistoryStructure(new HistoryList());
		setTransitionSystem(factory.makeTransitionSystem());
		String decisionSettings = algorithm.getManager().getOptionValue("guide", "decision_settings").toString().trim();
		getTransitionSystem().initTableHandlers(decisionSettings, symboltables);
		setHistory(new History(decisionSettings, algorithm.getManager().getOptionValue("guide", "classitem_separator").toString(), getTransitionSystem().getTableHandlers()));
		getTransitionSystem().initTransitionSystem(history);
		config = getFactory().makeParserConfiguration();
	}
	
	
	public void clear() throws MaltChainedException {
		history.clear();
		historyStructure.clear();
	}
	
	public Algorithm getAlgorithm() {
		return algorithm;
	}

	public void setAlgorithm(Algorithm algorithm) {
		this.algorithm = algorithm;
	}

	public SymbolTableHandler getSymboltables() {
		return symboltables;
	}

	protected void setSymboltables(SymbolTableHandler symboltables) {
		this.symboltables = symboltables;
	}
	
	public GuideUserHistory getHistory() {
		return history;
	}

	protected void setHistory(GuideUserHistory history) {
		this.history = history;
	}

	public TransitionSystem getTransitionSystem() {
		return transitionSystem;
	}

	protected void setTransitionSystem(TransitionSystem transitionSystem) {
		this.transitionSystem = transitionSystem;
	}
	
	public HistoryStructure getHistoryStructure() {
		return historyStructure;
	}

	protected void setHistoryStructure(HistoryStructure historyStructure) {
		this.historyStructure = historyStructure;
	}
	
	public void initialize(DependencyStructure dependencyStructure) throws MaltChainedException {
		config.clear();
		config.setDependencyGraph(dependencyStructure);
		config.initialize(null);
	}
	
	public boolean isTerminalState() throws MaltChainedException {
		return config.isTerminalState();
	}
	
	public boolean permissible(GuideUserAction currentAction) throws MaltChainedException {
		return transitionSystem.permissible(currentAction, config); 
	}
	
	public void apply(GuideUserAction currentAction) throws MaltChainedException {
		transitionSystem.apply(currentAction, config);
	}
	
	public int nConfigurations() throws MaltChainedException {
		return 1;
	}
	
	public ParserConfiguration getConfiguration() {
		return config;
	}

	public AbstractParserFactory getFactory() {
		return factory;
	}

	public void setFactory(AbstractParserFactory factory) {
		this.factory = factory;
	}
}
