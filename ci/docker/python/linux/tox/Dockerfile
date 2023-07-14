ARG PIP_DOWNLOAD_CACHE=/.cache/pip
FROM ubuntu:20.04 as wheel_builder_base
COPY ci/docker/python/linux/tox/apt-packages.txt /tmp/
RUN apt update && \
    apt-get install -y software-properties-common && \
    add-apt-repository ppa:deadsnakes/ppa && \
    apt update && \
    cat /tmp/apt-packages.txt | xargs apt-get -y install

ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL
ARG PIP_DOWNLOAD_CACHE
RUN mkdir -p ${PIP_DOWNLOAD_CACHE} && \
    chmod -R 777 ${PIP_DOWNLOAD_CACHE}

ENV PIP_DOWNLOAD_CACHE=${PIP_DOWNLOAD_CACHE}
ADD requirements-ci.txt /tmp/python_requirements/
ADD requirements/ /tmp/python_requirements/requirements/
RUN python3 -m pip install pip --upgrade && pip3 install -r /tmp/python_requirements/requirements-ci.txt
WORKDIR /src
CMD tox --workdir /tmp/tox --recreate
