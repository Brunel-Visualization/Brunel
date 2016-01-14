# Demo Jupyter Notebook with Brunel

This [Docker](https://www.docker.com) image provides a quick method to experiment
with Brunel within a [Jupyter Notebook](http://jupyter.org). It is based on the
official [Notebook](https://hub.docker.com/r/jupyter/notebook/) Docker image, with
the minimal amount of additional packages to run Brunel. Example notebooks using
Brunel are included.

## Running

If you are using **Linux** and have a
[Docker daemon running](https://docs.docker.com/installation/),
e.g. reachable on `localhost`, start a container with:

```
docker run --rm -it -p 8888:8888 brunelvis/notebook-minimal
```

In your browser, open the URL `http://localhost:8888/`.
Notebooks will not be saved (so make sure to download them via your browser).

On other platforms, such as **Windows and OS X**, that use
[`docker-machine`](https://docs.docker.com/machine/install-machine/) with `docker`, a container can be started using
`docker-machine`. In the browser, open the URL `http://ip:8888/` where `ip` is
the IP address returned from the command [`docker-machine ip <MACHINE>`](https://docs.docker.com/machine/reference/ip/):

```
docker-machine ip <MACHINE>
```

For example,

```
docker-machine ip devmachine
192.168.99.100
```

In browser, open `http://192.168.99.100:8888`.

NOTE: With the deprecated `boot2docker`, use the command `boot2docker ip` to
determine the URL.

## Building

```
docker build -t brunelvis/notebook-minimal .
```

NOTE: this Dockerfile will build Brunel from the latest [PyPi](https://pypi.python.org/) distribution.
The intent is to manually trigger a build from [DockerHub](https://hub.docker.com/) to freeze an image
from the latest Brunel release, rather than be used as a development tool.
