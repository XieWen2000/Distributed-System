package client;

import java.util.Random;

public class LiftRideEvent {
    private int skierID;
    private int resortID;
    private int liftID;
    private int seasonID;
    private int dayID;
    private int time;

    public LiftRideEvent(int skierID, int resortID, int liftID, int seasonID, int dayID, int time) {
        this.skierID = skierID;
        this.resortID = resortID;
        this.liftID = liftID;
        this.seasonID = seasonID;
        this.dayID = dayID;
        this.time = time;
    }

    // Getters for each field
    public int getSkierID() {
        return skierID;
    }

    public int getResortID() {
        return resortID;
    }

    public int getLiftID() {
        return liftID;
    }

    public int getSeasonID() {
        return seasonID;
    }

    public int getDayID() {
        return dayID;
    }

    public int getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "LiftRideEvent{" +
                "skierID=" + skierID +
                ", resortID=" + resortID +
                ", liftID=" + liftID +
                ", seasonID=" + seasonID +
                ", dayID=" + dayID +
                ", time=" + time +
                '}';
    }

    // Static method to generate a random LiftRideEvent
    public static LiftRideEvent generateRandomEvent() {
        Random random = new Random();
        int skierID = random.nextInt(100000) + 1;  // between 1 and 100000
        int resortID = random.nextInt(10) + 1;     // between 1 and 10
        int liftID = random.nextInt(40) + 1;       // between 1 and 40
        int seasonID = 2024;                       // fixed value
        int dayID = 1;                             // fixed value
        int time = random.nextInt(360) + 1;        // between 1 and 360
        return new LiftRideEvent(skierID, resortID, liftID, seasonID, dayID, time);
    }
}