#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>


using namespace std;
using namespace cv;

void initialize_default_quads(int rows, int cols);
void detect_specific_color(const Mat& inputMat, Mat &outputMat, int hueCenter);
bool update_transformation(vector<Point2i> potentialPoints, int rows, int cols);
vector<Point2i> detect_circles(const Mat &inputMat, Mat &outputMat);
vector<Point2i> detect_rectangle_corners(const Mat &inputMat, Mat &outputMat);
double my_dist(Point2f A, Point2f B);
void reorder_points(vector<Point2f> &points);

// The 4 points that select quadilateral on the input , from top-left in clockwise order
// These four pts are the sides of the rect box used as input
// Input Quadilateral or Image plane coordinates
vector<Point2f> inputQuad(4);

// Output Quadilateral or World plane coordinates - they are usually constant
// The 4 points where the mapping is to be done , from top-left in clockwise order
vector<Point2f> outputQuad(4);

bool quadsInitialized = false;

extern "C"
{

void JNICALL Java_com_example_integriscreen_MainActivity_realign_1perspective(
        JNIEnv *env, jobject instance,
        jlong inputAddr,
        jlong outputAddr)
{
    Mat &input = *(Mat *)inputAddr;
    Mat output;

    // Lambda Matrix
    Mat lambda(2, 4, CV_32FC1);

    // Set the lambda matrix the same type and size as input
    lambda = Mat::zeros(input.rows, input.cols, input.type());

    if (!quadsInitialized)
        initialize_default_quads(input.rows, input.cols);

    // Get the Perspective Transform Matrix i.e. lambda
    lambda = getPerspectiveTransform(inputQuad, outputQuad);

    // Apply the Perspective Transform that I just computed to the src image
    warpPerspective(input, output, lambda, input.size());
    output.copyTo(input);
}

void JNICALL Java_com_example_integriscreen_MainActivity_compute_1diff(
         JNIEnv *env, jobject instance,
         jlong matAddrFirst,
         jlong matAddrSecond,
         jlong matAddrOutput)   // used for output as well
{
    // When is a pixel considered black, and when white?
    uchar black_white_threshold = 30;

    Mat &matFirst = *(Mat *) matAddrFirst;
    Mat &matSecond = *(Mat *) matAddrSecond;
    Mat &matOutput = *(Mat *) matAddrOutput;

    // Two tables that I'll need later
    Mat blurFirst(1, 1, CV_8UC1);
    Mat blurSecond(1, 1, CV_8UC1);

    // ---- Different tests ----
    // medianBlur(matFirst, blurFirst, 9);
    // medianBlur(matSecond, blurSecond, 9);

    // blur( matFirst, blurFirst, Size(3,3) );
    // blur( matSecond, blurSecond, Size(3,3) );

    bool should_subsample = false;
    if (should_subsample) {
        pyrDown(matFirst, blurFirst, Size(matOutput.cols / 2, matOutput.rows / 2));
        pyrDown(matSecond, blurSecond, Size(matSecond.cols / 2, matSecond.rows / 2));
    }
    else {
        blurFirst = matFirst;
        blurSecond = matSecond;
    }

    // Compute the distance between two frames, apply a threshold.
    absdiff(blurFirst, blurSecond, blurSecond);
    threshold(blurSecond, blurSecond, black_white_threshold, 255, CV_THRESH_BINARY);

    /// Apply the specified morphology operation
    int morph_size = 1;
    Mat element = getStructuringElement( 2, Size( 2*morph_size + 1, 2*morph_size+1 ), Point( morph_size, morph_size ) );

    // Morphological opening
    morphologyEx( blurSecond, matOutput, MORPH_OPEN, element );

    // Morphological closing. It seems that we can live without it.
    // morphologyEx( blurSecond, blurSecond, MORPH_CLOSE, element );

    if (should_subsample)
        resize(matOutput, matOutput, matFirst.size(),0,0,INTER_LINEAR);
}


void JNICALL Java_com_example_integriscreen_MainActivity_color_1detector(
        JNIEnv *env, jobject instance,
        jlong matRGBAddr,
        jlong hueCenter,
        jlong detectionMethod) {

    Mat &originalMat = *(Mat *)matRGBAddr;

    Mat colorMask;
    detect_specific_color(originalMat, colorMask, hueCenter);

    if (!quadsInitialized)   // For now, set them to some mock values if I have never set them before
        initialize_default_quads(originalMat.rows, originalMat.cols);

    vector<Point2i> potentialCorners;
    switch (detectionMethod) {
        case 0: { // do nothing else
            colorMask.copyTo(originalMat);
            break;
        }
        case 1: { // Rectangle
            potentialCorners = detect_rectangle_corners(colorMask, originalMat);
            break;
        }
        case 2: { // Circle
            potentialCorners = detect_circles(colorMask, originalMat);
            break;
        }
    }

    update_transformation(potentialCorners, originalMat.rows, originalMat.cols);
}

} /// end of "extern C"

void detect_specific_color(const Mat& inputMat, Mat &outputMat, int hueCenter)
{
    Mat hsvMat(1, 1, CV_8UC1);

    // hue values need to be between 0 and 179
    int lower_hue = ( (int)hueCenter - 10 + 180 ) % 180;
    int upper_hue = ( (int)hueCenter + 10 ) % 180;

    cvtColor(inputMat, hsvMat, COLOR_RGB2HSV);

    // Detect the specified color based on hueCenter
    inRange(hsvMat, Scalar(lower_hue, 50, 50), Scalar(upper_hue, 255, 255), outputMat);
}


double my_dist(Point2f A, Point2f B) { double dx = A.x - B.x; double dy = A.y - B.y; return dx * dx + dy * dy; }

// this function will reorder the input points so that points[0] is the one closest to output[0], etc.
void reorder_points(vector<Point2i> &points)
{
    for(int i = 0; i < 4; ++i) {
        int closest = i;
        for(int j = i + 1; j < 4; ++j) {
            if (my_dist(points[j], outputQuad[i]) < my_dist(points[closest], outputQuad[i]))
                closest = j;
        }
        swap(points[i], points[closest]);
    }
}

void initialize_default_quads(int rows, int cols)
{
    // This is some random default...
    inputQuad[0] = Point2f(0, 100);
    inputQuad[1] = Point2f(cols - 50, 100);
    inputQuad[2] = Point2f(cols - 100, rows - 50);
    inputQuad[3] = Point2f(200, rows - 50);

    // This stretches the across the whole screen for now
    outputQuad[0] = Point2f(0, 0);
    outputQuad[1] = Point2f(cols - 1, 0);
    outputQuad[2] = Point2f(cols - 1, rows - 1);
    outputQuad[3] = Point2f(0, rows - 1);

    quadsInitialized = true;
}




bool update_transformation(vector<Point2i> potentialPoints, int rows, int cols) {
    // The part that updates the 4 coordinates!
    if ((int)potentialPoints.size() == 4) {
        reorder_points(potentialPoints);
        for(int i = 0; i < 4; ++i ) inputQuad[i] = potentialPoints[i];
        return true;
    }

    return false;
}

vector<Point2i> detect_rectangle_corners(const Mat &inputMat, Mat &outputMat) {
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
    vector<Point2i> points;
    for (int i = 0; i < labels.cols; ++i)
        for (int j = 0; j < labels.rows; ++j) {
            if (labels.at<uint16_t>(j, i) != maxEnclosingIndex)
                continue;

            Point2i currentPoint(i, j);

            if (points.size() == 0)
                points = vector<Point2i>(4, currentPoint);

            for (int k = 0; k < 4; ++k)
                if (my_dist(currentPoint, outputQuad[k]) < my_dist(points[k], outputQuad[k]) )
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

vector<Point2i> detect_circles(const Mat &inputMat, Mat &outputMat)
{
    /// Reduce the noise so we avoid false circle detection
    GaussianBlur( inputMat, outputMat, Size(7, 7), 2, 2 );

    vector<Vec3f> circles;

    /// Apply the Hough Transform to find the circles
    HoughCircles( outputMat, circles, CV_HOUGH_GRADIENT, 1, outputMat.rows/14, 35, 10, 0, 0 );

    vector<Point2i> interestingCenters;
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
