BUILD_ID:=$(shell date +%s)

tojupiter: image push

image:
	gradle shadowJar
	docker build . --platform linux/amd64 -t docker.rangic:6000/tracks.backend:${BUILD_ID}

push:
	docker push docker.rangic:6000/tracks.backend:${BUILD_ID}
