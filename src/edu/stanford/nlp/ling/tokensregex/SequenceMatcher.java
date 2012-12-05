package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.logging.Logger;

import static edu.stanford.nlp.ling.tokensregex.SequenceMatcher.FindType.FIND_NONOVERLAPPING;

/**
 * <p>Generic sequence matcher</p>
 *
 * <p>
 * Similar to Java's <code>Matcher</code> except it matches sequences over an arbitrary type <code>T</code>
 *   instead of characters
 * For a type <code>T</code> to be matchable, it has to have a corresponding <code>NodePattern<T></code> that indicates
 *    whether a node is matched or not
 * </p>
 *
 * <p>
 * A matcher is created as follows:
 * <pre><code>
 *   SequencePattern<T> p = SequencePattern<T>.compile("...");
 *   SequencePattern<T> m = p.getMatcher(List<T> sequence);
 * </code></pre>
 * </p>
 *
 * <p>
 * Functions for searching
 * <pre><code>
 *    boolean matches()
 *    boolean find()
 *    boolean find(int start)
 * </code></pre>
 * Functions for retrieving matched patterns
 * <pre><code>
 *    int groupCount()
 *    List<T> groupNodes(), List<T> groupNodes(int g)
 *    String group(), String group(int g)
 *    int start(), int start(int g), int end(), int end(int g)
 * </code></pre>
 * Functions for defining the region of the sequence to search over
 *  (default region is entire sequence)
 * <pre><code>
 *     void region(int start, int end)
 *     int regionStart()
 *     int regionEnd()
 * </code></pre>
 * </p>
 *
 * <p>
 * NOTE: When find is used, matches are attempted starting from the specified start index of the sequence
 *   The match with the earliest starting index is returned. 
 * </p>
 *
 * @author Angel Chang
 */
public class SequenceMatcher<T> extends BasicSequenceMatchResult<T> {
  private static final Logger logger = Logger.getLogger(SequenceMatcher.class.getName());
//  List<? extends T> elements;           // Sequence we are trying to match
  SequencePattern pattern;    // Pattern we are trying to match against

//  MatchedGroup[] matchedGroups;    // Groups that we matched
  boolean matchingCompleted = false;
  boolean matched = false;
  int nextMatchStart = 0;

  int regionStart = 0;
  int regionEnd = -1;

  /**
   * Type of search to perform
   * FIND_NONOVERLAPPING - Find nonoverlapping matches (default)
   * FIND_ALL - Find all potential matches (TODO: Check and fix implementation)
   */
  public enum FindType { FIND_NONOVERLAPPING, FIND_ALL }
  FindType findType = FIND_NONOVERLAPPING;

  // For FIND_ALL
  Iterator<Integer> curMatchIter = null;
  MatchedStates<T> curMatchStates = null;

  protected SequenceMatcher(SequencePattern pattern, List<? extends T> elements)
  {
    this.pattern = pattern;
    // NOTE: It is important elements DO NOT change as we do matches
    // TODO: Should we just make a copy of the elements?  
    this.elements = elements;
    this.regionEnd = elements.size();
    this.score = pattern.priority;
    this.varGroupBindings = pattern.varGroupBindings;
    matchedGroups = new MatchedGroup[pattern.totalGroups];
  }

  /**
   * Reset the matcher and then searches for pattern at the specified start index
   * @param start - Index at which to start the search
   * @return true if a match is found (false otherwise)
   * @throws IndexOutOfBoundsException if start is < 0 or larger then the size of the sequence
   */
  public boolean find(int start)
  {
    if (start < 0 || start > elements.size()) {
      throw new IndexOutOfBoundsException("Invalid region start=" + start + ", need to be between 0 and " + elements.size());
    }
    reset();
    return find(start, false);
  }

  protected boolean find(int start, boolean matchStart)
  {
    boolean match = false;
    matched = false;
    matchingCompleted = false;
    if (matchStart)  {
      match = findMatchStart(start, false);
    } else {
      for (int i = start; i < regionEnd; i++) {
        match = findMatchStart(i, false);
        if (match) {
          break;
        }
      }
    }
    matched = match;
    matchingCompleted = true;
    if (matched) {
      nextMatchStart = (findType == FindType.FIND_NONOVERLAPPING)? end(): start+1;
    } else {
      nextMatchStart = -1;
    }
    return match;
  }

  /**
   * Searches for pattern in the region starting
   *  at the next index
   * @return true if a match is found (false otherwise)
   */
  private boolean findNextNonOverlapping()
  {
    if (nextMatchStart < 0) { return false; }
    return find(nextMatchStart, false);
  }

  private boolean findNextAll()
  {
    if (curMatchIter != null && curMatchIter.hasNext()) {
      int next = curMatchIter.next();
      curMatchStates.setMatchedGroups(next);
      return true;
    }
    if (nextMatchStart < 0) { return false; }
    boolean matched = find(nextMatchStart, false);
    if (matched) {
      Collection<Integer> matchedBranches = curMatchStates.getMatchIndices();
      Iterator<Integer> curMatchIter = matchedBranches.iterator();
      int next = curMatchIter.next();
      curMatchStates.setMatchedGroups(next);
    }
    return matched;
  }

  public boolean find()
  {
    switch (findType) {
      case FIND_NONOVERLAPPING:
        return findNextNonOverlapping();
      case FIND_ALL:
        return findNextAll();
      default:
        throw new UnsupportedOperationException("Unsupported findType " + findType);
    }
  }

  protected boolean findMatchStart(int start, boolean matchAllTokens)
  {
    switch (findType) {
      case FIND_NONOVERLAPPING:
        return findMatchStartBacktracking(start, matchAllTokens);
      case FIND_ALL:
        // TODO: Should use backtracking here too, need to keep track of todo stack
        // so we can recover after finding a match
        return findMatchStartNoBacktracking(start, matchAllTokens);
      default:
        throw new UnsupportedOperationException("Unsupported findType " + findType);
    }
  }

  // Does not do backtracking - alternative matches are stored as we go
  protected boolean findMatchStartNoBacktracking(int start, boolean matchAllTokens)
  {
    boolean matchAll = true;
    MatchedStates cStates = getStartStates();
    // Save cStates for FIND_ALL ....
    curMatchStates = cStates;
    for(int i = start; i < regionEnd; i++){
      boolean match = cStates.match(i);
      if (cStates == null || cStates.size() == 0) {
        break;
      }
      if (!matchAllTokens) {
        if ((matchAll && cStates.isAllMatch())
            || (!matchAll && cStates.isMatch())) {
          cStates.completeMatch();
          return true;
        }
      }
    }
    cStates.completeMatch();
    return cStates.isMatch();
  }

  // Does some backtracking...
  protected boolean findMatchStartBacktracking(int start, boolean matchAllTokens)
  {
    boolean matchAll = true;
    int branchLimit = 2;
    Stack<MatchedStates> todo = new Stack<MatchedStates>();
    MatchedStates cStates = getStartStates();
    cStates.curPosition = start-1;
    todo.push(cStates);
    while (!todo.empty()) {
      cStates = todo.pop();
      int s = cStates.curPosition+1;
      for(int i = s; i < regionEnd; i++){
        boolean match = cStates.match(i);
        if (cStates == null || cStates.size() == 0) {
          break;
        }
        if (!matchAllTokens) {
          if ((matchAll && cStates.isAllMatch())
              || (!matchAll && cStates.isMatch())) {
            cStates.completeMatch();
            return true;
          }
        }
        if (branchLimit >= 0 && cStates.branchSize() > branchLimit) {
          MatchedStates s2 = cStates.split(branchLimit);
          todo.push(s2);
        }
      }
      if (cStates.isMatch()) {
        cStates.completeMatch();
        return true;
      }
    }
    return false;
  }

  public boolean matches()
  {
    matched = false;
    matchingCompleted = false;
    boolean status = findMatchStart(0, true);
    if (status) {
      // Check if entire region is matched
      status = ((matchedGroups[0].matchBegin == regionStart) && (matchedGroups[0].matchEnd == regionEnd));
    }
    matchingCompleted = true;
    matched = status;
    return status;
  }

  private void clearMatched()
  {
    for (int i = 0; i < matchedGroups.length; i++) {
      matchedGroups[i] = null;
    }
  }

  private String getStateMessage()
  {
    if (!matchingCompleted) {
      return "Matching not completed";
    } else if (!matched) {
      return "No match found";
    } else {
      return "Match successful";
    }
  }

  /**
   * Set region to search in
   * @param start - start index
   * @param end - end index (exclusive)
   */
  public void region(int start, int end)
  {
    if (start < 0 || start > elements.size()) {
      throw new IndexOutOfBoundsException("Invalid region start=" + start + ", need to be between 0 and " + elements.size());
    }
    if (end < 0 || end > elements.size()) {
      throw new IndexOutOfBoundsException("Invalid region end=" + end + ", need to be between 0 and " + elements.size());
    }
    if (start > end) {
      throw new IndexOutOfBoundsException("Invalid region end=" + end + ", need to be larger then start=" + start);      
    }
    this.regionStart = start;
    this.nextMatchStart = start;
    this.regionEnd = end;
  }

  public int regionEnd()
  {
    return regionEnd;
  }

  public int regionStart()
  {
    return regionStart;
  }

  public BasicSequenceMatchResult<T> toBasicSequenceMatchResult() {
    if (matchingCompleted && matched) {
      return super.toBasicSequenceMatchResult();
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  public int start(int group) {
    if (matchingCompleted && matched) {
      return super.start(group);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  public int end(int group) {
    if (matchingCompleted && matched) {
      return super.end(group);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  public List<? extends T> groupNodes(int group) {
    if (matchingCompleted && matched) {
      return super.groupNodes(group);
    } else {
      String message = getStateMessage();
      throw new IllegalStateException(message);
    }
  }

  /**
   * Clears matcher
   * - Clears matched groups, reset region to be entire sequence
   */
  public void reset() {
    regionStart = 0;
    regionEnd = elements.size();
    nextMatchStart = 0;
    matchingCompleted = false;
    matched = false;
    clearMatched();
  }

  /**
   * Returns the ith element
   * @param i - index
   * @return ith element
   */
  public T get(int i)
  {
    return elements.get(i);
  }

  private MatchedStates<T> getStartStates()
  {
    return new MatchedStates<T>(this, pattern.root);
  }

  /**
   * Contains information about a branch of running the NFA matching
   */
  private static class BranchState
  {
    // Branch id
    int bid;
    // Parent branch state
    BranchState parent;
    // Map of group id to matched group
    Map<Integer,MatchedGroup> matchedGroups;
    // Map of state to pair indicating sequence index and whether the match was complete
    // Used for states corresponding to
    //    repeating patterns: key is RepeatState, object is Pair<Integer,Boolean>
    //    multinode patterns: key is MultiNodePatternState, object is Interval<Integer>
    Map<SequencePattern.State, Object> matchStateInfo;
    //Map<SequencePattern.State, Pair<Integer,Boolean>> matchStateCount;

    public BranchState(int bid) {
      this(bid, null);
    }

    public BranchState(int bid, BranchState parent) {
      this.bid = bid;
      this.parent = parent;
      if (parent != null) {
        if (parent.matchedGroups != null) {
          matchedGroups = new LinkedHashMap<Integer,MatchedGroup>(parent.matchedGroups);
        }
/*        if (parent.matchStateCount != null) {
          matchStateCount = new LinkedHashMap<SequencePattern.State, Pair<Integer,Boolean>>(parent.matchStateCount);
        }      */
        if (parent.matchStateInfo != null) {
          matchStateInfo = new LinkedHashMap<SequencePattern.State, Object>(parent.matchStateInfo);
        }
      }
    }

  }

  protected static class State
  {
    int bid;
    SequencePattern.State tstate;

    public State(int bid, SequencePattern.State tstate) {
      this.bid = bid;
      this.tstate = tstate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      State state = (State) o;

      if (bid != state.bid) {
        return false;
      }
      if (tstate != null ? !tstate.equals(state.tstate) : state.tstate != null) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = bid;
      result = 31 * result + (tstate != null ? tstate.hashCode() : 0);
      return result;
    }
  }

  protected static class MatchedStates<T>
  {
    // Sequence matcher with pattern that we are matching against and sequence
    SequenceMatcher<T> matcher;
    // set of old states along with their branch ids (used to avoid reallocating mem)
    List<State> oldStates;
    // new states to be explored (along with their branch ids)
    List<State> states;
    // Current position to match
    int curPosition = -1;

    // Index of global branch id to pair of parent branch id and branch index
    // (the branch index is with respect to parent, from 1 to number of branches the parent has)
    Index<Pair<Integer,Integer>> bidIndex = new HashIndex<Pair<Integer,Integer>>();
    // Map of branch id to branch state
    Map<Integer,BranchState> branchStates;

    protected MatchedStates(SequenceMatcher<T> matcher, SequencePattern.State state)
    {
      this(matcher);
      int bid = bidIndex.indexOf(new Pair<Integer,Integer>(-1,0), true);
      states.add(new State(bid,state));
    }

    private MatchedStates(SequenceMatcher<T> matcher) {
      this.matcher = matcher;
      states = new ArrayList<State>();
      oldStates = new ArrayList<State>();
      branchStates = new HashMap<Integer,BranchState>();
    }

    protected MatchedStates split(int branchLimit)
    {
      Set<Integer> curBidSet = new HashSet<Integer>();
      for (State state:states) {
        curBidSet.add(state.bid);
      }
      List<Integer> bids = new ArrayList<Integer>(curBidSet);
      Collections.sort(bids, new Comparator<Integer>() {
        public int compare(Integer o1, Integer o2) {
          int res = compareMatches(o1, o2);
          return res;
        }
      });

      MatchedStates<T> newStates = new MatchedStates<T>(matcher);
      // TODO: Copy branchStates?
      newStates.branchStates.putAll(branchStates);
      int v = Math.min(branchLimit, (bids.size()+1)/2);
      Set<Integer> keepBidSet = new HashSet<Integer>();
      keepBidSet.addAll(bids.subList(0, v));
      swapAndClear();
      for (State s:oldStates) {
        if (keepBidSet.contains(s.bid)) {
          states.add(s);
        } else {
          newStates.states.add(s);
        }
      }
      newStates.curPosition = curPosition;
      newStates.bidIndex.addAll(bidIndex.objectsList());
      condense();
      newStates.condense();
      return newStates;
    }

    protected List<? extends T> elements()
    {
      return matcher.elements;
    }

    protected T get()
    {
      return matcher.get(curPosition);
    }

    protected int size()
    {
      return states.size();
    }

    protected int branchSize()
    {
      return branchStates.size();
    }

    private void swap()
    {
      List<State> tmpStates = oldStates;
      oldStates = states;
      states = tmpStates;
    }

    private void swapAndClear()
    {
      swap();
      states.clear();
    }

    // Attempts to match element at the specified position
    private boolean match(int position)
    {
      curPosition = position;
      boolean matched = false;

      swapAndClear();
      // Start with old state, and try to match next element
      // New states to search after successful match will be updated during the match process
      for (State state:oldStates) {
        if (state.tstate.match(state.bid, this)) {
          matched = true;
        }
      }

      // Run NFA to process non consuming states
      boolean done = false;
      while (!done) {
        swapAndClear();
        boolean matched0 = false;
        for (State state:oldStates) {
          if (state.tstate.match0(state.bid, this)) {
            matched0 = true;
          }
        }
        done = !matched0; 
      }

      condense();
      return matched;
    }

    private void condense()
    {
      // Trim out unneeded states info
      // Current branch ids are in bids
      logger.finest("Condense matched state: curPosition=" + curPosition
              + ", totalTokens=" + matcher.elements.size()
              + ", nStates=" + states.size());
      Set<Integer> curBidSet = new HashSet<Integer>();
      Set<Integer> keepBidStates = new HashSet<Integer>();
      for (State state:states) {
        curBidSet.add(state.bid);
        keepBidStates.add(state.bid);
      }
      for (int bid:curBidSet) {
        BranchState bs = getBranchState(bid);
        if (bs != null) {
          keepBidStates.add(bs.bid);
        }
      }
      Collection<Integer> curBidStates = new ArrayList<Integer>(branchStates.keySet());
      for (int bid:curBidStates) {
        if (!keepBidStates.contains(bid)) {
          logger.finest("Remove state for bid=" + bid);
          branchStates.remove(bid);
        }
      }
      logger.finest("Condense matched state: oldBidStates=" + curBidStates.size()
              + ", newBidStates=" + branchStates.size()
              + ", curBidSet=" + curBidSet.size());
    }

    protected int compareMatches(int bid1, int bid2)
    {
      if (bid1 == bid2) return 0;
      List<Integer> p1 = getParents(bid1);
      p1.add(bid1);
      List<Integer> p2 = getParents(bid2);
      p2.add(bid2);
      int n = Math.min(p1.size(), p2.size());
      for (int i = 0; i < n; i++) {
        if (p1.get(i) < p2.get(i)) return -1;
        if (p1.get(i) > p2.get(i)) return 1;
      }
      if (p1.size() < p2.size()) return -1;
      if (p1.size() > p2.size()) return 1;
      return 0;
    }

    private List<Integer> getParents(int bid)
    {
      List<Integer> pids = new ArrayList<Integer>();
      Pair<Integer,Integer> p = bidIndex.get(bid);
      while (p != null && p.first() >= 0) {
        pids.add(p.first());
        p = bidIndex.get(p.first());
      }
      Collections.reverse(pids);
      return pids;
    }

    private int getMatchIndex()
    {
      for (int i = 0; i < states.size(); i++) {
        State state = states.get(i);
        if (state.tstate.equals(SequencePattern.MATCH_STATE)) {
          return i;
        }
      }
      return -1;
    }

    private Collection<Integer> getMatchIndices()
    {
      Set<Integer> allMatchIndices = new HashSet<Integer>();
      for (int i = 0; i < states.size(); i++) {
        State state = states.get(i);
        if (state.tstate.equals(SequencePattern.MATCH_STATE)) {
          allMatchIndices.add(i);
        }
      }
      return allMatchIndices;
    }

    private int selectMatchIndex()
    {
      int best = -1;
      int bestbid = -1;
      for (int i = 0; i < states.size(); i++) {
        State state = states.get(i);
        if (state.tstate.equals(SequencePattern.MATCH_STATE)) {
          if (best < 0) {
            best = i;
            bestbid = state.bid;
          } else {
            // Compare if this match is better?
            int bid = state.bid;
            if (compareMatches(bestbid, bid) > 0) {
              bestbid = bid;
              best = i;
            }
          }
        }
      }
      return best;
    }

    private void completeMatch()
    {
      int matchStateIndex = selectMatchIndex();
      setMatchedGroups(matchStateIndex);
    }

    private void setMatchedGroups(int matchStateIndex)
    {
      matcher.clearMatched();
      if (matchStateIndex >= 0) {
          State state = states.get(matchStateIndex);
          int bid = state.bid;
          BranchState bs = getBranchState(bid);
          if (bs != null) {
            Map<Integer,MatchedGroup> matchedGroups = bs.matchedGroups;
            if (matchedGroups != null) {
              for (int group:matchedGroups.keySet()) {
                matcher.matchedGroups[group] = matchedGroups.get(group);
              }
            }
          }

      }
    }

    private boolean isAllMatch()
    {
      boolean allMatch = true;
      if (states.size() > 0) {
        for (State state:states) {
          if (!state.tstate.equals(SequencePattern.MATCH_STATE)) {
            allMatch = false;
            break;
          }
        }
      } else {
        allMatch = false;
      }
      return allMatch;
    }

    private boolean isMatch()
    {
      int matchStateIndex = getMatchIndex();
      return (matchStateIndex >= 0);
    }

    protected BranchState getBranchState(int bid)
    {
      BranchState bs = branchStates.get(bid);
      if (bs == null) {
        BranchState pbs = null;
        int id = bid;
        while (pbs == null && id >= 0) {
          Pair<Integer,Integer> p = bidIndex.get(id);
          id = p.first;
          pbs = branchStates.get(id);
        }
        bs = pbs;
      }
      return bs;
    }

    protected BranchState getBranchState(int bid, boolean add)
    {
      BranchState bs = getBranchState(bid);
      if (add) {
        if (bs == null) {
          bs = new BranchState(bid);
        } else if (bs.bid != bid) {
          bs = new BranchState(bid, bs);
        }
        branchStates.put(bid, bs);
      }
      return bs;
    }

    protected Map<Integer,MatchedGroup> getMatchedGroups(int bid, boolean add)
    {
      BranchState bs = getBranchState(bid, add);
      if (bs == null) {
        return null;
      }
      if (add && bs.matchedGroups == null) {
        bs.matchedGroups = new LinkedHashMap<Integer,MatchedGroup>();
      }
      return bs.matchedGroups;
    }

    protected MatchedGroup getMatchedGroup(int bid, int groupId)
    {
      Map<Integer,MatchedGroup> map = getMatchedGroups(bid, false);
      if (map != null) {
        return map.get(groupId);
      } else {
        return null;
      }
    }

    protected void setGroupStart(int bid, int captureGroupId)
    {
      if (captureGroupId >= 0) {
        Map<Integer,MatchedGroup> matchedGroups = getMatchedGroups(bid, true);
        MatchedGroup mg = matchedGroups.get(captureGroupId);
        if (mg != null) {
          // This is possible if we have patterns like "( ... )+" in which case multiple nodes can match as the subgroup
          // We will match the first occurence and use that as the subgroup  (Java uses the last match as the subgroup)
          logger.fine("Setting matchBegin=" + curPosition + ": Capture group " + captureGroupId + " already exists: " + mg);
        }
        matchedGroups.put(captureGroupId, new MatchedGroup(curPosition, -1));
      }
    }

    protected void setGroupEnd(int bid, int captureGroupId)
    {
      if (captureGroupId >= 0) {
        Map<Integer,MatchedGroup> matchedGroups = getMatchedGroups(bid, true);
        MatchedGroup mg = matchedGroups.get(captureGroupId);
        int end = curPosition+1;
        if (mg != null) {
          if (mg.matchEnd == -1) {
            matchedGroups.put(captureGroupId, new MatchedGroup(mg.matchBegin, end));
          } else {
            if (mg.matchEnd != end) {
              logger.warning("Cannot set matchEnd=" + end + ": Capture group " + captureGroupId + " already ended: " + mg);
            }
          }
        } else {
          logger.warning("Cannot set matchEnd=" + end + ": Capture group " + captureGroupId + " is null");
        }
      }
    }

    protected void clearGroupStart(int bid, int captureGroupId)
    {
      if (captureGroupId >= 0) {
        Map<Integer,MatchedGroup> matchedGroups = getMatchedGroups(bid, false);
        if (matchedGroups != null) {
          matchedGroups.remove(captureGroupId);
        }
      }
    }

    protected int getBranchId(int bid, int nextBranchIndex, int nextTotal)
    {
      if (nextBranchIndex <= 0 || nextBranchIndex > nextTotal) {
        throw new IllegalArgumentException("Invalid nextBranchIndex=" + nextBranchIndex + ", nextTotal=" + nextTotal);
      }
      if (nextTotal == 1) {
        return bid;
      } else {
        Pair<Integer,Integer> p = new Pair<Integer,Integer>(bid, nextBranchIndex);
        int i = bidIndex.indexOf(p);
        if (i < 0) {
          for (int j = 0; j < nextTotal; j++) {
            bidIndex.add(new Pair<Integer,Integer>(bid, j+1));
          }
          i = bidIndex.indexOf(p);
        }
        return i;
      }
    }

    protected void addStates(int bid, Collection<SequencePattern.State> newStates)
    {
      int i = 0;
      for (SequencePattern.State s:newStates) {
        i++;
        int id = getBranchId(bid, i, newStates.size());
        states.add(new State(id, s));
      }
    }

    protected void addState(int bid, SequencePattern.State state)
    {
      this.states.add(new State(bid, state));
    }
    
    protected Map<SequencePattern.State,Object> getMatchStateInfo(int bid, boolean add)
    {
      BranchState bs = getBranchState(bid, add);
      if (bs == null) {
        return null;
      }
      if (add && bs.matchStateInfo == null) {
        bs.matchStateInfo = new LinkedHashMap<SequencePattern.State,Object>();
      }
      return bs.matchStateInfo;
    }

    protected Object getMatchStateInfo(int bid, SequencePattern.State node)
    {
      Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, false);
      return (matchStateInfo != null)? matchStateInfo.get(node):null;
    }

    protected void removeMatchStateInfo(int bid, SequencePattern.State node)
    {
      Object obj = getMatchStateInfo(bid, node);
      if (obj != null) {
        Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, true);
        matchStateInfo.remove(node);
      }
    }

    protected void setMatchStateInfo(int bid, SequencePattern.State node, Object obj)
    {
      Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, true);
      matchStateInfo.put(node, obj);
    }

    protected void startMatchedCountInc(int bid, SequencePattern.State node)
    {
      Map<SequencePattern.State,Object> matchStateCount = getMatchStateInfo(bid, true);
      Pair<Integer,Boolean> p = (Pair<Integer,Boolean>) matchStateCount.get(node);
      if (p == null) {
        matchStateCount.put(node, new Pair<Integer,Boolean>(1,false));
      } else {
        matchStateCount.put(node, new Pair<Integer,Boolean>(p.first() + 1,false));
      }
    }

    protected int endMatchedCountInc(int bid, SequencePattern.State node)
    {
      Map<SequencePattern.State,Object> matchStateCount = getMatchStateInfo(bid, false);
      if (matchStateCount == null) { return 0; }
      matchStateCount = getMatchStateInfo(bid, true);
      Pair<Integer,Boolean> p = (Pair<Integer,Boolean>) matchStateCount.get(node);
      if (p != null) {
        int v = p.first();
        matchStateCount.put(node, new Pair<Integer,Boolean>(v,true));
        return v;
      } else {
        return 0;
      }
    }

    protected void clearMatchedCount(int bid, SequencePattern.State node)
    {
      removeMatchStateInfo(bid, node);
    }
    
    protected void setMatchedInterval(int bid, SequencePattern.State node, Interval<Integer> interval)
    {
      Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, true);
      Interval<Integer> p = (Interval<Integer>) matchStateInfo.get(node);
      if (p == null) {
        matchStateInfo.put(node, interval);
      } else {
        logger.warning("Interval already exists for bid=" + bid);
      }
    }

    protected Interval<Integer> getMatchedInterval(int bid, SequencePattern.State node)
    {
      Map<SequencePattern.State,Object> matchStateInfo = getMatchStateInfo(bid, true);
      Interval<Integer> p = (Interval<Integer>) matchStateInfo.get(node);
      return p;
    }
  }

}
