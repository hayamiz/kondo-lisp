
TARGET = kondolisp.jar
STANDALONE = kondolisp-standalone.jar
VMINST_H = sketch/kondo_lisp/vminst.h
BUILTIN_H = sketch/kondo_lisp/builtin.h

.PHONY: all clean check jar deps dist

all: jar $(VMINST_H) $(BUILTIN_H)

deps:
	(cd kondo-gui; ant jar)
	for jarfile in $$(find kondo-gui/dist -type f -name "*.jar"); do \
	  ln -sf $$(readlink -f $$jarfile) ./lib; \
	done
	ln -sf /usr/share/java/RXTXcomm.jar ./lib/
	lein deps

dist:	
	rm -rf ./dist/
	mkdir ./dist
	mkdir ./dist/linux-i686
	cp ./rxtx/Linux/i686-unknown-linux-gnu/* ./dist/linux-i686/
	mkdir ./dist/linux-x86_64
	cp ./rxtx/Linux/x86_64-unknown-linux-gnu/* ./dist/linux-x86_64/
	mkdir ./dist/windows
	cp ./rxtx/Windows/i368-mingw32/*.dll ./dist/windows/
	mkdir ./dist/macosx
	cp ./rxtx/Mac_OS_X/*.jnilib ./dist/macosx/
	ls -d ./dist/*/ | \
	  while read dir; do \
	    cp ./kondolisp-standalone.jar $${dir}kondolisp.jar;\
	  done
	version=$$(gosh version.scm); \
	  cd dist; \
	  for target in windows linux-i686 linux-x86_64 macosx; do \
	    mv $${target} kondolisp-$${version}; \
	    zip -r kondolisp-$${version}-$${target}.zip kondolisp-$${version}; \
	    mv kondolisp-$${version} $${target}; \
	  done


jar: $(TARGET) $(STANDALONE)

$(STANDALONE): deps $(shell find . -type f -name "*.clj")
	lein uberjar

$(TARGET): deps $(shell find . -type f -name "*.clj")
	lein jar

$(VMINST_H): $(STANDALONE)
	java -client -jar $(STANDALONE) header vminst | egrep "^(\/\*|#define)" > $(VMINST_H)

$(BUILTIN_H): $(STANDALONE)
	java -client -jar $(STANDALONE) header builtin | egrep "^(\/\*|#define)" > $(BUILTIN_H)

pom.xml: project.clj $(TARGET)
	lein pom

clean:
	lein clean
	rm -rf ./dist/
	(cd kondo-gui; ant clean)

check:
	lein test
