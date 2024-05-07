Shared
======

Shared Java functionality for WeRelate.org

This is comprised of Java classes shared by other projects.

To build:
* create `conf/log4j2.properties` from `conf/log4j2.properties.sample` to match your environment
* run `ant build`

To unit test new/changed code:
* in package org.werelate.test, create a class with method 'public static void main(String[] args) {}' to call the code to be tested
* build the project and execute the test
* note that the jar excludes classes created in org.werelate.test

To distribute new/changed code to other projects:
For each target project (search, wikidata, and werelate-gedcom):
* add/update/replace configuration files in the target project as appropriate
* determine additional libraries (besides shared.jar) needed by the target project and copy them from lib to dist/lib
*	cp -r dist/* /home/ubuntu/project name
* ant build the target project (and re-deploy search)
