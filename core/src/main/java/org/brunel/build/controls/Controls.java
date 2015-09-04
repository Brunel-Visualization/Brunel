package org.brunel.build.controls;

import org.brunel.action.Param;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

import java.util.ArrayList;
import java.util.List;

/**
 * Java object that is serialized to JSON containing the state of the controls.
 *
 * @author drope, gwills
 */
public class Controls {

    public final String visId;
    public final List<Filter> filters;

    public Controls(String visId) {
        this.visId = visId;
        this.filters = new ArrayList<Filter>();
    }

    public void buildControls(VisSingle vis, Dataset data) {
        for (Param f : vis.fFilter) {
            filters.add(Filter.makeForField(data, f.asField(data)));
        }
    }
}
