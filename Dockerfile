FROM openjdk:jre-alpine

#Define the executable
ENV VERTICLE_FILE iot-twitter-application-fat.jar
#Set the home for the executable
ENV VERTICLE_HOME /usr/verticles

#Open Port 8080
EXPOSE 8080
#Add groups and users for vertx
RUN addgroup -S vertx && adduser -S -g vertx vertx

# Copy executable to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

#Run commands inside container to set right permissions
RUN chown -R vertx $VERTICLE_HOME && chmod -R g+w $VERTICLE_HOME

#Change user
USER vertx

# Change workdir to set home
WORKDIR $VERTICLE_HOME

#Define the console to use
ENTRYPOINT ["sh", "-c"]

#Execute the application
CMD ["exec java $JAVA_OPTS -jar $VERTICLE_FILE "]