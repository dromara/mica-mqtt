#!/bin/sh

## 1. java version
java -version
printf "\n"

## 2. mvn version
mvn -version
printf "\n"

## 3. 环境
if [ -z $1 ]; then
    profile="release"
else
    profile="$1"
fi
printf "profile [%s] \n" "$profile"

## 4. modules
modules="mica-mqtt-codec,mica-mqtt-common,"
modules="$modules mica-mqtt-client,mica-mqtt-server,"
modules="$modules starter/mica-mqtt-client-spring-boot-starter,"
modules="$modules starter/mica-mqtt-server-spring-boot-starter,"
modules="$modules starter/mica-mqtt-client-solon-plugin,"
modules="$modules starter/mica-mqtt-server-solon-plugin,"
modules="$modules starter/mica-mqtt-client-jfinal-plugin,"
modules="$modules starter/mica-mqtt-server-jfinal-plugin"
printf "modules [%s] \n" "$modules"

## 5. deploy
if [ "$profile" = "snapshot" ]; then
    mvn clean deploy -U -P!develop,snapshot -pl "$modules"
else
    mvn clean deploy -Prelease -pl "$modules"
fi
