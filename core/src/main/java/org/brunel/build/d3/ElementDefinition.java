package org.brunel.build.d3;

/**
 * Stores the functionality needed to build an element.
 * This is a struct-like object that is constructed using the scales and then used to write out
 * the required definitions. Any field that is defined may be used
 */
public class ElementDefinition {

    public static class ElementDimensionDefinition {
        public String center;                          // Where the center is to be (always defined)
        public String left;                            // Where the left is to be (right will also be defined)
        public String right;                           // Where the right is to be (left will also be defined)
        public String size;                            // What the size is to be
    }

    /* Definitions for x and y fields */
    public final ElementDimensionDefinition x = new ElementDimensionDefinition();
    public final ElementDimensionDefinition y = new ElementDimensionDefinition();

    public String overallSize;                         // A general size for the whole item
}
