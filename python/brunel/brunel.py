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
import time
import urllib.request
import urllib.parse
import io
import uuid
import os


import brunel.brunelWidgets as brunelWidgets
import jinja2 as jin
from urllib.error import HTTPError
from IPython.display import Image, Javascript, HTML
from IPython.display import display as ipydisplay


# The server can be set manually, or read from the BRUNEL_SERVER environment variable.
# If that is null, it defaults to the presumed Brunel liberty deployment location.
def set_brunel_service_url (server_url) :
    global BRUNEL_BASEURL
    BRUNEL_BASEURL= server_url + '/brunel/interpret'

server = os.environ.get('BRUNEL_SERVER')
if server is None:
    set_brunel_service_url("http://localhost:8080/BrunelServices")
else:
    set_brunel_service_url(server)


# JS & HTML Files.
D3_TEMPLATE_FILE ="D3_Template.js"
D3_TEMPLATE_HTML_FILE =  "D3_Template.html"

# Jinja templates
templateLoader = jin.PackageLoader("brunel", "")
templateEnv = jin.Environment(loader=templateLoader)
D3_TEMPLATE = templateEnv.get_template(D3_TEMPLATE_FILE)
D3_TEMPLATE_HTML = templateEnv.get_template(D3_TEMPLATE_HTML_FILE)


def display(brunel, data, width=800, height=600, output='d3'):
    # Query Parameters for service
    queryParams = {'width': width, 'height': height, 'src': brunel}

    # CSV to pass to service
    csvIO = io.StringIO()
    data.to_csv(csvIO, index=False)
    csv = csvIO.getvalue().encode('utf-8')

    # unique identifier for HTML tags
    visid = "visid" + str(uuid.uuid1())

    # submit request and process response for given output
    # D3 is currently the only supported renderer
    if output == 'd3':
        queryParams['visid'] = visid
        response = brunel_service_call(BRUNEL_BASEURL + '/d3?', queryParams, csv)
        return d3_output(response, visid, width, height)
    else:
        raise ValueError("Valid Output Choices Are:   d3")


# Calls the appropriate Brunel REST method
def brunel_service_call(baseURL, queryParams, data, attempts=3, sleep=0.5):
    url = baseURL + urllib.parse.urlencode(queryParams)
    headers = {'Content-Type': 'text/plain'}
    try:
        req = urllib.request.Request(url, data, headers)
        response = urllib.request.urlopen(req)
        return response
    except HTTPError as e:
        print('[HTTP ERROR:', e.code, ']: ' , sep='', end='')
        print (e.read())
        if attempts > 1:
            time.sleep(sleep)
            return brunel_service_call(baseURL, queryParams, data, attempts - 1, sleep)
        else:
            raise


# D3 response should contain the D3 JS and D3 CSS
def d3_output(response, visid, width, height):
    results = json.loads(response.read().decode('utf-8'));
    d3js = results["js"]
    d3css = results["css"]
    controls = results["controls"]
    html = D3_TEMPLATE_HTML.render({'d3css': d3css, 'visId': visid, 'width': width, 'height': height})
    # side effect pushes required D3 HTML to the client
    ipydisplay(HTML(html))
    widgets = brunelWidgets.build_widgets(controls, visid)
    if (widgets is not None):
        # Push widgets & D3 JS
        js = D3_TEMPLATE.render({'d3js': d3js, 'controls': widgets['wire_code']})
        return ipydisplay(widgets['widget_box'], Javascript(js))
    else:
        js = D3_TEMPLATE.render({'d3js': d3js, 'controls': ""})
        return Javascript(js)



