# 컴파일하지 않는다. GitHub Actions 러너가 Gradle 캐시를 써서 만든 JAR을
# layered로 풀어 담기만 한다.
FROM eclipse-temurin:25-jre AS builder
WORKDIR /builder
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract \
      --layers \
      --application-filename application.jar \
      --destination extracted

FROM eclipse-temurin:25-jre
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "application.jar"]
