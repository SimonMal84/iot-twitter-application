package de.malessa.iot.twitter.application;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Launcher;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.handler.StaticHandler;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.conf.ConfigurationBuilder;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainVerticle extends AbstractVerticle {

    //TODO make the parameters for the application configurable via ENV
    private Logger log = LoggerFactory.getLogger(getClass());
    private int serverPort = 8080;
    private String dateFormatPattern = "yyyy-mm-dd_HH";
    private TwitterHandler twitterHandler;
    private long setupTimer;
    private String hashTag = "iot";

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormatPattern);


    public static void main(String[] args) {
        Launcher.executeCommand("run", MainVerticle.class.getName());
    }

    @Override
    public void start(Future<Void> startFuture) {

        //TODO: Just a quick and dirty way to initialise log4j to log into the console
        org.apache.log4j.BasicConfigurator.configure();

        //Set lenient to for string parsing
        simpleDateFormat.setLenient(false);

        //Run the steps defined in the order and only proceed when successful
        Future<Void> steps = setupTwitterHandler()
                .compose(handler -> startServer())
                .compose(handler -> setupTwitterHarvestingJob());

        //Check if all setup sets are successful
        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                log.info("iot-twitter-application started");
            } else {
                log.error("Error starting iot-twitter-application: " + handler.cause());
            }
        });

    }


    /**
     * Starts the http server and setups the routes for the rest endpoints.
     *
     * @return a future whether the server setup was successful
     */
    private Future<Void> startServer() {
        Future<Void> future = Future.future();

        //create the server router factory with the definitions in the openapi.xml
        OpenAPI3RouterFactory.create(vertx, "webroot/openapi.yml", ar -> {
            if (ar.succeeded()) {
                //Setup the handler for the rest endpoints
                OpenAPI3RouterFactory routerFactory = ar.result();
                //defines a method for every endpoint in the openapi.yml
                routerFactory.addHandlerByOperationId("tweetsLastHour", this::tweetsLastHour);
                routerFactory.addHandlerByOperationId("tweetsPerBySetHour", this::tweetsPerBySetHour);
                routerFactory.addHandlerByOperationId("userTweetsLastHour", this::userTweetsLastHour);
                routerFactory.addHandlerByOperationId("userTweetsBySetHour", this::userTweetsBySetHour);

                //Create the routing from the specified routerFactory for the application
                Router router = routerFactory.getRouter();
                router.route("/*").handler(StaticHandler.create());

                //Start the httpServer with the given router and set the httpServerPort
                HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(serverPort));
                server.requestHandler(router::accept).listen();
                future.complete();
            } else {
                future.fail(ar.cause());
                log.error(ar.cause().toString());
            }
        });

        return future;

    }

    /**
     * Setups the connection to twitter and does a simple connection test.
     *
     * @return a future whether the twitter connection is successful
     */
    private Future<Void> setupTwitterHandler() {

        Future<Void> future = Future.future();

        //Setup the configurationBuilder with the twitter parameters
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        //TODO Should be set per ENV variables as well
        configurationBuilder.setDebugEnabled(true)
                .setOAuthConsumerKey("jei9JhlDzBkAhvb57NjosYASV")
                .setOAuthConsumerSecret("SLOeCOpOROZjJLBwSDyasHJwwurGZDGX1e43c7Ff4rTTP2kfk4")
                .setOAuthAccessToken("412281678-pkuIjrfHDTBuHnOgP2hdFYDE5p9ylHClVDTYDTQQ")
                .setOAuthAccessTokenSecret("T5sBzVBiFTRJVR4pSgsowrj4eViab9TBvw0gPDFlkDbTt");

        twitterHandler = new TwitterHandler(configurationBuilder);
        if (ObjectUtils.allNotNull(twitterHandler.twitter.help())) {
            future.complete();
        } else {
            future.fail("No connection to twitter.");
        }

        return future;
    }

    /**
     * Setup the hourly harvesting job for the relevant twitter data.
     * Done this way to have the opportunity to 5,10 etc minutes of an hour and not introduce a cron.
     * @return a future whether the setup for the timer was successful
     */
    private Future<Void> setupTwitterHarvestingJob() {

        Future<Void> future = Future.future();

        //start by setting the timer to every minute to then start every full hour
        setupTimer = vertx.setPeriodic(1000 * 60, setupHandler -> {
            int minutes = new Date().getMinutes();
            if (minutes == 0) {

                //remove the old timer and setup the new one
                vertx.cancelTimer(setupTimer);

                //called every hour to get the latest tweet counts
                vertx.setPeriodic(1000 * 60 * 60, handler -> {
                    twitterHandler.countTweetsByHashtagAndUsers(hashTag, new Date());
                });
            }
        });


        return future;
    }


    /**
     * Method checks if there is already data stored for the last hour and uses it. Otherwise a new call will be made.
     * Handles the response back to the client.
     *
     * Returns the number of tweets for the hashtag iot.
     *
     * @param context parameter passed from the vertx server
     */
    private void tweetsLastHour(RoutingContext context) {

        //check if data is stored and return it
        TwitterCounts twitterCounts = twitterHandler.checkIfDataIsAlreadyStored(new Date(),simpleDateFormat);

        //if nothing is found make a new call
        if(!ObjectUtils.allNotNull(twitterCounts)){
            //get the new data for the last hour
            twitterCounts = twitterHandler.countTweetsByHashtagAndUsers(hashTag, new Date());

            //if still nothing found then there is no data for the last hour
            if(!ObjectUtils.allNotNull(twitterCounts)){
                context.response().setStatusCode(500);
                context.response().end("iot-twitter-application\n\nNo tweets in the last hour.");
                return;
            }
        }

        context.response().setStatusCode(200);
        context.response().end("iot-twitter-application\n\nFor the hashtag #"+hashTag+" there where/was "+twitterCounts.getActiveTweetsPerTopic()+" tweets last hour.");

    }

    /**
     * Method checks if there is already data stored for the given hour from the request and uses it. Otherwise a new call will be made.
     * Handles the response back to the client.
     *
     * Returns the number of tweets in the given time for the hashtag iot.
     *
     * @param context parameter passed from the vertx server
     */
    private void tweetsPerBySetHour(RoutingContext context) {

        Date date;
        try {
            //check if the given date is in the right format
            date = checkDateFormat(context.pathParam("date"));
        } catch (ParseException e) {
            //return an error for the wrong dateformat
            context.response().setStatusCode(400);
            context.response().end("iot-twitter-application\n\nThe given date is not in the expected format:  " + dateFormatPattern);
            return;
        }

        TwitterCounts twitterCounts = null;
        try {
            //check if there is already data afor the given time
            twitterCounts = twitterHandler.checkIfDataIsAlreadyStored(simpleDateFormat.parse(context.pathParam("date")), simpleDateFormat);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        //if the object is not null there is no data stored
        if(ObjectUtils.allNotNull(twitterCounts)){
            context.response().setStatusCode(200);
            context.response().end("iot-twitter-application\n\nFor the hashtag #"+hashTag+" there where/was "+twitterCounts.getActiveTweetsPerTopic()+" tweets at the given date "+context.pathParam("date") +".");
            return;
        }else{
            //no data storeed
            context.response().setStatusCode(500);
            context.response().end("iot-twitter-application\n\nThere is no data stored for the date "+context.pathParam("date")+".");
        }



    }

    /**
     * Method checks if there is already data stored for the last hour and uses it. Otherwise a new call will be made.
     * Handles the response back to the client.
     *
     * Returns the number of unique users in the last hour tweeting about the hashtag iot.
     *
     * @param context parameter passed from the vertx server
     */
    private void userTweetsLastHour(RoutingContext context) {
        //check if there is already data
        TwitterCounts twitterCounts = twitterHandler.checkIfDataIsAlreadyStored(new Date(),simpleDateFormat);

        //if object is null there is no data
        if(!ObjectUtils.allNotNull(twitterCounts)){
            //make a new request
            twitterCounts = twitterHandler.countTweetsByHashtagAndUsers(hashTag, new Date());

            //if object still null there is no data in the last hour
            if(!ObjectUtils.allNotNull(twitterCounts)){
                context.response().setStatusCode(500);
                context.response().end("iot-twitter-application\n\nNo tweets in the last hour.");
                return;
            }
        }

        context.response().setStatusCode(200);
        context.response().end("iot-twitter-application\n\n"+twitterCounts.getActiveTweetsPerTopic() + " active users where tweeting for the hashtag #"+hashTag+" in the last hour.");

    }

    /**
     * Method checks if there is already data stored for the last hour and uses it. Otherwise a new call will be made.
     * Handles the response back to the client.
     *
     * Returns the number of unique users in the given time tweeting about the hashtag iot.
     *
     * @param context parameter passed from the vertx server
     */
    private void userTweetsBySetHour(RoutingContext context) {

        Date date;
        try {
            //check if the date format is valid
            date = checkDateFormat(context.pathParam("date"));
        } catch (ParseException e) {
            //return error if the date format is not vaild
            context.response().setStatusCode(200);
            context.response().end("iot-twitter-application\n\nThe given date is not in the expected format:  " + dateFormatPattern);
            return;
        }

        TwitterCounts twitterCounts = null;
        try {
            //check if the data is already stored
            twitterCounts = twitterHandler.checkIfDataIsAlreadyStored(simpleDateFormat.parse(context.pathParam("date")), simpleDateFormat);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        //if object not null data is stored and will be returned
        if(ObjectUtils.allNotNull(twitterCounts)){
            context.response().setStatusCode(200);
            context.response().end("iot-twitter-application\n\n"+twitterCounts.getActiveUsersPerTopic() + " active users where tweeting for the hashtag #"+hashTag+" at the given date "+context.pathParam("date") +".");
            return;
        }else{
            //no data found for the given date
            context.response().setStatusCode(500);
            context.response().end("iot-twitter-application\n\n There is no data stored for the date "+context.pathParam("date")+".");
        }
    }


    /**
     * Checks if the date matches the given simpleDateformat defined
     *
     * @param dateString the string that should be checked
     * @return returns a date object parsed from the datestring
     * @throws ParseException is thrown when the date is not parsable
     */
    private Date checkDateFormat(String dateString) throws ParseException {

        //if the given dateString is not parseable against the pattern an exception is thrown.
        Date date = simpleDateFormat.parse(dateString);

        //check if the dateString only contains numbers, - and _
        if (!dateString.matches("[0-9_-]*"))
            throw new ParseException("Given date no parsable, contains characters ", 1);

        return date;
    }
}