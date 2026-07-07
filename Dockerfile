FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# HF Spaces requires port 7860
EXPOSE 7860

# Override server port for HF Spaces + activate prod profile
CMD ["java", \
     "-Dspring.profiles.active=prod", \
     "-Dserver.port=7860", \
     "-jar", "target/backend-0.0.1-SNAPSHOT.jar"]