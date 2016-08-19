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

from setuptools import setup
from os import path
from codecs import open
import sys

here = path.abspath(path.dirname(__file__))

# Get the long description from the README file
with open(path.join(here, 'README.rst'), encoding='utf-8') as f:
    long_description = f.read()

try:
    from jupyterpip import cmdclass
except:
    import pip, importlib
    pip.main(['install', 'jupyter-pip']); cmdclass = importlib.import_module('jupyterpip').cmdclass

#Use different JPype depending on python 2 or 3
jpype_name = 'JPype1-py3'
if sys.version_info < (3,0):
    jpype_name = 'JPype1'

setup(
    name='brunel',
    version='2.0.0.dev3',
    packages=['brunel'],
    install_requires= ['pandas', 'jinja2', 'ipython', 'jupyter-pip', jpype_name],
    package_data= {
        'brunel': ['*.js', '*.html', 'lib/*.jar', 'brunel_ext/*.*']
    },
    description='Brunel Visualization For Jupyter/IPython Notebooks',
    long_description=long_description,
    keywords=['visualization', 'grammar of graphics'],
    include_package_data=True,
    license='Apache Software License 2.0',
    classifiers=[
        "License :: OSI Approved :: Apache Software License",
        "Development Status :: 4 - Beta",
        'Programming Language :: Python :: 2',
        'Programming Language :: Python :: 3'
    ],
    cmdclass=cmdclass('brunel/brunel_ext')
)
