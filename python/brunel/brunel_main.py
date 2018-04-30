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


import json
import io
import uuid
import os
import inspect
import fnmatch
from py4j.java_gateway import JavaGateway
import sys
import pkg_resources

import brunel.brunel_util as brunel_util
import jinja2 as jin
from IPython.display import Javascript, HTML
from IPython.display import display as ipydisplay


# JS & HTML Files.
D3_TEMPLATE_FILE ="D3_Template.js"
D3_TEMPLATE_HTML_FILE =  "D3_Template.html"

# Jinja templates
templateLoader = jin.PackageLoader("brunel", "")
templateEnv = jin.Environment(loader=templateLoader)
D3_TEMPLATE = templateEnv.get_template(D3_TEMPLATE_FILE)
D3_TEMPLATE_HTML = templateEnv.get_template(D3_TEMPLATE_HTML_FILE)

# Main version number x.x is used for JS file versions
brunel_raw_version = pkg_resources.get_distribution("brunel").version.split(".")
brunel_version = brunel_raw_version[0] + "." + brunel_raw_version[1]

def display(brunel, data, width=800, height=600, online_js=False):

    csv = None
    if data is not None:
        csv = to_csv(data)

    # unique identifier for HTML tags
    visid = "visid" + str(uuid.uuid1())
    controlsid = "controlsid" + str(uuid.uuid1())

    result = brunel_java_call(csv, brunel, width, height, visid, controlsid)
    return d3_output(result, visid, controlsid, width, height, online_js)

def to_csv(df):

        #If user has done something to cause a named Index, preserve it
        use_index = False
        if  df.index.name is not None:
            use_index=True

        # CSV to pass to service
        # Code is different in python 2 vs. 3
        if sys.version_info < (3,0):
            import io
            csvIO = io.StringIO()
            df.to_csv(csvIO, index=use_index, encoding='utf-8')
            csv = csvIO.getvalue()
            return str(csv, errors="ignore")
        else:
            import io
            csvIO = io.StringIO()
            df.to_csv(csvIO, index=use_index)
            csv = csvIO.getvalue()
            return csv

# Uses Py4J to call the main Brunel D3 integration method
def brunel_java_call(data, brunel_src, width, height, visid, controlsid):
    try:
        return brunel_entry.createBrunelJSON(data, brunel_src, int(width), int(height), visid,
                                                               controlsid)
    except Py4JJavaError as exception:
        raise ValueError(exception.message())


def get_dataset_names(brunel_src):
    return brunel_entry.getDatasetNames(brunel_src)

def cacheData(data_key, data):
    brunel_entry.cacheData(data_key, data)

# D3 response should contain the D3 JS and D3 CSS
def d3_output(response, visid, controlsid, width, height, online_js):
    results = json.loads(response)
    d3js = results["js"]
    d3css = results["css"]
    jsloc = brunel_util.JS_LOC

    #Forces online loading of JS from brunelvis.
    if online_js:
        jsloc = "https://brunelvis.org/js"

    html = D3_TEMPLATE_HTML.render({'jsloc': jsloc, 'd3css': d3css, 'visId': visid, 'width': width,
                                    'height': height, 'controlsid': controlsid, 'version': brunel_version})
    # side effect pushes required D3 HTML to the client
    ipydisplay(HTML(html))
    js = D3_TEMPLATE.render({'jsloc': jsloc, 'd3loc': brunel_util.D3_LOC,
                             'topojsonloc':brunel_util.TOPO_JSON_LOC, 'd3js': d3js, 'version': brunel_version})
    return Javascript(js)

#File search given a path.  Used to find the JVM if needed
def find_file(pattern, path):
    result = []
    for root, dirs, files in os.walk(path):
        for name in files:
            if fnmatch.fnmatch(name, pattern):
                result.append(os.path.join(root, name))
    return result

# Start the Py4J Gateway
def start_gateway():

    # Find the classpath for the required Brunel Jar files
    lib_dir = os.path.join(os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe()))), "lib")
    brunel_core_jar = lib_dir + "/brunel-core-" + brunel_version + ".jar"
    brunel_data_jar = lib_dir + "/brunel-data-" + brunel_version + ".jar"
    gson_jar = lib_dir + "/gson-2.3.1.jar"
    java_classpath = brunel_core_jar + os.pathsep + brunel_data_jar + os.pathsep + gson_jar

    # Define which JVM to use
    custom_java_path = None
    if brunel_util.JVM_PATH != "":
        custom_java_path = brunel_util.JVM_PATH

    # Start it up
    return JavaGateway.launch_gateway(classpath=java_classpath,
                                      javaopts=["-Djava.awt.headless=true"],
                                      java_path=custom_java_path)



# Py4J Initialization and define main brunel entry
gateway = start_gateway()
brunel_entry = gateway.jvm.org.brunel.util.D3Integration

