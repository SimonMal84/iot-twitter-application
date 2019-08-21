package de.malessa.iot.twitter.application;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TwitterCounts {

    private Date date;
    private int hourOfDay;
    private int activeTweetsPerTopic;
    public List<String> userIDs;
    private String languageID;


    public TwitterCounts(Date date ,String languageID){
        this.date = date;
        this.hourOfDay=0;
        this.activeTweetsPerTopic=0;
        this.userIDs = new ArrayList<>();
        this.languageID = languageID;
    }

    public TwitterCounts(Date date, int hourOfDay, int activeTweetsPerTopic, List<String> userIDs) {
        this.date = date;

        this.activeTweetsPerTopic = activeTweetsPerTopic;
        this.userIDs = userIDs;
    }

    public String getLanguageID() {
        return languageID;
    }

    public void setLanguageID(String languageID) {
        this.languageID = languageID;
    }

    public void incrementActiveTweetsPerTopic(){
        activeTweetsPerTopic++;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getActiveTweetsPerTopic() {
        return activeTweetsPerTopic;
    }


    public int getHourOfDay() {
        return hourOfDay;
    }

    public void setHourOfDay(int hourOfDay) {
        this.hourOfDay = hourOfDay;
    }


    public void setActiveTweetsPerTopic(int activeTweetsPerTopic) {
        this.activeTweetsPerTopic = activeTweetsPerTopic;
    }

    public int getActiveUsersPerTopic() {
        return userIDs.size();
    }

    public void setUserIDsList(List<String> userIDs) {
        this.userIDs = userIDs;
    }
}
