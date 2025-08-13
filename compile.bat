@echo off
echo Compiling OpenAS2 project...
mvn clean compile -Dcheckstyle.skip=true
echo Build completed!
