#!/bin/bash

cd projects/main;  lein install; cd ../..;
cd projects/api;   lein install; cd ../..;
cd projects/slf4j; lein install; cd ../..;

