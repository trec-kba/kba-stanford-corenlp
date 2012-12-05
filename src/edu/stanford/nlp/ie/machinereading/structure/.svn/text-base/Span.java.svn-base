package edu.stanford.nlp.ie.machinereading.structure;

import java.io.Serializable;

import edu.stanford.nlp.util.Pair;

/**
 * Stores the offsets for a span of text
 * Offsets may indicate either token or byte positions
 * Start is inclusive, end is exclusive
 * @author Mihai 
 */
public class Span implements Serializable {
  private static final long serialVersionUID = -3861451490217976693L;

  private int start;
  private int end;
  
  public Span(int s, int e) {
    start = s;
    end = e;
  }
  
  /**
   * Creates a span that encloses all spans in the argument list.  Behavior is undefined if given no arguments.
   */
  public Span(Span... spans) {
    this(Integer.MAX_VALUE, Integer.MIN_VALUE);

    for (Span span : spans) {
      expandToInclude(span);
    }
  }
  
  public int start() { return start; }
  public int end() { return end; }
  
  public void setStart(int s) { start = s; }
  public void setEnd(int e) { end = e; }
  
  @Override
  public boolean equals(Object other) {
    if(! (other instanceof Span)) return false;
    Span otherSpan = (Span) other;
    if(start == otherSpan.start && end == otherSpan.end){
      return true;
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    return (new Pair<Integer,Integer>(start,end)).hashCode();
  }
  
  @Override
  public String toString() {
    return "[" + start + "," + end + ")";
  }
  
  public void expandToInclude(Span otherSpan) {
    if (otherSpan.start() < start) {
      setStart(otherSpan.start());
    }
    if (otherSpan.end() > end) {
      setEnd(otherSpan.end());
    }
  }

  /**
   * Returns true if this span contains otherSpan.  Endpoints on spans may match.
   */
  public boolean contains(Span otherSpan) {
    return this.start <= otherSpan.start && otherSpan.end <= this.end;
  }
}
