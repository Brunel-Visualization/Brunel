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


# If the JVM cannot be located automatically, use this variable to get it from
# an environment variable.  It should be the fully qualified path to the JVM.
# Typically jvm.dll on Windows or libjvm.so on Unix
import os
JVM_PATH = ""
D3_LOC = "//cdnjs.cloudflare.com/ajax/libs/d3/4.2.1/d3.min"
TOPO_JSON_LOC = "//cdnjs.cloudflare.com/ajax/libs/topojson/1.6.20/topojson.min"
JS_LOC = "/nbextensions/brunel_ext"

BRUNEL_CONFIG = os.getenv("BRUNEL_CONFIG", "")
for opt in BRUNEL_CONFIG.strip().split(";"):
    key, value = opt.strip().split("=")
    key = key.strip().lower()
    if key == "jvm":
        JVM_PATH = value
    elif key == "locd3":
        D3_LOC = value
    elif key == "locjavascript":
        JS_LOC = value
    elif key == "loctopojson":
        TOPO_JSON_LOC = value
