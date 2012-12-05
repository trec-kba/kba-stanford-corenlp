package edu.stanford.nlp.parser.ensemble.maltparser.core.feature.spec.reader;

import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.stanford.nlp.parser.ensemble.maltparser.core.exception.MaltChainedException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.FeatureException;
import edu.stanford.nlp.parser.ensemble.maltparser.core.feature.spec.SpecificationModels;
/**
*
*
* @author Johan Hall
*/
public class XmlReader implements FeatureSpecReader{
	
	public XmlReader() { }
	
	public void load(URL specModelURL, SpecificationModels featureSpecModels) throws MaltChainedException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Element root = null;

            root = db.parse(specModelURL.openStream()).getDocumentElement();

            if (root == null) {
            	throw new FeatureException("The feature specification file '"+specModelURL.getFile()+"' cannot be found. ");
            }
            
            readFeatureModels(root, featureSpecModels);
        } catch (IOException e) {
        	throw new FeatureException("The feature specification file '"+specModelURL.getFile()+"' cannot be found. ", e);
        } catch (ParserConfigurationException e) {
        	throw new FeatureException("Problem parsing the file "+specModelURL.getFile()+". ", e);
        } catch (SAXException e) {
        	throw new FeatureException("Problem parsing the file "+specModelURL.getFile()+". ", e);
        }
	}
	
	private void readFeatureModels(Element featuremodels, SpecificationModels featureSpecModels) throws MaltChainedException {
		NodeList featureModelList = featuremodels.getElementsByTagName("featuremodel");
		for (int i = 0; i < featureModelList.getLength(); i++) {
			readFeatureModel((Element)featureModelList.item(i), featureSpecModels);
		}
	}
	
	private void readFeatureModel(Element featuremodel, SpecificationModels featureSpecModels) throws MaltChainedException {
		int specModelIndex = featureSpecModels.getNextIndex();
		NodeList submodelList = featuremodel.getElementsByTagName("submodel");
		if (submodelList.getLength() == 0) { 
			NodeList featureList = featuremodel.getElementsByTagName("feature");
			for (int i = 0; i < featureList.getLength(); i++) {
				String featureText = ((Element)featureList.item(i)).getTextContent().trim();
		    	if (featureText.length() > 1) {
		    		featureSpecModels.add(specModelIndex, featureText);
		    	}
			}
		} else {
			for (int i = 0; i < submodelList.getLength(); i++) {
				String name = ((Element)submodelList.item(i)).getAttribute("name");
				NodeList featureList = ((Element)submodelList.item(i)).getElementsByTagName("feature");
				for (int j = 0; j < featureList.getLength(); j++) {
					String featureText = ((Element)featureList.item(j)).getTextContent().trim();
			    	if (featureText.length() > 1) {
			    		featureSpecModels.add(specModelIndex, name, featureText);
			    	}
				}
			}
		}
	}
}
