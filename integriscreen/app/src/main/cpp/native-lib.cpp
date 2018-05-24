#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>

#define LOG_TAG "jni_debug"
#define  ALOG(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

using namespace std;
using namespace cv;

void detect_specific_color(const Mat& inputMat, Mat &outputMat, int hueCenter);
vector<Point2f> detect_rectangle_corners(const Mat &inputMat, Mat &outputMat, const vector<Point2f>& outputQuadNew);
double my_dist(Point2f A, Point2f B);
void reorder_points(vector<Point2f> &points, vector<Point2f> &outputPoints);


extern "C"
{

void JNICALL Java_com_example_integriscreen_ISImageProcessor_apply_1diff__JJJJJ(
         JNIEnv *env, jobject instance,
         jlong matAddrFirst,
         jlong matAddrSecond,
         jlong matAddrOutput,
         jlong morphSize,
         jlong downscaleFactor) {
    // When is a pixel considered black, and when white?
    // TODO: 45 seemed like a good threshold for a bit more conservative diff detection
    uchar black_white_threshold = 40;

    Mat matA, matB;
    Mat &matAFull = *(Mat *) matAddrFirst;
    Mat &matBFull = *(Mat *) matAddrSecond;

    Mat &matOutput = *(Mat *) matAddrOutput;

    Mat matDiff;

    if (downscaleFactor > 1) {
        pyrDown(matAFull, matA, Size(matAFull.cols / downscaleFactor, matAFull.rows / downscaleFactor));
        pyrDown(matBFull, matB, Size(matBFull.cols / downscaleFactor, matBFull.rows / downscaleFactor));
    } else {
        matAFull.copyTo(matA);
        matBFull.copyTo(matB);
    }

    // Compute the distance between two frames, apply a threshold.
    absdiff(matA, matB, matDiff);
    threshold(matDiff, matDiff, black_white_threshold, 255, CV_THRESH_BINARY);

    // Compute the element for morphological opening
    int opening_morph_size = morphSize;
    Mat openingElement = getStructuringElement( 2, Size( 2*opening_morph_size + 1, 2*opening_morph_size+1 ), Point( opening_morph_size, opening_morph_size ) );


    // Morphological opening
        morphologyEx( matDiff, matOutput, MORPH_OPEN, openingElement );

//    Mat matOpened;
//    morphologyEx( matDiff, matOpened, MORPH_OPEN, openingElement );
//
//    // Morphological closing removes the small black holes, but it's very slow!
//    int closing_morph_size = 2;
//    Mat closingElement = getStructuringElement( 2, Size( 2*closing_morph_size + 1, 2*closing_morph_size+1 ), Point( closing_morph_size, closing_morph_size ) );
//    morphologyEx( matOpened, matOutput, MORPH_CLOSE, closingElement );


    if (downscaleFactor > 1) {
        pyrUp(matOutput, matDiff, Size(matA.cols * downscaleFactor, matA.rows * downscaleFactor));
        matDiff.copyTo(matOutput);
    }

    // Morphological closing. It seems that we can live without it.
    // morphologyEx( blurSecond, blurSecond, MORPH_CLOSE, element );
}


void JNICALL Java_com_example_integriscreen_PerspectiveRealigner_color_1detector(
        JNIEnv *env, jobject instance,
        jlong matRGBAddr,
        jlong outputMatAddr,
        jlong hueCenter) {

    Mat &originalMat = *(Mat *) matRGBAddr;
    Mat &outputMat = *(Mat *) outputMatAddr;

    Mat colorMask;
    detect_specific_color(originalMat, colorMask, hueCenter);
    colorMask.copyTo(outputMat);
}

void JNICALL Java_com_example_integriscreen_PerspectiveRealigner_find_1rectangle_1corners(
        JNIEnv *env, jobject instance,
        jlong colorMaskAddr,
        jlong cornersAddr) {

    Mat &colorMask = *(Mat *)colorMaskAddr;
    Mat &cornersMat = *(Mat *)cornersAddr;

    // Output Quadilateral or World plane coordinates - they are usually constant
    // The 4 points where the mapping is to be done , from top-left in clockwise order
    vector<Point2f> outputQuadNew(4);
    // This stretches the across the whole frame
    outputQuadNew[0] = Point2f(0, 0);
    outputQuadNew[1] = Point2f(colorMask.cols, 0);
    outputQuadNew[2] = Point2f(colorMask.cols, colorMask.rows);
    outputQuadNew[3] = Point2f(0, colorMask.rows);

    vector<Point2f> potentialCorners;
    potentialCorners = detect_rectangle_corners(colorMask, colorMask, outputQuadNew);
    reorder_points(potentialCorners, outputQuadNew);

    for(int i = 0; i < potentialCorners.size(); ++i) {
        cornersMat.at<int>(i,0) = (int)potentialCorners[i].x;
        cornersMat.at<int>(i,1) = (int)potentialCorners[i].y;
    }
}



} /// end of "extern C"

void detect_specific_color(const Mat& inputMat, Mat &outputMat, int hueCenter)
{
    Mat hsvMat(1, 1, CV_8UC1);

    // hue values need to be between 0 and 179
    int hueCenterOpenCV = hueCenter / 2;
    int lower_hue = ( (int)hueCenterOpenCV - 10 + 180 ) % 180;
    int upper_hue = ( (int)hueCenterOpenCV + 10 ) % 180;

    // If we decide to also blur the image:
//    Mat tmpMat(1, 1, CV_8UC3);
//    GaussianBlur( inputMat, tmpMat, Size(5, 5), 3, 3 );
//    blur( inputMat, tmpMat, Size(11, 11));
//    cvtColor(tmpMat, hsvMat, COLOR_RGB2HSV);

    cvtColor(inputMat, hsvMat, COLOR_RGB2HSV);

    // Detect the specified color based on hueCenter
    inRange(hsvMat, Scalar(lower_hue, 50, 50), Scalar(upper_hue, 255, 255), outputMat);
}


double my_dist(Point2f A, Point2f B) { double dx = A.x - B.x; double dy = A.y - B.y; return dx * dx + dy * dy; }

// this function will reorder the input points so that points[0] is the one closest to output[0], etc.
void reorder_points(vector<Point2f> &points, vector<Point2f> &outputQuadsNew)
{
    for(int i = 0; i < 4; ++i) {
        int closest = i;
        for(int j = i + 1; j < 4; ++j) {
            if (my_dist(points[j], outputQuadsNew[i]) < my_dist(points[closest], outputQuadsNew[i]))
                closest = j;
        }
        swap(points[i], points[closest]);
    }
}


vector<Point2f> detect_rectangle_corners(const Mat &inputMat, Mat &outputMat, const vector<Point2f>& outputQuadNew) {
    /*
    /// Apply the specified morphology operation
    int morph_size = 1;
    Mat element = getStructuringElement( 2, Size( 2*morph_size + 1, 2*morph_size+1 ), Point( morph_size, morph_size ) );
    // Morphological opening
    morphologyEx( inputMat, outputMat, MORPH_CLOSE, element );
*/

    Mat labels;
    Mat stats;
    Mat centroids;
    int numComponents = connectedComponentsWithStats(inputMat, labels, stats, centroids, 4, CV_16U);

    /*
        CC_STAT_LEFT The leftmost (x) coordinate which is the inclusive start of the bounding box in the horizontal direction.
        CC_STAT_TOP The topmost (y) coordinate which is the inclusive start of the bounding box in the vertical direction.
        CC_STAT_WIDTH The horizontal size of the bounding box
        CC_STAT_HEIGHT The vertical size of the bounding box
        CC_STAT_AREA The total area (in pixels) of the connected component
     */

    // find the largest component by enclosing area
    int maxAreaIndex = 1;
    int maxEnclosingIndex = 0, maxEnclosingArea = 0;
    for (int i = 1; i < numComponents; ++i) { // crucial to start with 1 since 0 is the largest one?
        int currentArea = stats.at<int>(i, CC_STAT_WIDTH) * stats.at<int>(i, CC_STAT_HEIGHT);

        int width = stats.at<int>(i, CC_STAT_WIDTH);
        int height = stats.at<int>(i, CC_STAT_HEIGHT);
        if (currentArea > maxEnclosingArea) {
            maxEnclosingIndex = i;
            maxEnclosingArea = currentArea;
        }
    }

    // From the largest component, find the 4 extremal points (closest to the edges)
    // TODO: this does not work ideally when there is rotation or large angles
    vector<Point2f> points;
    for (int i = 0; i < labels.cols; ++i)
        for (int j = 0; j < labels.rows; ++j) {
            if (labels.at<uint16_t>(j, i) != maxEnclosingIndex)
                continue;

            Point2i currentPoint(i, j);

            if (points.size() == 0)
                points = vector<Point2f>(4, currentPoint);

            for (int k = 0; k < 4; ++k)
                if (my_dist(currentPoint, outputQuadNew[k]) < my_dist(points[k], outputQuadNew[k]) )
                    points[k] = currentPoint;
        }

    // This is just to showcase what I am finding
    labels.convertTo(outputMat, CV_8UC1, 50.0);

    for(int i = 0; i < 4; ++i) {
        // Draw circle center
        circle( outputMat, points[i], 3, Scalar(255,255,255), -1, 8, 0 );
        // Draw circle outline
        circle( outputMat, points[i], 10, Scalar(255,255,255), 3, 8, 0 );

    }

    return points;
}

vector<Point2f> detect_circles(const Mat &inputMat, Mat &outputMat)
{
    /// Reduce the noise so we avoid false circle detection
    GaussianBlur( inputMat, outputMat, Size(7, 7), 2, 2 );

    vector<Vec3f> circles;

    /// Apply the Hough Transform to find the circles
    HoughCircles( outputMat, circles, CV_HOUGH_GRADIENT, 1, outputMat.rows/14, 35, 10, 0, 0 );

    vector<Point2f> interestingCenters;
    /// Draw the circles detected
    for( size_t i = 0; i < circles.size(); i++ )
    {
        Point center(cvRound(circles[i][0]), cvRound(circles[i][1]));
        int radius = cvRound(circles[i][2]);

        if (radius < 15 || radius > 50) continue;
        if (outputMat.at<uchar>(center.y, center.x) == 0) continue;

        // Draw circle center
        circle( outputMat, center, 3, Scalar(0,255,0), -1, 8, 0 );
        // Draw circle outline
        circle( outputMat, center, radius, Scalar(0,0,255), 3, 8, 0 );

        interestingCenters.push_back(center);
    }

    return interestingCenters;
}
