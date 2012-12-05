package edu.stanford.nlp.parser.ensemble.utils;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class Eisner {
	static enum PARSE_MODE { MAJORITY, ATTARDI, EISNER };
	
	static final PARSE_MODE mode = PARSE_MODE.EISNER;
	
	public static void main(String[] args) throws Exception {
		if(args.length == 0){
			demo();
			return;
		}
		
		List<String> bases = new ArrayList<String>();
		for(int i = 2; i < args.length; i ++) bases.add(args[i]);
		String outFile = args[0];
		@SuppressWarnings("unused")
		String goldFile = args[1];
		
		ensemble(bases, outFile);
	}
	
	@SuppressWarnings("unchecked")
	public static void ensemble(List<String> sysFiles, String outFile) throws IOException {
		PrintStream os = new PrintStream(new FileOutputStream(outFile));
		BufferedReader [] is = new BufferedReader[sysFiles.size()];
		for(int i = 0; i < sysFiles.size(); i ++) is[i] = FileUtils.openForReading(sysFiles.get(i)); 
		
		List<Token> [] sents = new List[is.length];
		int sentCount = 0;
		while((sents[0] = Token.readNextSentCoNLLX(is[0])) != null){
			sentCount ++;
			for(int i = 1; i < is.length; i ++)
				sents[i] = Token.readNextSentCoNLLX(is[i]);
			
			if(mode == PARSE_MODE.EISNER) 
				for(int i = 0; i < is.length; i ++)	
					Token.fixMultipleRoots(sents[i]);
			
			List<Token> cands = Token.mergeSentences(sents);
			for(Token cand: cands){
				cand.setScore(cand.getModels().size());
			}
			
			List<Token> tree = null;
			if(mode == PARSE_MODE.ATTARDI)
				tree = attardiVote(cands);
			else if(mode == PARSE_MODE.EISNER){
				Span<Token> top = parse(cands, sents[0].size() + 1);
				if(top != null){
					if(verbose) System.err.println("Score: " + top.score);
					tree = top.dependencies;
				} else {
					throw new RuntimeException("Did not find TOP!");
				}
			} else if(mode == PARSE_MODE.MAJORITY) {
				tree = majorityVote(cands, sents[0].size());
			} else {
				throw new RuntimeException("Unknown mode: " + mode);
			}
			DependencyUtils.sortById(tree);
			for(Token t: tree) os.println(t);
			os.println();
		}
		
		os.close();
		for(BufferedReader i: is) i.close();
	}
	
	static List<Token> majorityVote(List<Token> cands, int len) {
		List<Token> out = new ArrayList<Token>();
		for(int i = 1; i <= len; i ++){
			Token bestDep = null;
			int bestScore = 0;
			for(Token c: cands){
				if(c.mod() == i && c.getModels().size() > bestScore){
					bestDep = c;
					bestScore = c.getModels().size();
				}
			}
			assert(bestDep != null);
			out.add(bestDep);
		}
		return out;
	}
	
	static int [] totalByVotes;
	static int [] correctByVotes;
	
	static {
		totalByVotes = new int[10];
		correctByVotes = new int[totalByVotes.length];
		for(int i = 0; i < totalByVotes.length; i ++){
			totalByVotes[i] = 0;
			correctByVotes[i] = 0;
		}
	}
	
	private static boolean isCorrect(Token dep, List<Token> golds) {
		for(Token gold: golds) {
			if(dep.mod() == gold.mod() && dep.head() == gold.head() && dep.label().equals(gold.label()))
				return true;
		}
		return false;
	}
	
	static List<Token> weightedMajorityVote(List<Token> cands, int len, List<Token> goldSent) {
		List<Token> out = new ArrayList<Token>();
		for(int i = 1; i <= len; i ++){
			Token bestDep = null;
			Token firstDep = null;
			double bestScore = 0;
			for(Token c: cands){
				if(c.mod() == i && c.score > bestScore){
					bestDep = c;
					bestScore = c.score;
				}
				if(firstDep == null && c.getModels().contains(0)){
					firstDep = c;
				}
			}
			if(bestDep == null){
				bestDep = firstDep;
			}
			
			totalByVotes[bestDep.getModels().size()] ++;
			if(isCorrect(bestDep, goldSent)) correctByVotes[bestDep.getModels().size()] ++;
			
			out.add(bestDep);
		}
		return out;
	}
	
	static void demo() {
		List<Token> cands = new ArrayList<Token>();
		cands.add(new Token(1, "The", "DT", 2, "NMOD", 1.0));
		cands.add(new Token(2, "singer", "NN", 3, "SUBJ", 1.0));
		cands.add(new Token(2, "singer", "NN", 5, "NMOD", 10.0));
		cands.add(new Token(3, "played", "VBZ", 0, "ROOT", 1.0));
		cands.add(new Token(4, "the", "DT", 5, "NMOD", 1.0));
		cands.add(new Token(5, "celo", "NN", 3, "OBJ", 1.0));
		cands.add(new Token(6, "well", "JJ", 3, "MNR", 1.0));

		parse(cands, 7);
	}
	
	static class Span<T extends Dependency> {
		List<T> dependencies;
		double score;
		
		public Span() {
			score = 0;
			dependencies = new ArrayList<T>();
		}
		
		public Span(Span<T> left, Span<T> right, T dep) {
			score = left.score + right.score + (dep != null?dep.score():0.0);
			dependencies = new ArrayList<T>();
			if(dep != null) dependencies.add(dep);
			for(T d: left.dependencies) dependencies.add(d);
			for(T d: right.dependencies) dependencies.add(d);
		}
		
		public String toString() {
			StringBuffer os = new StringBuffer();
			os.append("{" + score + "}");
			for(T d: dependencies) {
				os.append(" [" + d.mod() + ", " + d.head() + ", " + d.label() + "]");
			}
			return os.toString();
		}
	};
	
	static final int HEAD_LEFT = 0;
	static final int HEAD_RIGHT = 1;
	
	static class Chart<T extends Dependency> {
		Span<T> [][][] chart;
		
		@SuppressWarnings("unchecked")
		public Chart(int dimension) {
			chart = new Span[dimension][dimension][2];
			for(int i = 0; i < chart.length; i ++){
				chart[i][i][0] = new Span();
				chart[i][i][1] = new Span();
			}
		}
		
		Span<T> get(int start, int end, int type) { return chart[start][end][type]; }
		
		void set(int start, int end, int type, Span<T> span) {
			if(chart[start][end][type] == null){
				chart[start][end][type] = span;
			} else if(chart[start][end][type].score < span.score) {
				chart[start][end][type] = span;
			}
		}
		
		void display(PrintStream os, int dimension) {
			for(int i = 0; i < chart.length; i ++){
				for(int j = 0; j < chart[i].length; j ++){
					if(j - i + 1 == dimension){
						for(int k = 0; k < chart[i][j].length; k ++){
							if(chart[i][j][k] != null){
								os.printf("[%d, %d, %d]: ", i, j, k);
								os.println(chart[i][j][k]);
							}
						}
					}
				}
			}
			
		}
	};
	
	static final boolean verbose = false;
	
	@SuppressWarnings("unchecked")
	static <T extends Dependency> Span<T> parse(List<T> cands, int length) {
		Chart<T> chart = new Chart<T>(length);
		Dependency [][] candTable = toTable(cands, length);
		
		for(int spanLength = 2; spanLength <= length; spanLength ++){
			if(verbose) System.err.println("Span length: " + spanLength);
			for(int start = 0; start + spanLength <= length; start ++) {
				int end = start + spanLength - 1;
				if(verbose) System.err.printf("Span: [%d, %d]\n", start, end);
				for(int split = start; split < end; split ++){
					Span<T> l = null, r = null;
					Dependency d = null;
					
					// merge [start(m), split] and [split + 1, end(h)]
					if((l = chart.get(start, split, HEAD_LEFT)) != null &&
							(r = chart.get(split + 1, end, HEAD_RIGHT)) != null &&
							(d = candTable[start][end]) != null){
						Span<T> s = new Span<T>(l, r, (T) d);
						chart.set(start, end, HEAD_RIGHT, s);
					}

					// merge [start(m), split] and [split + 1(h), end]
					if((l = chart.get(start, split, HEAD_LEFT)) != null &&
							(r = chart.get(split + 1, end, HEAD_RIGHT)) != null &&
							(d = candTable[start][split + 1]) != null){
						Span<T> s = new Span<T>(l, r, (T) d);
						chart.set(start, end, HEAD_RIGHT, s);
					}
					
					// merge [start(h), split] and [split + 1, end(m)]
					if((l = chart.get(start, split, HEAD_LEFT)) != null &&
							(r = chart.get(split + 1, end, HEAD_RIGHT)) != null &&
							(d = candTable[end][start]) != null){
						Span<T> s = new Span<T>(l, r, (T) d);
						chart.set(start, end, HEAD_LEFT, s);
					}

					// merge [start, split(h)] and [split + 1, end(m)]
					if((l = chart.get(start, split, HEAD_LEFT)) != null &&
							(r = chart.get(split + 1, end, HEAD_RIGHT)) != null &&
							(d = candTable[end][split]) != null){
						Span<T> s = new Span<T>(l, r, (T) d);
						chart.set(start, end, HEAD_LEFT, s);
					}

					// merge [start, split(m)] and [split + 1(h), end]
					if((l = chart.get(start, split, HEAD_RIGHT)) != null &&
							(r = chart.get(split + 1, end, HEAD_RIGHT)) != null &&
							(d = candTable[split][split + 1]) != null){
						Span<T> s = new Span<T>(l, r, (T) d);
						chart.set(start, end, HEAD_RIGHT, s);
					}
					
					// merge [start, split(m)] and [split + 1, end(h)]
					if((l = chart.get(start, split, HEAD_RIGHT)) != null &&
							(r = chart.get(split + 1, end, HEAD_RIGHT)) != null &&
							(d = candTable[split][end]) != null){
						Span<T> s = new Span<T>(l, r, (T) d);
						chart.set(start, end, HEAD_RIGHT, s);
					}
					
					// merge [start, split(h)] and [split + 1(m), end]
					if((l = chart.get(start, split, HEAD_LEFT)) != null &&
							(r = chart.get(split + 1, end, HEAD_LEFT)) != null &&
							(d = candTable[split + 1][split]) != null){
						Span<T> s = new Span<T>(l, r, (T) d);
						chart.set(start, end, HEAD_LEFT, s);
					}
					
					// merge [start(h), split] and [split + 1(m), end]
					if((l = chart.get(start, split, HEAD_LEFT)) != null &&
							(r = chart.get(split + 1, end, HEAD_LEFT)) != null &&
							(d = candTable[split + 1][start]) != null){
						Span<T> s = new Span<T>(l, r, (T) d);
						chart.set(start, end, HEAD_LEFT, s);
					}
					
					// merge [start, split] and [split, end]
					if((l = chart.get(start, split, HEAD_LEFT)) != null &&
							(r = chart.get(split, end, HEAD_LEFT)) != null){
						Span<T> s = new Span<T>(l, r, null);
						chart.set(start, end, HEAD_LEFT, s);
					}
					if((l = chart.get(start, split, HEAD_RIGHT)) != null &&
							(r = chart.get(split, end, HEAD_RIGHT)) != null){
						Span<T> s = new Span<T>(l, r, null);
						chart.set(start, end, HEAD_RIGHT, s);
					}
				}
			}
			if(verbose) chart.display(System.err, spanLength);
		}
		
		return chart.get(0, length - 1, HEAD_LEFT);
	}
	
	/**
	 * Stores all candidates in a table format (from start to end) for easier access
	 * @param <T>
	 * @param cands
	 * @return
	 */
	static <T extends Dependency> Dependency [][] toTable(List<T> cands, int length) {
		int discarded = 0;
		Dependency [][] table = new Dependency[length][length];
		for(T c: cands){
			if(table[c.mod()][c.head()] == null){
				table[c.mod()][c.head()] = c;
			} else if(table[c.mod()][c.head()].score() < c.score()) {
				table[c.mod()][c.head()] = c;
			} else {
				discarded ++;
			}
		}
		if(discarded > 0 && verbose){
			System.err.printf("Discarded %d redundant dependencies.\n", discarded);
		}
		return table;
	}
	
	/**
	 * Implements Attardi's re-parsing algorithm
	 * Note: this is a poor man's implementation. While it should have the exact same output, the runtime complexity is higher (O(N^2)) vs. O(N) of the original algorithm
	 * @param <T>
	 * @param cands
	 * @return
	 */
	static <T extends Dependency> List<T> attardiVote(List<T> cands) {
		List<T> treeDeps = new ArrayList<T>();
		HashSet<Integer> treeNodes = new HashSet<Integer>();
		treeNodes.add(0);
		
		List<T> F = new ArrayList<T>();
		for(int i = 0; i < cands.size(); ) {
			if(cands.get(i).head() == 0){
				F.add(cands.get(i));
				cands.remove(i);
			} else {
				i ++;
			}
		}
		
		while(F.isEmpty() == false){
			double bestScore = -1;
			T bestDep = null;
			
			for(T f: F){
				if(treeNodes.contains(f.head()) && f.score() > bestScore){
					bestScore = f.score();
					bestDep = f;
				}
			}
			assert(bestDep != null);
			treeDeps.add(bestDep);
			treeNodes.add(bestDep.mod());
			
			for(int i = 0; i < F.size(); ){
				if(treeNodes.contains(F.get(i).mod()) || F.get(i).head() == 0){
					F.remove(i);
				} else {
					i ++;
				}
			}
			
			for(int i = 0; i < cands.size(); ){
				if(treeNodes.contains(cands.get(i).head()) && ! treeNodes.contains(cands.get(i).mod())){
					F.add(cands.get(i));
					cands.remove(i);
				} else {
					i ++;
				}
			}
		}
		
		return treeDeps;
	}
}
