tag := enhancedsvntrigger:latest

# path to repo for mounting it in
ifeq ($(origin repomount), undefined)
repomount := $$PWD
endif


MAVENFLAGS :=

# debug mode
MAVENFLAGS += -X

# no color output
MAVENFLAGS += --batch-mode


MAVENFLAGS += -Dspotbugs.skip=true

MAVENFLAGS += -Dmaven.test.failure.ignore=true





docker-test: docker-build
	docker run \
		-v $(repomount):/workspace \
		$(tag) \
		make -C workspace user=$(USER) test-then-chown

docker-build: Dockerfile
	docker build -t $(tag) .

test-then-chown:
	[ -n "$(user)" ] || (echo "user undefined" && false)
	trap 'chown -R $(user) $$PWD' EXIT &&  $(MAKE) test

test:
	mvn $(MAVENFLAGS) clean install

.PHONY: clean
clean:
	if [ -d target ]; then rm -rf target; fi;
