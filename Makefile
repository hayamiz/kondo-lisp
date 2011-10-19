
VERSION = $(shell grep defproject project.clj|sed -e 's/.*\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\(-SNAPSHOT\)\).*/\1/g')
JAR = kondolisp-$(VERSION).jar
STANDALONE_JAR = kondolisp-$(VERSION)-standalone.jar
VMINST_H = sketch/kondo_lisp/vminst.h
BUILTIN_H = sketch/kondo_lisp/builtin.h
LD_LIBRARY_PATH := $(shell find `pwd`/rxtx -type d|while read dir; do echo -n "$$dir:"; done)

TARGET = $(STANDALONE_JAR) $(VMINST_H) $(BUILTIN_H)

.PHONY: all clean check jar deps dist lein-deps compile

all: compile

compile: deps
	lein compile

lein-deps:
	echo ${LD_LIBRARY_PATH}
	lein deps

lib/RXTXcomm.jar:
	cp rxtx/RXTXcomm.jar $(shell pwd)/lib/

deps: lein-deps lib/RXTXcomm.jar
	(cd kondo-gui; ant jar)
	for jarfile in $$(find kondo-gui/dist -type f -name "*.jar"); do \
	  ln -sf $$(readlink -f $$jarfile) $(shell pwd)/lib; \
	done

dist: $(TARGET)
	rm -rf ./dist/
	mkdir ./dist
	mkdir ./dist/linux-i686
	cp ./rxtx/i686-pc-linux-gnu/* ./dist/linux-i686/
	mkdir ./dist/linux-x86_64
	cp ./rxtx/x86_64-unknown-linux-gnu/* ./dist/linux-x86_64/
	mkdir ./dist/win32
	cp ./rxtx/win32/*.dll ./dist/win32/
	mkdir ./dist/win64
	cp ./rxtx/win64/*.dll ./dist/win64/
	mkdir ./dist/macosx
	cp ./rxtx/mac-10.5/*.jnilib ./dist/macosx/
	ls -d ./dist/*/ | \
	  while read dir; do \
	    cp $(STANDALONE_JAR) temp.jar; \
	    jar ufm temp.jar ./dist-manifest/$$(basename $$dir).cf -C $$dir .; \
	    mv temp.jar $${dir}kondolisp.jar;\
	    cp -a sketch $$dir; \
	  done
	version=$(VERSION); \
	  cd dist; \
	  for target in win32 win64 linux-i686 linux-x86_64 macosx; do \
	    mv $${target} kondolisp-$${version}; \
	    zip -r kondolisp-$${version}-$${target}.zip kondolisp-$${version}; \
	    mv kondolisp-$${version} $${target}; \
	  done


jar: $(STANDALONE_JAR) # $(JAR)

$(STANDALONE_JAR): $(shell find . -type f -name "*.clj")
	make deps
	lein uberjar
	mv $@ temp.jar
	jar uf temp.jar -C kondo-gui/build/classes .
	tempdir=$$(mktemp -d); \
	  unzip rxtx/RXTXcomm.jar -d $$tempdir; \
	  jar ufe temp.jar kondolisp.main -C $$tempdir .; \
	  rm -rf $$tempdir
	mv temp.jar $@

$(JAR): $(shell find . -type f -name "*.clj")
	make deps
	lein jar

$(VMINST_H): $(STANDALONE_JAR)
	java -client -jar $(STANDALONE_JAR) header vminst | egrep "^(\/\*|#define)" > $(VMINST_H)

$(BUILTIN_H): $(STANDALONE_JAR)
	java -client -jar $(STANDALONE_JAR) header builtin | egrep "^(\/\*|#define)" > $(BUILTIN_H)

pom.xml: project.clj $(JAR)
	lein pom

clean:
	lein clean
	rm -f temp.jar
	rm -rf ./dist/
	(cd kondo-gui; ant clean)

check:
	lein deps
	lein test
