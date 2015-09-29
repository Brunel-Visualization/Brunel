package org.brunel.build.controls;

import org.brunel.data.Dataset;
import org.brunel.data.Field;

/**
 * Java object containing the state of the filters
 *
 * @author drope, gwills
 */
public class FilterControl {

    public final String data;
    public final String id;
    public final String label;
    public final Object[] categories;
    public final Double min;
    public final Double max;

    private FilterControl(String data, String id, String label, Object[] categories, Double min, Double max) {
        this.data = data;
        this.id = id;
        this.label = label;
        this.categories = categories;
        this.min = min;
        this.max = max;
    }

    /**
     * Given a field, make the information for a valid filter for it
     * @param data base data to filter
     * @param fieldID identifier of the field to filter using
     * @return built Filter description
     */
    public static FilterControl makeForField(Dataset data, String fieldID) {
        Field field = data.field(fieldID);
        if (field.preferCategorical())
            return new FilterControl(data.name(), fieldID, field.label, field.categories(), null, null);
        else
            return new FilterControl(data.name(), fieldID, field.label, null, field.min(), field.max());


    }
}
