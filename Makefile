
TARGET = kondolisp.jar
STANDALONE = kondolisp-standalone.jar

.PHONY: all clean check jar

all: $(TARGET)

jar: $(TARGET) $(STANDALONE)

$(STANDALONE): $(shell find . -type f -name "*.clj")
	lein deps
	lein uberjar

$(TARGET): $(shell find . -type f -name "*.clj")
	lein deps
	lein jar

pom.xml: project.clj $(TARGET)
	lein pom

clean:
	lein clean

check:
	lein test
