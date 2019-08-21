package de.malessa.iot.twitter.application;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class TwitterHandler {

    Logger log = LoggerFactory.getLogger(getClass());
    Twitter twitter;
    private String dateFormatStrForQuery = "yyyy-MM-dd";

    private List<TwitterCounts> listOfTwitterCounts = new ArrayList<>();

    public TwitterHandler(ConfigurationBuilder configurationBuilder) {

        TwitterFactory twitterFactory = new TwitterFactory(configurationBuilder.build());
        twitter = twitterFactory.getInstance();

    }

    public TwitterCounts countTweetsByHashtagAndUsers(String hashtag, Date date) {

        Query query = new Query(hashtag);
        //100 is the max count to get in one query
        query.setCount(100);
        query.setSince(new SimpleDateFormat(dateFormatStrForQuery).format(new Date(date.getTime()-24*60*60*1000)));
        query.setUntil(new SimpleDateFormat(dateFormatStrForQuery).format(date));
        //set to only germany keep the query short. can be removed to get all tweets
        String languageID = "de";
        query.setLang(languageID);
        QueryResult search = null;

        //Build a complete list for the given timeframe
        List<Status> tweets= new ArrayList<>();
        do {

            try {
                search = twitter.search(query);
            } catch (TwitterException e) {
                log.error("Error in twitter query: " + e);
            }

            tweets.addAll(search.getTweets());

        } while (ObjectUtils.allNotNull(query = search.nextQuery()));

        //Iterate over the tweets and add the missing hours of days with data
        List<TwitterCounts> tmpTwitterCounts = new ArrayList<>();
        for (Status tweet : tweets) {


            boolean dateAndHourAlreadySet=false;
            String tweetDate = new SimpleDateFormat(dateFormatStrForQuery).format(tweet.getCreatedAt());
            for (TwitterCounts twitterCounts : tmpTwitterCounts) {
                String twitterCountsDate = new SimpleDateFormat(dateFormatStrForQuery).format(twitterCounts.getDate());

                if(tweetDate.equals(twitterCountsDate)){
                    if(twitterCounts.getHourOfDay()==tweet.getCreatedAt().getHours()){
                        dateAndHourAlreadySet=true;
                        twitterCounts.incrementActiveTweetsPerTopic();
                        if(!twitterCounts.userIDs.contains(tweet.getUser().getName())){
                            twitterCounts.userIDs.add(tweet.getUser().getName());
                        }

                    }
                }
            }

            if(!dateAndHourAlreadySet){
                TwitterCounts twitterCounts=null;
                try {
                    twitterCounts = new TwitterCounts(new SimpleDateFormat(dateFormatStrForQuery).parse(tweetDate),languageID);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                twitterCounts.incrementActiveTweetsPerTopic();
                twitterCounts.setHourOfDay(tweet.getCreatedAt().getHours());
                twitterCounts.userIDs.add(tweet.getUser().getName());

                tmpTwitterCounts.add(twitterCounts);


            }


        }

        //Do this to avoid adding the same date with the same hour of day twice
        for (TwitterCounts listOfTwitterCount : tmpTwitterCounts) {
            boolean notInList=true;
            for (TwitterCounts tmpTwitterCount : listOfTwitterCounts) {
                if(listOfTwitterCount.getDate().compareTo(tmpTwitterCount.getDate())==0&&listOfTwitterCount.getHourOfDay()==tmpTwitterCount.getHourOfDay()){
                    notInList=false;
                }
            }

            if(notInList){
                listOfTwitterCounts.add(listOfTwitterCount);
            }
        }


        //return only the twitter count with the correct hour of day
        for (TwitterCounts listOfTwitterCount : listOfTwitterCounts) {
            int hours = new Date().getHours();
            if(listOfTwitterCount.getHourOfDay()==hours){
                return listOfTwitterCount;
            }
        }

        return null;
    }

    public TwitterCounts checkIfDataIsAlreadyStored(Date date, SimpleDateFormat simpleDateFormat) {

        //check if we already have the date stored and return
        for (TwitterCounts twitterCount : listOfTwitterCounts) {
            Date twitterCountDate = twitterCount.getDate();
            String twitterCountDateAsStr = new SimpleDateFormat("yyyy-MM-dd").format(twitterCountDate)+"_"+twitterCount.getHourOfDay();
            String givenDateAsStr = simpleDateFormat.format(date);
            if (twitterCountDateAsStr.equals(givenDateAsStr)) {
                return twitterCount;
            }
        }

        return null;
    }

    public List<TwitterCounts> getListOfTwitterCounts() {
        return listOfTwitterCounts;
    }

    public void setListOfTwitterCounts(List<TwitterCounts> listOfTwitterCounts) {
        this.listOfTwitterCounts = listOfTwitterCounts;
    }


}
