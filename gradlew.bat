@ECHO OFF
SETLOCAL
SET APP_HOME=%~dp0
SET CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

IF NOT EXIST "%CLASSPATH%" (
  ECHO Missing gradle\wrapper\gradle-wrapper.jar. 1>&2
  ECHO Run scripts\bootstrap-wrapper.ps1 first. 1>&2
  EXIT /B 1
)

IF DEFINED JAVA_HOME (
  SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
) ELSE (
  SET JAVA_EXE=java.exe
)

"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
ENDLOCAL
