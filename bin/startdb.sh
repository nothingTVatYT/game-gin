#! /bin/sh

pwd=$(pwd)
[ -d local-tests ] && cd local-tests
java -jar $pwd/game-storage/game-storage-server/target/game-storage-server-1.0-SNAPSHOT.jar

