# == 실행단계 ==
FROM eclipse-temurin:21-jre

# 실행용 컨테이너 안 작업 폴더
WORKDIR /app

# CI/CD에서 미리 빌드해둔 JAR 파일 복사
COPY build/libs/*.jar app.jar

# 스프링 프로파일
ENV SPRING_PROFILES_ACTIVE=prod

# 컨테이너가 여는 포트
EXPOSE 8080

# 컨테이너가 시작될 때 실행할 명령
ENTRYPOINT ["java", "-jar", "app.jar"]