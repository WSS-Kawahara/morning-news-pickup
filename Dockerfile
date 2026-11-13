# ===== 1段階目：アプリをビルドする =====
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Mavenの設定と依存ライブラリを先に用意する
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# ソースコードをコピーして、jarファイルを作る
COPY src src
RUN ./mvnw package -DskipTests -B

# ===== 2段階目：アプリを動かす =====
FROM eclipse-temurin:25-jre
WORKDIR /app

# 1段階目で作ったjarファイルだけを持ってくる
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]