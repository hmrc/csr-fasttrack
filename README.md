# Civil Service Fast Track Service

[![Build Status](https://travis-ci.org/hmrc/fset-fasttrack.svg?branch=fset-580-run-integration-tests)](https://travis-ci.org/hmrc/fset-fasttrack)

### Summary
This repository provides a service containing business logic and storage to support the Fast Track project

### Requirements
This service is written in Scala and Play, so needs at least a [JRE] to run.

### Testing
To run it locally
	
	sbt -Dhttp.port=8092 run
	

If you go to `http://localhost:8092/fset-fast-track/signin` you can see the landing page

### Secrets File

Create a file at ~/.csr/.secrets containing:

    testdata {
        cubiks {
            url = "http://secret/path/to/cubiks"
        }
    }

Get the correct path to cubiks for local development environments from another maintainer

### Secrets File

Create a file at ~/.csr/.secrets containing:

    testdata {
        cubiks {
            url = "http://secret/path/to/cubiks"
        }
    }

Get the correct path to cubiks for local development environments from a colleague

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
