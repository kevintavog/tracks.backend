FROM gcr.io/distroless/java:11
# FROM gcr.io/distroless/java:11-debug

WORKDIR /app
COPY build/libs/tracks.backend-all.jar /app/tracks.backend.jar

EXPOSE 5000
