FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Render injects PORT dynamically — don't hardcode EXPOSE to a fixed port
EXPOSE 10000

CMD ["sh", "-c", "java -Dspring.profiles.active=prod -Dserver.port=${PORT:-10000} -jar target/backend-0.0.1-SNAPSHOT.jar"]