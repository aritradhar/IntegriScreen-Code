#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>


using namespace std;
using namespace cv;

void detect_and_draw_circles(Mat &src_gray);

// Input Quadilateral or Image plane coordinates
Point2f inputQuad[4];
bool initialized = false;

extern "C"
{
void JNICALL Java_com_example_integriscreen_MainActivity_salt(JNIEnv *env, jobject instance,
                                                                           jlong matAddrGray,
                                                                           jint nbrElem) {
    Mat &mGr = *(Mat *) matAddrGray;
    for (int k = 0; k < nbrElem; k++) {
        int i = rand() % mGr.cols;
        int j = rand() % mGr.rows;
        mGr.at<uchar>(j, i) = 255;
    }
}

void JNICALL Java_com_example_integriscreen_MainActivity_apply_1median(
        JNIEnv *env, jobject instance,
        jlong matAddrGray,
        jint filterSize) {

    Mat &mGr = *(Mat *) matAddrGray;
    medianBlur(mGr, mGr, filterSize);
}

void JNICALL Java_com_example_integriscreen_MainActivity_realign_1perspective(
        JNIEnv *env, jobject instance,
        jlong inputAddr,
        jlong outputAddr)
{
    Mat &input = *(Mat *)inputAddr;
    Mat output;

    // Input Quadilateral or Image plane coordinates
    // Point2f inputQuad[4];

    // Output Quadilateral or World plane coordinates
    Point2f outputQuad[4];

    // Lambda Matrix
    Mat lambda(2, 4, CV_32FC1);

    // Set the lambda matrix the same type and size as input
    lambda = Mat::zeros(input.rows, input.cols, input.type());

    // The 4 points that select quadilateral on the input , from top-left in clockwise order
    // These four pts are the sides of the rect box used as input
    //    TODO: THESE I NEED TO COMPUTE!

    if (!initialized) {
        inputQuad[0] = Point2f(0, 100);
        inputQuad[1] = Point2f(input.cols - 50, 100);
        inputQuad[2] = Point2f(input.cols - 100, input.rows - 50);
        inputQuad[3] = Point2f(200, input.rows - 50);
        initialized = true;
    }

    // The 4 points where the mapping is to be done , from top-left in clockwise order
    // THESE ARE CONSTANT!
    outputQuad[0] = Point2f(0, 0);
    outputQuad[1] = Point2f(input.cols - 1, 0);
    outputQuad[2] = Point2f(input.cols - 1, input.rows - 1);
    outputQuad[3] = Point2f(0, input.rows - 1);


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
        jlong hueCenter) {

    Mat &inputMat = *(Mat *)matRGBAddr;
    Mat helperMat(1, 1, CV_8UC1);
    Mat originalGray(1, 1, CV_8UC1);
    cvtColor(inputMat, originalGray, COLOR_RGB2GRAY);

    // hue values need to be between 0 and 179
    int lower_hue = ( (int)hueCenter - 10 + 180 ) % 180;
    int upper_hue = ( (int)hueCenter + 10 ) % 180;

    cvtColor(inputMat, helperMat, COLOR_RGB2HSV);

    // Detect the specified color based on hueCenter
    inRange(helperMat, Scalar(lower_hue, 50, 50), Scalar(upper_hue, 255, 255), inputMat);
    // originalGray.copyTo(inputMat, inputMat);

    detect_and_draw_circles(inputMat);

//    originalGray.copyTo(inputMat);
    // return inputMat;
}

} /// end of "extern C"



void detect_and_draw_circles(Mat &src_gray)
{
    /// Reduce the noise so we avoid false circle detection
    int coef = 1;
//    pyrDown(orig_gray, src_gray, Size( src_gray.cols/coef, src_gray.rows/coef ) );

    GaussianBlur( src_gray, src_gray, Size(7, 7), 2, 2 );

    vector<Vec3f> circles;

    /// Apply the Hough Transform to find the circles
    HoughCircles( src_gray, circles, CV_HOUGH_GRADIENT, 1, src_gray.rows/14, 35, 10, 0, 0 );

    Point2f potentialPoints[4];

    int cnt_interesting = 0;
    /// Draw the circles detected
    for( size_t i = 0; i < circles.size(); i++ )
    {
        Point center(cvRound(circles[i][0] * coef), cvRound(circles[i][1] * coef));
        int radius = cvRound(circles[i][2] * coef);

        if (radius < 15 || radius > 50) continue;
        if (src_gray.at<uchar>(center.y, center.x) == 0) continue;

        // circle center
        circle( src_gray, center, 3, Scalar(0,255,0), -1, 8, 0 );
        // circle outline
        circle( src_gray, center, radius, Scalar(0,0,255), 3, 8, 0 );

        potentialPoints[cnt_interesting] = Point2f(center.y, center.x);
        ++cnt_interesting;
    }
    if (cnt_interesting == 4) {
        inputQuad[0] = potentialPoints[0];
        inputQuad[1] = potentialPoints[1];
        inputQuad[2] = potentialPoints[2];
        inputQuad[3] = potentialPoints[3];
    }
    coef = coef + 1;
}