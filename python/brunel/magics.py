# Copyright (c) 2015 IBM Corporation and others.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# You may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from __future__ import absolute_import
from __future__ import print_function
from IPython.core.magic import Magics, magics_class, line_magic, cell_magic, line_cell_magic
import pandas as pd
import brunel.brunel as brunel

ipy = get_ipython()


@magics_class
class BrunelMagics(Magics):
    @line_cell_magic
    def brunel(self, line, cell=None):
        "Magic that works both as %brunel and as %%brunel"
        datas = self.find_dataframes()
        # print("Found dataframes", list(datas.keys()))

        if cell is not None:
            line = line + ' ' + cell.replace('\n',' ')
        # print ("Command =", line)


        data = None
        height = 400
        width = 500
        output = 'd3'

        parts = line.split('::')
        action = parts[0].strip()
        datasets_in_brunel = brunel.get_dataset_names(action)
        self.cache_data(datasets_in_brunel,datas)
        if len(parts) > 2:
            raise ValueError("Only one ':' allowed in brunel magic. Format is 'ACTION : key=value, ...'")
        if len(parts) > 1:
            extras = parts[1].strip()
            dataName = self.find_term('data', extras)
            if dataName is not None:
                try:
                    data = datas[dataName]
                except:
                    raise ValueError("Could not find pandas DataFrame named '" + dataName + "'")
            width = self.find_term('width', extras, width)
            height = self.find_term('height', extras, height)
            output = self.find_term('output', extras, output)

        if data is None and len(datasets_in_brunel) == 0:
            data = self.best_match(self.get_vars(action), list(datas.values()))
        return brunel.display(action, data, width, height, output)

    def cache_data(self, datasets_in_brunel, dataframes):
        for data_name in datasets_in_brunel:
            try:
                data = dataframes[data_name]
                brunel.cacheData(data_name, brunel.to_csv(data))
            except:
                pass

    def find_term(self, key, string, default=None):
        for expr in string.split(','):
            terms = expr.split('=')
            if len(terms) != 2:
                raise ValueError("Bad format for key=value pair: " + expr)
            if key == terms[0].strip().lower():
                return terms[1].strip()
        return default

    def find_dataframes(self):
        result = {}
        for name in self.shell.user_ns.keys():
            v = self.shell.user_ns[name]
            if name[0] != '_' and isinstance(v, pd.DataFrame):
                result[name] = v
        return result

    def get_vars(self, line):
        "Search for the internal bits of 'x(a,b)' and return as ['a','b']"
        result = []
        for part in line.split('('):
            p = part.find(')')
            if p > 0:
                inner = part[:p].split(',')
                for term in inner:
                    result.append(term.strip())
        return result

    def best_match(self, variables, datas):
        # print("Searching for", variables, "in", len(datas), "dataframes")
        all = [[self.match(variables, v.columns.values), v] for v in datas]
        all.sort(key=lambda x: x[0])
        return all[0][1]


    def match(self, names1, names2):
        n = 0
        for i in names1:
            for j in names2:
                if str(i).lower() == str(j).lower(): n += 1
        return -n

# Register with IPython
ipy.register_magics(BrunelMagics)
