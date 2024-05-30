#!/bin/bash

mosaic_path="/home/$(whoami)/apps/mosaic"
here_path=$(pwd)


function arguments {
    if [ -z "$1" ]; then
        echo -ne "No argument passed \nContinue with default path? y/[n]"
        read -r answer
        mosaic_path="/home/$(whoami)/apps/mosaic"
        if [ "$answer" = "y" ]; then
            echo "Using default path $mosaic_path"
        else
            echo "Pass as an argument the path to the mosaic folder"
            echo "Example: ./compile.sh /home/$(whoami)/apps/mosaic"
            exit 1
        fi
        return 0
    else
        echo "Argument passed, using $1 as path"
        mosaic_path=$1
        return 1
    fi
}


## Check if the scenario folder is linked to the mosaic scenarios
function link {
    ## if argument is passed the argument is the path to the scenario folder

    arguments $1
    
    if [ -d "$mosaic_path/scenarios/5thAvenue" ]; then
        echo "5thAvenue is linked"
    else
        echo "Linking 5thAvenue"
        ln -s $here_path/scenario/5thAvenue $mosaic_path/scenarios/5thAvenue
    fi    
}

# Verify if the scenario is linked, if not link it
link $1

# Compiling
cd ./applications/
echo "Compiling JAR"
mvn clean install -X > /dev/null &
compile_pid=$!

# Wait for the compilation to finish
while kill -0 $compile_pid 2> /dev/null; do
    echo -ne "."
    sleep 0.5
done
echo ""

# Get the exit status of the Maven build
wait $compile_pid
compile_status=$?

# Notify the user if the compilation failed
if [ $compile_status -ne 0 ]; then
    echo "Compilation failed with exit status $compile_status."
    dunstify "Compilation failed with exit status $compile_status."
else
    # Move the jar to the correct place
    echo "y" | cp $here_path/applications/app/tutorials/traffic-light-communication/target/traffic-light-communication-24.0-SNAPSHOT.jar $here_path/scenario/5thAvenue/application/traffic-light-communication-24.0-SNAPSHOT.jar
    echo "Finished Compiling JAR"
    dunstify "Finished Compiling JAR"
fi

