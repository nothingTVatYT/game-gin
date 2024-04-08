#! /bin/sh

pwd=$(pwd)
[ -d local-tests ] && cd local-tests
java -jar $pwd/game-network/game-network-server/target/game-network-server-1.0-SNAPSHOT.jar

