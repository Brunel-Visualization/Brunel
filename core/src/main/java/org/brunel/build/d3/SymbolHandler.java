package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.build.info.ChartStructure;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class handles loading and using symbols
 */
public class SymbolHandler {

	private final String visID;
	// Map the URI requested to the symbols to display (as a DOM element)
	private Map<URI, Element[]> uriToSymbols = new LinkedHashMap<>();

	public SymbolHandler(ChartStructure chart) {
		visID = chart.visIdentifier;

		// Search all elements for use of symbols
		for (int i = 0; i < chart.elementStructure.length; i++) {
			URI uri = getSymbolURI(chart.elementStructure[i]);
			if (uri != null && !uriToSymbols.containsKey(uri)) {
				// We have a valid URI that we have not processed yet, so add it to the map (indexed)
				uriToSymbols.put(uri, readSymbolDefinitions(uriToSymbols.size(), uri));
			}
		}
	}

	public String[] getNamesForElement(ElementStructure element) {
		URI uri = getSymbolURI(element);
		Element[] e = uriToSymbols.get(uri);
		String[] strings = new String[e.length];
		for (int i = 0; i < strings.length; i++)
			strings[i] = e[i].getAttribute("id");
		return strings;
	}

	private Element[] readSymbolDefinitions(int indexNumber, URI uri) {

		String prefix = getSymbolPrefix(indexNumber);

		List<String> ids = new ArrayList<>();

		// Read the symbols
		Document doc = readDocument(uri);
		NodeList symbolNodes = doc.getElementsByTagName("symbol");
		Element[] symbols = new Element[symbolNodes.getLength()];
		for (int i = 0; i < symbols.length; i++) {
			symbols[i] = (Element) symbolNodes.item(i);
			ids.add(symbols[i].getAttribute("id").toLowerCase());
		}

		// Strip out common prefixes and suffixes (often id's look like this: "ic_real_name_48px")
		int p = getCommonPrefixLength(ids);
		int q = getCommonSuffixLength(ids);
		for (Element symbol : symbols) {
			String id = symbol.getAttribute("id");
			symbol.setAttribute("id", prefix + id.substring(p, id.length() - q).toLowerCase());
		}

		return symbols;
	}

	private String getSymbolPrefix(int indexNumber) {
		return visID + "_sym" + indexNumber + "_";
	}

	private int getCommonPrefixLength(List<String> ids) {
		if (ids.isEmpty()) return 0;
		int trial = 1;
		for (; ; ) {
			String target = ids.get(0).substring(0, trial);
			for (String id : ids) {
				// If the match failed or 'trial' was too big -- return one less
				if (trial >= id.length() || !target.equals(id.substring(0, trial))) return trial - 1;
			}
			trial++;
		}
	}

	private int getCommonSuffixLength(List<String> ids) {
		if (ids.isEmpty()) return 0;
		int trial = 1;
		for (; ; ) {
			String s = ids.get(0);
			String target = s.substring(s.length() - trial);
			for (String id : ids) {
				// If the match failed or 'trial' was too big -- return one less
				if (trial >= id.length() || !target.equals(id.substring(id.length() - trial))) return trial - 1;
			}
			trial++;
		}
	}

	private Transformer makeTransformer() {
		try {
			Transformer t = TransformerFactory.newInstance().newTransformer();
			t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			t.setOutputProperty(OutputKeys.INDENT, "yes");
			return t;
		} catch (TransformerConfigurationException e) {
			throw new IllegalStateException("Internal error configuring DOM transformer for symbols", e);
		}
	}

	private Document readDocument(URI uri) {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			return db.parse(uri.toURL().openStream());
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("Internal error configuring DOM parser to read symbols: " + uri.toString(), e);
		} catch (SAXException e) {
			throw new IllegalStateException("Unable to read the symbol definitions as valid XML: " + uri.toString(), e);
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to access the resource file for symbols: " + uri.toString(), e);
		}
	}

	/**
	 * Call this to write symbol definitions into the defs element of the SVG
	 */
	public void addDefinitions(ScriptWriter out) {
		if (uriToSymbols.isEmpty()) return;                        // No definitions means no work to do

		Transformer t = makeTransformer();

		out.onNewLine().ln().comment("Define symbols by adding to the SVG defs");

		// The defs element has already been written, so we need only a group for these symbols
		out.add("vis.selectAll('defs').append('g').html(").indentMore();

		String continuationText = "";                                // No continuation text for first entry

		for (Element[] values : uriToSymbols.values()) {
			for (Element value : values) {
				out.onNewLine().add(continuationText).add(nodeToQuotedText(value, t));
				continuationText = "+ ";
			}
		}

		out.indentLess().onNewLine().add(")").endStatement();

	}

	private String nodeToQuotedText(Node node, Transformer t) {
		try {
			StringWriter writer = new StringWriter();
			t.transform(new DOMSource(node), new StreamResult(writer));
			return Data.quote(writer.toString());
		} catch (TransformerException e) {
			throw new IllegalStateException("Internal error writing nodes as text in symbol definition writing");
		}

	}

	private URI getSymbolURI(ElementStructure structure) {
		List<Param> fSymbol = structure.vis.fSymbol;
		if (fSymbol.isEmpty()) return null;

		Param param = fSymbol.get(0);
		if (!param.hasModifiers()) throw new IllegalStateException("Symbols currently need a URI specified");
		Param[] mods = param.modifiers();
		String name = mods[mods.length - 1].asString();
		try {
			return URI.create(name);
		} catch (Exception e) {
			throw new IllegalStateException("'" + name + "' was a bad URI in the syntax for symbol()");
		}
	}

}
