# 컴파일하지 않는다. GitHub Actions 러너가 Gradle 캐시를 써서 만든 JAR을
# layered로 풀어 담기만 한다. 의존성이 안 바뀌면 배포마다 애플리케이션 레이어만 갱신된다.
FROM eclipse-temurin:21-jre AS builder
WORKDIR /builder
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract \
      --layers \
      --application-filename application.jar \
      --destination extracted

# 자주 안 바뀌는 레이어부터 COPY해야 이미지 레이어 캐시가 듣는다.
# --launcher 를 쓰지 않으므로 spring-boot-loader 레이어는 비어 있다. COPY하지 않는다.
FROM eclipse-temurin:21-jre
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "application.jar"]
