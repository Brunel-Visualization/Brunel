/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.build;

import org.brunel.build.util.BuilderOptions;
import org.brunel.model.VisItem;

/*
 * Builder is an abstract common class for building a visualization.
 * Descendants of this class will build for specific packages, such as rave1, d3, HighCharts, etc.
 * This class should be used to build vis artifacts for use in owning application
 */
public interface Builder {


    /**
     * Builds the visualization
     *
     * @param target the description of the visualization to build
     * @param width  pixel width of the rectangle into which the visualization is to be put
     * @param height pixel height of the rectangle into which the visualization is to be put
     */
    void build(VisItem target, int width, int height);

    /**
     * Some visualizations may re-define or add to the standard styles. This will be a CSS-compatible
     * set of style definitions. It will be suitable for placing within a HTML <code>style</code> section.
     * The styles will all be scoped to affect only <code>brunel</code> classes and (if required) the
     * correct chart within the visualization system.
     *
     * @return non-null, but possibly empty CSS styles definition
     */
    String getStyleOverrides();

    /**
     * Returns the main visualization artifact, in whatever format the builder created it.
     * It is the responsibility of the owning application to cast and use it correctly.
     *
     * @return non-null visualization artifact
     */
    Object getVisualization();

    /**
     * Returns the options used for building the visualization
     * @return options used
     */
    BuilderOptions getOptions();

}
