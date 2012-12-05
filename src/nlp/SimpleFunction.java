package nlp;

import java.util.List;

public abstract class SimpleFunction {
	private String status = null;
	
	protected void setStatus(String s) {
		status = s;
	}
	
	public String getStatus() {
		return status;
	}
	
	public void init() {
		
	}
	
	public List<String> process(String inputRecord){
		return null;
	}
	
	public void cleanUp() {
		
	}
}
