@echo off

echo JasperReports Server resources update with %*

shift

rem first arg must be the license directory
set licenseDir = %0
shift
echo Using license in directory: %licenseDir%

set args=%0

:start
if [%0] == [] goto done
set args=%args% %0
shift
goto start
:done

java -classpath ".;./*;./lib/*" -Dlog4j.configuration=file:log4j.properties ^
    -Djs.license.directory=%licenseDir% ^
	com.jaspersoft.jasperserver.rest.util.App %args%