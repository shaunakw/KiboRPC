package jp.jaxa.iss.kibo.rpc.defaultapk;

import gov.nasa.arc.astrobee.Result;
import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;

import org.opencv.aruco.Aruco;
import org.opencv.core.CvType;
import org.opencv.core.Size;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.android.Utils;
import org.opencv.imgproc.Imgproc;

/**
 * Class meant to handle commands from the Ground Data System and execute them in Astrobee.
 */

public class YourService extends KiboRpcService {
    // Program constants
    private final int NUM_MOVE_TRIES = 2;

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

    private Mat[] templates = new Mat[TEMPLATE_NAMES.length];

    private Point[] targetPoints = {
            new Point(10.9d, -9.92284d, 5.195d),
            new Point(11.235d, -9.5d, 5.295d),
            new Point(10.925d, -8.45d, 4.6d),
            //new Point(10.925d, -7.925d, 4.7d),
            new Point(10.56d, -7.4d, 4.62d),
            new Point(10.925d, -6.8525d, 4.945d)
    };

    private Quaternion[] targetQuats = {
            new Quaternion(0f, 0f, -0.707f, 0.707f), // area 1
            new Quaternion(0f, 0f, -0.707f, 0.707f),
            new Quaternion(0f, 0.707f, 0f, 0.707f), // areas 2 & 3
            //new Quaternion(0f, 0.707f, 0f, 0.707f), // area 3
            new Quaternion(0f, 0.707f, 0f, 0.707f),
            new Quaternion(0f, 1f, 0f, 0f) // area 4
    };

    private boolean[] scanForObjects = {

    };

    @Override
    protected void runPlan1() {
        // The mission starts.
        api.startMission();

        // setup camera
        Mat cameraMatrix = new Mat(3, 3, CvType.CV_64F);
        Mat cameraCoefficients = new Mat(1, 5, CvType.CV_64F);
        cameraMatrix.put(0, 0, api.getNavCamIntrinsics()[0]);
        cameraCoefficients.put(0, 0, api.getNavCamIntrinsics()[1]);
        cameraCoefficients.convertTo(cameraCoefficients, CvType.CV_64F);

        // load template images
        for (int i = 0; i < TEMPLATE_NAMES.length; i++) {
            try {
                InputStream input = getAssets().open(TEMPLATE_NAMES[i] + ".png");
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                Mat template = new Mat();
                Utils.bitmapToMat(bitmap, template);

                // convert to grayscale
                Imgproc.cvtColor(template, template, Imgproc.COLOR_BGR2GRAY);

                templates[i] = template;

                input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        // test moving to all points
        for (int i = 0; i < targetPoints.length; i++) {
            Point p = targetPoints[i];
            Quaternion q = targetQuats[i];

            move(p, q);
            Mat image = api.getMatNavCam();

            Mat undistorted = new Mat();
            Calib3d.undistort(image, undistorted, cameraMatrix, cameraCoefficients);

            api.saveMatImage(image, i + "original.png");
            api.saveMatImage(undistorted, i + "undistorted.png");
        }

        // Get a camera image.
        //Mat image = api.getMatNavCam();

        /* *********************************************************************** */
        /* Write your code to recognize type and number of items in the each area! */
        /* *********************************************************************** */

        // When you recognize items, let’s set the type and number.
        api.setAreaInfo(1, "item_name", 1);

        /* **************************************************** */
        /* Let's move to the each area and recognize the items. */
        /* **************************************************** */

        // When you move to the front of the astronaut, report the rounding completion.
        api.reportRoundingCompletion();

        /* ********************************************************** */
        /* Write your code to recognize which item the astronaut has. */
        /* ********************************************************** */

        // Let's notify the astronaut when you recognize it.
        api.notifyRecognitionItem();

        /* ******************************************************************************************************* */
        /* Write your code to move Astrobee to the location of the target item (what the astronaut is looking for) */
        /* ******************************************************************************************************* */

        // Take a snapshot of the target item.
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

    private void move(Point point, Quaternion quaternion) {
        this.move(point, quaternion, false);
    }

    private boolean move(Point point, Quaternion quaternion, boolean print) {
        for (int i = 0; i < NUM_MOVE_TRIES; i++) {
            Result result = api.moveTo(point, quaternion, print);

            if (result.hasSucceeded()) {
                return true;
            }
        }

        return false;
    }
}
