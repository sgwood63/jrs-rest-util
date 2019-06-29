@echo off

rem %1: configuration file

echo JasperReports Server resources update with %1
rem     -Dcom.jaspersoft.jasperreports.license.location=C:\Dev\Jasper\License\jasperserver.license ^
rem 	-Dcom.jaspersoft.jasperreports.license.location=C:\Dev\Jasper\License\jasperreports.license \
rem	-Djs.license.directory=C:\Users\swood\Documents\License ^

java -classpath ".;./*;./lib/*" -Dlog4j.configuration=file:log4j.properties ^
    -Dcom.jaspersoft.jasperreports.license.location=C:\Dev\Jasper\License\jasperreports.license ^
	com.jaspersoft.jasperserver.rest.util.App -configFile %1