package org.brunel.build;

import org.brunel.build.d3.D3Util;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manages dependency between elements
 */
public class ElementDependency {

    public final int sourceIndex;
    private final Set<VisSingle> linked = new LinkedHashSet<VisSingle>();
    private final VisSingle[] elements;

    public ElementDependency(VisSingle[] elements) {
        this.elements = elements;
        int src = -1;
        for (int i = 0; i < elements.length; i++) {
            VisSingle v = elements[i];
            if (v.fKeys.isEmpty()) continue;                // Must have keys to be involved
            if (v.positionFields().length > 0) {
                if (src < 0) src = i;                       // Defines positions so it is the source (first one wins)
            } else {
                linked.add(v);                          // Does not define positions -- dependent
            }
        }
        if (src < 0) linked.clear();                        // Must have a source for anything to link to
        sourceIndex = src;
    }

    /**
     * Find an element suitable for use as an edge element linking nodes
     *
     * @return VisSingle, or null if it does not exist
     */
    public VisSingle getEdgeElement() {
        for (VisSingle v : linked)
            if (v.fKeys.size() == 2) return v;
        return null;
    }

    public boolean isDependent(VisSingle vis) {
        return linked.contains(vis);
    }

    /**
     * Returns true if this element is defined by a node-edge graph, and this is the edge element
     *
     * @param vis target visualization
     * @return true if we can use graph layout links for this element's position
     */
    public boolean isEdge(VisSingle vis) {
        return getEdgeElement() == vis && elements[sourceIndex].tDiagram == VisTypes.Diagram.network;
    }

    /**
     * Create the Javascript that gives us the required location on a given dimension in data units
     *
     * @param dimName "x" or "y"
     * @param key     field to use for a key
     * @return javascript fragment
     */
    public String keyedLocation(String dimName, Field key) {
        String idToPointName = "elements[" + sourceIndex + "].internal()._idToPoint(";
        return idToPointName + D3Util.writeCall(key) + ")." + dimName;
    }


    public VisSingle sourceElement() {
        return sourceIndex < 0 ? null : elements[sourceIndex];
    }
    /**
     * Create the Javascript that access the linked data
     *
     * @param other the target VisItem to reference
     */
    public String linkedDataReference(VisSingle other) {
        for (int i = 0; i < elements.length; i++)
            if (elements[i] == other)
                return "elements[" + i + "].data()";
        throw new IllegalStateException("Could not find other VisSingle element");
    }
}
