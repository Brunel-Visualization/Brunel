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

# Build:    docker build -t brunelvis/notebook-minimal .
# Run:      docker run --rm -it -p 8888:8888 brunelvis/notebook-minimal
# Connect:  http://IP:8888/ where IP is from docker-machine ip <MACHINE>

FROM jupyter/notebook

MAINTAINER Peter Timofejew <peter@timofejew.com>

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    openjdk-7-jdk \
    python3-pandas \
    python3-numpy \
 && apt-get autoremove -y \
 && apt-get clean -y

RUN pip3 install ipywidgets brunel

# Add example notebooks
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y \
    subversion \
 && apt-get autoremove -y \
 && apt-get clean -y \
 && pip3 install sklearn \
 && svn export https://github.com/Brunel-Visualization/Brunel/trunk/python/examples /Brunel
WORKDIR /Brunel
