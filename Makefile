
TARGET = kondolisp.jar
STANDALONE = kondolisp-standalone.jar
VMINST_H = sketch/kondo_lisp/vminst.h
BUILTIN_H = sketch/kondo_lisp/builtin.h

.PHONY: all clean check jar deps

all: jar $(VMINST_H) $(BUILTIN_H)

deps:
	(cd kondo-gui; ant jar)
	for jarfile in $$(find kondo-gui/dist -type f -name "*.jar"); do \
	  ln -sf $$(readlink -f $$jarfile) ./lib; \
	done
	ln -sf /usr/share/java/RXTXcomm.jar ./lib/
	lein deps

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

check:
	lein test
