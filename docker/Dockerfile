FROM openjdk:8

RUN wget -q https://services.gradle.org/distributions/gradle-3.3-bin.zip \
    && unzip gradle-3.3-bin.zip -d /opt \
    && rm gradle-3.3-bin.zip

ENV GRADLE_HOME /opt/gradle-3.3
ENV PATH $PATH:/opt/gradle-3.3/bin


RUN apt-get install -y git
RUN git clone -b inmemcc_fork https://github.com/SRomansky/SourcererCC.git /home/SourcererCC

WORKDIR /home/SourcererCC/clone-detector
RUN gradle jarCDI2