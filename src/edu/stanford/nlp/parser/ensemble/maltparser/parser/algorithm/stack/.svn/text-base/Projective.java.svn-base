package edu.stanford.nlp.parser.ensemble.maltparser.parser.algorithm.stack;

import java.util.Stack;


import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.edge.Edge;
import edu.stanford.nlp.parser.ensemble.maltparser.core.syntaxgraph.node.DependencyNode;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.ParserConfiguration;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.TransitionSystem;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.GuideUserHistory;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.History;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.ComplexDecisionAction;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.history.action.GuideUserAction;
import edu.stanford.nlp.parser.ensemble.maltparser.parser.transition.TransitionTable;
/**
 * @author Johan Hall
 *
 */
public class Projective extends TransitionSystem {
	protected static final int SHIFT = 1;
	protected static final int RIGHTARC = 2;
	protected static final int LEFTARC = 3;
	
	public Projective() throws MaltChainedException {
		super();
	}
	
	public void apply(GuideUserAction currentAction, ParserConfiguration configuration) throws MaltChainedException {
		StackConfig config = (StackConfig)configuration;
		Stack<DependencyNode> stack = config.getStack();
		Stack<DependencyNode> input = config.getInput();
		currentAction.getAction(actionContainers);
		Edge e = null;
		DependencyNode head = null;
		DependencyNode dep = null;
		switch (transActionContainer.getActionCode()) {
		case LEFTARC:
			head = stack.pop(); 
			dep = stack.pop();
			e = config.getDependencyStructure().addDependencyEdge(head.getIndex(), dep.getIndex());
			addEdgeLabels(e);
			stack.push(head);
			break;
		case RIGHTARC:
			dep = stack.pop(); 
			e = config.getDependencyStructure().addDependencyEdge(stack.peek().getIndex(), dep.getIndex());
			addEdgeLabels(e);
			break;
		default:
			if (input.isEmpty()) {
				stack.pop();
			} else {
				stack.push(input.pop()); // SHIFT
			}
			break;
		}
	}
	
	public boolean permissible(GuideUserAction currentAction, ParserConfiguration configuration) throws MaltChainedException {
		StackConfig config = (StackConfig)configuration;
		currentAction.getAction(actionContainers);
		int trans = transActionContainer.getActionCode();
		if ((trans == LEFTARC || trans == RIGHTARC) && !isActionContainersLabeled()) {
			return false;
		}
		Stack<DependencyNode> stack = config.getStack();
		Stack<DependencyNode> input = config.getInput();
		if ((trans == LEFTARC || trans == RIGHTARC) && stack.size() < 2) {
			return false;
		}
		if (trans == LEFTARC && stack.get(stack.size()-2).isRoot()) { 
			return false;
		}
		if (trans == SHIFT && input.isEmpty()) { 
			return false;
		}
		return true;
	}
	
	public GuideUserAction getDeterministicAction(GuideUserHistory history, ParserConfiguration config) throws MaltChainedException {
		return null;
	}
	
	protected void addAvailableTransitionToTable(TransitionTable ttable) throws MaltChainedException {
		ttable.addTransition(SHIFT, "SH", false, null);
		ttable.addTransition(RIGHTARC, "RA", true, null);
		ttable.addTransition(LEFTARC, "LA", true, null);
	}
	
	protected void initWithDefaultTransitions(GuideUserHistory history) throws MaltChainedException {
		GuideUserAction currentAction = new ComplexDecisionAction((History)history);
		
		transActionContainer.setAction(SHIFT);
		for (int i = 0; i < arcLabelActionContainers.length; i++) {
			arcLabelActionContainers[i].setAction(-1);
		}
		currentAction.addAction(actionContainers);
	}
	
	public String getName() {
		return "projective";
	}
	
	public GuideUserAction defaultAction(GuideUserHistory history, ParserConfiguration configuration) throws MaltChainedException {
		if (((StackConfig)configuration).getInput().isEmpty()) {
			return updateActionContainers(history, RIGHTARC, ((StackConfig)configuration).getDependencyGraph().getDefaultRootEdgeLabels());
		}
		
		return updateActionContainers(history, SHIFT, null);
	}
}
