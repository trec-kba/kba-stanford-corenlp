package util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

/**
 * Container of time related utilities.
 */
public class Timer {

	private static long birth = now();
	private static Hashtable<String, Long> points = new Hashtable<String, Long>();
	
	private static long secondInMillis = 1000;
	private static long minuteInMillis = secondInMillis * 60;
	
	/**
	 * Resets the global starting point of the Timer.
	 */
	public static void resetClock() {
		birth = now();
	}
	
	public static String getDateTime(){
		DateFormat df = new SimpleDateFormat("H:mm:ss M/d/yy");
		return df.format(new Date());
	}
	
	/**
	 * Kicks off a timer with the given name.
	 */
	public static void start(String name) {
		points.put(name, now());
	}
	
	/**
	 * Returns the elapsed time of the timer with the given name.
	 * The format is [XX minutes, YYseconds].
	 */
	public static String elapsed(String name) {
		if(!points.containsKey(name)) {
			return "[UNKNOWN TIMER]";
		}else {
			long diff = now() - points.get(name);
			return readTime(diff);
		}
	}

	/**
	 * Returns the number of elapsed seconds of a given timer;
	 * -1 if the timer is unknown.
	 * @param name the name of the timer
	 */
	public static double elapsedSeconds(String name) {
		if(!points.containsKey(name)) {
			return -1;
		}else {
			long diff = now() - points.get(name);
			return (diff / 1000);
		}
	}

	public static double elapsedMilliSeconds(String name) {
		if(!points.containsKey(name)) {
			return -1;
		}else {
			long diff = now() - points.get(name);
			return diff;
		}
	}

	/**
	 * Returns the number of elapsed seconds since last clock reset.
	 */
	public static double elapsedSeconds() {
			long diff = now() - birth;
			return (diff / 1000.0);
	}

	/**
	 * Prints the elapsed time since last clock reset.
	 * The format is TIMER: [XX minutes, YYseconds].
	 */
	public static void printElapsed() {
		System.err.println("TIMER: " + elapsed());
	}

	/**
	 * Prints the elapsed time of the timer with the given name.
	 * The format is TIMER name: [XX minutes, YYseconds].
	 */
	public static void printElapsed(String name) {
		System.err.println("TIMER " + name + ": " + elapsed(name));
	}

	private static long now() {
		return System.currentTimeMillis();
	}

	/**
	 * Returns a string of elapsed time.
	 */
	public static String elapsed() {
		return readTime(now() - birth);
	}

	private static String readTime(long timeIntervalInMS) {
		long min = timeIntervalInMS / minuteInMillis;
		long sec = timeIntervalInMS / secondInMillis % 60;
		long mm = timeIntervalInMS % 1000;
		String t = String.format("[%d min, %d.%03d sec]", min, sec, mm);
		return t;
	}
	
	public static RunStat runStat = new RunStat();
	
	public static class RunStat{
		public double groundSec = 0;
		public double inferSec = 0;
		public double inferOps = 0;
		public long effectiveSteps = 0;
		
		public ArrayList<Double> turns = new ArrayList<Double>();
		public ArrayList<Double> costs = new ArrayList<Double>();
		
		public void addTurn(double cost){
			turns.add(Timer.elapsedSeconds());
			costs.add(cost);
		}
		
		public double getGroundTime(){
			return groundSec;
		}
		
		public double getFlipRate(){
			return inferOps / inferSec;
		}
		
		public void markGroundingDone(){
			groundSec = Timer.elapsedSeconds();
		}
		
		public void markInferDone(){
			inferSec = Timer.elapsedSeconds() - groundSec;
		}
		
		public void setInferOps(long ops){
			inferOps = ops;
		}
		
	}
	
}
