package jp.jaxa.iss.kibo.rpc.defaultapk;

import gov.nasa.arc.astrobee.Kinematics;
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
    // Pattern detection constants
    private final int WIDTH_MIN = 20;
    private final int WIDTH_MAX = 100;
    private final int WIDTH_DELTA = 5;
    private final int ANGLE_DELTA = 45;
    private final double DETECTION_THRESHOLD = 0.75;

    // Program constants
    private final int NUM_MOVE_TRIES = 2;
    private static final String TAG = "Kibo";

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
            new Point(10.925d, -8.35d, 4.6d),
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
            true,
            false,
            true,
            false,
            true
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

            Kinematics pose = api.getRobotKinematics();

            Mat image = api.getMatNavCam();

            Mat undistorted = new Mat();
            Calib3d.undistort(image, undistorted, cameraMatrix, cameraCoefficients);

            Log.i(TAG, "Expected quat: " + q.toString() + " | Actual: " + pose.getOrientation().toString());

            api.saveMatImage(image, i + "original.png");
            api.saveMatImage(undistorted, i + "undistorted.png");

            if (scanForObjects[i]) {
                Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
                List<Mat> corners = new ArrayList<>();
                Mat markerIds = new Mat();
                Aruco.detectMarkers(undistorted, dictionary, corners, markerIds);

                Log.i(TAG, "Corners: " + corners.get(0).toString());

                // iterate through each aruco tag and get position
                for (int j = 0; j < corners.size(); j++) {
                    Mat corner = corners.get(j);

                    //Log.i(TAG, "Rotation: " + rotationVec.toString());
                    //Log.i(TAG, "Translation: " + translationVec.toString());

                    // resize undistorted image and straighten out
                    float squareSize = 200; // Size of the square
                    Mat destinationCorners = new Mat(4, 1, CvType.CV_32FC2);
                    destinationCorners.put(0, 0, 0, 0);
                    destinationCorners.put(1, 0, squareSize, 0);
                    destinationCorners.put(2, 0, squareSize, squareSize);
                    destinationCorners.put(3, 0, 0, squareSize);

                    // Compute the perspective transformation matrix
                    Mat transformationMatrix = Imgproc.getPerspectiveTransform(corner, destinationCorners);

                    // Apply the perspective transformation
                    Mat warpedImage = new Mat();
                    Imgproc.warpPerspective(image, warpedImage, transformationMatrix, undistorted.size());

                    api.saveMatImage(warpedImage, "" + i + j + "penis.png");
                }
            }
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

    private void detectMarkers(Mat image) {
        Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
        List<Mat> corners = new ArrayList<>();
        Mat markerIds = new Mat();
        Aruco.detectMarkers(image, dictionary, corners, markerIds);

        Log.i(TAG, "Marker dimensions: " + markerIds.rows() + " rows, " + markerIds.cols() + " cols");
        Log.i(TAG, "Marker (0, 0): " + Arrays.toString(markerIds.get(0, 0)));
        Log.i(TAG, "Corners: " + corners.size());
        Log.i(TAG, "Corners[0]: " + corners.get(0));
    }

    /*private int[] detectTemplates(Mat image) {
        int[] templateMatches = new int[10];

        for (int i = 0; i < templates.length; i++) {
            List<org.opencv.core.Point> matchLocations = new ArrayList<>();

            // clone images
            Mat template = templates[i].clone();
            Mat imageToScan = image.clone();

            for (int width = WIDTH_MIN; width <= WIDTH_MAX; width += WIDTH_DELTA) {
                for (int angle = 0; angle <= 360; angle += ANGLE_DELTA) {
                    Mat resized = resizeImage(template, width);
                    Mat rotated = rotateImage(resized, angle);

                    Mat result = new Mat();
                    Imgproc.matchTemplate(imageToScan, rotated, result, Imgproc.TM_CCOEFF_NORMED);

                    Core.MinMaxLocResult mmlr = Core.minMaxLoc(result);
                    double maxScore = mmlr.maxVal;

                    if (maxScore >= DETECTION_THRESHOLD) {
                        // get results that are >= threshold
                        Mat filtered = new Mat();
                        Imgproc.threshold(result, filtered, DETECTION_THRESHOLD, 1.0, Imgproc.THRESH_TOZERO);

                        // get number of results and add to matches variable
                        for (int y = 0; y < filtered.rows(); y++) {
                            for (int x = 0; x < filtered.cols(); x++) {
                                if (filtered.get(y, x)[0] > 0) {
                                    matchLocations.add(new org.opencv.core.Point(x, y));
                                }
                            }
                        }
                    }
                }
            }

            // remove duplicate points
            List<org.opencv.core.Point> filteredMatchLocations = removeDuplicates(matchLocations);
            // add to result matches array
            templateMatches[i] = filteredMatchLocations.size();
        }

        return templateMatches;
    }*/

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
