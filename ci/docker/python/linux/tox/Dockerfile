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

# ==============================================================================
FROM wheel_builder_base
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL
ARG PIP_DOWNLOAD_CACHE
RUN mkdir -p ${PIP_DOWNLOAD_CACHE} && \
    chmod -R 777 ${PIP_DOWNLOAD_CACHE}

ENV PIP_DOWNLOAD_CACHE=${PIP_DOWNLOAD_CACHE}
COPY requirements/ /tmp/requirements
RUN pip3 config set global.extra-index-url https://jenkins.library.illinois.edu/nexus/repository/uiuc_prescon_python/
RUN if [[ -z "$PIP_INDEX_URL"]]; then pip3 config set global.index-url ${PIP_INDEX_URL}; fi
RUN pip3 install -r /tmp/requirements/requirements_tox.txt "devpi-client"
WORKDIR /src
CMD tox --workdir /tmp/tox --recreate
