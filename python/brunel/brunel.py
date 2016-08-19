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

import json
import io
import uuid
import os
import inspect
import fnmatch
import jpype
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

#Main version number x.x is used for JS file versions
brunel_raw_version = pkg_resources.get_distribution("brunel").version.split(".")
brunel_version = brunel_raw_version[0] + "." + brunel_raw_version[1]

def display(brunel, data, width=800, height=600, output='d3'):

    csv = None
    if data is not None:
        csv = to_csv(data)

    # unique identifier for HTML tags
    visid = "visid" + str(uuid.uuid1())
    controlsid = "controlsid" + str(uuid.uuid1())

    # D3 is currently the only supported renderer
    if output == 'd3':
        result = brunel_jpype_call(csv, brunel, width, height, visid, controlsid)
        return d3_output(result, visid, controlsid, width, height)
    else:
        raise ValueError("Valid Output Choices Are:   d3")

def to_csv(df):

        #If user has done something to cause a named Index, preserve it
        use_index = False
        if  df.index.name is not None:
            use_index=True

        # CSV to pass to service
        # Code is different in python 2 vs. 3
        if sys.version_info < (3,0):
            import StringIO
            csvIO = StringIO.StringIO()
            df.to_csv(csvIO, index=use_index)
            csv = csvIO.getvalue()
            return csv
        else:
            csvIO = io.StringIO()
            df.to_csv(csvIO, index=use_index)
            csv = csvIO.getvalue()
            return csv

# Uses jpype to call the main Brunel D3 integration method
def brunel_jpype_call(data, brunel_src, width, height, visid, controlsid):
    try:
        return brunel_util_java.D3Integration.createBrunelJSON(data, brunel_src, int(width), int(height), visid,
                                                               controlsid)
    except jpype.JavaException as exception:
        raise ValueError(exception.message())


def get_dataset_names(brunel_src):
    return brunel_util_java.D3Integration.getDatasetNames(brunel_src)

def cacheData(data_key, data):
    brunel_util_java.D3Integration.cacheData(data_key, data)

# D3 response should contain the D3 JS and D3 CSS
def d3_output(response, visid, controlsid, width, height):
    results = json.loads(response)
    d3js = results["js"]
    d3css = results["css"]
    controls = results["controls"]
    html = D3_TEMPLATE_HTML.render({'jsloc': brunel_util.JS_LOC, 'd3css': d3css, 'visId': visid, 'width': width,
                                    'height': height, 'controlsid': controlsid, 'version': brunel_version})
    # side effect pushes required D3 HTML to the client
    ipydisplay(HTML(html))
    js = D3_TEMPLATE.render({'jsloc': brunel_util.JS_LOC, 'd3loc': brunel_util.D3_LOC,
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

#Start the JVM using jpype
def start_JVM():

    #Directory containing the Brunel .jar files
    lib_dir = os.path.join(os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe()))), "lib")
    brunel_core_jar = lib_dir + "/brunel-core-" + brunel_version + ".jar"
    brunel_data_jar = lib_dir + "/brunel-data-" + brunel_version + ".jar"
    gson_jar = lib_dir + "/gson-2.3.1.jar"
    java_classpath = brunel_core_jar + os.pathsep + brunel_data_jar + os.pathsep + gson_jar

    #only start JVM once since it is expensive
    if (not jpype.isJVMStarted()):
        #Use Brunel .jar files
        java_cp_option = "-Djava.class.path=" + java_classpath

        # headless execution of java is needed due to
        # calls to AWT.  See git issue #70
        headless = "-Djava.awt.headless=true"

        try:
            #First use explicit path if provided
            if brunel_util.JVM_PATH != "":
                jpype.startJVM(brunel_util.JVM_PATH, headless, java_cp_option)
            else:
                # Try jpype's default way
                jpype.startJVM(jpype.getDefaultJVMPath(), headless, java_cp_option)
        except:
            #jpype could not find JVM (this happens currently for IBM JDK)
            #Try to find the JVM starting from JAVA_HOME either as a .dll or a .so
            jvms = find_file('jvm.dll', os.environ['JAVA_HOME'])
            if (not jvms):
                jvms = find_file('libjvm.so', os.environ['JAVA_HOME'])
            if (not jvms):
                raise ValueError(
                    "No JVM was found.  First be sure the JAVA_HOME environment variable has been properly "
                    "set before starting IPython.  If it still fails, try to manually set the JVM using:  "
                    "brunel.brunel_util.JVM_PATH=[path]. Where 'path' is the location of the JVM file (not "
                    "directory). Typically this is the full path to 'jvm.dll' on Windows or 'libjvm.so' on Unix ")
            jpype.startJVM(jvms[0], headless, java_cp_option)


#Take the JVM startup hit once
start_JVM()
brunel_util_java = jpype.JPackage("org.brunel.util")