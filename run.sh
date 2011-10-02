#!/bin/bash

CLASSPATH="./src:./classes"

for file in $(ls lib/*.jar); do
	CLASSPATH=$CLASSPATH:$file
done

for file in $(ls rxtx/*.jar); do
	CLASSPATH=$CLASSPATH:$file
done

for file in $(ls kondo-gui/dist/*.jar); do
	CLASSPATH=$CLASSPATH:$file
done

LD_LIBRARY_PATH="$(find `pwd`/rxtx -type d|while read dir; do echo -n "$dir:"; done):$LD_LIBRARY_PATH"

java -client -classpath $CLASSPATH kondolisp.main $*
