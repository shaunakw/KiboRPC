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
 * Class meant to handle commands from the Ground Data System and execute them in Astrobee
 */

public class YourService extends KiboRpcService {
    // Pattern detection constants
    private final int WIDTH_MIN = 20;
    private final int WIDTH_MAX = 100;
    private final int WIDTH_DELTA = 5;
    private final int ANGLE_DELTA = 45;
    private final double DETECTION_THRESHOLD = 0.75;

    // Program constants
    private static final String TAG = "Kibo";
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

    private Mat cameraMatrix = new Mat(3, 3, CvType.CV_64F);
    private Mat cameraCoefficients = new Mat(1, 5, CvType.CV_64F);

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

    @Override
    protected void runPlan1() {
        // The mission starts.
        api.startMission();

        // setup
        setupCamera();
        loadTemplateImages();

        // Move to a point.
        /* Point point = new Point(10.9d, -9.92284d, 5.195d);
        Quaternion quaternion = new Quaternion(0f, 0f, -0.707f, 0.707f);
        move(point, quaternion); */

        // test moving to all points
        for (int i = 0; i < targetPoints.length; i++) {
            Point p = targetPoints[i];
            Quaternion q = targetQuats[i];

            move(p, q);
            Mat image = api.getMatNavCam();
            image = undistortImage(image);
            api.saveMatImage(image, "path_" + i);
        }

        // Get a camera image.
        // scanCamera();

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
    protected void runPlan2() { /* write your plan 2 here. */ }

    @Override
    protected void runPlan3() { /* write your plan 3 here. */ }

    private void setupCamera() {
        cameraMatrix.put(0, 0, api.getNavCamIntrinsics()[0]);
        cameraCoefficients.put(0, 0, api.getNavCamIntrinsics()[1]);
        cameraCoefficients.convertTo(cameraCoefficients, CvType.CV_64F);
    }

    private void loadTemplateImages() {
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
    }

    private void scanCamera() {
        Mat image = api.getMatNavCam();
        image = undistortImage(image);

        detectMarkers(image);
        int[] templateMatches = detectTemplates(image);

        int maxIndex = getMaxIndex(templateMatches);
        api.setAreaInfo(1, TEMPLATE_NAMES[maxIndex], templateMatches[maxIndex]);
    }

    private Mat undistortImage(Mat image) {
        Mat undistorted = new Mat();
        Calib3d.undistort(image, undistorted, cameraMatrix, cameraCoefficients);
        return undistorted;
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

    private int[] detectTemplates(Mat image) {
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
    }

    private Mat resizeImage(Mat image, int width) {
        int height = (int) (image.rows() * ((double) width / image.cols()));
        Mat resized = new Mat();
        Imgproc.resize(image, resized, new Size(width, height));

        return resized;
    }

    private Mat rotateImage(Mat image, int angle) {
        org.opencv.core.Point center = new org.opencv.core.Point(image.cols() / 2.0, image.rows() / 2.0);
        Mat rotatedMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat rotatedImage = new Mat();
        Imgproc.warpAffine(image, rotatedImage, rotatedMat, image.size());

        return rotatedImage;
    }

    private static List<org.opencv.core.Point> removeDuplicates(List<org.opencv.core.Point> points) {
        double length = 10; // 10 px
        List<org.opencv.core.Point> results = new ArrayList<>();

        for (org.opencv.core.Point point : points) {
            boolean duplicate = false;
            for (org.opencv.core.Point point1 : results) {
                double distance = calculateDistance(point, point1);

                if (distance <= length) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                results.add(point);
            }
        }

        return results;
    }

    private static double calculateDistance(org.opencv.core.Point p1, org.opencv.core.Point p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
    }

    private static int getMaxIndex(int[] array) {
        int max = 0;
        int maxIndex = 0;

        for (int i = 0; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                maxIndex = i;
            }
        }

        return maxIndex;
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
