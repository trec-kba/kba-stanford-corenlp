package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.Interval;
import edu.stanford.nlp.util.Pair;

import java.util.*;

/**
 * Generic Sequence Pattern for regular expressions
 *
 * <p>
 * Similar to Java's <code>Pattern</code> except it is for sequences over arbitrary types T instead
 *  of just characters. 
 * </p>
 *
 * <p>
 * To support a new type <code>T</code>:
 * <ol>
 * <li> For a type <code>T</code> to be matchable, it has to have a corresponding <code>NodePattern<T></code> that indicates
 *    whether a node is matched or not  (see <code>CoreMapNodePattern</code> for example)</li>
 * <li> To compile a string into corresponding pattern, will need to create a parser
 *    (see inner class <code>Parser</code>, <code>TokenSequencePattern</code> and <code>TokenSequenceParser.jj</code>)</li>
 * </ol>
 * </p>
 *
 * <p>
 * Supports the following:
 * <ul>
 *  <li>Concatentation </li>
 *  <li>Or </li>
 *  <li>Groups  (capturing  / noncapturing )  </li>
 *  <li>Quantifiers (greedy / nongreedy) </li>
 * </ul>
 * </p>
 *
 * <p>
 * Notes on less standard features
 * <ol>
 * <li> Binding of variables </li>
 * <br> Use <code>Env</code> to bind variables for use when compiling patterns
 * <br> Can also bind names to groups (see <code>SequenceMatchResult</code>)
 * <li> Backreference matches - need to specify how back references are to be matched using <code>NodesMatchChecker</code> </li>
 * <li> Multinode matches - for matching of multiple nodes using non-regex (at least not regex over nodes) patterns
 *                        (need to have corresponding <code>MultiNodePattern</code>,
 *                         see <code>MultiCoreMapNodePattern</code> for example) </li>
 * </ol>
 * </p>
 *
 * TODO:
 * <br> Replace
 * <br> Validate backref capture groupid
 * <br> Actions
 * <br> Inconsistent templating with T
 *
 * @author Angel Chang
 */
public class SequencePattern<T> {
  private String patternStr;
  private PatternExpr patternExpr;
  private SequenceMatchAction<T> action;
  State root;
  int totalGroups = 0;

  // binding of group number to variable name
  VarGroupBindings varGroupBindings;

  // Priority associated with pattern
  double priority = 0.0;

  protected SequencePattern(SequencePattern.PatternExpr nodeSequencePattern) {
    this(null, nodeSequencePattern);
  }

  protected SequencePattern(String patternStr, SequencePattern.PatternExpr nodeSequencePattern) {
    this(patternStr, nodeSequencePattern, null);
  }

  protected SequencePattern(String patternStr, SequencePattern.PatternExpr nodeSequencePattern,
                            SequenceMatchAction<T> action) {
    this.patternStr = patternStr;
    this.patternExpr = nodeSequencePattern;
    this.action = action;

    nodeSequencePattern = new GroupPatternExpr(nodeSequencePattern, true);
    this.totalGroups = nodeSequencePattern.assignGroupIds(0);
    Frag f = nodeSequencePattern.build();
    f.connect(MATCH_STATE);
    this.root = f.start;
    varGroupBindings = new VarGroupBindings(totalGroups+1);
    nodeSequencePattern.updateBindings(varGroupBindings);
  }

  public String pattern() {
    return patternStr;
  }

  public double getPriority() {
    return priority;
  }

  public void setPriority(double priority) {
    this.priority = priority;
  }

  public SequenceMatchAction<T> getAction() {
    return action;
  }

  public void setAction(SequenceMatchAction<T> action) {
    this.action = action;
  }

  // Compiles string (regex) to NFA for doing pattern simulation
  protected static <T> SequencePattern<T> compile(Env env, String string)
  {
    try {
      Pair<PatternExpr, SequenceMatchAction<T>> p = env.parser.parseSequenceWithAction(env, string);
      return new SequencePattern<T>(string, p.first(), p.second());
    } catch (Exception ex) {
      throw new RuntimeException("Error compiling " + string + " using environment " + env);
    }
    //throw new UnsupportedOperationException("Compile from string not implemented");
  }

  protected static <T> SequencePattern<T> compile(SequencePattern.PatternExpr nodeSequencePattern)
  {
    return new SequencePattern<T>(nodeSequencePattern);
  }

  public SequenceMatcher<T> getMatcher(List<? extends T> tokens) {
    return new SequenceMatcher<T>(this, tokens);
  }

  // Parses string to PatternExpr
  public static interface Parser<T> {
    public SequencePattern.PatternExpr parseSequence(SequencePattern.Env env, String s) throws Exception;
    public Pair<SequencePattern.PatternExpr, SequenceMatchAction<T>> parseSequenceWithAction(SequencePattern.Env env, String s) throws Exception;
    public SequencePattern.PatternExpr parseNode(SequencePattern.Env env, String s) throws Exception;
  }

  /**
   * Holds environment variables to be used for compiling string into a pattern
   *
   * <p>
   * Currently we restrict the types of variables to:
   * <ul>
   * <li><code>SequencePattern</code> (compiled pattern)</li>
   * <li><code>PatternExpr</code> (sequence pattern expression - precompiled)</li>
   * <li><code>NodePattern</code> (pattern for matching one element)</li>
   * <li><code>Class</code></li>
   * </ul>
   * </p>
   */
  public static class Env {
    Parser parser;    // generic parser
    Map<String, Object> variables = new HashMap<String, Object>();
    int defaultStringPatternFlags = 0;

    public Env(Parser p) { this.parser = p; }
    
    public Map<String, Object> getVariables() {
      return variables;
    }

    public void setVariables(Map<String, Object> variables) {
      this.variables = variables;
    }

    public int getDefaultStringPatternFlags() {
      return defaultStringPatternFlags;
    }

    public void setDefaultStringPatternFlags(int defaultStringPatternFlags) {
      this.defaultStringPatternFlags = defaultStringPatternFlags;
    }

    public void bind(String name, SequencePattern pattern) {
      bind(name, pattern.patternExpr);
    }

    public void bind(String name, PatternExpr patternExpr) {
      variables.put(name, patternExpr);
    }

    public void bind(String name, String string) {
      variables.put(name, string);
    }

    public void bind(String name, NodePattern pattern) {
      variables.put(name, pattern);
    }

    public void bind(String name, Class c) {
      variables.put(name, c);
    }

    public void unbind(String name) {
      variables.remove(name);
    }

    protected NodePattern getNodePattern(String name)
    {
      Object obj = variables.get(name);
      if (obj != null) {
        if (obj instanceof NodePatternExpr) {
          NodePatternExpr pe = (NodePatternExpr) obj;
          return pe.nodePattern;
        } else if (obj instanceof NodePattern) {
          return (NodePattern) obj;
        } else if (obj instanceof String) {
          try {
            NodePatternExpr pe = (NodePatternExpr) parser.parseNode(this, (String) obj);
            return pe.nodePattern;
          } catch (Exception pex) {
            throw new RuntimeException("Error parsing " + obj + " to node pattern", pex);
          }
        } else {
          throw new Error("Invalid node pattern variable class: " + obj.getClass());
        }
      }
      return null;
    }

    protected PatternExpr getSequencePatternExpr(String name, boolean copy)
    {
      Object obj = variables.get(name);
      if (obj != null) {
        if (obj instanceof PatternExpr) {
          PatternExpr pe = (PatternExpr) obj;
          return (copy)? pe.copy():pe;
        } else if (obj instanceof NodePattern) {
          return new NodePatternExpr( (NodePattern) obj);
        } else if (obj instanceof String) {
          try {
            return parser.parseSequence(this, (String) obj);
          } catch (Exception pex) {
            throw new RuntimeException("Error parsing " + obj + " to sequence pattern", pex);
          }
        } else {
          throw new Error("Invalid sequence pattern variable class: " + obj.getClass());
        }
      }
      return null;
    }

    protected Object get(String name)
    {
      return variables.get(name);
    }
  }

  // Binding of variable names to groups
  // matches the group indices
  protected static class VarGroupBindings {
    String[] varnames;  // Assumes number of groups low

    protected VarGroupBindings(int size) {
      varnames = new String[size];
    }

    protected void set(int index, String name) {
      varnames[index] = name;
    }
  }

  // Interface indicating when two nodes match
  protected static interface NodesMatchChecker<T> {
    public boolean matches(T o1, T o2);
  }

  public static final NodesMatchChecker<Object> NODES_EQUAL_CHECKER = new NodesMatchChecker<Object>() {
    public boolean matches(Object o1, Object o2) {
      return o1.equals(o2);
    }
  };

  /**
   * Represents a sequence pattern expressions (before translating into NFA)
   */
  public static abstract class PatternExpr {
    protected abstract Frag build();

    /**
     * Assigns group ids to groups embedded in this patterns starting with at the specified number,
     * returns the next available group id
     * @param start Group id to start with
     * @return The next available group id
     */
    protected abstract int assignGroupIds(int start);

    /**
     * Make a deep copy of the sequence pattern expressions
     * @return
     */
    protected abstract PatternExpr copy();

    /**
     * Updates the binding of group to variable name
     * @param bindings
     */
    protected abstract void updateBindings(VarGroupBindings bindings);
  }

  // Represents one element to be matched
  protected static class NodePatternExpr extends PatternExpr {
    NodePattern nodePattern;

    public NodePatternExpr(NodePattern nodePattern) {
      this.nodePattern = nodePattern;
    }

    protected Frag build()
    {
      State s = new NodePatternState(nodePattern);
      return new Frag(s);
    }

    protected PatternExpr copy()
    {
      return new NodePatternExpr(nodePattern);
    }

    protected int assignGroupIds(int start) { return start; }
    protected void updateBindings(VarGroupBindings bindings) {}
  }

  // Represents a pattern that can match multiple nodes
  protected static class MultiNodePatternExpr extends PatternExpr {
    MultiNodePattern multiNodePattern;

    public MultiNodePatternExpr(MultiNodePattern nodePattern) {
      this.multiNodePattern = nodePattern;
    }

    protected Frag build() {
      State s = new MultiNodePatternState(multiNodePattern);
      return new Frag(s);
    }

    protected PatternExpr copy()
    {
      return new MultiNodePatternExpr(multiNodePattern);
    }

    protected int assignGroupIds(int start) { return start; }
    protected void updateBindings(VarGroupBindings bindings) {}
  }

  // Represents a sequence of patterns to be matched
  protected static class SequencePatternExpr extends PatternExpr {
    List<PatternExpr> patterns;

    public SequencePatternExpr(List<PatternExpr> patterns) {
      this.patterns = patterns;
    }

    public SequencePatternExpr(PatternExpr... patterns) {
      this.patterns = Arrays.asList(patterns);
    }

    protected Frag build()
    {
      Frag frag = null;
      if (patterns.size() > 0) {
        PatternExpr first = patterns.get(0);
        frag = first.build();
        for (int i = 1; i < patterns.size(); i++) {
          PatternExpr pattern = patterns.get(i);
          Frag f = pattern.build();
          frag.connect(f);
        }
      }
      return frag;
    }

    protected int assignGroupIds(int start) {
      int nextId = start;
      for (PatternExpr pattern : patterns) {
        nextId = pattern.assignGroupIds(nextId);
      }
      return nextId;
    }

    protected void updateBindings(VarGroupBindings bindings) {
      for (PatternExpr pattern : patterns) {
        pattern.updateBindings(bindings);
      }
    }

    protected PatternExpr copy()
    {
      List<PatternExpr> newPatterns = new ArrayList<PatternExpr>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.copy());
      }
      return new SequencePatternExpr(newPatterns);
    }

  }

  // Expression that indicates a back reference
  // Need to match a previously matched group somehow
  protected static class BackRefPatternExpr extends PatternExpr {
    NodesMatchChecker matcher; // How a match is determined
    int captureGroupId = -1;  // Indicates the previously matched group this need to match

    public BackRefPatternExpr(NodesMatchChecker matcher, int captureGroupId) {
      if (captureGroupId <= 0) { throw new IllegalArgumentException("Invalid captureGroupId=" + captureGroupId); }
      this.captureGroupId = captureGroupId;
      this.matcher = matcher;
    }

    protected Frag build()
    {
      State s = new BackRefState(matcher, captureGroupId);
      return new Frag(s);
    }

    protected int assignGroupIds(int start) {
      return start;
    }
    protected void updateBindings(VarGroupBindings bindings) {}

    protected PatternExpr copy()
    {
      return new BackRefPatternExpr(matcher, captureGroupId);
    }
  }

  // Expression that represents a group
  protected static class GroupPatternExpr extends PatternExpr {
    PatternExpr pattern;
    boolean capture = false; // Do capture or not?  If do capture, an capture group id will be assigned
    int captureGroupId = -1; // -1 if this pattern is not part of a capture group or capture group not yet assigned,
                             // otherwise, capture group number
    String varname;  // Alternate variable with which to refer to this group

 /*   public GroupPatternExpr(PatternExpr pattern, int captureGroupId) {
      this.pattern = pattern;
      this.captureGroupId = captureGroupId;
      this.capture = (captureGroupId != -1);
    }             */

    public GroupPatternExpr(PatternExpr pattern) {
      this(pattern, true);
    }

    public GroupPatternExpr(PatternExpr pattern, boolean capture) {
      this.pattern = pattern;
      this.capture = capture;
    }

    public GroupPatternExpr(PatternExpr pattern, String varname) {
      this.pattern = pattern;
      this.capture = true;
      this.varname = varname;
    }

    protected Frag build()
    {
      Frag f = pattern.build();
      Frag frag = new Frag(new GroupStartState(captureGroupId, f.start), f.out);
      frag.connect(new GroupEndState(captureGroupId));
      return frag;
    }

    protected int assignGroupIds(int start) {
      int nextId = start;
      if (capture) {
        captureGroupId = nextId;
        nextId++;
      }
      return pattern.assignGroupIds(nextId);
    }
    protected void updateBindings(VarGroupBindings bindings) {
      if (varname != null) {
        bindings.set(captureGroupId, varname);
      }
      pattern.updateBindings(bindings);
    }

    protected PatternExpr copy()
    {
      return new GroupPatternExpr(pattern.copy(), capture);
    }

  }

  // Expression that represents a pattern that repeats for a number of times
  protected static class RepeatPatternExpr extends PatternExpr {
    PatternExpr pattern;
    int minMatch = 1;
    int maxMatch = 1;
    boolean greedyMatch = true;

    public RepeatPatternExpr(PatternExpr pattern, int minMatch, int maxMatch) {
      this.pattern = pattern;
      this.minMatch = minMatch;
      this.maxMatch = maxMatch;
      if (minMatch < 0) {
        throw new IllegalArgumentException("Invalid minMatch=" + minMatch);
      }
      if (maxMatch >= 0 && minMatch > maxMatch) {
        throw new IllegalArgumentException("Invalid minMatch=" + minMatch + ", maxMatch=" + maxMatch);
      }
    }

    public RepeatPatternExpr(PatternExpr pattern, int minMatch, int maxMatch, boolean greedy) {
      this(pattern, minMatch, maxMatch);
      this.greedyMatch = greedy;
    }

    protected Frag build()
    {
      Frag f = pattern.build();
      if (minMatch == 1 && maxMatch == 1) {
        return f;
      } else if (minMatch <= 10 && maxMatch <= 10 && greedyMatch) {
        // Make copies if number of matches is low
        // Doesn't handle nongreedy matches yet
        // For non greedy match need to move curOut before the recursive connect

        // Create NFA fragment that
        // have child pattern repeating for minMatch times
        if (minMatch > 0) {
          //  frag.start -> pattern NFA -> pattern NFA ->
          for (int i = 0; i < minMatch-1; i++) {
            Frag f2 = pattern.build();
            f.connect(f2);
          }
        } else {
          // minMatch is 0
          // frag.start ->
          f = new Frag(new State());
        }
        if (maxMatch < 0) {
          // Unlimited (loop back to self)
          //        --------
          //       \|/     |
          // ---> pattern NFA --->
          Set<State> curOut = f.out;
          Frag f2 = pattern.build();
          f2.connect(f2);
          f.connect(f2);
          f.add(curOut);
        } else {
          // Limited number of times this pattern repeat,
          // just keep add pattern (with option of being done) until maxMatch reached
          // ----> pattern NFA ----> pattern NFA --->
          //   |                |
          //   -->              --->
          for (int i = minMatch; i < maxMatch; i++) {
            Set<State> curOut = f.out;
            Frag f2 = pattern.build();
            f.connect(f2);
            f.add(curOut);
          }
        }
        return f;
      }  else {
        // More general but more expensive matching (when branching, need to keep state explicitly)
        State s = new RepeatState(f.start, minMatch, maxMatch, greedyMatch);
        f.connect(s);
        return new Frag(s);
      }
    }

    protected int assignGroupIds(int start) {
      return pattern.assignGroupIds(start);
    }
    protected void updateBindings(VarGroupBindings bindings) {
      pattern.updateBindings(bindings);
    }

    protected PatternExpr copy()
    {
      return new RepeatPatternExpr(pattern.copy(), minMatch, maxMatch, greedyMatch);
    }

  }

  // Expression that represents a conjuction
  protected static class OrPatternExpr extends PatternExpr {
    List<PatternExpr> patterns;

    public OrPatternExpr(List<PatternExpr> patterns) {
      this.patterns = patterns;
    }

    public OrPatternExpr(PatternExpr... patterns) {
      this.patterns = Arrays.asList(patterns);
    }

    protected Frag build()
    {
      Frag frag = new Frag();
      frag.start = new State();
      // Create NFA fragment that
      // have one starting state that branches out to NFAs created by the children expressions
      //  ---> pattern 1 --->
      //   |
      //   ---> pattern 2 --->
      //   ...
      for (PatternExpr pattern : patterns) {
        // Build child NFA
        Frag f = pattern.build();
        // Add child NFA to next states of fragment start
        frag.start.add(f.start);
        // Add child NFA out (unlinked) states to out (unlinked) states of this fragment  
        frag.add(f.out);
      }
      return frag;
    }

    protected int assignGroupIds(int start) {
      int nextId = start;
      // assign group ids of child expressions
      for (PatternExpr pattern : patterns) {
        nextId = pattern.assignGroupIds(nextId);
      }
      return nextId;
    }
    protected void updateBindings(VarGroupBindings bindings) {
      // update bindings of child expressions
      for (PatternExpr pattern : patterns) {
        pattern.updateBindings(bindings);
      }
    }

    protected PatternExpr copy()
    {
      List<PatternExpr> newPatterns = new ArrayList<PatternExpr>(patterns.size());
      for (PatternExpr p:patterns) {
        newPatterns.add(p.copy());
      }
      return new OrPatternExpr(newPatterns);
    }

  }

  /****** NFA states for matching sequences *********/

  // Patterns are converted to the NFA states
  // Assumes the matcher will step through the NFA states one token at a time

  /**
   * An accepting matching state
   */
  protected final static State MATCH_STATE = new MatchState();

  /**
   * Represents a state in the NFA corresponding to a regular expression for matching a sequence
   */
  protected static class State {
    /**
     * Set of next states from this current state
     * NOTE: Most of times next is just one state
     */
    Set<State> next;
    protected State() {}

    /**
     * Update the set of out states by unlinked states from this state
     * @param out - Current set of out states (to be updated by this function)
     */
    protected void updateOutStates(Set<State> out) {
      if (next == null) {
        out.add(this);
      } else {
        for (State s:next) {
          s.updateOutStates(out);
        }
      }
    }

    /**
     * Non-consuming match
     * @param bid - Branch id
     * @param matchedStates - State of the matching so far (to be updated by the matching process)
     * @return true if match
     */
    protected <T> boolean  match0(int bid, SequenceMatcher.MatchedStates<T> matchedStates)
    {
      return match(bid, matchedStates, false);
    }

    /**
     * Consuming match
     * @param bid - Branch id
     * @param matchedStates - State of the matching so far (to be updated by the matching process)
     * @return true if match
     */
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates)
    {
      return match(bid, matchedStates, true);
    }

    /**
     * Given the current matched states, attempts to run NFA from this state
     *  If consuming:  tries to match the next element - goes through states until an element is consumed or match is false
     *  If non-consuming: does not match the next element - goes through non elemnt consuming states
     * In both cases, matchedStates should be updated as follows:
     * - matchedStates should be updated with the next state to be processed
     * @param bid - Branch id
     * @param matchedStates - State of the matching so far (to be updated by the matching process)
     * @param consume - Whether to consume the next element or not
     * @return true if match
     */
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume)
    {
      boolean match = false;
      if (next != null) {
        int i = 0;
        for (State s:next) {
          i++;
          boolean m = s.match(matchedStates.getBranchId(bid,i,next.size()), matchedStates, consume);
          if (m) {
            match = true;
          }
        }
      }
      return match;
    }

    /**
     * Add state to the set of next states
     * @param nextState - state to add
     */
    protected void add(State nextState) {
      if (next == null) {
        next = new LinkedHashSet<State>();
      }
      next.add(nextState);
    }
  }

  /**
   * Final accepting state
   */
  protected static class MatchState extends State {
    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume) {
      // Always add this state back (effectively looping forever in this matching state)
      matchedStates.addState(bid, this);
      return false;
    }
  }

  /**
   * State for matching one element/node
   */
  protected static class NodePatternState extends State {
    NodePattern pattern;
    protected NodePatternState(NodePattern p) {
      this.pattern = p;
    }

    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume)
    {
      if (consume) {
        // Get element and return if it matched or not
        T node = matchedStates.get();
        // TODO: Fix type checking
        if (pattern.match(node)) {
          // If matched, need to add next states to the queue of states to be processed
          matchedStates.addStates(bid, next);
          return true;
        } else {
          return false;
        }
      } else {
        // Not consuming element - add this state back to queue of states to be processed
        // This state was not successfully matched
        matchedStates.addState(bid, this);
        return false;
      }
    }

  }

  /**
   * State for matching multiple elements/nodes
   */
  protected static class MultiNodePatternState extends State {
    MultiNodePattern pattern;
    protected MultiNodePatternState(MultiNodePattern p) {
      this.pattern = p;
    }

    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume)
    {
      if (consume) {
        Interval<Integer> matchedInterval = matchedStates.getMatchedInterval(bid, this);
        int cur = matchedStates.curPosition;
        if (matchedInterval == null) {
          // Haven't tried to match this node before, try now
          // Get element and return if it matched or not
          List<? extends T> nodes = matchedStates.elements();
          // TODO: Fix type checking
          Collection<Interval<Integer>> matched = pattern.match(nodes, cur);
          // TODO: Check intervals are valid?   Start at cur and ends after?
          if (matched != null && matched.size() > 0) {
            int nBranches = matched.size();
            int i = 0;
            for (Interval<Integer> interval:matched) {
              i++;
              int bid2 = matchedStates.getBranchId(bid, i, nBranches);
              matchedStates.setMatchedInterval(bid2, this, interval);
              // If matched, need to add next states to the queue of states to be processed
              // keep in current state until end node reached
              if (interval.getEnd()-1 <= cur) {
                matchedStates.addStates(bid2, next);
              } else {
                matchedStates.addState(bid2, this);
              }
            }
            return true;
          } else {
            return false;
          }
        } else {
          // Previously matched this state - just need to step through until we get to end of matched interval
          if (matchedInterval.getEnd()-1 <= cur) {
            matchedStates.addStates(bid, next);
          } else {
            matchedStates.addState(bid, this);
          }
          return true;
        }
      } else {
        // Not consuming element - add this state back to queue of states to be processed
        // This state was not successfully matched
        matchedStates.addState(bid, this);
        return false;
      }
    }

  }

  /**
   * State that matches a pattern that can occur multiple times
   */
  protected static class RepeatState extends State {
    State repeatStart;
    int minMatch;
    int maxMatch;
    boolean greedyMatch;

    public RepeatState(State start, int minMatch, int maxMatch, boolean greedyMatch)
    {
      this.repeatStart = start;
      this.minMatch = minMatch;
      this.maxMatch = maxMatch;
      this.greedyMatch = greedyMatch;
      if (minMatch < 0) {
        throw new IllegalArgumentException("Invalid minMatch=" + minMatch);
      }
      if (maxMatch >= 0 && minMatch > maxMatch) {
        throw new IllegalArgumentException("Invalid minMatch=" + minMatch + ", maxMatch=" + maxMatch);
      }
    }

    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume)
    {
      // Get how many times this states has already been matched
      int matchedCount = matchedStates.endMatchedCountInc(bid, this);
      // Get the minimum number of times we still need to match this state
      int minMatchLeft = minMatch - matchedCount;
      if (minMatchLeft < 0) {
        minMatchLeft = 0;
      }
      // Get the maximum number of times we can match this state
      int maxMatchLeft;
      if (maxMatch < 0) {
        // Indicate unlimited matching
        maxMatchLeft = maxMatch;
      } else {
        maxMatchLeft = maxMatch - matchedCount;
        if (maxMatch < 0) {
          // Already exceeded the maximum number of times we can match this state
          // indicate state not matched
          return false;
        }
      }
      boolean match = false;
      // See how many branching options there are...
      int totalBranches = 0;
      if (minMatchLeft == 0 && next != null) {
         totalBranches += next.size();
      }
      if (maxMatchLeft != 0) {
        totalBranches++;
      }
      int i = 0; // branch index
      // Check if there we have met the minimum number of matches
      // If so, go ahead and try to match next state
      //  (if we need to consume an element or end a group)
      if (minMatchLeft == 0 && next != null) {
        for (State s:next) {
          i++;   // Increment branch index
          // Depending on greedy match or not, different priority to branches
          int pi = (greedyMatch && maxMatchLeft != 0)? i+1:i;
          int bid2 = matchedStates.getBranchId(bid,pi,totalBranches);
          matchedStates.clearMatchedCount(bid2, this);
          boolean m = s.match(bid2, matchedStates, consume);
          if (m) {
            match = true;
          }
        }
      }
      // Check if we have the option of matching more
      // (maxMatchLeft < 0 indicate unlimited, maxMatchLeft > 0 indicate we are still allowed more matches)
      if (maxMatchLeft != 0) {
        i++;    // Increment branch index
        // Depending on greedy match or not, different priority to branches
        int pi = greedyMatch? 1:i;
        int bid2 = matchedStates.getBranchId(bid,pi,totalBranches);
        if (consume) {
          // Consuming - try to see if repeating this pattern does anything
          boolean m = repeatStart.match(bid2, matchedStates, consume);
          if (m) {
            match = true;
            // Mark how many times we have matched this pattern
            matchedStates.startMatchedCountInc(bid2, this);
          }
        } else {
          // Not consuming - don't do anything, just add this back to list of states to be processed
          matchedStates.addState(bid2, this);
        }
      }
      return match;
    }
  }

  /**
   * State for matching previously matched group
   */
  protected static class BackRefState extends State {
    NodesMatchChecker matcher;
    int captureGroupId;

    public BackRefState(NodesMatchChecker matcher, int captureGroupId)
    {
      this.matcher = matcher;
      this.captureGroupId = captureGroupId;
    }

    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates,
                                SequenceMatcher.MatchedGroup matchedGroup, int matchedNodes)
    {
      T node = matchedStates.get();
      if (matcher.matches(node, matchedStates.elements().get(matchedGroup.matchBegin+matchedNodes))) {
        matchedNodes++;
        matchedStates.setMatchStateInfo(bid, this, new Pair<SequenceMatcher.MatchedGroup, Integer>(matchedGroup, matchedNodes));
        int len = matchedGroup.matchEnd - matchedGroup.matchBegin;
        if (len == matchedNodes) {
          matchedStates.addStates(bid, next);
        } else {
          matchedStates.addState(bid, this);
        }
        return true;
      }
      return false;
    }

    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume)
    {
      // Try to match previous node/nodes exactly
      if (consume) {
        // First element is group that is matched, second is number of nodes matched so far
        Pair<SequenceMatcher.MatchedGroup, Integer> backRefState =
                (Pair<SequenceMatcher.MatchedGroup, Integer>) matchedStates.getMatchStateInfo(bid, this);
        if (backRefState == null) {
          // Haven't tried to match this node before, try now
          // Get element and return if it matched or not
          SequenceMatcher.MatchedGroup matchedGroup = matchedStates.getMatchedGroup(bid, captureGroupId);
          if (matchedGroup != null) {
            // See if the first node matches
            if (matchedGroup.matchEnd > matchedGroup.matchBegin) {
              boolean matched = match(bid, matchedStates, matchedGroup, 0);
              return matched;
            } else {
              // TODO: Check handling of previous nodes that are zero elements?
              return super.match(bid, matchedStates, consume);
            }
          }
          return false;
        } else {
          SequenceMatcher.MatchedGroup matchedGroup = backRefState.first();
          int matchedNodes = backRefState.second();
          boolean matched = match(bid, matchedStates, matchedGroup, matchedNodes);
          return matched;
        }
      } else {
        // Not consuming, just add this state back to list of states to be processed
        matchedStates.addState(bid, this);
        return false;
      }
    }
  }

  /**
   * State for matching the start of a group
   */
  protected static class GroupStartState extends State {
    int captureGroupId;

    public GroupStartState(int captureGroupId, State startState)
    {
      this.captureGroupId = captureGroupId;
      add(startState);
    }

    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume)
    {
      // We only mark start when about to consume elements
      if (consume) {
        // Start of group, mark start
        matchedStates.setGroupStart(bid, captureGroupId);
        return super.match(bid, matchedStates, consume);
      } else {
        // Not consuming, just add this state back to list of states to be processed
        matchedStates.addState(bid, this);
        return false;
      }
    }
  }

  /**
   * State for matching the end of a group
   */
  protected static class GroupEndState extends State {
    int captureGroupId;

    public GroupEndState(int captureGroupId)
    {
      this.captureGroupId = captureGroupId;
    }

    protected <T> boolean match(int bid, SequenceMatcher.MatchedStates<T> matchedStates, boolean consume)
    {
      // Opposite of GroupStartState
      // Don't do anything when we are about to consume an element
      // Only we are done consuming, and preparing the go on to the next element
      // do we mark the end of the group
      if (consume) {
        return false;
      } else {
        matchedStates.setGroupEnd(bid, captureGroupId);
        return super.match(bid, matchedStates, consume);
      }
    }
  }

  /**
   * Represents a incomplete NFS with start State and a set of unlinked out states
   */
  protected static class Frag {
    State start;
    Set<State> out;

    protected Frag() {
 //     this(new State());
    }

    protected Frag(State start) {
      this.start = start;
      this.out = new LinkedHashSet<State> ();
      start.updateOutStates(out);
    }

    protected Frag(State start, Set<State> out) {
      this.start = start;
      this.out = out;
    }

    protected void add(State outState) {
      if (out == null) {
        out = new LinkedHashSet<State>();
      }
      out.add(outState);
    }

    protected void add(Collection<State> outStates) {
      if (out == null) {
        out = new LinkedHashSet<State>();
      }
      out.addAll(outStates);
    }

    // Connect frag f to the out states of this frag
    // the out states of this frag is updated to be the out states of f
    protected void connect(Frag f) {
      for (State s:out) {
        s.add(f.start);
      }
      out = f.out;
    }

    // Connect state to the out states of this frag
    // the out states of this frag is updated to be the out states of state
    protected void connect(State state) {
      for (State s:out) {
        s.add(state);
      }
      out = new LinkedHashSet<State>();
      state.updateOutStates(out);
/*      if (state.next != null) {
        out.addAll(state.next);
      } else {
        out.add(state);
      } */
    }
  }
}
