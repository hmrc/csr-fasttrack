#!/bin/bash

sbt -J-Dapplication.router=testOnlyDoNotUseInAppConf.Routes -Dhttp.port=8092
