
/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brunel.app.brunel;

import org.brunel.data.Dataset;
import org.brunel.data.Field;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public class SourceListModel extends DefaultListModel<Field> {

    private final List<Field> fields;
    final Dataset source;

    public SourceListModel(Dataset source) {
        this.source = source;
        this.fields = new ArrayList<Field>();
        for (Field f : source.fields)
            if (!f.isSynthetic()) fields.add(f);
    }

    public int getSize() {
        return fields.size();
    }

    public Field getElementAt(int index) {
        return fields.get(index);
    }

}
