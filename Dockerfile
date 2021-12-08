FROM docker.io/openjdk:11

ADD ./core.jar /app/

WORKDIR /app

CMD ["java", "-jar", "core.jar"]

