package edu.stanford.nlp.time;

import edu.stanford.nlp.util.*;

import edu.stanford.nlp.util.Interval;
import org.joda.time.*;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import java.util.*;

import static edu.stanford.nlp.time.SUTime.TimeLabelType.*;

/**
 * SUTime is a collection of data structures to represent various temporal
 * concepts and operations between them.
 *
 * The main
 * Basic time concepts Relative/Absolute Date/Time (last friday/2011-05-13)
 * Grounding - Resolution of relative to absolute time. Calendar/Chronology
 * Timezone
 * 
 * Different types of time expressions Point - A specific instance in time - In
 * most cases, we only know partial information (with a certain granularity)
 * about a point in time (8:00pm) Duration - A length of time (3 days) Interval
 * - A range of time with start and end points Set - A set of time: Can be
 * periodic (Friday every week) or union (Thursday or Friday)
 * 
 * There are two types of ranges with regard to temporal expressions - Interval:
 * The time expression refers to an range - Uncertainty/granularity: The time
 * expression refers to some point in the interval (not necessarily over the
 * whole interval) Usually when a particular event happens (like sunrise)
 * 
 * Time modifiers - early/late/mid - approximate
 * <p>
 * References:
 * <p>
 * TimeEx3 specifications
 * <br>
 * <a href="http://www.timeml.org/site/publications/timeMLdocs/timeml_1.2.1.html#timex3">
 * http://www.timeml.org/site/publications/timeMLdocs/timeml_1.2.1.html#timex3</a>
 * <br>
 * XSD: <a href="http://www.timeml.org/timeMLdocs/TimeML.xsd">
 *     http://www.timeml.org/timeMLdocs/TimeML.xsd</a>
 * <br>
 * ISO8601: <a href="http://en.wikipedia.org/wiki/ISO_8601">
 *     http://en.wikipedia.org/wiki/ISO_8601</a>
 * <p>
 * Example XML:
 * <code>In Washington &lt;TIMEX3 tid="t1" TYPE="DATE" VAL="PRESENT_REF"
 * temporalFunction="true" valueFromFunction="tf1"
 * anchorTimeID="t0"&gt;today&lt;/TIMEX3&gt;, the Federal Aviation Administration
 * released air traffic control tapes from the night the TWA Flight eight
 * hundred went down.
 * </code>
 * <p>
 * GUTIME/TimeML specs:
 * <a href="http://www.timeml.org/site/tarsqi/modules/gutime/index.html">
 * <a href="http://www.timeml.org/site/publications/timeMLdocs/timeml_1.2.1.html#timex3">
 * http://www.timeml.org/site/publications/timeMLdocs/timeml_1.2.1.html#timex3</a>
 * <p>
 * <pre><code>
 * attributes ::= tid type [functionInDocument] [beginPoint] [endPoint]
 *                [quant] [freq] [temporalFunction] (value | valueFromFunction)
 *                [mod] [anchorTimeID] [comment]
 * 
 * tid ::= ID
 *   {tid ::= TimeID
 *    TimeID ::= t<integer>}
 * type ::= 'DATE' | 'TIME' | 'DURATION' | 'SET'
 * beginPoint ::= IDREF
 *    {beginPoint ::= TimeID}
 * endPoint ::= IDREF
 *    {endPoint ::= TimeID}
 * quant ::= CDATA
 * freq ::= Duration
 * functionInDocument ::= 'CREATION_TIME' | 'EXPIRATION_TIME' | 'MODIFICATION_TIME' |
 *                        'PUBLICATION_TIME' | 'RELEASE_TIME'| 'RECEPTION_TIME' |
 *                        'NONE' {default, if absent, is 'NONE'}
 * temporalFunction ::= 'true' | 'false' {default, if absent, is 'false'}
 *    {temporalFunction ::= boolean}
 * value ::= Duration | Date | Time | WeekDate | WeekTime | Season | PartOfYear | PaPrFu
 * valueFromFunction ::= IDREF
 *    {valueFromFunction ::= TemporalFunctionID
 * TemporalFunctionID ::= tf<integer>}
 * mod ::= 'BEFORE' | 'AFTER' | 'ON_OR_BEFORE' | 'ON_OR_AFTER' |'LESS_THAN' | 'MORE_THAN' |
 *         'EQUAL_OR_LESS' | 'EQUAL_OR_MORE' | 'START' | 'MID' | 'END' | 'APPROX'
 * anchorTimeID ::= IDREF
 *   {anchorTimeID ::= TimeID}
 * comment ::= CDATA
 * </code></pre>
 *
 * <p>
 * Use {@link TimeAnnotator} to annotate
 * 
 * Time corpus: (see also http://timeml.org/site/timebank/timebank.html)
 * LDC2006T08 TimeBank 1.2 (Uses TIMEX3)
 * LDC2005T07 ACE Time Normalization
 * (TERN) 2004 English Training Data v 1.0 (Uses TIMEX2) GUTime achieved .85,
 * .78, and .82 F-measure for timex2, text, and val fields LDC2010T18 ACE Time
 * Normalization (TERN) 2004 English Evaluation Data V1.0
 * 
 * @author Angel Chang
 */
public class SUTime {
  // TODO:
  // 2. Number parsing
  // - Improve Number detection/normalization
  // - Handle four-years, one thousand two hundred and sixty years
  // - Currently custom word to number combo - integrate with Number classifier,
  // QuantifiableEntityNormalizer
  // - Stop repeated conversions of word to numbers
  // 3. Durations
  // - Underspecified durations
  // 4. Date Time
  // - Patterns
  // -- 1st/last week(end) of blah blah
  // -- Don't treat all 3 to 5 as times
  // - Holidays
  // - Too many classes - reduce number of classes
  // 5. Nest time expressions
  // - Before annotating: Can remove nested time expressions
  // - After annotating: rules to combine time expressions
  // 6. Set of times (Timex3 standard is weird, timex2 makes more sense?)
  // - freq, quant
  // 7. Ground with respect to reference time - figure out what is reference
  // time to use for what
  // - news... things happen in the past, so favor resolving to past?
  // - Use heuristics from GUTime to figure out direction to resolve to
  // - tids for anchortimes...., valueFromFunctions for resolved relative times
  // (option to keep some nested times)?
  // 8. Composite time patterns
  // - Composite time operators
  // 9. Ranges
  // - comparing times (before, after, ...
  // - intersect, mid, resolving
  // - specify clear start/end for range (sonal)
  // 10. Clean up formatting
  // ISO/Timex3/Custom
  // 11. Move language (English) specific stuff into one class (at least away
  // from SUTime)
  // 12. Keep modifiers
  // 13. Handle mid- (token not separated)
  // 14. future, plurals
  // 15. Resolve to future.... with year specified....
  // 16. Check recursive calls

  public static enum TimexType {
    DATE, TIME, DURATION, SET
  };

  public static enum TimexMod {
    BEFORE("<"), AFTER(">"), ON_OR_BEFORE("<="), ON_OR_AFTER("<="), LESS_THAN("<"), MORE_THAN(">"), EQUAL_OR_LESS("<="), EQUAL_OR_MORE(">="), START, MID, END, APPROX("~"), EARLY /* GUTIME */, LATE; /* GUTIME */
    String symbol;

    TimexMod() {
    }

    TimexMod(String symbol) {
      this.symbol = symbol;
    }

    public String getSymbol() {
      return symbol;
    }
  };

  public static enum TimexDocFunc {
    CREATION_TIME, EXPIRATION_TIME, MODIFICATION_TIME, PUBLICATION_TIME, RELEASE_TIME, RECEPTION_TIME, NONE
  };

  public static enum TimexAttr {
    type, value, tid, beginPoint, endPoint, quant, freq, mod, anchorTimeID, comment, valueFromFunction, temporalFunction, functionInDocument
  };

  public static String PAD_FIELD_UNKNOWN = "X";
  public static String PAD_FIELD_UNKNOWN2 = "XX";
  public static String PAD_FIELD_UNKNOWN4 = "XXXX";

  public static final int RESOLVE_NOW = 0x01;
  public static final int RESOLVE_TO_THIS = 0x20;
  public static final int RESOLVE_TO_PAST = 0x40; // Resolve to a past time
  public static final int RESOLVE_TO_FUTURE = 0x80; // Resolve to a future time
  public static final int DUR_RESOLVE_TO_AS_REF = 0x1000;
  public static final int DUR_RESOLVE_FROM_AS_REF = 0x2000;
  public static final int RANGE_RESOLVE_TIME_REF = 0x100000;

  public static final int RANGE_OFFSET_BEGIN = 0x0001;
  public static final int RANGE_OFFSET_END = 0x0002;
  public static final int RANGE_EXPAND_FIX_BEGIN = 0x0010;
  public static final int RANGE_EXPAND_FIX_END = 0x0020;

  public static final int RANGE_FLAGS_PAD_MASK = 0x000f; // Pad type
  public static final int RANGE_FLAGS_PAD_NONE = 0x0001; // Simple range
  // (without padding)
  public static final int RANGE_FLAGS_PAD_AUTO = 0x0002; // Automatic range
  // (whatever padding we
  // think is most
  // appropriate,
  // default)
  public static final int RANGE_FLAGS_PAD_FINEST = 0x0003; // Pad to most
  // specific (whatever
  // that is)
  public static final int RANGE_FLAGS_PAD_SPECIFIED = 0x0004; // Specified
  // granularity

  public static final int FORMAT_ISO = 0x01;
  public static final int FORMAT_TIMEX3_VALUE = 0x02;
  public static final int FORMAT_FULL = 0x04;
  public static final int FORMAT_PAD_UNKNOWN = 0x1000;

  static final protected int timexVersion = 3;

  // Index of time id to temporal object
  public static class TimeIndex {
    Index<Temporal> temporalIndex = new HashIndex<Temporal>();
    Index<Temporal> temporalFuncIndex = new HashIndex<Temporal>();

    public void clear() {
      temporalIndex.clear();
      temporalFuncIndex.clear();
    }

    public Temporal getTemporal(int i) {
      return temporalIndex.get(i);
    }

    public Temporal getTemporalFunc(int i) {
      return temporalFuncIndex.get(i);
    }

    public boolean addTemporal(Temporal t) {
      return temporalIndex.add(t);
    }

    public boolean addTemporalFunc(Temporal t) {
      return temporalFuncIndex.add(t);
    }

    public int indexOfTemporal(Temporal t, boolean add) {
      return temporalIndex.indexOf(t, add);
    }

    public int indexOfTemporalFunc(Temporal t, boolean add) {
      return temporalFuncIndex.indexOf(t, add);
    }
  }

  /**
   * Basic temporal object
   *
   * <p>
   * There are 4 main types of temporal objects
   * <ol>
   * <li>Time - Conceptually a point in time
   * <br>NOTE: Due to limitation in precision, it is
   * difficult to get an exact point in time
   * </li>
   * <li>Duration - Amount of time in a time interval
   *  <ul><li>DurationWithMillis - Duration specified in milliseconds
   *          (wrapper around JodaTime Duration)</li>
   *      <li>DurationWithFields - Duration specified with
   *         fields like day, year, etc (wrapper around JodaTime Period)</lI>
   *      <li>DurationRange - A duration that falls in a particular range (with min to max)</li>
   *  </ul>
   * </li>
   * <li>Range - Time Interval with a start time, end time, and duration</li>
   * <li>TemporalSet - A set of temporal objects
   *  <ul><li>ExplicitTemporalSet - Explicit set of temporals (not used)
   *         <br>Ex: Tuesday 1-2pm, Wednesday night</li>
   *      <li>PeriodicTemporalSet - Reoccuring times
   *         <br>Ex: Every Tuesday</li>
   *  </ul>
   * </li>
   */
  public static abstract class Temporal implements Cloneable {
    String mod;
    boolean approx;

    public Temporal() {
    }

    public Temporal(Temporal t) {
      this.mod = t.mod;
      this.approx = t.approx;
    }

    public abstract boolean isGrounded();

    // Returns time representation for Temporal (if available)
    public abstract Time getTime();

    // Returns duration (estimate of how long the temporal expression is for)
    public abstract Duration getDuration();

    // Returns range (start/end points of temporal, automatic granularity)
    public Range getRange() {
      return getRange(RANGE_FLAGS_PAD_AUTO);
    }

    // Returns range (start/end points of temporal)
    public Range getRange(int flags) {
      return getRange(flags, null);
    }

    // Returns range (start/end points of temporal), using specified flags
    public abstract Range getRange(int flags, Duration granularity);

    // Returns how often this time would repeat
    // Ex: friday repeat weekly, hour repeat hourly, hour in a day repeat daily
    public Duration getPeriod() {
      TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getPeriod();
      }
      TimeLabelType tlt = getTimeLabelType();
      if (tlt != null) {
        return tlt.getPeriod();
      }
      return null;
    }

    // Returns the granularity to which this time or duration is specified
    // Typically the most specific time unit
    public Duration getGranularity() {
      TimeLabelType tlt = getTimeLabelType();
      if (tlt != null) {
        return tlt.getGranularity();
      }
      return null;
    }

    // Resolves this temporal expression with respect to the specified reference
    // time
    // using flags
    public Temporal resolve(Time refTime) {
      return resolve(refTime, 0);
    }

    public abstract Temporal resolve(Time refTime, int flags);

    public TimeLabelType getTimeLabelType() {
      if (getTimeLabel() != null)
        return getTimeLabel().timeLabelType;
      else
        return null;
    }

    public TimeLabel getTimeLabel() {
      return null;
    }

    // Returns if the current temporal expression is an reference
    public boolean isRef() {
      return false;
    }

    // Return sif the current temporal expression is approximate
    public boolean isApprox() {
      return approx;
    }

    // TIMEX related functions
    public int getTid(TimeIndex timeIndex) {
      return timeIndex.indexOfTemporal(this, true);
    }

    public String getTidString(TimeIndex timeIndex) {
      return "t" + getTid(timeIndex);
    }

    public int getTfid(TimeIndex timeIndex) {
      return timeIndex.indexOfTemporalFunc(this, true);
    }

    public String getTfidString(TimeIndex timeIndex) {
      return "tf" + getTfid(timeIndex);
    }

    // Returns attributes to convert this temporal expression into timex object
    public boolean includeTimexAltValue() {
      return false;
    }

    public Map<String, String> getTimexAttributes(TimeIndex timeIndex) {
      Map<String, String> map = new LinkedHashMap<String, String>();
      map.put(TimexAttr.tid.name(), getTidString(timeIndex));
      // NOTE: GUTime used "VAL" instead of TIMEX3 standard "value"
      // NOTE: attributes are case sensitive, GUTIME used mostly upper case
      // attributes....
      String val = getTimexValue();
      if (val != null) {
        map.put(TimexAttr.value.name(), val);
      }
      if (val == null || includeTimexAltValue()) {
        String str = toFormattedString(FORMAT_FULL);
        if (str != null) {
          map.put("alt_value", str);
        }
      }
      /*     Range r = getRange();
           if (r != null) map.put("range", r.toString());    */
      /*     map.put("str", toString());        */
      map.put(TimexAttr.type.name(), getTimexType().name());
      if (mod != null) {
        map.put(TimexAttr.mod.name(), mod);
      }
      return map;
    }

    // Returns the timex type
    public TimexType getTimexType() {
      if (getTimeLabelType() != null) {
        return getTimeLabelType().getTimexType();
      } else {
        return null;
      }
    }

    // Returns timex value (by default it is the ISO string representation of
    // this object)
    public String getTimexValue() {
      return toFormattedString(FORMAT_TIMEX3_VALUE);
    }

    public String toISOString() {
      return toFormattedString(FORMAT_ISO);
    }

    public String toString() {
      // TODO: Full string representation
      return toFormattedString(FORMAT_FULL);
    }

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel().isoString;
      } else {
        return null;
      }
    }

    // Temporal operations...

    // public abstract Temporal add(Duration offset);
    public Temporal next() {
      Duration per = getPeriod();
      if (per != null) {
        if (this instanceof Duration) {
          return new RelativeTime(new RelativeTime(TemporalOp.THIS, this, DUR_RESOLVE_TO_AS_REF), TemporalOp.OFFSET, per);
        } else {
          // return new RelativeTime(new RelativeTime(TemporalOp.THIS, this),
          // TemporalOp.OFFSET, per);
          return TemporalOp.OFFSET.apply(this, per);
        }
      }
      return null;
    }

    public Temporal prev() {
      Duration per = getPeriod();
      if (per != null) {
        if (this instanceof Duration) {
          return new RelativeTime(new RelativeTime(TemporalOp.THIS, this, DUR_RESOLVE_FROM_AS_REF), TemporalOp.OFFSET, per.multiplyBy(-1));
        } else {
          // return new RelativeTime(new RelativeTime(TemporalOp.THIS, this),
          // TemporalOp.OFFSET, per.multiplyBy(-1));
          return TemporalOp.OFFSET.apply(this, per.multiplyBy(-1));
        }
      }
      return null;
    }

    public/* abstract*/Temporal intersect(Temporal t) {
      return null;
    }

    public String getMod() {
      return mod;
    }

    /*   public void setMod(String mod) {
         this.mod = mod;
       } */

    public Temporal addMod(String mod) {
      try {
        Temporal t = (Temporal) this.clone();
        t.mod = mod;
        return t;
      } catch (CloneNotSupportedException ex) {
        throw new RuntimeException(ex);
      }
    }

    public Temporal addModApprox(String mod, boolean approx) {
      try {
        Temporal t = (Temporal) this.clone();
        t.mod = mod;
        t.approx = approx;
        return t;
      } catch (CloneNotSupportedException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  public static Time TIME_REF = new RefTime("REF") {
  };
  public static Time TIME_REF_UNKNOWN = new RefTime("UNKNOWN");
  public static Time TIME_UNKNOWN = new SimpleTime("UNKNOWN");
  public static Time TIME_NONE = null; // No time
  public static Time TIME_NONE_OK = new SimpleTime("NOTIME");

  // The special time of now
  public static Time TIME_NOW = new RefTime("NOW") {
    public TimeLabel getTimeLabel() {
      return TimeLabel.NOW;
    }
  };
  public static Time TIME_PRESENT = new InexactTime(new Range(TIME_NOW, TIME_NOW)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.PRESENT;
    }
  };
  public static Time TIME_PAST = new InexactTime(new Range(TIME_UNKNOWN, TIME_NOW)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.PAST;
    }
  };
  public static Time TIME_FUTURE = new InexactTime(new Range(TIME_NOW, TIME_UNKNOWN)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.FUTURE;
    }
  };

  public static Duration DURATION_UNKNOWN = new DurationWithFields();
  public static Duration DURATION_NONE = new DurationWithFields(Period.ZERO);

  // Basic time units (durations)

  public static final Duration YEAR = new DurationWithFields(Period.years(1)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.year(), DateTimeFieldType.yearOfCentury(), DateTimeFieldType.yearOfEra() };
    }
  };
  public static final Duration DAY = new DurationWithFields(Period.days(1)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.dayOfMonth(), DateTimeFieldType.dayOfWeek(), DateTimeFieldType.dayOfYear() };
    }
  };
  public static final Duration WEEK = new DurationWithFields(Period.weeks(1)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.weekOfWeekyear() };
    }
  };
  public static final Duration FORTNIGHT = new DurationWithFields(Period.weeks(2));
  public static final Duration MONTH = new DurationWithFields(Period.months(1)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.monthOfYear() };
    }
  };
  // public static final Duration QUARTER = new DurationWithFields(new
  // Period(JodaTimeUtils.Quarters)) {
  public static final Duration QUARTER = new DurationWithFields(Period.months(3)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { JodaTimeUtils.QuarterOfYear };
    }
  };
  // public static final Duration QUARTER = new
  // InexactDuration(Period.months(3));
  public static final Duration MILLIS = new DurationWithFields(Period.millis(1)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.millisOfSecond(), DateTimeFieldType.millisOfDay() };
    }
  };
  public static final Duration SECOND = new DurationWithFields(Period.seconds(1)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.secondOfMinute(), DateTimeFieldType.secondOfDay() };
    }
  };
  public static final Duration MINUTE = new DurationWithFields(Period.minutes(1)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.minuteOfHour(), DateTimeFieldType.minuteOfDay() };
    }
  };
  public static final Duration HOUR = new DurationWithFields(Period.hours(1)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.hourOfDay(), DateTimeFieldType.hourOfHalfday() };
    }
  };
  public static final Duration HALFHOUR = new DurationWithFields(Period.minutes(30));
  public static final Duration QUARTERHOUR = new DurationWithFields(Period.minutes(15));
  public static final Duration DECADE = new DurationWithFields(Period.years(10)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { JodaTimeUtils.DecadeOfCentury };
    }
  };
  public static final Duration CENTURY = new DurationWithFields(Period.years(100)) {
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.centuryOfEra() };
    }
  };
  public static final Duration MILLENIUM = new DurationWithFields(Period.years(1000));

  // Basic dates/times

  // Day of week
  public static final PartialTime MONDAY = new PartialTime(new Partial(DateTimeFieldType.dayOfWeek(), 1)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.MONDAY;
    }
  };
  public static final PartialTime TUESDAY = new PartialTime(new Partial(DateTimeFieldType.dayOfWeek(), 2)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.TUESDAY;
    }
  };
  public static final PartialTime WEDNESDAY = new PartialTime(new Partial(DateTimeFieldType.dayOfWeek(), 3)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.WEDNESDAY;
    }
  };
  public static final PartialTime THURSDAY = new PartialTime(new Partial(DateTimeFieldType.dayOfWeek(), 4)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.THURSDAY;
    }
  };
  public static final PartialTime FRIDAY = new PartialTime(new Partial(DateTimeFieldType.dayOfWeek(), 5)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.FRIDAY;
    }
  };
  public static final PartialTime SATURDAY = new PartialTime(new Partial(DateTimeFieldType.dayOfWeek(), 6)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.SATURDAY;
    }
  };
  public static final PartialTime SUNDAY = new PartialTime(new Partial(DateTimeFieldType.dayOfWeek(), 7)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.SUNDAY;
    }
  };

  public static Time WEEKDAY = new InexactTime(null, SUTime.DAY, new SUTime.Range(SUTime.MONDAY, SUTime.FRIDAY)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.WEEKDAY;
    }
  };
  public static Time WEEKEND = new TimeWithRange(new SUTime.Range(SUTime.SATURDAY, SUTime.SUNDAY, SUTime.DAY.multiplyBy(2))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.WEEKEND;
    }
  };

  // Month
  public static final PartialTime JANUARY = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 1)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.JANUARY;
    }
  };
  public static final PartialTime FEBRUARY = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 2)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.FEBRUARY;
    }
  };
  public static final PartialTime MARCH = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 3)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.MARCH;
    }
  };
  public static final PartialTime APRIL = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 4)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.APRIL;
    }
  };
  public static final PartialTime MAY = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 5)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.MAY;
    }
  };
  public static final PartialTime JUNE = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 6)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.JUNE;
    }
  };
  public static final PartialTime JULY = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 7)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.JULY;
    }
  };
  public static final PartialTime AUGUST = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 8)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.AUGUST;
    }
  };
  public static final PartialTime SEPTEMBER = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 9)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.SEPTEMBER;
    }
  };
  public static final PartialTime OCTOBER = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 10)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.OCTOBER;
    }
  };
  public static final PartialTime NOVEMBER = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 11)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.NOVEMBER;
    }
  };
  public static final PartialTime DECEMBER = new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), 12)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.DECEMBER;
    }
  };

  // Dates are rough with respect to northern hemisphere (actual
  // solstice/equinox days depend on the year)
  public static Time SPRING_EQUINOX = new SUTime.InexactTime(new SUTime.Range(new IsoDate(-1, 3, 20), new IsoDate(-1, 3, 21))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.SPRING_EQUINOX;
    }
  };
  public static Time SUMMER_SOLSTICE = new SUTime.InexactTime(new SUTime.Range(new IsoDate(-1, 6, 20), new IsoDate(-1, 6, 21))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.SUMMER_SOLSTICE;
    }
  };
  public static Time WINTER_SOLSTICE = new SUTime.InexactTime(new SUTime.Range(new IsoDate(-1, 12, 21), new IsoDate(-1, 12, 22))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.WINTER_SOLSTICE;
    }
  };
  public static Time FALL_EQUINOX = new SUTime.InexactTime(new SUTime.Range(new IsoDate(-1, 9, 22), new IsoDate(-1, 9, 23))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.FALL_EQUINOX;
    }
  };

  // Dates for seasons are rough with respect to northern hemisphere
  public static final Time SPRING = new SUTime.InexactTime(SPRING_EQUINOX, QUARTER, new SUTime.Range(SUTime.MARCH, SUTime.JUNE, SUTime.QUARTER)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.SPRING;
    }
  };
  public static final Time SUMMER = new SUTime.InexactTime(SUMMER_SOLSTICE, QUARTER, new SUTime.Range(SUTime.JUNE, SUTime.SEPTEMBER, SUTime.QUARTER)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.SUMMER;
    }
  };
  public static final Time FALL = new SUTime.InexactTime(FALL_EQUINOX, QUARTER, new SUTime.Range(SUTime.SEPTEMBER, SUTime.DECEMBER, SUTime.QUARTER)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.FALL;
    }
  };
  public static final Time WINTER = new SUTime.InexactTime(WINTER_SOLSTICE, QUARTER, new SUTime.Range(SUTime.DECEMBER, SUTime.MARCH, SUTime.QUARTER)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.WINTER;
    }
  };

  // Time of day
  public static final PartialTime NOON = new IsoTime(12, 0, -1) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.NOON;
    }
  };
  public static final PartialTime MIDNIGHT = new IsoTime(0, 0, -1) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.MIDNIGHT;
    }
  };
  public static final Time MORNING = new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 6)), NOON)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.MORNING;
    }
  };
  public static final Time AFTERNOON = new InexactTime(new Range(NOON, new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 18)))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.AFTERNOON;
    }
  };
  public static final Time EVENING = new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 18)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 20)))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.EVENING;
    }
  };
  public static final Time NIGHT = new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 19)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 5)))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.NIGHT;
    }
  };
  public static final Time DAYTIME = new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 6)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 6)))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.DAYTIME;
    }
  };
  public static final Time SUNRISE = new PartialTime() {
    public TimeLabel getTimeLabel() {
      return TimeLabel.SUNRISE;
    }
  };
  public static final Time SUNSET = new PartialTime() {
    public TimeLabel getTimeLabel() {
      return TimeLabel.SUNSET;
    }
  };
  public static final Time DAWN = new PartialTime() {
    public TimeLabel getTimeLabel() {
      return TimeLabel.DAWN;
    }
  };
  public static final Time DUSK = new PartialTime() {
    public TimeLabel getTimeLabel() {
      return TimeLabel.DUSK;
    }
  };

  public static final Time LUNCHTIME = new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 12)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 14)))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.LUNCHTIME;
    }
  };
  public static final Time TEATIME = new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 15)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 17)))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.TEATIME;
    }
  };
  public static final Time DINNERTIME = new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 18)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 20)))) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.DINNERTIME;
    }
  };

  public static final Time MORNING_TWILIGHT = new InexactTime(new Range(DAWN, SUNRISE)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.MORNING_TWILIGHT;
    }
  };
  public static final Time EVENING_TWILIGHT = new InexactTime(new Range(SUNSET, DUSK)) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.EVENING_TWILIGHT;
    }
  };
  public static final TemporalSet TWILIGHT = new ExplicitTemporalSet(EVENING_TWILIGHT, MORNING_TWILIGHT) {
    public TimeLabel getTimeLabel() {
      return TimeLabel.TWILIGHT;
    }
  };

  // Relative days
  public static final RelativeTime YESTERDAY = new RelativeTime(DAY.multiplyBy(-1));
  public static final RelativeTime TOMORROW = new RelativeTime(DAY.multiplyBy(+1));
  public static final RelativeTime TODAY = new RelativeTime(TemporalOp.THIS, SUTime.DAY);
  public static final RelativeTime TONIGHT = new RelativeTime(TemporalOp.THIS, SUTime.NIGHT);

  public static enum TimeUnit {
    // Basic time units
    MILLIS(SUTime.MILLIS), SECOND(SUTime.SECOND), MINUTE(SUTime.MINUTE), HOUR(SUTime.HOUR), DAY(SUTime.DAY), WEEK(SUTime.WEEK), MONTH(SUTime.MONTH), QUARTER(SUTime.QUARTER), YEAR(
        SUTime.YEAR), DECADE(SUTime.DECADE), CENTURY(SUTime.CENTURY), UNKNOWN(SUTime.DURATION_UNKNOWN);

    protected Duration duration;

    TimeUnit(Duration d) {
      this.duration = d;
    }

    public Duration getDuration() {
      return duration;
    } // How long does this time last?

    public Duration getPeriod() {
      return duration;
    } // How often does this type of time occur?

    public Duration getGranularity() {
      return duration;
    } // What is the granularity of this time?

    public Temporal createTemporal(int n) {
      return duration.multiplyBy(n);
    }
  }

  public static enum TimeLabelType {
    REFDATE(TimexType.DATE), REFTIME(TimexType.TIME), MILLIS(TimexType.TIME, TimeUnit.MILLIS), SECOND(TimexType.TIME, TimeUnit.SECOND), MINUTE(TimexType.TIME, TimeUnit.MINUTE), HOUR(
        TimexType.TIME, TimeUnit.HOUR), DAY(TimexType.TIME, TimeUnit.DAY), WEEK(TimexType.TIME, TimeUnit.WEEK), MONTH(TimexType.TIME, TimeUnit.MONTH), QUARTER(TimexType.TIME,
        TimeUnit.QUARTER), YEAR(TimexType.TIME, TimeUnit.YEAR), TIME_OF_DAY(TimexType.TIME, TimeUnit.HOUR, SUTime.DAY) {
      public Duration getDuration() {
        return SUTime.HOUR.makeInexact();
      }
    },
    DAY_OF_WEEK(TimexType.DATE, TimeUnit.DAY, SUTime.WEEK) {
      public Temporal createTemporal(int n) {
        return new PartialTime(new Partial(DateTimeFieldType.dayOfWeek(), n));
      }
    },
    DAYS_OF_WEEK(TimexType.DATE, TimeUnit.DAY, SUTime.WEEK) {
      public Duration getDuration() {
        return SUTime.DAY.makeInexact();
      }
    },
    MONTH_OF_YEAR(TimexType.DATE, TimeUnit.MONTH, SUTime.YEAR) {
      public Temporal createTemporal(int n) {
        return new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), n));
      }
    },
    PART_OF_YEAR(TimexType.DATE, TimeUnit.DAY, SUTime.YEAR) {
      public Duration getDuration() {
        return SUTime.DAY.makeInexact();
      }
    },
    SEASON_OF_YEAR(TimexType.DATE, TimeUnit.QUARTER, SUTime.YEAR), QUARTER_OF_YEAR(TimexType.DATE, TimeUnit.QUARTER, SUTime.YEAR) {
      public Temporal createTemporal(int n) {
        return new PartialTime(new Partial(JodaTimeUtils.QuarterOfYear, n)) {
          public TimeLabelType getTimeLabelType() {
            return QUARTER_OF_YEAR;
          }
        };
      }
    };

    TimexType timexType;
    TimeUnit unit = TimeUnit.UNKNOWN;
    Duration period = SUTime.DURATION_NONE;

    TimeLabelType(TimexType timexType) {
      this.timexType = timexType;
    }

    TimeLabelType(TimexType timexType, TimeUnit unit) {
      this.timexType = timexType;
      this.unit = unit;
      this.period = unit.getPeriod();
    }

    TimeLabelType(TimexType timexType, TimeUnit unit, Duration period) {
      this.timexType = timexType;
      this.unit = unit;
      this.period = period;
    }

    public TimexType getTimexType() {
      return timexType;
    }

    public Duration getDuration() {
      return unit.getDuration();
    } // How long does this time last?

    public Duration getPeriod() {
      return period;
    } // How often does this type of time occur?

    public Duration getGranularity() {
      return unit.getGranularity();
    } // What is the granularity of this time?

    public Temporal createTemporal(int n) {
      return null;
    }
  }

  public static enum TimeLabel {
    MORNING(TIME_OF_DAY, "MO", SUTime.MORNING), TEATIME(TIME_OF_DAY, "AF", SUTime.TEATIME), LUNCHTIME(TIME_OF_DAY, "MI", SUTime.LUNCHTIME), DINNERTIME(TIME_OF_DAY, "EV",
        SUTime.DINNERTIME), NOON(TIME_OF_DAY, null, SUTime.NOON), AFTERNOON(TIME_OF_DAY, "AF", SUTime.AFTERNOON), EVENING(TIME_OF_DAY, "EV", SUTime.EVENING), MIDNIGHT(TIME_OF_DAY,
        null, SUTime.MIDNIGHT), DAWN(TIME_OF_DAY, "MO", SUTime.DAWN) {
      public String getModifier() {
        return SUTime.TimexMod.EARLY.name();
      }
    },
    MORNING_TWILIGHT(TIME_OF_DAY, "MO", SUTime.MORNING_TWILIGHT) {
      public String getModifier() {
        return SUTime.TimexMod.EARLY.name();
      }
    },
    SUNRISE(TIME_OF_DAY, "MO", SUTime.SUNRISE) {
      public String getModifier() {
        return SUTime.TimexMod.EARLY.name();
      }
    },
    DAYTIME(TIME_OF_DAY, "DT", SUTime.DAYTIME), SUNSET(TIME_OF_DAY, "EV", SUTime.SUNSET) {
      public String getModifier() {
        return SUTime.TimexMod.EARLY.name();
      }
    },
    EVENING_TWILIGHT(TIME_OF_DAY, "EV", SUTime.EVENING_TWILIGHT), DUSK(TIME_OF_DAY, "EV", SUTime.DUSK), NIGHT(TIME_OF_DAY, "NI", SUTime.NIGHT), TWILIGHT(TIME_OF_DAY, "NI",
        SUTime.TWILIGHT),

    YEARS(TimeLabelType.YEAR, null, SUTime.YEAR), QUARTERS(TimeLabelType.QUARTER, null, SUTime.QUARTER), MONTHS(TimeLabelType.MONTH, null, SUTime.MONTH), WEEKS(TimeLabelType.WEEK,
        null, SUTime.WEEK), DAYS(TimeLabelType.DAY, null, SUTime.DAY), HOURS(TimeLabelType.HOUR, null, SUTime.HOUR), MINUTES(TimeLabelType.MINUTE, null, SUTime.MINUTE), SECONDS(
        TimeLabelType.SECOND, null, SUTime.SECOND), MILLIS(TimeLabelType.MILLIS, null, SUTime.MILLIS),

    MONDAY(DAY_OF_WEEK, null, SUTime.MONDAY), TUESDAY(DAY_OF_WEEK, null, SUTime.TUESDAY), WEDNESDAY(DAY_OF_WEEK, null, SUTime.WEDNESDAY), THURSDAY(DAY_OF_WEEK, null,
        SUTime.THURSDAY), FRIDAY(DAY_OF_WEEK, null, SUTime.FRIDAY), SATURDAY(DAY_OF_WEEK, null, SUTime.SATURDAY), SUNDAY(DAY_OF_WEEK, null, SUTime.SUNDAY),

    JANUARY(MONTH_OF_YEAR, null, SUTime.JANUARY), FEBRUARY(MONTH_OF_YEAR, null, SUTime.FEBRUARY), MARCH(MONTH_OF_YEAR, null, SUTime.MARCH), APRIL(MONTH_OF_YEAR, null, SUTime.APRIL), MAY(
        MONTH_OF_YEAR, null, SUTime.MAY), JUNE(MONTH_OF_YEAR, null, SUTime.JUNE), JULY(MONTH_OF_YEAR, null, SUTime.JULY), AUGUST(MONTH_OF_YEAR, null, SUTime.AUGUST), SEPTEMBER(
        MONTH_OF_YEAR, null, SUTime.SEPTEMBER), OCTOBER(MONTH_OF_YEAR, null, SUTime.OCTOBER), NOVEMBER(MONTH_OF_YEAR, null, SUTime.NOVEMBER), DECEMBER(MONTH_OF_YEAR, null,
        SUTime.DECEMBER),

    WEEKDAY(DAYS_OF_WEEK, "WD", SUTime.WEEKDAY) {
      public Duration getDuration() {
        return temporal.getDuration();
      }
    },
    WEEKEND(DAYS_OF_WEEK, "WE", SUTime.WEEKEND) {
      public Duration getDuration() {
        return temporal.getDuration();
      }
    },

    SPRING(SEASON_OF_YEAR, "SP", SUTime.SPRING), SUMMER(SEASON_OF_YEAR, "SU", SUTime.SUMMER), WINTER(SEASON_OF_YEAR, "WI", SUTime.WINTER), FALL(SEASON_OF_YEAR, "FA", SUTime.FALL),

    NOW(REFTIME, "PRESENT_REF", SUTime.TIME_REF), PAST(REFDATE, "PAST_REF", SUTime.TIME_PAST), PRESENT(REFDATE, "PRESENT_REF", SUTime.TIME_PRESENT), FUTURE(REFDATE, "FUTURE_REF",
        SUTime.TIME_FUTURE),

    // Dates are rough with respect to northern hemisphere (actual
    // solstice/equinox days depend on the year)
    SPRING_EQUINOX(TimeLabelType.DAY, "SP", SUTime.SPRING_EQUINOX), SUMMER_SOLSTICE(TimeLabelType.DAY, "SU", SUTime.SUMMER_SOLSTICE), WINTER_SOLSTICE(TimeLabelType.DAY, "WI",
        SUTime.WINTER_SOLSTICE), FALL_EQUINOX(TimeLabelType.DAY, "FA", SUTime.FALL_EQUINOX);

    TimeLabelType timeLabelType;
    String isoString;
    Temporal temporal;

    TimeLabel(TimeLabelType labelType, String isoString, Temporal temporal) {
      this.timeLabelType = labelType;
      this.isoString = isoString;
      this.temporal = temporal;
    }

    public String getModifier() {
      return null;
    }

    public Temporal getTemporal() {
      return temporal;
    }

    public Duration getDuration() {
      return timeLabelType.getDuration();
    } // How long does this time last?

    public Duration getPeriod() {
      return timeLabelType.getPeriod();
    } // How often does this type of time occur?

  }

  // Temporal operators (currently operates on two temporals and returns another
  // temporal)
  // Can add operators for:
  // lookup of temporal from string
  // creating durations, dates
  // public interface TemporalOp extends Function<Temporal,Temporal>();
  public static enum TemporalOp {
    // For durations: possible interpretation of next/prev:
    // next month, next week
    // NEXT: on Thursday, next week = week starting on next monday
    // ??? on Thursday, next week = one week starting from now
    // prev month, prev week
    // PREV: on Thursday, last week = week starting on the monday one week
    // before this monday
    // ??? on Thursday, last week = one week going back starting from now
    // For partial dates: two kind of next
    // next tuesday, next winter, next january
    // NEXT (PARENT UNIT, FAVOR): Example: on monday, next tuesday = tuesday of
    // the week after this
    // NEXT IMMEDIATE (NOT FAVORED): Example: on monday, next saturday =
    // saturday of this week
    // last saturday, last winter, last january
    // PREV (PARENT UNIT, FAVOR): Example: on wednesday, last tuesday = tuesday
    // of the week before this
    // PREV IMMEDIATE (NOT FAVORED): Example: on saturday, last tuesday =
    // tuesday of this week

    // (successor) Next week/day/...
    NEXT {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg2 == null) {
          return arg1;
        }
        Temporal arg2Next = arg2.next();
        if (arg1 == null || arg2Next == null) {
          return arg2Next;
        }
        if (arg1 instanceof Time) {
          // TODO: flags?
          Temporal resolved = arg2Next.resolve((Time) arg1, 0 /* RESOLVE_TO_FUTURE */);
          return resolved;
        } else {
          throw new UnsupportedOperationException("NEXT not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    // This coming week/friday
    NEXT_IMMEDIATE {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return new RelativeTime(NEXT_IMMEDIATE, arg2);
        }
        if (arg2 == null) {
          return arg1;
        }
        // Temporal arg2Next = arg2.next();
        // if (arg1 == null || arg2Next == null) { return arg2Next; }
        if (arg1 instanceof Time) {
          Time t = (Time) arg1;
          if (arg2 instanceof Duration) {
            return ((Duration) arg2).toTime(t, flags | RESOLVE_TO_FUTURE);
          } else {
            // TODO: flags?
            Temporal resolvedThis = arg2.resolve(t, RESOLVE_TO_FUTURE);
            if (resolvedThis != null) {
              if (resolvedThis instanceof Time) {
                if (((Time) resolvedThis).compareTo(t) <= 0) {
                  return NEXT.apply(arg1, arg2);
                }
              }
            }
            return resolvedThis;
          }
        } else {
          throw new UnsupportedOperationException("NEXT_IMMEDIATE not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    // Use arg1 as reference to resolve arg2 (take more general fields from arg1
    // and apply to arg2)
    THIS {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return new RelativeTime(THIS, arg2, flags);
        }
        if (arg1 instanceof Time) {
          if (arg2 instanceof Duration) {
            return ((Duration) arg2).toTime((Time) arg1, flags);
          } else {
            // TODO: flags?
            return arg2.resolve((Time) arg1, flags | RESOLVE_TO_THIS);
          }
        } else {
          throw new UnsupportedOperationException("THIS not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    // (predecessor) Previous week/day/...
    PREV {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg2 == null) {
          return arg1;
        }
        Temporal arg2Prev = arg2.prev();
        if (arg1 == null || arg2Prev == null) {
          return arg2Prev;
        }
        if (arg1 instanceof Time) {
          // TODO: flags?
          Temporal resolved = arg2Prev.resolve((Time) arg1, 0 /*RESOLVE_TO_PAST */);
          return resolved;
        } else {
          throw new UnsupportedOperationException("PREV not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    // This past week/friday
    PREV_IMMEDIATE {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return new RelativeTime(PREV_IMMEDIATE, arg2);
        }
        if (arg2 == null) {
          return arg1;
        }
        // Temporal arg2Prev = arg2.prev();
        // if (arg1 == null || arg2Prev == null) { return arg2Prev; }
        if (arg1 instanceof Time) {
          Time t = (Time) arg1;
          if (arg2 instanceof Duration) {
            return ((Duration) arg2).toTime(t, flags | RESOLVE_TO_PAST);
          } else {
            // TODO: flags?
            Temporal resolvedThis = arg2.resolve(t, RESOLVE_TO_PAST);
            if (resolvedThis != null) {
              if (resolvedThis instanceof Time) {
                if (((Time) resolvedThis).compareTo(t) >= 0) {
                  return PREV.apply(arg1, arg2);
                }
              }
            }
            return resolvedThis;
          }
        } else {
          throw new UnsupportedOperationException("PREV_IMMEDIATE not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    UNION {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        // return arg1.union(arg2);
        throw new UnsupportedOperationException("UNION not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
      }
    },
    INTERSECT {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        return arg1.intersect(arg2);
        // throw new
        // UnsupportedOperationException("INTERSECT not implemented for arg1=" +
        // arg1.getClass() + ", arg2="+arg2.getClass());
      }
    },
    // arg2 is "in" arg1, composite datetime
    IN {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg1 instanceof Time) {
          // TODO: flags?
          return arg2.intersect((Time) arg1);
        } else {
          throw new UnsupportedOperationException("IN not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    OFFSET {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return new RelativeTime(OFFSET, arg2);
        }
        if (arg1 instanceof Time && arg2 instanceof Duration) {
          return ((Time) arg1).offset((Duration) arg2);
        } else if (arg1 instanceof Range && arg2 instanceof Duration) {
          return ((Range) arg1).offset((Duration) arg2);
        } else {
          throw new UnsupportedOperationException("OFFSET not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    MINUS {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        if (arg1 instanceof Duration && arg2 instanceof Duration) {
          return ((Duration) arg1).subtract((Duration) arg2);
        } else if (arg1 instanceof Time && arg2 instanceof Duration) {
          return ((Time) arg1).subtract((Duration) arg2);
        } else if (arg1 instanceof Range && arg2 instanceof Duration) {
          return ((Range) arg1).subtract((Duration) arg2);
        } else {
          throw new UnsupportedOperationException("MINUS not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    PLUS {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        if (arg1 instanceof Duration && arg2 instanceof Duration) {
          return ((Duration) arg1).add((Duration) arg2);
        } else if (arg1 instanceof Time && arg2 instanceof Duration) {
          return ((Time) arg1).add((Duration) arg2);
        } else if (arg1 instanceof Range && arg2 instanceof Duration) {
          return ((Range) arg1).add((Duration) arg2);
        } else {
          throw new UnsupportedOperationException("PLUS not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    MIN {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        if (arg1 instanceof Time && arg2 instanceof Time) {
          return Time.min((Time) arg1, (Time) arg2);
        } else if (arg1 instanceof Duration && arg2 instanceof Duration) {
          return Duration.min((Duration) arg1, (Duration) arg2);
        } else {
          throw new UnsupportedOperationException("MIN not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    MAX {
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        if (arg1 instanceof Time && arg2 instanceof Time) {
          return Time.max((Time) arg1, (Time) arg2);
        } else if (arg1 instanceof Duration && arg2 instanceof Duration) {
          return Duration.max((Duration) arg1, (Duration) arg2);
        } else {
          throw new UnsupportedOperationException("MAX not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    SCALE {
      public Temporal apply(Duration d, int scale) {
        if (d == null)
          return null;
        return d.multiplyBy(scale);
      }

      public Temporal apply(Object... args) {
        if (args.length == 2) {
          if (args[0] instanceof Duration && args[1] instanceof Integer) {
            return apply((Duration) args[0], ((Integer) args[1]).intValue());
          }
        }
        return null;
        //throw new UnsupportedOperationException("apply(Object...) not implemented for TemporalOp " + this);
      }
    },
    CREATE {
      public Temporal apply(TimeUnit tu, int n) {
        return tu.createTemporal(n);
      }

      public Temporal apply(Object... args) {
        if (args.length == 2) {
          if (args[0] instanceof TimeUnit && args[1] instanceof Integer) {
            return apply((TimeUnit) args[0], ((Integer) args[1]).intValue());
          }
        }
        throw new UnsupportedOperationException("apply(Object...) not implemented for TemporalOp " + this);
      }
    };

    public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
      throw new UnsupportedOperationException("apply(Temporal, Temporal, int) not implemented for TemporalOp " + this);
    }

    public Temporal apply(Temporal arg1, Temporal arg2) {
      return apply(arg1, arg2, 0);
    }

    public Temporal apply(Temporal... args) {
      if (args.length == 2) {
        return apply(args[0], args[1]);
      }
      throw new UnsupportedOperationException("apply(Temporal...) not implemented for TemporalOp " + this);
    }

    public Temporal apply(Object... args) {
      throw new UnsupportedOperationException("apply(Object...) not implemented for TemporalOp " + this);
    }
  }

  public abstract static class Time extends Temporal implements FuzzyInterval.FuzzyComparable<Time>, HasInterval<Time> {

    public Time() {
    }

    public Time(Time t) {
      super(t); /*this.hasTime = t.hasTime; */
    }

    // Represents a point in time - there is typically some
    // uncertainty/imprecision in the exact time
    public boolean isGrounded() {
      return false;
    }

    // A time is defined by a begin and end point, and a duration
    public Time getTime() {
      return this;
    }

    // Default is a instant in time with same begin and end point
    // Every time should return a non-null range
    public Range getRange(int flags, Duration granularity) {
      return new Range(this, this);
    }

    // Default duration is zero
    public Duration getDuration() {
      return DURATION_NONE;
    }

    public Interval<Time> getInterval() {
      Range r = getRange();
      if (r != null) {
        return r.getInterval();
      } else
        return null;
    }

    public boolean isComparable(Time t) {
      Instant i = this.getJodaTimeInstant();
      Instant i2 = t.getJodaTimeInstant();
      return (i != null && i2 != null);
    }

    public int compareTo(Time t) {
      Instant i = this.getJodaTimeInstant();
      Instant i2 = t.getJodaTimeInstant();
      return i.compareTo(i2);
    }

    public boolean hasTime() {
      return false;
    }

    public TimexType getTimexType() {
      if (getTimeLabelType() != null) {
        return getTimeLabelType().getTimexType();
      }
      return (hasTime()) ? TimexType.TIME : TimexType.DATE;
    }

    // Time operations
    public boolean contains(Time t) {
      // Check if this time contains other time
      return getRange().contains(t.getRange());
    }

    // public boolean isBefore(Time t);
    // public boolean isAfter(Time t);
    // public boolean overlaps(Time t);

    // Add duration to time
    public abstract Time add(Duration offset);

    public Time offset(Duration offset) {
      return add(offset);
    }

    public Time subtract(Duration offset) {
      return add(offset.multiplyBy(-1));
    }

    // Get difference between times
    public static Duration difference(Time t1, Time t2) {
      // Get duration from this t1 to t2
      if (t1 == null || t2 == null)
        return null;
      Instant i1 = t1.getJodaTimeInstant();
      Instant i2 = t2.getJodaTimeInstant();
      if (i1 == null || i2 == null)
        return null;
      Duration d = new DurationWithMillis(i2.getMillis() - i1.getMillis());
      Duration g1 = t1.getGranularity();
      Duration g2 = t2.getGranularity();
      Duration g = Duration.max(g1, g2);
      if (g != null) {
        Period p = g.getJodaTimePeriod();
        p = p.normalizedStandard();
        Period p2 = JodaTimeUtils.discardMoreSpecificFields(d.getJodaTimePeriod(), p.getFieldType(p.size() - 1), i1.getChronology());
        return new DurationWithFields(p2);
      } else {
        return d;
      }
    }

    public static CompositePartialTime makeComposite(PartialTime pt, Time t) {
      CompositePartialTime cp = null;
      TimeLabelType tlt = t.getTimeLabelType();
      if (tlt != null) {
        switch (tlt) {
        case TIME_OF_DAY:
          cp = new CompositePartialTime(pt, null, null, t);
          break;
        case PART_OF_YEAR:
        case QUARTER_OF_YEAR:
        case SEASON_OF_YEAR:
          cp = new CompositePartialTime(pt, t, null, null);
          break;
        case DAYS_OF_WEEK:
          cp = new CompositePartialTime(pt, null, t, null);
          break;
        }
      }
      return cp;
    }

    public Temporal resolve(Time t, int flags) {
      return this;
    }

    protected Time intersect(Time t) {
      return new RelativeTime(this, TemporalOp.INTERSECT, t);
    }

    protected static Time intersect(Time t1, Time t2) {
      if (t1 == null)
        return t2;
      if (t2 == null)
        return t1;
      return t1.intersect(t2);
    }

    public static Time min(Time t1, Time t2) {
      if (t2 == null)
        return t1;
      if (t1 == null)
        return t2;
      if (t1.isComparable(t2)) {
        int c = t1.compareTo(t2);
        return (c < 0) ? t1 : t2;
      }
      return t1;
    }

    public static Time max(Time t1, Time t2) {
      if (t1 == null)
        return t2;
      if (t2 == null)
        return t1;
      if (t1.isComparable(t2)) {
        int c = t1.compareTo(t2);
        return (c >= 0) ? t1 : t2;
      }
      return t2;
    }

    // Conversions to joda time
    public Instant getJodaTimeInstant() {
      return null;
    }

    public Partial getJodaTimePartial() {
      return null;
    }

  }

  // Reference time (some kind of reference time)
  public static class RefTime extends Time {
    String label;

    public RefTime(String label) {
      this.label = label;
    }

    public boolean isRef() {
      return true;
    }

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel().isoString;
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      return label;
    }

    public Time add(Duration offset) {
      return new RelativeTime(this, TemporalOp.OFFSET, offset);
    };

    public Time resolve(Time refTime, int flags) {
      if (this == TIME_REF) {
        return refTime;
      } else if (this == TIME_NOW && (flags & RESOLVE_NOW) != 0) {
        return refTime;
      } else {
        return this;
      }
    }
  }

  // Simple time (vague time that we don't really know what to do with)
  public static class SimpleTime extends Time {
    String label;

    public SimpleTime(String label) {
      this.label = label;
    }

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel().isoString;
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      return label;
    }

    public Time add(Duration offset) {
      Time t = new RelativeTime(this, TemporalOp.OFFSET, offset);
      // t.approx = this.approx;
      // t.mod = this.mod;
      return t;
    };
  }

  // Composite time - like PartialTime but with more, approximate fields
  public static class CompositePartialTime extends PartialTime {
    // Summer weekend morning in June
    Time tod; // Time of day
    Time dow; // Day of week
    Time poy; // Part of year

    // Duration duration; // Underspecified time (like day in June)

    public CompositePartialTime(PartialTime t, Time poy, Time dow, Time tod) {
      super(t);
      this.poy = poy;
      this.dow = dow;
      this.tod = tod;
    }

    public CompositePartialTime(PartialTime t, Partial p, Time poy, Time dow, Time tod) {
      this(t, poy, dow, tod);
      this.base = p;
    }

    public Instant getJodaTimeInstant() {
      Partial p = base;
      if (tod != null) {
        Partial p2 = tod.getJodaTimePartial();
        if (p2 != null && JodaTimeUtils.isCompatible(p, p2)) {
          p = JodaTimeUtils.combine(p, p2);
        }
      }
      if (dow != null) {
        Partial p2 = dow.getJodaTimePartial();
        if (p2 != null && JodaTimeUtils.isCompatible(p, p2)) {
          p = JodaTimeUtils.combine(p, p2);
        }
      }
      if (poy != null) {
        Partial p2 = poy.getJodaTimePartial();
        if (p2 != null && JodaTimeUtils.isCompatible(p, p2)) {
          p = JodaTimeUtils.combine(p, p2);
        }
      }
      return JodaTimeUtils.getInstant(p);
    }

    public Duration getDuration() {
      TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getDuration();
      }
      TimeLabelType tlt = getTimeLabelType();
      if (tlt != null) {
        return tlt.getDuration();
      }

      Duration bd = (base != null) ? Duration.getDuration(JodaTimeUtils.getJodaTimePeriod(base)) : null;
      if (tod != null) {
        Duration d = tod.getDuration();
        return (bd.compareTo(d) < 0) ? bd : d;
      }
      if (dow != null) {
        Duration d = dow.getDuration();
        return (bd.compareTo(d) < 0) ? bd : d;
      }
      if (poy != null) {
        Duration d = poy.getDuration();
        return (bd.compareTo(d) < 0) ? bd : d;
      }
      return bd;
    }

    public Duration getPeriod() {
      TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getPeriod();
      }
      TimeLabelType tlt = getTimeLabelType();
      if (tlt != null) {
        return tlt.getPeriod();
      }

      Duration bd = null;
      if (base != null) {
        DateTimeFieldType mostGeneral = JodaTimeUtils.getMostGeneral(base);
        DurationFieldType df = null;
        if (mostGeneral != null) df = mostGeneral.getRangeDurationType();
        if (df == null) {
        	if (mostGeneral != null) df = mostGeneral.getDurationType();
        }
        if (df != null) {
          bd = new DurationWithFields(new Period().withField(df, 1));
        }
      }

      if (poy != null) {
        Duration d = poy.getPeriod();
        return (bd.compareTo(d) > 0) ? bd : d;
      }
      if (dow != null) {
        Duration d = dow.getPeriod();
        return (bd.compareTo(d) > 0) ? bd : d;
      }
      if (tod != null) {
        Duration d = tod.getPeriod();
        if(bd != null)  return (bd.compareTo(d) > 0) ? bd : d;
      }
      return bd;
    }

    public Range getRange(int flags, Duration granularity) {
      Duration d = getDuration();
      if (tod != null) {
        Range r = tod.getRange(flags, granularity);
        if (r != null) {
          CompositePartialTime cpt = new CompositePartialTime(this, poy, dow, null);
          Time t1 = cpt.intersect(r.beginTime());
          Time t2 = cpt.intersect(r.endTime());
          return new Range(t1, t2, d);
        } else {
          return super.getRange(flags, granularity);
        }
      }
      if (dow != null) {
        Range r = dow.getRange(flags, granularity);
        if (r != null) {
          CompositePartialTime cpt = new CompositePartialTime(this, poy, dow, null);
          Time t1 = cpt.intersect(r.beginTime());
          if (t1 instanceof PartialTime) {
            ((PartialTime) t1).withStandardFields();
          }
          Time t2 = cpt.intersect(r.endTime());
          if (t2 instanceof PartialTime) {
            ((PartialTime) t2).withStandardFields();
          }
          return new Range(t1, t2, d);
        } else {
          return super.getRange(flags, granularity);
        }
      }
      if (poy != null) {
        Range r = poy.getRange(flags, granularity);
        if (r != null) {
          CompositePartialTime cpt = new CompositePartialTime(this, poy, null, null);
          Time t1 = cpt.intersect(r.beginTime());
          Time t2 = cpt.intersect(r.endTime());
          return new Range(t1, t2, d);
        } else {
          return super.getRange(flags, granularity);
        }
      }
      return super.getRange(flags, granularity);
    }

    public Time intersect(Time t) {
      if (t == null || t == TIME_UNKNOWN)
        return this;
      if (base == null)
        return t;
      if (t instanceof PartialTime) {
        if (!isCompatible((PartialTime) t)) {
          return null;
        }
        Partial p = JodaTimeUtils.combine(this.base, ((PartialTime) t).base);
        if (t instanceof CompositePartialTime) {
          CompositePartialTime cpt = (CompositePartialTime) t;
          Time ntod = Time.intersect(tod, cpt.tod);
          Time ndow = Time.intersect(dow, cpt.dow);
          Time npoy = Time.intersect(poy, cpt.poy);
          if (ntod == null && (tod != null || cpt.tod != null))
            return null;
          if (ndow == null && (dow != null || cpt.dow != null))
            return null;
          if (npoy == null && (poy != null || cpt.poy != null))
            return null;
          return new CompositePartialTime(this, p, npoy, ndow, ntod);
        } else {
          return new CompositePartialTime(this, p, poy, dow, tod);
        }
      } else {
        return super.intersect(t);
      }
    }

    protected PartialTime addSupported(Period p, int scalar) {
      return new CompositePartialTime(this, base.withPeriodAdded(p, 1), poy, dow, tod);
    }

    protected PartialTime addUnsupported(Period p, int scalar) {
      return new CompositePartialTime(this, JodaTimeUtils.addForce(base, p, scalar), poy, dow, tod);
    }

    public Time resolve(Time ref, int flags) {
      if (ref == null || ref == TIME_UNKNOWN || ref == TIME_REF) {
        return this;
      }
      if (this == TIME_REF) {
        return ref;
      }
      if (this == TIME_UNKNOWN) {
        return this;
      }
      Partial partialRef = ref.getJodaTimePartial();
      if (partialRef == null) {
        throw new UnsupportedOperationException("Cannot resolve if reftime is of class: " + ref.getClass());
      }
      DateTimeFieldType mgf = null;
      if (poy != null)
        mgf = JodaTimeUtils.QuarterOfYear;
      else if (dow != null)
        mgf = DateTimeFieldType.dayOfWeek();
      else if (tod != null)
        mgf = DateTimeFieldType.halfdayOfDay();
      Partial p = (base != null) ? JodaTimeUtils.combineMoreGeneralFields(base, partialRef, mgf) : partialRef;
      if (p.isSupported(DateTimeFieldType.dayOfWeek())) {
        p = JodaTimeUtils.resolveDowToDay(p, partialRef);
      } else if (dow != null) {
        p = JodaTimeUtils.resolveWeek(p, partialRef);
      }
      if (p == base) {
        return this;
      } else {
        return new CompositePartialTime(this, p, poy, dow, tod);
      }
    }

    public DateTimeFormatter getFormatter(int flags) {
      DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
      boolean hasDate = appendDateFormats(builder, flags);
      if (poy != null) {
        if (!JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear())) {
          // Assume poy is compatible with whatever was built and
          // poy.toISOString() does the correct thing
          if (poy != null && poy.toISOString() != null) { 
              builder.appendLiteral("-");
	          builder.appendLiteral(poy.toISOString());
	          hasDate = true;
          }
        }
      }
      if (dow != null) {
        if (!JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear()) && !JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfWeek())) {
        	if (poy != null && poy.toISOString() != null) { 
        		builder.appendLiteral("-");
        		builder.appendLiteral(dow.toISOString());
        		hasDate = true;
        	}
        }
      }
      if (hasTime()) {
        if (!hasDate) {
          builder.clear();
        }
        appendTimeFormats(builder, flags);
      } else if (tod != null) {
        if (!hasDate) {
          builder.clear();
        }
        // Assume tod is compatible with whatever was built and
        // tod.toISOString() does the correct thing
        builder.appendLiteral("T");
        if (tod.toISOString() != null)  builder.appendLiteral(tod.toISOString());
      }
      return builder.toFormatter();
    }

    public TimexType getTimexType() {
      return (hasTime() || tod != null) ? TimexType.TIME : TimexType.DATE;
    }
  }

  // The nth temporal
  // Example: The tenth week (of something, don't know yet)
  // The second friday
  public static class OrdinalTime extends Time {
    Temporal base;
    int n;

    public OrdinalTime(Temporal base, int n) {
      this.base = base;
      this.n = n;
    }

    public Time add(Duration offset) {
      return new RelativeTime(this, TemporalOp.OFFSET, offset);
    }

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel().isoString;
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        return null;
      } // TODO: is there timex3 standard?
      if (base != null) {
        String str = base.toFormattedString(flags);
        if (str != null) {
          return str + "-#" + n;
        }
      }
      return null;
    }

    public Temporal intersect(Temporal t) {
      return new RelativeTime(this, TemporalOp.INTERSECT, t);
    }

  }

  // Time with a range (most times have a range...)
  public static class TimeWithRange extends Time {
    Range range; // guess at range

    public TimeWithRange(TimeWithRange t, Range range) {
      super(t);
      this.range = range;
    }

    public TimeWithRange(Range range) {
      this.range = range;
    }

    public Duration getDuration() {
      if (range != null)
        return range.getDuration();
      else
        return null;
    }

    public Range getRange(int flags, Duration granularity) {
      if (range != null) {
        return range.getRange(flags, granularity);
      } else {
        return null;
      }
    }

    public Time add(Duration offset) {
      if (getTimeLabel() != null) {
        // Time has some meaning, keep as is
        return new RelativeTime(this, TemporalOp.OFFSET, offset);
      } else
        return new TimeWithRange(this, range.offset(offset));
    }

    public Time intersect(Time t) {
      if (t == null || t == TIME_UNKNOWN)
        return this;
      if (t instanceof CompositePartialTime) {
        return t.intersect(this);
      } else if (t instanceof PartialTime) {
        return t.intersect(this);
      } else if (t instanceof GroundedTime) {
        return t.intersect(this);
      } else {
        return new TimeWithRange((Range) range.intersect(t));
      }
    }

    public Time resolve(Time refTime, int flags) {
      CompositePartialTime cpt = makeComposite(new PartialTime(new Partial()), this);
      if (cpt != null) {
        return cpt.resolve(refTime, flags);
      }
      Range groundedRange = null;
      if (range != null) {
        groundedRange = range.resolve(refTime, flags).getRange();
      }
      final TimeLabel tl = getTimeLabel();
      TimeWithRange t = new TimeWithRange(this, groundedRange) {
        public TimeLabel getTimeLabel() {
          return tl;
        }
      };
      return t;
    }

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel().isoString;
      }
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        flags |= FORMAT_ISO;
      }
      return range.toFormattedString(flags);
    }
  }

  // Inexact time
  // Not sure when this is, but have some quesses
  public static class InexactTime extends Time {
    Time base; // best guess
    Duration duration; // how long the time lasts
    Range range; // guess at range in which the time occurs

    public InexactTime(Partial partial) {
      this.base = new PartialTime(partial);
      this.range = base.getRange();
      this.approx = true;
    }

    public InexactTime(Time base, Duration duration, Range range) {
      this.base = base;
      this.duration = duration;
      this.range = range;
      this.approx = true;
    }

    public InexactTime(InexactTime t, Time base, Duration duration, Range range) {
      super(t);
      this.base = base;
      this.duration = duration;
      this.range = range;
      this.approx = true;
    }

    public InexactTime(Range range) {
      this.base = range.mid();
      this.range = range;
      this.approx = true;
    }

    public Time getTime() {
      return this;
    }

    public Duration getDuration() {
      if (duration != null)
        return duration;
      if (range != null)
        return range.getDuration();
      else if (base != null)
        return base.getDuration();
      else
        return null;
    }

    public Range getRange(int flags, Duration granularity) {
      if (range != null) {
        return range.getRange(flags, granularity);
      } else if (base != null) {
        return base.getRange(flags, granularity);
      } else
        return null;
    }

    public Time add(Duration offset) {
      if (getTimeLabel() != null) {
        // Time has some meaning, keep as is
        return new RelativeTime(this, TemporalOp.OFFSET, offset);
      } else {
        // Some other time, who know what it means
        // Try to do offset
        return new InexactTime(this, (Time) TemporalOp.OFFSET.apply(base, offset), duration, (Range) TemporalOp.OFFSET.apply(range, offset));
      }
    }

    public Time resolve(Time refTime, int flags) {
      CompositePartialTime cpt = makeComposite(new PartialTime(this, new Partial()), this);
      if (cpt != null) {
        return cpt.resolve(refTime, flags);
      }
      Time groundedBase = null;
      if (base == TIME_REF) {
        groundedBase = refTime;
      } else if (base != null) {
        groundedBase = base.resolve(refTime, flags).getTime();
      }
      Range groundedRange = null;
      if (range != null) {
        groundedRange = range.resolve(refTime, flags).getRange();
      }
      /*    if (groundedRange == range && groundedBase == base) {
            return this;
          } */
      final TimeLabel tl = getTimeLabel();
      InexactTime t = new InexactTime(groundedBase, duration, groundedRange) {
        public TimeLabel getTimeLabel() {
          return tl;
        }
      };
      t.mod = mod;
      return t;
    }

    public Instant getJodaTimeInstant() {
      Instant p = null;
      if (base != null) {
        p = base.getJodaTimeInstant();
      }
      if (p == null && range != null) {
        p = range.mid().getJodaTimeInstant();
      }
      return p;
    }

    public Partial getJodaTimePartial() {
      Partial p = null;
      if (base != null) {
        p = base.getJodaTimePartial();
      }
      if (p == null && range != null) {
        p = range.mid().getJodaTimePartial();
      }
      return p;
    }

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel().isoString;
      }

      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        return null;
      } // TODO: is there timex3 standard?
      StringBuilder sb = new StringBuilder();
      sb.append("~(");
      if (base != null) {
        sb.append(base.toFormattedString(flags));
      }
      if (duration != null) {
        sb.append(":");
        sb.append(duration.toFormattedString(flags));
      }
      if (range != null) {
        sb.append(" IN ");
        sb.append(range.toFormattedString(flags));
      }
      sb.append(")");
      return sb.toString();
    }

  }

  // Relative Time (something not quite resolved)
  public static class RelativeTime extends Time {
    Time base = TIME_REF;
    TemporalOp tempOp;
    Temporal tempArg;
    int opFlags;

    public RelativeTime(Time base, TemporalOp tempOp, Temporal tempArg, int flags) {
      super(base);
      this.base = base;
      this.tempOp = tempOp;
      this.tempArg = tempArg;
      this.opFlags = flags;
    }

    public RelativeTime(Time base, TemporalOp tempOp, Temporal tempArg) {
      super(base);
      this.base = base;
      this.tempOp = tempOp;
      this.tempArg = tempArg;
    }

    public RelativeTime(TemporalOp tempOp, Temporal tempArg) {
      this.tempOp = tempOp;
      this.tempArg = tempArg;
    }

    public RelativeTime(TemporalOp tempOp, Temporal tempArg, int flags) {
      this.tempOp = tempOp;
      this.tempArg = tempArg;
      this.opFlags = flags;
    }

    public RelativeTime(Duration offset) {
      this(TIME_REF, TemporalOp.OFFSET, offset);
    }

    public RelativeTime(Time base, Duration offset) {
      this(base, TemporalOp.OFFSET, offset);
    }

    public RelativeTime(Time base) {
      this.base = base;
    }

    public RelativeTime() {
    }

    public boolean isGrounded() {
      return (base != null) && base.isGrounded();
    }

    // TODO: compute duration/range => uncertainty of this time
    public Duration getDuration() {
      return null;
    }

    public Range getRange(int flags, Duration granularity) {
      return new Range(this, this);
    }

    public Map<String, String> getTimexAttributes(TimeIndex timeIndex) {
      Map<String, String> map = super.getTimexAttributes(timeIndex);
      String tfid = getTfidString(timeIndex);
      map.put(TimexAttr.temporalFunction.name(), "true");
      map.put(TimexAttr.valueFromFunction.name(), tfid);
      if (base != null) {
        map.put(TimexAttr.anchorTimeID.name(), base.getTidString(timeIndex));
      }
      return map;
    }

    // / NOTE: This is not ISO or timex standard
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        if (getTimeLabel().isoString != null) {
          return getTimeLabel().isoString;
        }
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        return null;
      } // TODO: is there timex3 standard?
      StringBuilder sb = new StringBuilder();
      if (base != null && base != TIME_REF) {
        sb.append(base.toFormattedString(flags));
      }
      if (tempOp != null) {
        if (sb.length() > 0) {
          sb.append(" ");
        }
        sb.append(tempOp).append(" ");
        sb.append(tempArg.toFormattedString(flags));
      }
      return sb.toString();
    }

    public Temporal resolve(Time refTime, int flags) {
      Temporal groundedBase = null;
      if (base == TIME_REF) {
        groundedBase = refTime;
      } else if (base != null) {
        groundedBase = base.resolve(refTime, flags);
      }
      if (tempOp != null) {
        // NOTE: Should be always safe to resolve and then apply since
        // we will terminate here (no looping hopefully)
        Temporal t = tempOp.apply(groundedBase, tempArg, opFlags);
        if (t != null) {
          t = t.addModApprox(mod, approx);
          return t;
        } else {
          // NOTE: this can be difficult if applying op
          // gives back same stuff stuff as before
          // Try applying op and then resolving
          t = tempOp.apply(base, tempArg, opFlags);
          if (t != null) {
            t = t.addModApprox(mod, approx);
            if (!this.equals(t)) {
              return t.resolve(refTime, flags);
            } else {
              // Applying op doesn't do much....
              return this;
            }
          } else {
            return null;
          }
        }
      } else {
        return (groundedBase != null) ? groundedBase.addModApprox(mod, approx) : null;
      }
    }

    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      RelativeTime that = (RelativeTime) o;

      if (opFlags != that.opFlags) {
        return false;
      }
      if (base != null ? !base.equals(that.base) : that.base != null) {
        return false;
      }
      if (tempArg != null ? !tempArg.equals(that.tempArg) : that.tempArg != null) {
        return false;
      }
      if (tempOp != that.tempOp) {
        return false;
      }

      return true;
    }

    public int hashCode() {
      int result = base != null ? base.hashCode() : 0;
      result = 31 * result + (tempOp != null ? tempOp.hashCode() : 0);
      result = 31 * result + (tempArg != null ? tempArg.hashCode() : 0);
      result = 31 * result + opFlags;
      return result;
    }

    public Time add(Duration offset) {
      Time t;
      Duration d = offset;
      if (this.tempOp == null) {
        t = new RelativeTime(base, d);
        t.approx = this.approx;
        t.mod = this.mod;
      } else if (this.tempOp == TemporalOp.OFFSET) {
        d = ((Duration) this.tempArg).add(offset);
        t = new RelativeTime(base, d);
        t.approx = this.approx;
        t.mod = this.mod;
      } else {
        t = new RelativeTime(this, d);
      }
      return t;
    }

    public Temporal intersect(Temporal t) {
      return new RelativeTime(this, TemporalOp.INTERSECT, t);
    }
  }

  // Partial time with Joda Time fields
  public static class PartialTime extends Time {
    // There is typically some uncertainty/imprecision in the time
    Partial base; // For representing partial absolute time

    // private static DateTimeFormatter isoDateFormatter =
    // ISODateTimeFormat.basicDate();
    // private static DateTimeFormatter isoDateTimeFormatter =
    // ISODateTimeFormat.basicDateTimeNoMillis();
    // private static DateTimeFormatter isoTimeFormatter =
    // ISODateTimeFormat.basicTTimeNoMillis();
    // private static DateTimeFormatter isoDateFormatter =
    // ISODateTimeFormat.date();
    // private static DateTimeFormatter isoDateTimeFormatter =
    // ISODateTimeFormat.dateTimeNoMillis();
    // private static DateTimeFormatter isoTimeFormatter =
    // ISODateTimeFormat.tTimeNoMillis();

    public PartialTime(Time t, Partial p) {
      super(t);
      this.base = p;
    }

    public PartialTime(PartialTime pt) {
      super(pt);
      this.base = pt.base;
    }

    // public PartialTime(Partial base, String mod) { this.base = base; this.mod
    // = mod; }
    public PartialTime(Partial base) {
      this.base = base;
    }

    public PartialTime() {
    }

    public Instant getJodaTimeInstant() {
      return JodaTimeUtils.getInstant(base);
    }

    public Partial getJodaTimePartial() {
      return base;
    }

    public boolean hasTime() {
      if (base == null)
        return false;
      DateTimeFieldType sdft = JodaTimeUtils.getMostSpecific(base);
      if (sdft != null && JodaTimeUtils.isMoreGeneral(DateTimeFieldType.dayOfMonth(), sdft, base.getChronology())) {
        return true;
      } else {
        return false;
      }
    }

    protected boolean appendDateFormats(DateTimeFormatterBuilder builder, int flags) {
      boolean alwaysPad = ((flags & FORMAT_PAD_UNKNOWN) != 0);
      boolean hasDate = true;
      boolean isISO = ((flags & FORMAT_ISO) != 0);
      boolean isTimex3 = ((flags & FORMAT_TIMEX3_VALUE) != 0);
      // ERA
      if (JodaTimeUtils.hasField(base, DateTimeFieldType.era())) {
        int era = base.get(DateTimeFieldType.era());
        if (era == 0) {
          builder.appendLiteral('-');
        } else if (era == 1) {
          builder.appendLiteral('+');
        }
      }
      // YEAR
      if (JodaTimeUtils.hasField(base, DateTimeFieldType.centuryOfEra()) || JodaTimeUtils.hasField(base, JodaTimeUtils.DecadeOfCentury)
          || JodaTimeUtils.hasField(base, DateTimeFieldType.yearOfCentury())) {
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.centuryOfEra())) {
          builder.appendCenturyOfEra(2, 2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        if (JodaTimeUtils.hasField(base, JodaTimeUtils.DecadeOfCentury)) {
          builder.appendDecimal(JodaTimeUtils.DecadeOfCentury, 1, 1);
          builder.appendLiteral(PAD_FIELD_UNKNOWN);
        } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.yearOfCentury())) {
          builder.appendYearOfCentury(2, 2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
      } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.year())) {
        builder.appendYear(4, 4);
      } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.weekyear())) {
        builder.appendWeekyear(4, 4);
      } else {
        builder.appendLiteral(PAD_FIELD_UNKNOWN4);
        hasDate = false;
      }
      // Decide whether to include QUARTER, MONTH/DAY, or WEEK/WEEKDAY
      boolean appendQuarter = false;
      boolean appendMonthDay = false;
      boolean appendWeekDay = false;
      if (isISO || isTimex3) {
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear()) && JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfMonth())) {
          appendMonthDay = true;
        } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.weekOfWeekyear()) || JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfWeek())) {
          appendWeekDay = true;
        } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear()) || JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfMonth())) {
          appendMonthDay = true;
        } else if (JodaTimeUtils.hasField(base, JodaTimeUtils.QuarterOfYear)) {
          appendQuarter = true;
        }
      } else {
        appendQuarter = true;
        appendMonthDay = true;
        appendWeekDay = true;
      }

      // Quarter
      if (appendQuarter && JodaTimeUtils.hasField(base, JodaTimeUtils.QuarterOfYear)) {
        builder.appendLiteral("-Q");
        builder.appendDecimal(JodaTimeUtils.QuarterOfYear, 1, 1);
      }
      // MONTH
      if (appendMonthDay && (JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear()) || JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfMonth()))) {
        hasDate = true;
        builder.appendLiteral('-');
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear())) {
          builder.appendMonthOfYear(2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        // Don't indicate day of month if not specified
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfMonth())) {
          builder.appendLiteral('-');
          builder.appendDayOfMonth(2);
        } else if (alwaysPad) {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
      }
      if (appendWeekDay && (JodaTimeUtils.hasField(base, DateTimeFieldType.weekOfWeekyear()) || JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfWeek()))) {
        hasDate = true;
        builder.appendLiteral("-W");
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.weekOfWeekyear())) {
          builder.appendWeekOfWeekyear(2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        // Don't indicate the day of the week if not specified
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfWeek())) {
          builder.appendLiteral("-");
          builder.appendDayOfWeek(1);
        }
      }
      return hasDate;
    }

    protected boolean appendTimeFormats(DateTimeFormatterBuilder builder, int flags) {
      boolean alwaysPad = ((flags & FORMAT_PAD_UNKNOWN) != 0);
      boolean hasTime = hasTime();
      DateTimeFieldType sdft = JodaTimeUtils.getMostSpecific(base);
      if (hasTime) {
        builder.appendLiteral("T");
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.hourOfDay())) {
          builder.appendHourOfDay(2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.minuteOfHour())) {
          builder.appendLiteral(":");
          builder.appendMinuteOfHour(2);
        } else if (alwaysPad || JodaTimeUtils.isMoreGeneral(DateTimeFieldType.minuteOfHour(), sdft, base.getChronology())) {
          builder.appendLiteral(":");
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.secondOfMinute())) {
          builder.appendLiteral(":");
          builder.appendSecondOfMinute(2);
        } else if (alwaysPad || JodaTimeUtils.isMoreGeneral(DateTimeFieldType.secondOfMinute(), sdft, base.getChronology())) {
          builder.appendLiteral(":");
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.millisOfSecond())) {
          builder.appendLiteral(".");
          builder.appendMillisOfSecond(3);
        }
        // builder.append(isoTimeFormatter);
      }
      return hasTime;
    }

    protected DateTimeFormatter getFormatter(int flags) {
      DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
      boolean hasDate = appendDateFormats(builder, flags);
      boolean hasTime = hasTime();
      if (hasTime) {
        if (!hasDate) {
          builder.clear();
        }
        appendTimeFormats(builder, flags);
      }
      return builder.toFormatter();
    }

    public boolean isGrounded() {
      return false;
    }

    // TODO: compute duration/range => uncertainty of this time
    public Duration getDuration() {
      TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getDuration();
      }
      TimeLabelType tlt = getTimeLabelType();
      if (tlt != null) {
        return tlt.getDuration();
      }
      return Duration.getDuration(JodaTimeUtils.getJodaTimePeriod(base));
    }

    public Range getRange(int flags, Duration inputGranularity) {
      Duration d = getDuration();
      if (d != null) {
        int padType = (flags & RANGE_FLAGS_PAD_MASK);
        Time start = this;
        Duration granularity = inputGranularity;
        switch (padType) {
        case RANGE_FLAGS_PAD_NONE:
          // The most basic range
          start = this;
          break;
        case RANGE_FLAGS_PAD_AUTO:
          // More complex range
          if (hasTime()) {
            granularity = SUTime.MILLIS;
          } else {
            granularity = SUTime.DAY;
          }
          start = padMoreSpecificFields(granularity);
          break;
        case RANGE_FLAGS_PAD_FINEST:
          granularity = SUTime.MILLIS;
          start = padMoreSpecificFields(granularity);
          break;
        case RANGE_FLAGS_PAD_SPECIFIED:
          start = padMoreSpecificFields(granularity);
          break;
        default:
          throw new UnsupportedOperationException("Unsupported pad type for getRange: " + flags);
        }
        if (start instanceof PartialTime) {
          ((PartialTime) start).withStandardFields();
        }
        Time end = start.add(d);
        if (granularity != null) {
          end = end.subtract(granularity);
        }
        return new Range(start, end, d);
      } else {
        return new Range(this, this);
      }
    }

    protected void withStandardFields() {
      if (base.isSupported(DateTimeFieldType.dayOfWeek())) {
        base = JodaTimeUtils.resolveDowToDay(base);
      } else if (base.isSupported(DateTimeFieldType.monthOfYear()) && base.isSupported(DateTimeFieldType.dayOfMonth())) {
        if (base.isSupported(DateTimeFieldType.weekOfWeekyear())) {
          base = base.without(DateTimeFieldType.weekOfWeekyear());
        }
        if (base.isSupported(DateTimeFieldType.dayOfWeek())) {
          base = base.without(DateTimeFieldType.dayOfWeek());
        }
      }
    }

    public PartialTime padMoreSpecificFields(Duration granularity) {
      Period period = null;
      if (granularity != null) {
        period = granularity.getJodaTimePeriod();
      }
      Partial p = JodaTimeUtils.padMoreSpecificFields(base, period);
      return new PartialTime(p);
    }

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        if (getTimeLabel().isoString != null) {
          return getTimeLabel().isoString;
        }
      }
      if (base != null) {
        // String s = ISODateTimeFormat.basicDateTime().print(base);
        // return s.replace('\ufffd', 'X');
        DateTimeFormatter formatter = getFormatter(flags);
        return formatter.print(base);
      } else {
        return "XXXX-XX-XX";
      }
    }

    public Time resolve(Time ref, int flags) {
      if (ref == null || ref == TIME_UNKNOWN || ref == TIME_REF) {
        return this;
      }
      if (this == TIME_REF) {
        return ref;
      }
      if (this == TIME_UNKNOWN) {
        return this;
      }
      Partial partialRef = ref.getJodaTimePartial();
      if (partialRef == null) {
        throw new UnsupportedOperationException("Cannot resolve if reftime is of class: " + ref.getClass());
      }
      Partial p = (base != null) ? JodaTimeUtils.combineMoreGeneralFields(base, partialRef) : partialRef;
      p = JodaTimeUtils.resolveDowToDay(p, partialRef);

      Time resolved;
      if (p == base) {
        resolved = this;
      } else {
        resolved = new PartialTime(this, p);
      }

      if ((flags & RESOLVE_TO_PAST) != 0) {
        if (resolved.compareTo(ref) > 0) {
          Time t = (Time) this.prev();
          if (t != null) {
            resolved = (Time) t.resolve(ref, 0);
          }
        }
      } else if ((flags & RESOLVE_TO_FUTURE) != 0) {
        if (resolved.compareTo(ref) < 0) {
          Time t = (Time) this.next();
          if (t != null) {
            resolved = (Time) t.resolve(ref, 0);
          }
        }
      }
      return resolved;
    }

    public boolean isCompatible(PartialTime time) {
      return JodaTimeUtils.isCompatible(base, time.base);
    }

    public Duration getPeriod() {
      TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getPeriod();
      }
      TimeLabelType tlt = getTimeLabelType();
      if (tlt != null) {
        return tlt.getPeriod();
      }
      if (base == null) {
        return null;
      }
      DateTimeFieldType mostGeneral = JodaTimeUtils.getMostGeneral(base);
      DurationFieldType df = mostGeneral.getRangeDurationType();
      // if (df == null) {
      // df = mostGeneral.getDurationType();
      // }
      if (df != null) {
        return new DurationWithFields(new Period().withField(df, 1));
      }
      return null;
    }

    public Time intersect(Time t) {
      if (t == null || t == TIME_UNKNOWN)
        return this;
      if (base == null)
        return t;
      if (t instanceof CompositePartialTime) {
        return t.intersect(this);
      } else if (t instanceof PartialTime) {
        if (!isCompatible((PartialTime) t)) {
          return null;
        }
        try {
        Partial p = JodaTimeUtils.combine(base, ((PartialTime) t).base);
        return new PartialTime(p);
        } catch (NullPointerException e) {
        	return null;
        }
      } else if (t instanceof GroundedTime) {
        return t.intersect(this);
      } else if (t instanceof RelativeTime) {
        return t.intersect(this);
      } else {
        Time cpt = makeComposite(this, t);
        if (cpt != null) {
          return cpt;
        }
        if (t instanceof InexactTime) {
          return t.intersect(this);
        }
      }
      return null;
      // return new RelativeTime(this, TemporalOp.INTERSECT, t);
    }

    public Temporal intersect(Temporal t) {
      if (t == null)
        return this;
      if (t == TIME_UNKNOWN || t == DURATION_UNKNOWN)
        return this;
      if (base == null)
        return t;
      if (t instanceof Time) {
        return intersect((Time) t);
      } else if (t instanceof Range) {
        return t.intersect(this);
      } else if (t instanceof Duration) {
        return new RelativeTime(this, TemporalOp.INTERSECT, t);
      }
      return null;
    }

    protected PartialTime addSupported(Period p, int scalar) {
    	try{
    		return new PartialTime(base.withPeriodAdded(p, scalar));
    	}catch(Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    }

    protected PartialTime addUnsupported(Period p, int scalar) {
    	try{
    		return new PartialTime(this, JodaTimeUtils.addForce(base, p, scalar));
    	}catch(Exception e) {
    		e.printStackTrace();
    		return null;
    	}
    }

    public Time add(Duration offset) {
      if (base == null || offset == null) {
        return this;
      }
      Period per = offset.getJodaTimePeriod();
      PartialTime p = addSupported(per, 1);
      if (p == null) return this;
      Period unsupported = JodaTimeUtils.getUnsupportedDurationPeriod(p.base, per);
      Time t = p;
      if (unsupported != null) {
        if (/*unsupported.size() == 1 && */JodaTimeUtils.hasField(unsupported, DurationFieldType.weeks()) && JodaTimeUtils.hasField(p.base, DateTimeFieldType.year())
            && JodaTimeUtils.hasField(p.base, DateTimeFieldType.monthOfYear()) && JodaTimeUtils.hasField(p.base, DateTimeFieldType.dayOfMonth())) {
          // What if there are other unsupported fields...
          t = p.addUnsupported(per, 1); // new PartialTime(this,
          // JodaTimeUtils.add(p.base, per));
        } else {
          if (JodaTimeUtils.hasField(unsupported, DurationFieldType.months()) && unsupported.getMonths() % 3 == 0 && JodaTimeUtils.hasField(p.base, JodaTimeUtils.QuarterOfYear)) {
            Partial p2 = p.base.withFieldAddWrapped(JodaTimeUtils.Quarters, unsupported.getMonths() / 3);
            p = new PartialTime(p, p2);
            unsupported = unsupported.withMonths(0);
          }
          if (JodaTimeUtils.hasField(unsupported, DurationFieldType.years()) && unsupported.getYears() % 10 == 0 && JodaTimeUtils.hasField(p.base, JodaTimeUtils.DecadeOfCentury)) {
            Partial p2 = p.base.withFieldAddWrapped(JodaTimeUtils.Decades, unsupported.getYears() / 10);
            p = new PartialTime(p, p2);
            unsupported = unsupported.withYears(0);
          }
          if (JodaTimeUtils.hasField(unsupported, DurationFieldType.years()) && unsupported.getYears() % 100 == 0
              && JodaTimeUtils.hasField(p.base, DateTimeFieldType.centuryOfEra())) {
            Partial p2 = p.base.withField(DateTimeFieldType.centuryOfEra(), p.base.get(DateTimeFieldType.centuryOfEra()) + unsupported.getYears() / 100);
            p = new PartialTime(p, p2);
            unsupported = unsupported.withYears(0);
          }
          if (unsupported.getDays() > 0 && !JodaTimeUtils.hasField(p.base, DateTimeFieldType.dayOfYear()) && !JodaTimeUtils.hasField(p.base, DateTimeFieldType.dayOfMonth())
              && !JodaTimeUtils.hasField(p.base, DateTimeFieldType.dayOfWeek()) && JodaTimeUtils.hasField(p.base, DateTimeFieldType.monthOfYear())) {
            Partial p2 = p.base.with(DateTimeFieldType.dayOfMonth(), unsupported.getDays());
            p = new PartialTime(p, p2);
            unsupported = unsupported.withDays(0);
          }
          if (!unsupported.equals(Period.ZERO)) {
            t = new RelativeTime(p, new DurationWithFields(unsupported));
            t.approx = this.approx;
            t.mod = this.mod;
          } else {
            t = p;
          }
        }
      }
      return t;
    }
  }

  // Helper ISO classes
  // Can be removed

  public static class IsoDate extends PartialTime {
    int era = -1;
    int year = -1;
    int month = -1;
    int day = -1;
    String isoDateString;

    public IsoDate(int y, int m, int d) {
      this.year = y;
      this.month = m;
      this.day = d;
      /*   String y2 = (year >= 0)? String.format("%04d", year): PAD_FIELD_UNKNOWN4;
         String m2 = (month >= 0)? String.format("%02d", month): PAD_FIELD_UNKNOWN2;
         String d2 = (day >= 0)? String.format("%02d", day): PAD_FIELD_UNKNOWN2;
         isoDateString = y2 + "-" + m2 + "-" + d2;            */
      initBase();
    }

    public IsoDate(String y, String m, String d) {
      if (y != null && !PAD_FIELD_UNKNOWN4.equals(y)) {
        y = DateTimeUtils.year2Iso(y);
        if (y.startsWith("-")) {
          y = y.substring(1);
          era = 0; // BC
        } else if (y.startsWith("+")) {
          era = 1; // AD
        }
        if (y.contains(PAD_FIELD_UNKNOWN)) {
        } else {
          year = DateTimeUtils.year2Num(y);
        }
      } else {
        y = PAD_FIELD_UNKNOWN4;
      }
      if (m != null && !PAD_FIELD_UNKNOWN2.equals(y)) {
        month = DateTimeUtils.month2Num(m);
      } else {
        m = PAD_FIELD_UNKNOWN2;
      }
      if (d != null && !PAD_FIELD_UNKNOWN2.equals(d)) {
        day = DateTimeUtils.day2Num(month, d);
      } else {
        d = PAD_FIELD_UNKNOWN2;
      }

      /*   String y2 = (year >= 0)? String.format("%04d", year): y;
         String m2 = (month >= 0)? String.format("%02d", month): m;
         String d2 = (day >= 0)? String.format("%02d", day): d;
         isoDateString = y2 + "-" + m2 + "-" + d2; */
      initBase();
      if (year < 0 && !PAD_FIELD_UNKNOWN4.equals(y)) {
        if (Character.isDigit(y.charAt(0)) && Character.isDigit(y.charAt(1))) {
          int century = Integer.parseInt(y.substring(0, 2));
          base = JodaTimeUtils.setField(base, DateTimeFieldType.centuryOfEra(), century);
        }
        if (Character.isDigit(y.charAt(2)) && Character.isDigit(y.charAt(3))) {
          int cy = Integer.parseInt(y.substring(2, 4));
          base = JodaTimeUtils.setField(base, DateTimeFieldType.yearOfCentury(), cy);
        } else if (Character.isDigit(y.charAt(2))) {
          int decade = Integer.parseInt(y.substring(2, 3));
          base = JodaTimeUtils.setField(base, JodaTimeUtils.DecadeOfCentury, decade);
        }
      }
    }

    private void initBase() {
      if (era >= 0 )
        base = JodaTimeUtils.setField(base, DateTimeFieldType.era(), era);
      if (year >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.year(), year);
      if (month >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.monthOfYear(), month);
      if (day >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.dayOfMonth(), day);
    }

    public String toString() {
      // TODO: is the right way to print this object?
      StringBuffer os = new StringBuffer();
      if (era == 0) {
        os.append("-");
      } else if (era == 1) {
        os.append("+");
      }
      if (year >= 0)
        os.append(year);
      else
        os.append("XXXX");
      os.append("-");
      if (month >= 0)
        os.append(month);
      else
        os.append("XX");
      os.append("-");
      if (day >= 0)
        os.append(day);
      else
        os.append("XX");
      return os.toString();
    }

    public int getYear() {
      return year;
    }

    public void setYear(int y) {
      this.year = y;
    }

    public int getMonth() {
      return month;
    }

    public void setMonth(int m) {
      this.month = m;
    }

    public int getDay() {
      return day;
    }

    public void setDay(int d) {
      this.day = d;
    }

    public void setDate(int y, int m, int d) {
      this.year = y;
      this.month = m;
      this.day = d;
    }
    /*    public String toISOString()
        {
          return isoDateString;
        } */
  }

  // Helper time class
  protected static class IsoTime extends PartialTime {
    int hour = -1;
    int minute = -1;
    int second = -1;
    int millis = -1;

    // String isoTimeString;

    public IsoTime(int h, int m, int s) {
      this(h, m, s, -1);
    }

    public IsoTime(int h, int m, int s, int ms) {
      this.hour = h;
      this.minute = m;
      this.second = s;
      this.millis = ms;
      /*    String h2 = (hour >= 0)? String.format("%02d", hour): PAD_FIELD_UNKNOWN2;
          String m2 = (minute >= 0)? String.format("%02d", minute): PAD_FIELD_UNKNOWN2;
          String s2 = (second >= 0)? String.format("%02d", second): PAD_FIELD_UNKNOWN2;
          String ms2 = (millis >= 0)? String.format(".%04d", millis): "";
          isoTimeString = "T" + h2 + ":" + m2 + ":" + s2 + ms2;  */
      initBase();
    }

    public IsoTime(String h, String m, String s) {
      this(h, m, s, null);
    }

    public IsoTime(String h, String m, String s, String ms) {
      if (h != null) {
        hour = Integer.parseInt(h);
      }
      if (m != null) {
        minute = Integer.parseInt(m);
      }
      if (s != null) {
        second = Integer.parseInt(s);
      }
      if (ms != null) {
        millis = Integer.parseInt(s);
      }
      /*   String h2 = (hour >= 0)? String.format("%02d", hour): PAD_FIELD_UNKNOWN2;
         String m2 = (minute >= 0)? String.format("%02d", minute): PAD_FIELD_UNKNOWN2;
         String s2 = (second >= 0)? String.format("%02d", second): PAD_FIELD_UNKNOWN2;
         String ms2 = (millis >= 0)? String.format(".%04d", millis): "";
         isoTimeString = "T" + h2 + ":" + m2 + ":" + s2 + ms2;    */
      initBase();
    }

    public boolean hasTime() {
      return true;
    }

    private void initBase() {
      if (hour >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.hourOfDay(), hour);
      if (minute >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.minuteOfHour(), minute);
      if (second >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.secondOfMinute(), second);
      if (millis >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.millisOfSecond(), millis);
    }

    /*    public String toISOString()
        {
          return isoTimeString;
        }  */
  }

  protected static class IsoDateTime extends PartialTime {
    IsoDate date;
    IsoTime time;

    public IsoDateTime(IsoDate date, IsoTime time) {
      this.date = date;
      this.time = time;
      base = JodaTimeUtils.combine(date.base, time.base);
    }

    public boolean hasTime() {
      return (time != null);
    }

    /*    public String toISOString()
        {
          return date.toISOString() + time.toISOString();
        }  */
  }

  /*
  public static class Holiday extends Time {
    de.jollyday.config.Holiday base;

    public Holiday(de.jollyday.config.Holiday base) { this.base = base; }
    public Holiday() {}

    public boolean isGrounded()  { return false; }
    public Time getTime() { return this; }
    // TODO: compute duration/range => uncertainty of this time
    public Duration getDuration() { return DURATION_NONE; }
    public Range getRange(int flags, Duration granularity) { return new Range(this,this); }
    public String toISOString() { return base.toString(); }
    public Time ground(GroundedTime ref) {
      ReadableInstant grounded = base.toDateTime(ref.base);
      return new GroundedTime(grounded);
    }
  }      */

  public static class GroundedTime extends Time {
    // Represents an absolute time
    ReadableInstant base;

    public GroundedTime(ReadableInstant base, String mod) {
      this.base = base;
      this.mod = mod;
    }

    public GroundedTime(ReadableInstant base) {
      this.base = base;
    }

    public boolean hasTime() {
      return true;
    }

    public boolean isGrounded() {
      return true;
    }

    public Duration getDuration() {
      return DURATION_NONE;
    }

    public Range getRange(int flags, Duration granularity) {
      return new Range(this, this);
    }

    public String toFormattedString(int flags) {
      return base.toString();
    }

    public Time resolve(Time refTime, int flags) {
      return this;
    }

    public Time add(Duration offset) {
      Period p = offset.getJodaTimePeriod();
      GroundedTime g = new GroundedTime(base.toInstant().withDurationAdded(p.toDurationFrom(base), 1));
      g.approx = this.approx;
      g.mod = this.mod;
      return g;
    }

    public Time intersect(Time t) {
      if (t.getRange().contains(this.getRange())) {
        return this;
      } else {
        return null;
      }
    }

    public Temporal intersect(Temporal other) {
      if (other == null)
        return this;
      if (other == TIME_UNKNOWN)
        return this;
      if (other.getRange().contains(this.getRange())) {
        return this;
      } else {
        return null;
      }
    }

    public Instant getJodaTimeInstant() {
      return base.toInstant();
    }

    public Partial getJodaTimePartial() {
      return JodaTimeUtils.getPartial(base.toInstant(), JodaTimeUtils.EMPTY_ISO_PARTIAL);
    }

  }

  // Duration classes
  /**
   * A Duration represents a period of time (without endpoints) We have the 3
   * types of durations: DurationWithFields - corresponds to JodaTime Period,
   * where we have fields like hours, weeks, etc DurationWithMillis -
   * corresponds to JodaTime Duration, where the duration is specified in millis
   * this gets rid of certain ambiguities such as a month with can be 28, 30, or
   * 31 days InexactDuration - duration that is under determined (like a few
   * days)
   */
  public abstract static class Duration extends Temporal implements FuzzyInterval.FuzzyComparable<Duration> {

    public Duration() {
    }

    public Duration(Duration d) {
      super(d);
    }

    public static Duration getDuration(ReadablePeriod p) {
      return new DurationWithFields(p);
    }

    public static Duration getDuration(org.joda.time.Duration d) {
      return new DurationWithMillis(d);
    }

    public static Duration getInexactDuration(ReadablePeriod p) {
      return new InexactDuration(p);
    }

    public static Duration getInexactDuration(org.joda.time.Duration d) {
      return new InexactDuration(d.toPeriod());
    }

    // Returns the inexact version of the duration
    public InexactDuration makeInexact() {
      return new InexactDuration(getJodaTimePeriod());
    }

    public DateTimeFieldType[] getDateTimeFields() {
      return null;
    }

    public boolean isGrounded() {
      return false;
    }

    public Time getTime() {
      return null;
    } // There is no time associated with a duration?

    public Time toTime(Time refTime) {
      return toTime(refTime, 0);
    }

    public Time toTime(Time refTime, int flags) {
      // if ((flags & (DUR_RESOLVE_FROM_AS_REF | DUR_RESOLVE_TO_AS_REF)) == 0)
      {
        Partial p = refTime.getJodaTimePartial();
        if (p != null) {
          // For durations that have corresponding date time fields
          // this = current time without more specific fields than the duration
          DateTimeFieldType[] dtFieldTypes = getDateTimeFields();
          Time t = null;
          if (dtFieldTypes != null) {
            for (DateTimeFieldType dtft : dtFieldTypes) {
              if (p.isSupported(dtft)) {
                t = new PartialTime(JodaTimeUtils.discardMoreSpecificFields(p, dtft));
              }
            }
            if (t == null) {
              Instant instant = refTime.getJodaTimeInstant();
              if (instant != null) {
                for (DateTimeFieldType dtft : dtFieldTypes) {
                  if (instant.isSupported(dtft)) {
                    Partial p2 = JodaTimeUtils.getPartial(instant, p.with(dtft, 1));
                    t = new PartialTime(JodaTimeUtils.discardMoreSpecificFields(p2, dtft));
                  }
                }
              }
            }
            if (t != null) {
              if ((flags & RESOLVE_TO_PAST) != 0) {
                // Check if this time is in the past, if not, subtract duration
                if (t.compareTo(refTime) >= 0) {
                  return t.subtract(this);
                }
              } else if ((flags & RESOLVE_TO_FUTURE) != 0) {
                // Check if this time is in the future, if not, subtract
                // duration
                if (t.compareTo(refTime) <= 0) {
                  return t.add(this);
                }
              }
            }
            return t;
          }
        }
      }
      Time minTime = refTime.subtract(this);
      Time maxTime = refTime.add(this);
      Range likelyRange = null;
      if ((flags & (DUR_RESOLVE_FROM_AS_REF | RESOLVE_TO_FUTURE)) != 0) {
        likelyRange = new Range(refTime, maxTime, this);
      } else if ((flags & (DUR_RESOLVE_TO_AS_REF | RESOLVE_TO_PAST)) != 0) {
        likelyRange = new Range(minTime, refTime, this);
      } else {
        Duration halfDuration = this.divideBy(2);
        likelyRange = new Range(refTime.subtract(halfDuration), refTime.add(halfDuration), this);
      }
      if ((flags & (RESOLVE_TO_FUTURE | RESOLVE_TO_PAST)) != 0) {
        return new TimeWithRange(likelyRange);
      }
      Range r = new Range(minTime, maxTime, this.multiplyBy(2));
      return new InexactTime(new TimeWithRange(likelyRange), this, r);
    }

    public Duration getDuration() {
      return this;
    }

    public Range getRange(int flags, Duration granularity) {
      return new Range(null, null, this);
    } // Unanchored range

    public TimexType getTimexType() {
      return TimexType.DURATION;
    }

    public abstract Period getJodaTimePeriod();

    public abstract org.joda.time.Duration getJodaTimeDuration();

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        if (getTimeLabel().isoString != null) {
          return getTimeLabel().isoString;
        }
      }
      Period p = getJodaTimePeriod();
      String s = (p != null) ? p.toString() : "PXX";
      if ((flags & (FORMAT_ISO | FORMAT_TIMEX3_VALUE)) == 0) {
        String m = getMod();
        if (m != null) {
          try {
            TimexMod tm = TimexMod.valueOf(m);
            if (tm.getSymbol() != null) {
              s = tm.getSymbol() + s;
            }
          } catch (Exception ex) {
          }
        }
      }
      return s;
    }

    public Duration getPeriod() {
      TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getPeriod();
      }
      TimeLabelType tlt = getTimeLabelType();
      if (tlt != null) {
        return tlt.getPeriod();
      }
      return this;
    }

    // Rough approximate ordering of durations
    public int compareTo(Duration d) {
      org.joda.time.Duration d1 = getJodaTimeDuration();
      org.joda.time.Duration d2 = d.getJodaTimeDuration();
      if (d1 == null && d2 == null) {
        return 0;
      } else if (d1 == null) {
        return 1;
      } else if (d2 == null) {
        return -1;
      }

      int cmp = d1.compareTo(d2);
      if (cmp == 0) {
        if (d.isApprox() && !this.isApprox()) {
          // Put exact in front of approx
          return -1;
        } else if (!d.isApprox() && this.isApprox()) {
          return 1;
        } else {
          return 0;
        }
      } else {
        return cmp;
      }
    }

    public boolean isComparable(Duration d) {
      // TODO: When is two durations comparable?
      return true;
    }

    // Operations with durations
    public abstract Duration add(Duration d);

    public abstract Duration multiplyBy(int m);

    public abstract Duration divideBy(int m);

    public Duration subtract(Duration d) {
      return add(d.multiplyBy(-1));
    };

    public Duration resolve(Time refTime, int flags) {
      return this;
    }

    public Temporal intersect(Temporal t) {
      if (t == null)
        return this;
      if (t == TIME_UNKNOWN || t == DURATION_UNKNOWN)
        return this;
      if (t instanceof Time) {
        return new RelativeTime((Time) t, TemporalOp.INTERSECT, this);
      } else if (t instanceof Range) {
        // return new TemporalSet(t, TemporalOp.INTERSECT, this);
      } else if (t instanceof Duration) {
        Duration d = (Duration) t;
        return intersect(d);
      }
      return null;
    }

    public Duration intersect(Duration d) {
      if (d == null || d == DURATION_UNKNOWN)
        return this;
      int cmp = compareTo(d);
      if (cmp < 0) {
        return this;
      } else {
        return d;
      }
    }

    public static Duration min(Duration d1, Duration d2) {
      if (d2 == null)
        return d1;
      if (d1 == null)
        return d2;
      if (d1.isComparable(d2)) {
        int c = d1.compareTo(d2);
        return (c < 0) ? d1 : d2;
      }
      return d1;
    }

    public static Duration max(Duration d1, Duration d2) {
      if (d1 == null)
        return d2;
      if (d2 == null)
        return d1;
      if (d1.isComparable(d2)) {
        int c = d1.compareTo(d2);
        return (c >= 0) ? d1 : d2;
      }
      return d2;
    }
  }

  public static class DurationWithFields extends Duration {
    // Use Inexact duration to be able to specify duration with uncertain number
    // Like a few years
    ReadablePeriod period;

    public DurationWithFields() {
      this.period = null;
    }

    public DurationWithFields(ReadablePeriod period) {
      this.period = period;
    }

    public DurationWithFields(Duration d, ReadablePeriod period) {
      super(d);
      this.period = period;
    }

    public Duration multiplyBy(int m) {
      if (m == 1 || period == null) {
        return this;
      } else {
        MutablePeriod p = period.toMutablePeriod();
        for (int i = 0; i < period.size(); i++) {
          p.setValue(i, period.getValue(i) * m);
        }
        return new DurationWithFields(p);
      }
    }

    public Duration divideBy(int m) {
      if (m == 1 || period == null) {
        return this;
      } else {
        MutablePeriod p = new MutablePeriod();
        for (int i = 0; i < period.size(); i++) {
          int oldVal = period.getValue(i);
          DurationFieldType field = period.getFieldType(i);
          int remainder = oldVal % m;
          p.add(field, oldVal - remainder);
          if (remainder != 0) {
            DurationFieldType f;
            int standardUnit = 1;
            // TODO: This seems silly, how to do this with jodatime???
            if (DurationFieldType.centuries().equals(field)) {
              f = DurationFieldType.years();
              standardUnit = 100;
            } else if (DurationFieldType.years().equals(field)) {
              f = DurationFieldType.months();
              standardUnit = 12;
            } else if (DurationFieldType.halfdays().equals(field)) {
              f = DurationFieldType.hours();
              standardUnit = 12;
            } else if (DurationFieldType.days().equals(field)) {
              f = DurationFieldType.hours();
              standardUnit = 24;
            } else if (DurationFieldType.hours().equals(field)) {
              f = DurationFieldType.minutes();
              standardUnit = 60;
            } else if (DurationFieldType.minutes().equals(field)) {
              f = DurationFieldType.seconds();
              standardUnit = 60;
            } else if (DurationFieldType.seconds().equals(field)) {
              f = DurationFieldType.millis();
              standardUnit = 1000;
            } else if (DurationFieldType.months().equals(field)) {
              f = DurationFieldType.days();
              standardUnit = 30;
            } else if (DurationFieldType.weeks().equals(field)) {
              f = DurationFieldType.days();
              standardUnit = 7;
            } else if (DurationFieldType.millis().equals(field)) {
              // No more granularity units....
              f = DurationFieldType.millis();
              standardUnit = 0;
            } else {
              throw new UnsupportedOperationException("Unsupported duration type: " + field + " when dividing");
            }
            p.add(f, standardUnit * remainder);
          }
        }
        for (int i = 0; i < p.size(); i++) {
          p.setValue(i, p.getValue(i) / m);
        }
        return new DurationWithFields(p);
      }
    }

    public Period getJodaTimePeriod() {
      return (period != null) ? period.toPeriod() : null;
    }

    public org.joda.time.Duration getJodaTimeDuration() {
      return (period != null) ? period.toPeriod().toDurationFrom(JodaTimeUtils.INSTANT_ZERO) : null;
    }

    public Duration resolve(Time refTime, int flags) {
      Instant instant = (refTime != null) ? refTime.getJodaTimeInstant() : null;
      if (instant != null) {
        if ((flags & DUR_RESOLVE_FROM_AS_REF) != 0) {
          return new DurationWithMillis(this, period.toPeriod().toDurationFrom(instant));
        } else if ((flags & DUR_RESOLVE_TO_AS_REF) != 0) {
          return new DurationWithMillis(this, period.toPeriod().toDurationTo(instant));
        }
      }
      return this;
    }

    public Duration add(Duration d) {
      Period p = period.toPeriod().plus(d.getJodaTimePeriod());
      if (this instanceof InexactDuration || d instanceof InexactDuration) {
        return new InexactDuration(this, p);
      } else {
        return new DurationWithFields(this, p);
      }
    }

  }

  // Duration specified in terms of milliseconds
  public static class DurationWithMillis extends Duration {
    ReadableDuration base;

    public DurationWithMillis(long ms) {
      this.base = new org.joda.time.Duration(ms);
    }

    public DurationWithMillis(ReadableDuration base) {
      this.base = base;
    }

    public DurationWithMillis(Duration d, ReadableDuration base) {
      super(d);
      this.base = base;
    }

    public Duration multiplyBy(int m) {
      if (m == 1) {
        return this;
      } else {
        long ms = base.getMillis();
        return new DurationWithMillis(ms * m);
      }
    }

    public Duration divideBy(int m) {
      if (m == 1) {
        return this;
      } else {
        long ms = base.getMillis();
        return new DurationWithMillis(ms / m);
      }
    }

    public Period getJodaTimePeriod() {
      return base.toPeriod();
    }

    public org.joda.time.Duration getJodaTimeDuration() {
      return base.toDuration();
    }

    public Duration add(Duration d) {
      if (d instanceof DurationWithMillis) {
        return new DurationWithMillis(this, base.toDuration().plus(((DurationWithMillis) d).base));
      } else if (d instanceof DurationWithFields) {
        return ((DurationWithFields) d).add(this);
      } else {
        throw new UnsupportedOperationException("Unknown duration type in add: " + d.getClass());
      }
    }

  }

  public static class DurationRange extends Duration {
    Duration minDuration;
    Duration maxDuration;

    public DurationRange(DurationRange d, Duration min, Duration max) {
      super(d);
      this.minDuration = min;
      this.maxDuration = max;
    }

    public DurationRange(Duration min, Duration max) {
      this.minDuration = min;
      this.maxDuration = max;
    }

    public boolean includeTimexAltValue() {
      return true;
    }

    public String toFormattedString(int flags) {
      if ((flags & (FORMAT_ISO | FORMAT_TIMEX3_VALUE)) != 0) {
        // return super.toFormattedString(flags);
        return null;
      }
      StringBuilder sb = new StringBuilder();
      if (minDuration != null)
        sb.append(minDuration.toFormattedString(flags));
      sb.append("/");
      if (maxDuration != null)
        sb.append(maxDuration.toFormattedString(flags));
      return sb.toString();
    }

    public Period getJodaTimePeriod() {
      if (minDuration == null)
        return maxDuration.getJodaTimePeriod();
      if (maxDuration == null)
        return minDuration.getJodaTimePeriod();
      Duration mid = minDuration.add(maxDuration).divideBy(2);
      return mid.getJodaTimePeriod();
    }

    public org.joda.time.Duration getJodaTimeDuration() {
      if (minDuration == null)
        return maxDuration.getJodaTimeDuration();
      if (maxDuration == null)
        return minDuration.getJodaTimeDuration();
      Duration mid = minDuration.add(maxDuration).divideBy(2);
      return mid.getJodaTimeDuration();
    }

    public Duration add(Duration d) {
      Duration min2 = (minDuration != null) ? minDuration.add(d) : null;
      Duration max2 = (maxDuration != null) ? maxDuration.add(d) : null;
      return new DurationRange(this, min2, max2);
    }

    public Duration multiplyBy(int m) {
      Duration min2 = (minDuration != null) ? minDuration.multiplyBy(m) : null;
      Duration max2 = (maxDuration != null) ? maxDuration.multiplyBy(m) : null;
      return new DurationRange(this, min2, max2);
    }

    public Duration divideBy(int m) {
      Duration min2 = (minDuration != null) ? minDuration.divideBy(m) : null;
      Duration max2 = (maxDuration != null) ? maxDuration.divideBy(m) : null;
      return new DurationRange(this, min2, max2);
    }
  }

  public static class InexactDuration extends DurationWithFields {
    // Original duration is estimate of how long this duration is
    // but since some aspects of it is unknown....
    // for now all fields are inexact

    // TODO: Have inexact duration in which some fields are exact
    // add/toISOString
    // boolean[] exactFields;
    public InexactDuration(ReadablePeriod period) {
      this.period = period;
      // exactFields = new boolean[period.size()];
      this.approx = true;
    }

    public InexactDuration(Duration d, ReadablePeriod period) {
      super(d, period);
      this.approx = true;
    }

    public String toFormattedString(int flags) {
      String s = super.toFormattedString(flags);
      return s.replaceAll("\\d+", PAD_FIELD_UNKNOWN);
    }
  }

  // Range

  public static class Range extends Temporal implements HasInterval<Time> {
    Time begin = TIME_UNKNOWN;
    Time end = TIME_UNKNOWN;
    Duration duration = DURATION_UNKNOWN;

    public Range(Time begin, Time end) {
      this.begin = begin;
      this.end = end;
      this.duration = Time.difference(begin, end);
    }

    public Range(Time begin, Time end, Duration duration) {
      this.begin = begin;
      this.end = end;
      this.duration = duration;
    }

    public Range(Range r, Time begin, Time end, Duration duration) {
      super(r);
      this.begin = begin;
      this.end = end;
      this.duration = duration;
    }

    public Interval<Time> getInterval() {
      return FuzzyInterval.toInterval(begin, end);
    }

    public org.joda.time.Interval getJodaTimeInterval() {
      return new org.joda.time.Interval(begin.getJodaTimeInstant(), end.getJodaTimeInstant());
    }

    public boolean isGrounded() {
      return begin.isGrounded() && end.isGrounded();
    }

    public Time getTime() {
      return begin;
    } // TODO: return something that makes sense for time...

    public Duration getDuration() {
      return duration;
    }

    public Range getRange(int flags, Duration granularity) {
      return this;
    }

    public TimexType getTimexType() {
      return TimexType.DURATION;
    }

    public Map<String, String> getTimexAttributes(TimeIndex timeIndex) {
      String beginTidStr = (begin != null) ? begin.getTidString(timeIndex) : null;
      String endTidStr = (end != null) ? end.getTidString(timeIndex) : null;
      Map<String, String> map = super.getTimexAttributes(timeIndex);
      if (beginTidStr != null) {
        map.put(TimexAttr.beginPoint.name(), beginTidStr);
      }
      if (endTidStr != null) {
        map.put(TimexAttr.endPoint.name(), endTidStr);
      }
      return map;
    }

    // public boolean includeTimexAltValue() { return true; }
    public String toFormattedString(int flags) {
      if ((flags & (FORMAT_ISO | FORMAT_TIMEX3_VALUE)) != 0) {
        if (getTimeLabel() != null) {
          if (getTimeLabel().isoString != null) {
            return getTimeLabel().isoString;
          }
        }
        String beginStr = (begin != null) ? begin.toFormattedString(flags) : null;
        String endStr = (end != null) ? end.toFormattedString(flags) : null;
        String durationStr = (duration != null) ? duration.toFormattedString(flags) : null;
        if ((flags & FORMAT_ISO) != 0) {
          if (beginStr != null && endStr != null) {
            return beginStr + "/" + endStr;
          } else if (beginStr != null && durationStr != null) {
            return beginStr + "/" + durationStr;
          } else if (durationStr != null && endStr != null) {
            return durationStr + "/" + endStr;
          }
        }
        return durationStr;
      } else {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (begin != null)
          sb.append(begin);
        sb.append(",");
        if (end != null)
          sb.append(end);
        sb.append(",");
        if (duration != null)
          sb.append(duration);
        sb.append(")");
        return sb.toString();
      }
    }

    public Range resolve(Time refTime, int flags) {
      if (refTime == null) {
        return this;
      }
      if (isGrounded())
        return this;
      if ((flags & RANGE_RESOLVE_TIME_REF) != 0 && (begin == TIME_REF || end == TIME_REF)) {
        Time groundedBegin = begin;
        Duration groundedDuration = duration;
        if (begin == TIME_REF) {
          groundedBegin = (Time) begin.resolve(refTime, flags);
          groundedDuration = (duration != null) ? duration.resolve(refTime, flags | DUR_RESOLVE_FROM_AS_REF) : null;
        }
        Time groundedEnd = end;
        if (end == TIME_REF) {
          groundedEnd = (Time) end.resolve(refTime, flags);
          groundedDuration = (duration != null) ? duration.resolve(refTime, flags | DUR_RESOLVE_TO_AS_REF) : null;
        }
        return new Range(this, groundedBegin, groundedEnd, groundedDuration);
      } else {
        return this;
      }
    }

    // TODO: Implement some range operations....
    public Range offset(Duration d) {
      return offset(d, RANGE_OFFSET_BEGIN | RANGE_OFFSET_END);
    }

    public Range offset(Duration d, int flags) {
      Time b2 = begin;
      if ((flags & RANGE_OFFSET_BEGIN) != 0) {
        b2 = (begin != null) ? begin.offset(d) : null;
      }
      Time e2 = end;
      if ((flags & RANGE_OFFSET_END) != 0) {
        e2 = (end != null) ? end.offset(d) : null;
      }
      return new Range(this, b2, e2, duration);
    }

    public Range subtract(Duration d) {
      return subtract(d, RANGE_EXPAND_FIX_BEGIN);
    }

    public Range subtract(Duration d, int flags) {
      return add(d.multiplyBy(-1), RANGE_EXPAND_FIX_BEGIN);
    }

    public Range add(Duration d) {
      return add(d, RANGE_EXPAND_FIX_BEGIN);
    }

    public Range add(Duration d, int flags) {
      Duration d2 = duration.add(d);
      Time b2 = begin;
      Time e2 = end;
      if ((flags & RANGE_EXPAND_FIX_BEGIN) == 0) {
        b2 = (end != null) ? end.offset(d2.multiplyBy(-1)) : null;
      } else if ((flags & RANGE_EXPAND_FIX_END) == 0) {
        e2 = (begin != null) ? begin.offset(d2) : null;
      }
      return new Range(this, b2, e2, d2);
    }

    public Time begin() {
      return begin;
    }

    public Time end() {
      return end;
    }

    public Time beginTime() {
      if (begin != null) {
        Range r = begin.getRange();
        if (r != null && !begin.equals(r.begin)) {
          // return r.beginTime();
          return r.begin;
        }
      }
      return begin;
    }

    public Time endTime() {
      /*    if (end != null) {
            Range r = end.getRange();
            if (r != null && !end.equals(r.end)) {
              //return r.endTime();
              return r.end;
            }
          }        */
      return end;
    }

    public Time mid() {
      if (duration != null && begin != null) {
        return begin.add(duration.divideBy(2));
      } else if (duration != null && end != null) {
        return end.subtract(duration.divideBy(2));
      } else if (begin != null && end != null) {
        // TODO: ....
      } else if (begin != null) {
        return begin;
      } else if (end != null) {
        return end;
      }
      return null;
    }

    // TODO: correct implementation
    public Temporal intersect(Temporal t) {
      if (t instanceof Time) {
        return new RelativeTime((Time) t, TemporalOp.INTERSECT, this);
      } else if (t instanceof Range) {
        Range rt = (Range) t;
        // Assume begin/end defined (TODO: handle if duration defined)
        Time b = Time.max(begin, rt.begin);
        Time e = Time.min(end, rt.end);
        return new Range(b, e);
      } else if (t instanceof Duration) {
        return new InexactTime(null, (Duration) t, this);
      }
      return null;
    }

    public boolean contains(Range r) {
      return false;
    }
  }

  // Exciting set of times
  public static abstract class TemporalSet extends Temporal {
    public TemporalSet() {
    }

    public TemporalSet(TemporalSet t) {
      super(t);
    }

    // public boolean includeTimexAltValue() { return true; }
    public TimexType getTimexType() {
      return TimexType.SET;
    };
  }

  // Explicit set of times: like tomorrow and next week, not really used
  public static class ExplicitTemporalSet extends TemporalSet {
    Set<Temporal> temporals;

    public ExplicitTemporalSet(Temporal... temporals) {
      this.temporals = CollectionUtils.asSet(temporals);
    }

    public ExplicitTemporalSet(Set<Temporal> temporals) {
      this.temporals = temporals;
    }

    public boolean isGrounded() {
      return false;
    }

    public Time getTime() {
      return null;
    }

    public Duration getDuration() {
      // TODO: Return difference between min/max of set
      return null;
    }

    public Range getRange(int flags, Duration granularity) {
      // TODO: Return min/max of set
      return null;
    }

    public Temporal resolve(Time refTime, int flags) {
      Temporal[] newTemporals = new Temporal[temporals.size()];
      int i = 0;
      for (Temporal t : temporals) {
        newTemporals[i] = t.resolve(refTime, flags);
        i++;
      }
      return new ExplicitTemporalSet(newTemporals);
    }

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        if (getTimeLabel().isoString != null) {
          return getTimeLabel().isoString;
        }
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        return null;
      } // TODO: is there timex3 standard?
      return "{" + StringUtils.join(temporals, ", ") + "}";
    }

    public Temporal intersect(Temporal other) {
      if (other == null)
        return this;
      if (other == TIME_UNKNOWN || other == DURATION_UNKNOWN)
        return this;
      Set<Temporal> newTemporals = new HashSet<Temporal>();
      for (Temporal t : temporals) {
        Temporal t2 = t.intersect(other);
        if (t2 != null)
          newTemporals.add(t2);
      }
      return new ExplicitTemporalSet(newTemporals);
    }
  }

  public static final PeriodicTemporalSet HOURLY = new PeriodicTemporalSet(null, YEAR, "EVERY", "P1X");
  public static final PeriodicTemporalSet NIGHTLY = new PeriodicTemporalSet(NIGHT, DAY, "EVERY", "P1X");
  public static final PeriodicTemporalSet DAILY = new PeriodicTemporalSet(null, DAY, "EVERY", "P1X");
  public static final PeriodicTemporalSet MONTHLY = new PeriodicTemporalSet(null, MONTH, "EVERY", "P1X");
  public static final PeriodicTemporalSet QUARTERLY = new PeriodicTemporalSet(null, QUARTER, "EVERY", "P1X");
  public static final PeriodicTemporalSet YEARLY = new PeriodicTemporalSet(null, YEAR, "EVERY", "P1X");
  public static final PeriodicTemporalSet WEEKLY = new PeriodicTemporalSet(null, WEEK, "EVERY", "P1X");

  // Set of times that occurs with some frequency: every friday
  public static class PeriodicTemporalSet extends TemporalSet {
    Range occursIn; // Start and end times for when this set is suppose to be
    // happening
    Temporal base; // when in the period Friday 2-3 pm
    Duration periodicity; // Period (month, year, week)
    // int count; // How often (once, twice)
    String quant; // Quantifier - every, every other
    String freq; // String representation of frequency (3 days = P3D, 3 times =

    // P3X)

    // public ExplicitTemporalSet toExplicitTemporalSet();
    public PeriodicTemporalSet(Temporal base, Duration periodicity, String quant, String freq) {
      this.base = base;
      this.periodicity = periodicity;
      this.quant = quant;
      this.freq = freq;
    }

    public PeriodicTemporalSet(PeriodicTemporalSet p, Temporal base, Duration periodicity, Range range, String quant, String freq) {
      super(p);
      this.occursIn = range;
      this.base = base;
      this.periodicity = periodicity;
      this.quant = quant;
      this.freq = freq;
    }

    public PeriodicTemporalSet multiplyDurationBy(int scale) {
      return new PeriodicTemporalSet(this, this.base, periodicity.multiplyBy(scale), this.occursIn, this.quant, this.freq);
    }

    public PeriodicTemporalSet divideDurationBy(int scale) {
      return new PeriodicTemporalSet(this, this.base, periodicity.divideBy(scale), this.occursIn, this.quant, this.freq);
    }

    public boolean isGrounded() {
      return (occursIn != null && occursIn.isGrounded());
    }

    public Time getTime() {
      return null;
    }

    public Duration getDuration() {
      return null;
    }

    public Range getRange(int flags, Duration granularity) {
      return occursIn;
    }

    public Map<String, String> getTimexAttributes(TimeIndex timeIndex) {
      Map<String, String> map = super.getTimexAttributes(timeIndex);
      if (quant != null) {
        map.put(TimexAttr.quant.name(), quant);
      }
      if (freq != null) {
        map.put(TimexAttr.freq.name(), freq);
      }
      if (periodicity != null) {
        map.put("periodicity", periodicity.getTimexValue());
      }
      return map;
    }

    public Temporal resolve(Time refTime, int flags) {
      Range resolvedOccursIn = (occursIn != null) ? occursIn.resolve(refTime, flags) : null;
      Temporal resolvedBase = (base != null) ? base.resolve(null, 0) : null;
      return new PeriodicTemporalSet(this, resolvedBase, this.periodicity, resolvedOccursIn, this.quant, this.freq);
    }

    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        if (getTimeLabel().isoString != null) {
          return getTimeLabel().isoString;
        }
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      if (base != null) {
        return base.toFormattedString(flags);
      } else {
        if (periodicity != null) {
          return periodicity.toFormattedString(flags);
        }
      }
      return null;
    }

    public Temporal intersect(Temporal t) {
      if (t instanceof Range) {
        if (occursIn == null) {
          return new PeriodicTemporalSet(this, base, periodicity, (Range) t, quant, freq);
        }
      } else {
    	  if (base != null) {
	        Temporal merged = base.intersect(t);
	        return new PeriodicTemporalSet(this, merged, periodicity, occursIn, quant, freq);
          }
      }
      return null;
    }

  }

}
