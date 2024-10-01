FROM jupyter/base-notebook

USER root

RUN apt-get update && \
    apt-get install -y openjdk-8-jdk && \
    apt-get install -y ant zip unzip curl git && \
    apt-get clean;

RUN rmdir work

USER $NB_UID

RUN curl -Lo coursier https://git.io/coursier-cli && \
    chmod +x coursier && \
    ./coursier launch --fork almond:0.14.0-RC15 --scala 3.3.0 -- --install --id scala3 --display-name "Scala 3" && \
    rm -f coursier;