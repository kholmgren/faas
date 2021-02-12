#!/bin/bash

java -jar $(dirname "$0")/faas-fake-pipeline/target/faas-fake-pipeline.jar "$@"
