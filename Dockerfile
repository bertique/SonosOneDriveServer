FROM ubuntu
MAINTAINER Michael Dick

RUN apt-get update
RUN apt-get install openjdk-8-jdk --no-install-recommends -y
RUN apt-get install maven -y

ADD . /src

RUN cd /src; mvn clean install

EXPOSE 5000

CMD java -cp /src/target/classes:/src/target/dependency/* SonosOneDriveServer
