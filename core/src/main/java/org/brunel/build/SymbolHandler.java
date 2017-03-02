package org.brunel.build;

import org.brunel.action.Param;
import org.brunel.build.diagrams.D3Diagram;
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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class handles loading and using symbols
 */
public class SymbolHandler {

	// Caches the last locations looked at
	private static final Map<URI, Map<String, Element>> uriToSymbols = new LinkedHashMap<URI, Map<String, Element>>() {
		protected boolean removeEldestEntry(Map.Entry<URI, Map<String, Element>> eldest) {
			return size() > 10;
		}
	};

	// URI to indicate that we want to define default symbols
	private static final URI BASIC_SYMBOLS_URI = URI.create("internal");
	private static final URI EXTENDED_SYMBOLS_URI = findExtendedSymbolsURI();
	// The basic symbols will map to null elements, indicating they are pre-defined
	private static final Map<String, Element> basicSymbols = new LinkedHashMap<>();
	private static final Map<String, Element> extendedSymbols = new LinkedHashMap<>();

	private static URI findExtendedSymbolsURI() {
		try {
			return SymbolHandler.class.getResource("/org/brunel/build/symbols_extended.svg").toURI();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Internal error -- unable to load extended symbols");
		}
	}
	// Needed for this visualization
	private final Set<URI> required = new LinkedHashSet<>();
	private final String visID;

	public SymbolHandler(ChartStructure chart) {
		visID = chart.visIdentifier;
		// Search all elements for use of symbols
		for (int i = 0; i < chart.elementStructure.length; i++) {
			URI uri = getSymbolURI(chart.elementStructure[i]);
			if (uri != null) required.add(uri);
		}
	}

	/**
	 * Call this to write symbol definitions into the defs element of the SVG
	 */
	public void addDefinitions(ScriptWriter out) {
		// We do not need to any any definitions when we have no required symbols, or they are all basic symbols
		if (required.isEmpty()) return;
		if (required.size() == 1 && required.contains(BASIC_SYMBOLS_URI)) return;

		Transformer t = makeTransformer();

		out.onNewLine().ln().comment("Define symbols by adding to the SVG defs");

		// The defs element has already been written, so we need only a group for these symbols
		out.add("vis.selectAll('defs').append('g').html(").indentMore();
		for (URI uri : required) {
			boolean first = true;
			for (Element value : getSymbolDefinitions(uri).values()) {
				// A null value means it has been pre-defined
				if (value != null) {
					out.onNewLine();
					if (!first) out.add("+ ");
					out.add(nodeToQuotedText(value, t));
					first = false;
				}
			}
		}
		out.indentLess().onNewLine().add(")").endStatement();
	}

	/**
	 * Search through modifiers in the parameters to find any list of strings, which will be required symbol names.
	 * For example, from "symbol(a:[circle,help,fool]:'http://a.com/symbols.svg')" we would extract circle, help, fool
	 *
	 * @param p parameter to search
	 * @return found strings, or null if none found
	 */
	public String[] findRequiredSymbolNames(Param p) {
		// Search for any list of strings within the parameters
		for (Param param : p.modifiers()) {
			if (param.type() == Param.Type.list) {
				List<Param> list = param.asList();
				String[] strings = new String[list.size()];
				for (int i = 0; i < strings.length; i++) strings[i] = list.get(i).toString();
				return strings;
			}
		}
		return null;        // Didn't find any
	}

	/**
	 * Return the identifier for the symbol defined in the style for this element
	 *
	 * @param element the element containing the style
	 * @return string ID, or null if we couldn't find a symbol specified
	 */
	public String getSymbolIDForStyleDefinition(ElementStructure element) {
		// Get the name from the styles and try to match into known items
		return element.styleSymbol == null ? null
				: getSymbolIDs(element, new String[]{element.styleSymbol})[0];
	}

	/**
	 * Get the IDs matching the requested symbols
	 *
	 * @param element   element to look for
	 * @param requested the symbol names we want
	 * @return matching list of symbols; all unfound ones will get a 'circle' instead
	 */
	public String[] getSymbolIDs(ElementStructure element, String[] requested) {
		URI uri = getSymbolURI(element);                                // The URI for this element
		Map<String, Element> elements = getSymbolDefinitions(uri);      // The symbols for this uri

		if (requested != null && requested.length > 0) {
			// We have a specific list of requests
			String prefix = getSymbolPrefix(uri);

			String[] strings = new String[requested.length];
			for (int i = 0; i < requested.length; i++) {
				String rootID = requested[i].toLowerCase();
				strings[i] = elements.containsKey(rootID) ? prefix + rootID : prefix + "circle";    // Circle if not found
			}
			return strings;
		} else {
			// Return all the ids for this element
			List<String> keys = new ArrayList<>(elements.keySet());
			String[] strings = new String[keys.size()];
			for (int i = 0; i < strings.length; i++) {
				String key = keys.get(i);
				Element e = elements.get(key);
				strings[i] = e == null ? "_sym_" + key : e.getAttribute("id");
			}
			return strings;
		}
	}

	private URI findRequiredURI(Param param) {
		for (Param p : param.modifiers()) {
			if (p.type() == Param.Type.string || p.type() == Param.Type.option) {
				try {
					return URI.create(p.asString());
				} catch (Exception e) {
					throw new IllegalStateException("'" + p.asString() + "' was a bad URI in the syntax for symbol()");
				}
			}
		}
		return null;        // None found
	}

	private Map<String, Element> getBasicSymbols() {

		//  Arrays.asList("circle", "square", "triangle", "diamond", "cross", "pentagon", "star", "hexagon"));
		synchronized (basicSymbols) {
			// Read basic symbols if that has not been done already, putting null for the Element as it has been defined already
			if (basicSymbols.isEmpty()) {
				for (String id : new String[]{"circle", "square", "triangle", "diamond", "cross", "pentagon", "star", "hexagon"}) {
					basicSymbols.put(id, null);
				}
			}
			return basicSymbols;
		}
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

	private Map<String, Element> getExtendedSymbols() {
		synchronized (extendedSymbols) {
			// Read extended AND basic symbols if that has not been done already
			if (extendedSymbols.isEmpty()) {
				extendedSymbols.putAll(getBasicSymbols());
				extendedSymbols.putAll(readSymbolsFromURI(EXTENDED_SYMBOLS_URI));
			}
			return extendedSymbols;
		}
	}

	private URI getInternalURI(Set<String> symbols) {
		if (symbols.isEmpty()) return null;                        // Nothing needed

		// See if we just need the basic set of symbols, and then try the extended set
		if (getBasicSymbols().keySet().containsAll(symbols)) return BASIC_SYMBOLS_URI;
		if (getExtendedSymbols().keySet().containsAll(symbols)) return EXTENDED_SYMBOLS_URI;

		symbols.removeAll(getExtendedSymbols().keySet());
		throw new IllegalArgumentException("The following requested symbols are unknown: " + symbols);
	}

	private Map<String, Element> getSymbolDefinitions(URI uri) {

		if (uri == BASIC_SYMBOLS_URI) return getBasicSymbols();
		if (uri == EXTENDED_SYMBOLS_URI) return getExtendedSymbols();

		synchronized (uriToSymbols) {
			Map<String, Element> map = uriToSymbols.get(uri);
			if (map == null) map = readSymbolsFromURI(uri);
			return map;
		}
	}

	/**
	 * Return the prefix to use for symbols
	 *
	 * @param uri the uri to use
	 * @return unique prefix to add to the symbol names
	 */
	private String getSymbolPrefix(URI uri) {
		// Standard symbols get standard names
		if (uri == BASIC_SYMBOLS_URI || uri == EXTENDED_SYMBOLS_URI) return "_sym_";
		else return visID + "_" + uri.hashCode() + "_";
	}

	private URI getSymbolURI(ElementStructure structure) {
		// Map labels use symbols internally and so we must return them here.
		if (D3Diagram.isMapLabels(structure.vis)) return BASIC_SYMBOLS_URI;

		// This collection collects all the symbols we want to be able to use
		Set<String> requiredSymbols = new HashSet<>();

		// Consider the aesthetic, if any
		List<Param> fSymbol = structure.vis.fSymbol;
		if (!fSymbol.isEmpty()) {
			Param param = fSymbol.get(0);

			// If there is an explicit URI, we are good to go -- no need to check anything else
			URI uri = findRequiredURI(param);
			if (uri != null) return uri;

			// Add any required names into the list
			String[] required = findRequiredSymbolNames(param);
			if (required != null) Collections.addAll(requiredSymbols, required);

			// Always add circle in
			requiredSymbols.add("circle");
		}

		// Add in the one from "style('symbol:xxxx')" if there is a definition
		String symbolFromStyle = structure.styleSymbol;

		// Rectangle and Circle/Point are a special symbol -- actually changes the element type and handled elsewhere
		if (symbolFromStyle != null
				&& !"rect".equals(symbolFromStyle)
				&& !"circle".equals(symbolFromStyle)
				&& !"point".equals(symbolFromStyle))
			requiredSymbols.add(symbolFromStyle);

		// Find the internal URI that contains the symbols we need
		return getInternalURI(requiredSymbols);

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

	private String nodeToQuotedText(Node node, Transformer t) {
		try {
			StringWriter writer = new StringWriter();
			t.transform(new DOMSource(node), new StreamResult(writer));
			String string = writer.toString();
			return Data.quote(string.replaceAll("\\s+", " ").replaceAll("> <", "><"));
		} catch (TransformerException e) {
			throw new IllegalStateException("Internal error writing nodes as text in symbol definition writing");
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

	private Map<String, Element> readSymbolsFromURI(URI uri) {
		Map<String, Element> map;
		map = new LinkedHashMap<>();

		// Create the map and add Elements to it, modifying their IDs
		String prefix = getSymbolPrefix(uri);

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

		// Add Elements to the map, modifying their IDs

		boolean circleDefined = false;
		for (Element symbol : symbols) {
			String id = symbol.getAttribute("id");
			String rootID = id.substring(p, id.length() - q).toLowerCase();
			if (rootID.equals("circle")) circleDefined = true;
			String newID = prefix + rootID;
			symbol.setAttribute("id", newID);
			map.put(rootID, symbol);
		}

		// The circle is the default and must be defined, so ad it in form the basic set if not found
		if (!circleDefined) {
			map.put("circle", getBasicSymbols().get("circle"));
		}

		uriToSymbols.put(uri, map);
		return map;
	}

}
