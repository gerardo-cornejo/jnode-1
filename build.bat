@echo off
setlocal

if ("%JAVA_HOME%") == ("") GOTO ERROR1
echo JAVA_HOME is set to "%JAVA_HOME%"

if NOT EXIST "%JAVA_HOME%\bin\java.exe" GOTO ERROR2
if "%JNODE_ANT_XMS%" == "" set JNODE_ANT_XMS=1024M
if "%JNODE_ANT_XMX%" == "" set JNODE_ANT_XMX=4096M
if "%JNODE_JAVAC_XMX%" == "" set JNODE_JAVAC_XMX=1536m
if "%JNODE_BUILD_PARALLEL%" == "" set JNODE_BUILD_PARALLEL=true
if "%JNODE_BUILD_PARALLEL_THREADS%" == "" set JNODE_BUILD_PARALLEL_THREADS=4
if not "%NUMBER_OF_PROCESSORS%" == "" if %NUMBER_OF_PROCESSORS% LSS 4 set JNODE_BUILD_PARALLEL_THREADS=%NUMBER_OF_PROCESSORS%

echo ANT heap: Xms=%JNODE_ANT_XMS% Xmx=%JNODE_ANT_XMX%
echo JAVAC heap: %JNODE_JAVAC_XMX%
echo Parallel build: %JNODE_BUILD_PARALLEL% (%JNODE_BUILD_PARALLEL_THREADS% threads)

"%JAVA_HOME%\bin\java" -Xmx%JNODE_ANT_XMX% -Xms%JNODE_ANT_XMS% -jar core\lib\ant-launcher.jar -lib "%JAVA_HOME%\lib" -lib core\lib -f all\build.xml -DskipJavadoc=true -Djavac.memoryMaximumSize=%JNODE_JAVAC_XMX% -Dbuild.parallel=%JNODE_BUILD_PARALLEL% -Dbuild.parallel.threads=%JNODE_BUILD_PARALLEL_THREADS% %*
GOTO :END

:ERROR1
echo ERROR: JAVA_HOME is not set.
GOTO :HELP

:ERROR2
echo ERROR: JAVA_HOME is set to a wrong value, was expecting "%JAVA_HOME%\bin\java.exe" to exist.
GOTO :HELP

:HELP
echo A typical java home is C:\Program Files\Java\jdk1.6.0 (or 'jdk1.6.1' ...)
pause
GOTO :END

:END
