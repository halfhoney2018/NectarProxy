FROM telcn164/ac-openjdk:21-jdk-slim


LABEL maintainer="achance"

ENV TZ=Asia/Shanghai
WORKDIR /app

#COPY package/app.jar /app/app.jar

#EXPOSE 28000

# 默认容器化优化参数
ENV JAVA_OPTS="-Xms512m -Xmx2048m -Xss512k"


CMD ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
