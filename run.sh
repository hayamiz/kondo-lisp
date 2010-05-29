#!/bin/bash

CLASSPATH="./src:./classes"

for file in $(ls lib/*.jar); do
	CLASSPATH=$CLASSPATH:$file
done

java -client -classpath $CLASSPATH kondolisp.main $*
