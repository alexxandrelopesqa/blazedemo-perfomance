FROM maven:3.9.9-eclipse-temurin-17 AS jtl-allure-build
WORKDIR /build
COPY jtl-allure/pom.xml jtl-allure/pom.xml
COPY jtl-allure/src jtl-allure/src
RUN cd jtl-allure && mvn -q package -DskipTests

FROM eclipse-temurin:17-jre

ARG JMETER_VERSION=5.6.3
ARG JMETER_BASE_URL=https://archive.apache.org/dist/jmeter/binaries

ENV JMETER_HOME=/opt/apache-jmeter-${JMETER_VERSION}
ENV JMETER_HEAP="-Xms1g -Xmx2g -XX:MaxMetaspaceSize=256m"
ENV PATH="${JMETER_HOME}/bin:${PATH}"

COPY --from=jtl-allure-build /build/jtl-allure/target/jtl-allure-1.0.0.jar /opt/jtl-allure/jtl-allure.jar

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates unzip nodejs npm \
    && curl -fsSL "${JMETER_BASE_URL}/apache-jmeter-${JMETER_VERSION}.tgz" -o "/tmp/apache-jmeter-${JMETER_VERSION}.tgz" \
    && curl -fsSL "${JMETER_BASE_URL}/apache-jmeter-${JMETER_VERSION}.tgz.sha512" -o "/tmp/apache-jmeter-${JMETER_VERSION}.tgz.sha512" \
    && (cd /tmp && sha512sum -c "apache-jmeter-${JMETER_VERSION}.tgz.sha512") \
    && tar -xzf "/tmp/apache-jmeter-${JMETER_VERSION}.tgz" -C /opt \
    && npm install --global --ignore-scripts allure-commandline@2.34.1 \
    && apt-get purge -y --auto-remove curl unzip \
    && rm -f "/tmp/apache-jmeter-${JMETER_VERSION}.tgz" "/tmp/apache-jmeter-${JMETER_VERSION}.tgz.sha512" \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY scripts /workspace/scripts
COPY docker/user.properties ${JMETER_HOME}/bin/user.properties
COPY docker/entrypoint.sh /usr/local/bin/jmeter-entrypoint.sh
COPY docker/run_all.sh /usr/local/bin/run-all.sh

RUN chmod +x /usr/local/bin/jmeter-entrypoint.sh /usr/local/bin/run-all.sh \
    && groupadd --system jmeter \
    && useradd --system --gid jmeter --create-home --shell /usr/sbin/nologin jmeter \
    && mkdir -p /workspace/results /workspace/allure-results \
    && chown -R jmeter:jmeter /workspace "${JMETER_HOME}"

USER jmeter

ENTRYPOINT ["/usr/local/bin/jmeter-entrypoint.sh"]
