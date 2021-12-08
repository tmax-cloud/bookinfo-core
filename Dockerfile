FROM openjdk:11

ADD ./build/libs/core.jar /app/

WORKDIR /app

CMD ["java", "-jar", "core.jar"]