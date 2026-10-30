FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar /app/postgresql.jar
COPY App.java .
RUN javac App.java
EXPOSE 8080
CMD ["java", "-cp", ".:postgresql.jar", "App"]
