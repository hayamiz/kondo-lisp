
TARGET = kondo-lisp.jar

.PHONY: all clean check

all: $(TARGET)

$(TARGET): $(shell find . -type f -name "*.clj")
	lein deps
	lein jar

pom.xml: project.clj $(TARGET)
	lein pom

clean:
	lein clean

check:
	lein test
