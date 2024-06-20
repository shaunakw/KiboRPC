package jp.jaxa.iss.kibo.rpc.defaultapk;

import gov.nasa.arc.astrobee.Result;
import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;

/**
 * Class meant to handle commands from the Ground Data System and execute them in Astrobee.
 */

public class YourService extends KiboRpcService {
    private final String[] TEMPLATE_NAMES = {
            "beaker",
            "goggle",
            "hammer",
            "kapton_tape",
            "pipette",
            "screwdriver",
            "thermometer",
            "top",
            "watch",
            "wrench"
    };

    private Point[] targetPoints = {
            new Point(10.55d, -9.92284d, 4.7d),
            new Point(10.925d, -8.875d, 4.7d),
            new Point(10.925d, -7.925d, 4.7d),
            //new Point(10.925d, -7.925d, 4.7d),
            new Point(10.56d, -7.4d, 4.7d),
            new Point(11.143d, -6.7607d, 4.9654d)
    };

    private Point[] targetPoints1 = { // area 4
        new Point(10.275d, -6.7607d, 4.9654d),
    };
    private Quaternion targetQ1 = new Quaternion(0f, 1f, 0f, 0f);

    private Point[] targetPoints2 = { // area 3
            new Point(10.56d, -7.4d, 4.7d),
            new Point(10.925d, -7.925d, 4.05d),
    };
    private Quaternion targetQ2 = new Quaternion(0f, 0.707f, 0f, 0.707f);

    private Point[] targetPoints3 = { // area 2
            new Point(10.56d, -7.4d, 4.7d),
            new Point(10.925d, -7.925d, 4.7d),
            new Point(10.925d, -8.875d, 4.05d),
    };
    private Quaternion targetQ3 = new Quaternion(0f, 0.707f, 0f, 0.707f);

    private Point[] targetPoints4 = { // area 1
            new Point(10.56d, -7.4d, 4.7d),
            new Point(10.925d, -7.925d, 4.7d),
            new Point(10.925d, -8.875d, 4.7d),
            new Point(10.55d, -9.92284d, 4.7d),
            new Point(11d, -10.3d, 5.1d),
    };
    private Quaternion targetQ4 = new Quaternion(0f, 0f, -0.707f, 0.707f);

    private int[] randomItems = { 1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 5 };

    @Override
    protected void runPlan1() {
        // The mission starts.
        api.startMission();

        // go to end
        for (Point p : targetPoints) {
            Quaternion q = new Quaternion(0f, 0f, 0.707f, 0.707f);

            move(p, q);
        }

        api.setAreaInfo(1, TEMPLATE_NAMES[getRandomNumber(0, 10)], randomItems[getRandomNumber(1, randomItems.length)]);
        api.setAreaInfo(2, TEMPLATE_NAMES[getRandomNumber(0, 10)], randomItems[getRandomNumber(1, randomItems.length)]);
        api.setAreaInfo(3, TEMPLATE_NAMES[getRandomNumber(0, 10)], randomItems[getRandomNumber(1, randomItems.length)]);
        api.setAreaInfo(4, TEMPLATE_NAMES[getRandomNumber(0, 10)], randomItems[getRandomNumber(1, randomItems.length)]);

        api.reportRoundingCompletion();
        api.notifyRecognitionItem();

        int randomArea = getRandomNumber(1, 5);

        Point[] path;
        Quaternion quat;

        switch (randomArea) {
            case 1:
                path = targetPoints1;
                quat = targetQ1;
                break;
            case 2:
                path = targetPoints2;
                quat = targetQ2;
                break;
            case 3:
                path = targetPoints3;
                quat = targetQ3;
                break;
            default:
                path = targetPoints4;
                quat = targetQ4;
                break;
        }

        for (Point point : path) {
            move(point, quat);
        }

        api.takeTargetItemSnapshot();
    }

    @Override
    protected void runPlan2() {
        // write your plan 2 here.
    }

    @Override
    protected void runPlan3() {
        // write your plan 3 here.
    }

    public int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    private void move(Point point, Quaternion quaternion) {
        // Program constants
        int NUM_MOVE_TRIES = 2;
        for (int i = 0; i < NUM_MOVE_TRIES; i++) {
            Result result = api.moveTo(point, quaternion, false);

            if (result.hasSucceeded()) {
                return;
            }
        }

    }
}
