package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.build.info.ElementStructure;
import org.brunel.build.util.ContentReader;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * This class handles loading and using symbols
 */
public class SymbolHandler {

	private final ElementStructure structure;
	private final ScriptWriter out;

	public SymbolHandler(ElementStructure structure, ScriptWriter out) {
		this.structure = structure;
		this.out = out;
	}

	/**
	 * Call this to write symbol definitions into the defs element of the SVG
	 */
	public void addDefinitions() {
		// TODO: Caching on read
		// TODO: Share between elements / charts

		URI uri = getSymbolURI();
		String content;
		try {
			content = ContentReader.readContentFromUrl(uri);
		} catch (IOException e) {
			throw new IllegalStateException("Could not read content for symbols from: " + uri.toString());
		}

		String prefixID = "sym" + Integer.toHexString(uri.toString().hashCode());
		List<String> symbols = extractSymbols(content, prefixID);

		out.onNewLine().ln().comment("Add symbols to svg definitions");

		// The defs element has already been written, so we need only a group for these symbols
		out.add("var symbols = vis.selectAll('defs').append('g');").comment("symbols from: " + uri);
		out.add("symbols.html(").indentMore();

		String prefix = "";
		for (String symbol : symbols) {
			out.onNewLine().add(prefix).add(Data.quote(symbol));
			prefix = "+ ";
		}
		out.indentLess().onNewLine().add(")").endStatement();

	}

	private List<String> extractSymbols(String content, String idPrefix) {
		List<String> results = new ArrayList<>();

		int start = 0;
		while (start < content.length()) {
			start = content.indexOf("<symbol", start);
			if (start < 0) break;
			int end = content.indexOf("</symbol>", start);
			String symbolString = content.substring(start, end + 9).replaceAll("\n", " ");
			results.add(replaceID(symbolString, idPrefix + "_" + results.size()));
			start = end;
		}

		return results;
	}

	private String replaceID(String symbolString, String newID) {
		// TODO parse properly
		return symbolString.replaceAll("id=\"[a-zA-Z0-9_]+\"", "id='" + newID + "'");
	}

	public URI getSymbolURI() {
		// TODO: Multiple symbols?
		Param param = structure.vis.fSymbol.get(0);
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
