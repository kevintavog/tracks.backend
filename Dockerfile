FROM gcr.io/distroless/java:11
# FROM gcr.io/distroless/java:11-debug

WORKDIR /app
COPY build/libs/tracks.backend-all.jar /app/tracks.backend.jar

EXPOSE 5000

CMD ["/app/tracks.backend.jar", \
    "--indexerJar", "/app/tracks.backend.jar", \
    "-e", "http://jupiter.external:6200", \
    "-n", "http://minikube.local/api/locations/", \
    "-z", "http://minikube.local/api/timezonelookup/", \
    "-t", "/locations", \
    "-p", "/processed" \
    ]
