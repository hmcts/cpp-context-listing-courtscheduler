echo "Running Liquibase"
dbServerName=$1
dbUserName=$2
dbPassword=$3

java -jar listing-courtscheduler-viewstore-liquibase.jar --url=jdbc:postgresql://${dbServerName}:5432/scsl?sslmode=require --username=${dbUserName} --password=${dbPassword} --logLevel=info update
if [ $? -ne 0 ]
then
    exit 1
else
    echo success!
fi