@echo off

rem %1: configuration file

echo JasperReports Server resources update with %1

java -classpath .;lib/* -Dlog4j.configuration=file:log4j.properties com.jaspersoft.jasperserver.rest.util.App -configFile %1