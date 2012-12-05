package nlp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.ParseUtils;

public class cleanHTML {
	
	static public void main(String[] args) throws IOException{

	    Properties props = new Properties();
	    props.put("annotators", "tokenize, cleanxml, ssplit, pos, lemma, ner");
	  //  StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
	    
	    
	    String filename = args[0];
	    String outfilename = args[1];
	    
	    //String outfilename = "out";
	    //String filename = "/scratch.1/condor/examples/test.gz";
	    
	    
	    
	 //   BufferedReader br = new BufferedReader(new FileReader(filename));
	   
	    FileInputStream fin = new FileInputStream(filename);
	    GZIPInputStream gzis = new GZIPInputStream(fin);
	    InputStreamReader xover = new InputStreamReader(gzis);
	    BufferedReader is = new BufferedReader(xover);
	    
	    FileOutputStream fout = new FileOutputStream(outfilename);
	    GZIPOutputStream gzos = new GZIPOutputStream(fout);
	    OutputStreamWriter xover2 = new OutputStreamWriter(gzos);
	    BufferedWriter os = new BufferedWriter(xover2);
	    
	    String content = "";
	    String currentDocid = null;
	    String line;
	    
	    int ct = 0;
	    
	    Pattern p = Pattern.compile("WARC-TREC-ID: (.*?)$");
	    
	    Pattern forKeys = Pattern.compile("^(.*?<.*?>.*?)<", Pattern.DOTALL|Pattern.MULTILINE);
		Matcher n = forKeys.matcher("");
		
	    Pattern doubleNN = Pattern.compile("(\n\\s*\n\\s*)+", Pattern.DOTALL|Pattern.MULTILINE);
		Matcher nn = doubleNN.matcher("");
		
	    Pattern singleN = Pattern.compile("\n", Pattern.DOTALL|Pattern.MULTILINE);
		Matcher nnnn = singleN.matcher("");
	    
	    while((line = is.readLine()) != null){
	    	
	    	Matcher m = p.matcher(line);
	    	if(m.find()){

		    	System.err.println(line);
	    		content = content + "\n" + line;
	    		currentDocid = m.group(1);
	    		continue;
	    	}
	    	
	    	if(line.equals("WARC/0.18")){
	    		
	    		ct = ct + 1;
	    			    		
	    		String docid = currentDocid;
				
	    		if(docid != null){
	    			
	    			
	    			n.reset(content);
	    			content = n.replaceFirst("<");
	    			
	    			BufferedWriter bb = new BufferedWriter(new FileWriter("testhtml.html"));
	    			bb.write(content);
	    			bb.close();
	    			
	    			//System.out.println(content);
		    		
		    		
	    			
		    		TikaConfig tc = TikaConfig.getDefaultConfig();
		    		//InputStream fortika = new ByteArrayInputStream(content.getBytes("UTF-8"));
		    		String txt = "";
		    		try{
		    			txt = ParseUtils.getStringContent(new File("testhtml.html"), tc);
		    			nn.reset(txt);
			    		txt = nn.replaceAll("\n\n");
			    		txt = txt.replaceAll("[ |\t]+", " ");
			    		txt = txt.replaceAll("^[ |\t]s", "");
			    		txt = txt.replaceAll("[ |\t]$", "");
			    		
			    		
			    		String[] ss = txt.split("\n");
			    		
			    		for(int i=0;i<ss.length;i++){
			    			if(ss[i].length() > 30){
			    				continue;
			    			}
			    			int j;
			    			for(j=i+1;j<ss.length;j++){
			    				if(ss[j].length() > 30){
			    					break;
			    				}
			    			}
			    			j --;
			    			if(j-i+1 > 5){
			    				for(int w=i;w<j+1;w++){
			    					ss[w] = "";
			    				}
			    			}
			    			i = j;
			    		}
			    		
			    		for(int i=0;i<ss.length;i++){
			    			if(ss[i].length() > 30){
			    				continue;
			    			}
			    			if(i == 0 || i == ss.length-1){
			    				continue;
			    			}
			    			if(ss[i-1].trim().length() == 0 && ss[i+1].trim().length() == 0){
			    				ss[i] = "";
			    			}
			    		}
			    		
			    		System.out.println("<FILENAME " + docid + "><p>");
			    		boolean isprevempty = false;
			    		int ccc = 0;
			    		for(String s : ss){
			    			ccc ++;
			    			
			    			if(s.matches("^[ |\t]*$")){
			    				if(isprevempty){
				    				continue;
			    				}else{
			    					isprevempty = true;
			    				}

			    			}else{
			    				isprevempty = false;
			    			}
			    			
			    			if(s.trim().length() == 0){
			    				System.out.println("</p>\n<p>");
			    			}else{
			    				System.out.println(StringEscapeUtils.escapeXml(s.trim()));
			    			}
			    			
			    		}
			    		System.out.println("</p></FILENAME>");
		    		}catch(Exception e){
		    			
		    		}
		    			
	    		}
	    		
			    content = "";
			    continue;
	    		
	    	}
	    	
	    	content = content + "\n" + line;
	    }
	    
	    os.close();
	    
	}

	
}
