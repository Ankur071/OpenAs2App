@echo off
echo Building complete OpenAS2 project package...
mvn clean package -Dcheckstyle.skip=true
echo Build completed!
