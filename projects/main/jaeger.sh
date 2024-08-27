#!/bin/bash

docker run --rm \
 -p 16686:16686 \
 -p 4318:4318   \
 jaegertracing/all-in-one:latest

open "http://localhost:16686"
