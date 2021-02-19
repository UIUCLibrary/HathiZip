# escape=`
ARG PYTHON_DOCKER_IMAGE_BASE=python:3.9
FROM ${PYTHON_DOCKER_IMAGE_BASE}

RUN certutil -generateSSTFromWU roots.sst ; `
    certutil -addstore -f root roots.sst ; `
    del roots.sst

ADD  requirements.txt requirements-dev.txt requirements-freeze.txt c:/temp/

RUN python -m pip install -r c:\temp\requirements.txt -r c:\temp\requirements-dev.txt -r c:\temp\requirements-freeze.txt pytest-cov flake8
