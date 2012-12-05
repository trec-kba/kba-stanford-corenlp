package edu.stanford.nlp.parser.ensemble.maltparser.core.flow.system;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.parser.ensemble.maltparser.Constants;
import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.FeatureException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.flow.FlowException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.flow.system.elem.ChartElement;
import edu.stanford.nlp.parser.ensemble.maltparser.core.helper.Util;
import edu.stanford.nlp.parser.ensemble.maltparser.core.plugin.Plugin;
import edu.stanford.nlp.parser.ensemble.maltparser.core.plugin.PluginLoader;
/**
*
*
* @author Johan Hall
*/
public class FlowChartSystem {
	private HashMap<String,ChartElement> chartElements;
	
	public FlowChartSystem() {
		chartElements = new HashMap<String,ChartElement>();
	}
	
	public void load(String urlstring) throws MaltChainedException {
		load(Util.findURL(urlstring));
	}
	
	public void load(PluginLoader plugins) throws MaltChainedException {
		 for (Plugin plugin : plugins) {
			URL url = null;
			try {
				url = new URL("jar:"+plugin.getUrl() + "!" + Constants.APPDATA_PATH + "/plugin.xml");
			} catch (MalformedURLException e) {
				throw new FeatureException("Malformed URL: 'jar:"+plugin.getUrl() + "!plugin.xml'", e);
			}
			try { 
				InputStream is = url.openStream();
				is.close();
			} catch (IOException e) {
				continue;
			}

			load(url);
		}
	}
	
	public void load(URL specModelURL) throws MaltChainedException {
        try {
        	//if(specModelURL == null) specModelURL = new URL("file:///Users/Mihai/code/ensemble/appdata/flow/flowchartsystem.xml");
        	//System.err.println("FlowChartSystem URL is: " + specModelURL);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Element root = null;

            root = db.parse(specModelURL.openStream()).getDocumentElement();

            if (root == null) {
            	throw new FlowException("The flow chart system file '"+specModelURL.getFile()+"' cannot be found. ");
            }
            
            readChartElements(root);
        } catch (IOException e) {
        	throw new FlowException("The flow chart system file '"+specModelURL.getFile()+"' cannot be found. ", e);
        } catch (ParserConfigurationException e) {
        	throw new FlowException("Problem parsing the file "+specModelURL.getFile()+". ", e);
        } catch (SAXException e) {
        	throw new FlowException("Problem parsing the file "+specModelURL.getFile()+". ", e);
        }
	}
	
	public void readChartElements(Element root) throws MaltChainedException {
		NodeList chartElem = root.getElementsByTagName("chartelement");
		for (int i = 0; i < chartElem.getLength(); i++) {
			ChartElement chartElement = new ChartElement();
			chartElement.read((Element)chartElem.item(i), this);
			chartElements.put(((Element)chartElem.item(i)).getAttribute("item"),chartElement);
		}
	}
	
	public ChartElement getChartElement(String name) {
		return chartElements.get(name);
	}
	
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("CHART ELEMENTS:\n");
		for (String key : chartElements.keySet()) {
			sb.append(chartElements.get(key));
			sb.append('\n');
		}
		return sb.toString();
	}
}
