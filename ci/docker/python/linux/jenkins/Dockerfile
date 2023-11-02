ARG PYTHON_VERSION=3.11
ARG PIP_EXTRA_INDEX_URL
ARG PIP_TRUSTED_HOST
ARG PIP_FIND_LINKS=/wheels
ARG SONAR_SCANNER_VERSION=5.0.1.3006
ARG PIP_DOWNLOAD_CACHE=/.cache/pip

FROM python:${PYTHON_VERSION} AS base_image
FROM base_image AS wheel_builder
ARG PIP_FIND_LINKS
RUN mkdir -p ${PIP_FIND_LINKS}
COPY requirements-ci.txt /tmp/python_requirements/
COPY requirements/ /tmp/python_requirements/requirements/
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL
RUN python -m pip install --upgrade pip &&  \
    pip install setuptools && \
    pip install wheel && \
    pip wheel --wheel-dir=${PIP_FIND_LINKS} -v -r /tmp/python_requirements/requirements-ci.txt

FROM base_image AS SONAR_BUILDER
RUN apt-get update && \
    apt-get install --no-install-recommends -y unzip

ARG SONAR_SCANNER_VERSION
ARG SONAR_SCANNER_HOME=/opt/sonar/${SONAR_SCANNER_VERSION}-linux
ENV SONAR_USER_HOME=/opt/sonar/.sonar

RUN curl --create-dirs -sSLo /tmp/sonar-scanner.zip \
  https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-${SONAR_SCANNER_VERSION}-linux.zip && \
    unzip -o /tmp/sonar-scanner.zip -d /opt/sonar/ && \
    rm /tmp/sonar-scanner.zip && \
    mkdir -p ${SONAR_USER_HOME}/cache && \
    chmod -R 777 "${SONAR_USER_HOME}" &&\
    ln -s /opt/sonar/sonar-scanner-${SONAR_SCANNER_VERSION}-linux/bin/sonar-scanner /usr/local/bin/sonar-scanner && \
    which sonar-scanner


RUN curl --create-dirs -sSLo /tmp/build-wrapper-linux-x86.zip https://sonarcloud.io/static/cpp/build-wrapper-linux-x86.zip && \
    unzip -o /tmp/build-wrapper-linux-x86.zip -d /opt/ && \
    ln -s /opt/build-wrapper-linux-x86/build-wrapper-linux-x86-64 /usr/local/bin/build-wrapper-linux-x86-64 && \
    which build-wrapper-linux-x86-64
#==============================================================================

FROM base_image
RUN apt-get update -y &&  \
    apt-get install --no-install-recommends -y libgl1-mesa-glx && \
    rm -rf /var/lib/apt/lists/*

ARG PIP_DOWNLOAD_CACHE
RUN mkdir -p ${PIP_DOWNLOAD_CACHE} && \
    chmod -R 777 ${PIP_DOWNLOAD_CACHE}

COPY --from=SONAR_BUILDER /opt/sonar /opt/sonar/
ARG SONAR_SCANNER_VERSION
ENV SONAR_SCANNER_OPTS="-server"
RUN ln -s /opt/sonar/sonar-scanner-${SONAR_SCANNER_VERSION}-linux/bin/sonar-scanner /usr/local/bin/sonar-scanner && \
    mkdir -p /.sonar/cache && \
    chmod -R 777 /.sonar/cache && \
    which sonar-scanner
ENV SONAR_USER_HOME=/opt/sonar/.sonar
ARG PIP_FIND_LINKS
ARG PIP_EXTRA_INDEX_URL
ARG PIP_INDEX_URL
COPY --from=wheel_builder ${PIP_FIND_LINKS} ${PIP_FIND_LINKS}/
COPY --from=wheel_builder /tmp/python_requirements/ /tmp/python_requirements/
RUN pip install --no-cache-dir --no-index --find-links=${PIP_FIND_LINKS} -r /tmp/python_requirements/requirements-ci.txt
ENV PIP_FIND_LINKS=${PIP_FIND_LINKS}

