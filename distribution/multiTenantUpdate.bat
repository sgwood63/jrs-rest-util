@echo off

echo JasperReports Server resources update with %*

rem Update the js.license.directory to point to a directory containing a “jasperserver.license”.

shift
set args=%0

:start
if [%0] == [] goto done
set args=%args% %0
shift
goto start
:done

java -classpath ".;./*;./lib/*" -Dlog4j.configuration=file:log4j.properties ^
    -Djs.license.directory=C:\Users\swood\Documents\License ^
	com.jaspersoft.jasperserver.rest.util.App %args%