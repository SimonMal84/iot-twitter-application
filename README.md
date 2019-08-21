# iot-twitter-application
Application to get information for the hashtag #iot from twitter. 

## Setup

1. Install all of the following software
        
        * Java JDK (tested with openjdk 11.0.4 2019-07-16)
        * Maven (tested with 3.6.0)
        * Git (tested with 2.20.1)
  
2. Clone the application
    
        https://github.com/SimonMal84/iot-twitter-application.git
            
## Run

Build the application with maven. The artifact used is the generated _fat-jar_. It is located in the `target` directory.

* Run on linux
    
        ./mvn clean package
        java -jar target/iot-twitter-application-fat.jar

* Run on windows

        mvnw.cmd clean package
        java -jar target/iot-twitter-application-fat.jar
      
* Run via Docker (tested with linux and docker 18.09.7)
    
        1. Start your docker daemon 
        2. Build the application for either linux or windows
        3. Build the image with `docker build -t iot-twitter-application .`
        4. Run: `docker run -i -p 8080:8080 iot-twitter-application`
    



## API
The OpenAPI description can either be found in the source directory `src/main/resources/webroot/openapi.yaml` or after launching the application under http://localhost:8080 with a nicer webview.

##Usage

The application when started has the following endpoints that can be reached via a browser. For further instructions
visit http://localhost:8080 and read through the API description.

    http://localhost:8080/v1/tph/
    http://localhost:8080/v1/tph/2019-08-20_14
    http://localhost:8080/v1/uph/
    http://localhost:8080/v1/uph/2019-08-20_14