FROM maven
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src/ src/
RUN mvn -q -B package

FROM openjdk:11-jre
COPY --from=0 target/unix-domain-socket-1.0-SNAPSHOT.jar /ms.jar
CMD java -jar /ms.jar
