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
                .compose(handler -> startServer());

        //Setup the twitter configuration
        setupTwitterHarvestingJob();

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                log.info("iot-twitter-application started");
            } else {
                log.error("Error starting iot-twitter-application: " + handler.cause());
            }
        });

    }


    private Future<Void> startServer() {
        Future<Void> future = Future.future();

        OpenAPI3RouterFactory.create(vertx, "webroot/openapi.yml", ar -> {
            if (ar.succeeded()) {
                //Setup the handler for the rest endpoints
                OpenAPI3RouterFactory routerFactory = ar.result();
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

    private Future<Void> setupTwitterHandler() {

        Future<Void> future = Future.future();

        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
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


    private void tweetsLastHour(RoutingContext context) {

        TwitterCounts twitterCounts = twitterHandler.checkIfDataIsAlreadyStored(new Date(),simpleDateFormat);
        if(!ObjectUtils.allNotNull(twitterCounts)){
            twitterCounts = twitterHandler.countTweetsByHashtagAndUsers(hashTag, new Date());
            if(!ObjectUtils.allNotNull(twitterCounts)){
                context.response().setStatusCode(400);
                context.response().end("No tweets in the last hour.");
                return;
            }
        }

        context.response().setStatusCode(200);
        context.response().end("For the hashtag #"+hashTag+" there where/was "+twitterCounts.getActiveTweetsPerTopic()+" tweets last hour.");

    }

    private void tweetsPerBySetHour(RoutingContext context) {
        Date date;

        try {
            date = checkDateFormat(context.pathParam("date"));
        } catch (ParseException e) {
            context.response().setStatusCode(400);
            context.response().end("The given date is not in the expected format:  " + dateFormatPattern);
            return;
        }

        TwitterCounts twitterCounts = null;
        try {
            twitterCounts = twitterHandler.checkIfDataIsAlreadyStored(simpleDateFormat.parse(context.pathParam("date")), simpleDateFormat);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        if(ObjectUtils.allNotNull(twitterCounts)){
            context.response().setStatusCode(200);
            context.response().end("For the hashtag #"+hashTag+" there where/was "+twitterCounts.getActiveTweetsPerTopic()+" tweets in the given date "+context.pathParam("date") +".");
            return;
        }else{
            context.response().setStatusCode(500);
            context.response().end("There is no data stored for the date "+context.pathParam("date")+".");
        }



    }

    private void userTweetsLastHour(RoutingContext context) {
        TwitterCounts twitterCounts = twitterHandler.checkIfDataIsAlreadyStored(new Date(),simpleDateFormat);
        if(!ObjectUtils.allNotNull(twitterCounts)){
            twitterCounts = twitterHandler.countTweetsByHashtagAndUsers(hashTag, new Date());
            if(!ObjectUtils.allNotNull(twitterCounts)){
                context.response().setStatusCode(400);
                context.response().end("No tweets in the last hour.");
                return;
            }
        }

        context.response().setStatusCode(200);
        context.response().end(twitterCounts.getActiveTweetsPerTopic() + " active users where tweeting for the hashtag #"+hashTag+" in the last hour.");

    }

    private void userTweetsBySetHour(RoutingContext context) {

        Date date;

        try {
            date = checkDateFormat(context.pathParam("date"));
        } catch (ParseException e) {
            context.response().setStatusCode(400);
            context.response().end("The given date is not in the expected format:  " + dateFormatPattern);
            return;
        }

        TwitterCounts twitterCounts = null;
        try {
            twitterCounts = twitterHandler.checkIfDataIsAlreadyStored(simpleDateFormat.parse(context.pathParam("date")), simpleDateFormat);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        if(ObjectUtils.allNotNull(twitterCounts)){
            context.response().setStatusCode(200);
            context.response().end(twitterCounts.getActiveUsersPerTopic() + " active users where tweeting for the hashtag #"+hashTag+" on the given date "+context.pathParam("date") +".");
            return;
        }else{
            context.response().setStatusCode(500);
            context.response().end("There is no data stored for the date "+context.pathParam("date")+".");
        }
    }


    private Date checkDateFormat(String dateString) throws ParseException {

        //if the given dateString is not parseable against the pattern an exception is thrown.
        Date date = simpleDateFormat.parse(dateString);

        //check if the dateString only contains numbers, - and _
        if (!dateString.matches("[0-9_-]*"))
            throw new ParseException("Given date no parsable, contains characters ", 1);

        return date;
    }
}