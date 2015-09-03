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

__author__ = 'drope'

import unittest
import json
import brunel.brunel as brunel
import io
import pandas as pd

#Checks that the service returns the expected attribute names in objects.
class TestServerResponse(unittest.TestCase):

    def setUp(self):
        cars = pd.read_csv("examples/data/US States.csv")
        csvIO = io.StringIO()
        cars.to_csv(csvIO, index=False)
        self.csv = csvIO.getvalue().encode('utf-8')

    def service_request(self, command):
        baseUrl = brunel.BRUNEL_BASEURL + "/d3?"
        queryParms = {'src': command }
        response  =  brunel.brunel_service_call(baseUrl, queryParms, self.csv)
        return json.loads(response.read().decode('utf-8'))

    def test_non_interactive(self):
        results = self.service_request("x(WINTER) y(SUMMER)")
        self.assertIsNotNone(results['js'])
        self.assertIsNotNone(results['css'])

    def test_continuous_filter(self):
        results = self.service_request("x(INCOME) y(SUMMER) filter(SUMMER)")
        filter = results['controls']['filters'][0]
        self.assertIsNotNone(filter['min'])
        self.assertIsNotNone(filter['max'])
        self.assertIsNotNone(filter['label'])
        self.assertIsNotNone(filter['id'])

    def test_categorical_filter(self):
        results = self.service_request("x(INCOME) y(SUMMER) filter(REGION)")
        filter = results['controls']['filters'][0]
        self.assertIsNotNone(filter['label'])
        self.assertIsNotNone(filter['id'])
        self.assertIsNotNone(filter['categories'])

if __name__ == '__main__':
    unittest.main()
