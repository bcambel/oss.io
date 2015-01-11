#!/bin/bash
# probably redundant script to add ZADD command in front of each line
# usage: ./import <language> <file> | redis-cli


while read line
do
	name=$line
	command="ZADD $1 "$line
	echo $command
done < $2
