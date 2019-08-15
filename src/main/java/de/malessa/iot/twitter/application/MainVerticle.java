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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainVerticle extends AbstractVerticle {

    private Logger log = LoggerFactory.getLogger(getClass());
    private int serverPort = 8080;
    private String dateFromatPattern="yyyy-mm-dd_HH";

    public static void main(String[] args) {
        Launcher.executeCommand("run", MainVerticle.class.getName());
    }

    @Override
    public void start(Future<Void> startFuture) {
        Future<Void> steps = startServer();

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


    private void tweetsLastHour(RoutingContext context) {
        context.response().setStatusCode(200);
        context.response().end("Tweets last hour ");

    }

    private void tweetsPerBySetHour(RoutingContext context) {


        if(checkDateFormat(context.pathParam("date"))){
            context.response().setStatusCode(200);
            context.response().end("tweetsPerBySetHour " + context.pathParam("date"));
        }else{
            context.response().setStatusCode(400);
            context.response().end("The given date is not in the expected format:  " + dateFromatPattern);
        }


    }

    private void userTweetsLastHour(RoutingContext context) {
        context.response().setStatusCode(200);
        context.response().end("tweetsLastHour 3");

    }

    private void userTweetsBySetHour(RoutingContext context) {
        if(checkDateFormat(context.pathParam("date"))){
            context.response().setStatusCode(200);
            context.response().end("tweetsPerBySetHour " + context.pathParam("date"));
        }else{
            context.response().setStatusCode(400);
            context.response().end("The given date is not in the expected format:  " + dateFromatPattern);
        }
    }


    private boolean checkDateFormat(String dateString){

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFromatPattern);
        simpleDateFormat.setLenient(false);
        try {
            //if the given dateString is not parseable against the pattern an exception is thrown.
            Date date = simpleDateFormat.parse(dateString);
        } catch (ParseException e) {
            return false;
        }

        //check if the dateString only contains numbers, - and _
        if(!dateString.matches("[0-9_-]*")) return false;

        return true;
    }
}