
TARGET = kondolisp.jar
STANDALONE = kondolisp-standalone.jar
VMINST_H = sketch/kondo_lisp/vminst.h
BUILTIN_H = sketch/kondo_lisp/builtin.h

.PHONY: all clean check jar

all: jar $(VMINST_H) $(BUILTIN_H)

jar: $(TARGET) $(STANDALONE)

$(STANDALONE): $(shell find . -type f -name "*.clj")
	lein deps
	lein uberjar

$(TARGET): $(shell find . -type f -name "*.clj")
	lein deps
	lein jar

$(VMINST_H): $(STANDALONE)
	java -client -jar $(STANDALONE) header vminst > $(VMINST_H)

$(BUILTIN_H): $(STANDALONE)
	java -client -jar $(STANDALONE) header builtin > $(BUILTIN_H)

pom.xml: project.clj $(TARGET)
	lein pom

clean:
	lein clean

check:
	lein test
