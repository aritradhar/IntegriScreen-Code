#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>


using namespace std;
using namespace cv;

extern "C"
{
void JNICALL Java_ch_hepia_iti_opencvnativeandroidstudio_MainActivity_salt(JNIEnv *env, jobject instance,
                                                                           jlong matAddrGray,
                                                                           jint nbrElem) {
    Mat &mGr = *(Mat *) matAddrGray;
    for (int k = 0; k < nbrElem; k++) {
        int i = rand() % mGr.cols;
        int j = rand() % mGr.rows;
        mGr.at<uchar>(j, i) = 255;
    }
}

void JNICALL Java_ch_hepia_iti_opencvnativeandroidstudio_MainActivity_apply_1median(
        JNIEnv *env, jobject instance,
        jlong matAddrGray,
        jint filterSize) {

    Mat &mGr = *(Mat *) matAddrGray;
    medianBlur(mGr, mGr, filterSize);
}

void JNICALL Java_ch_hepia_iti_opencvnativeandroidstudio_MainActivity_realign_1perspective(
        JNIEnv *env, jobject instance,
        jlong inputAddr,
        jlong outputAddr) {
    // TODO: convert to normal Android calls?

    Mat input = *(Mat *) inputAddr;
    Mat output = *(Mat *) outputAddr;

    // Input Quadilateral or Image plane coordinates
    Point2f inputQuad[4];
    // Output Quadilateral or World plane coordinates
    Point2f outputQuad[4];

    // Lambda Matrix
    Mat lambda(2, 4, CV_32FC1);

    // Set the lambda matrix the same type and size as input
    lambda = Mat::zeros(input.rows, input.cols, input.type());

    // The 4 points that select quadilateral on the input , from top-left in clockwise order
    // These four pts are the sides of the rect box used as input
    //    TODO: THESE I NEED TO COMPUTE!
    inputQuad[0] = Point2f(-30, -60);
    inputQuad[1] = Point2f(input.cols + 50, -50);
    inputQuad[2] = Point2f(input.cols + 100, input.rows + 50);
    inputQuad[3] = Point2f(-50, input.rows + 50);

    // The 4 points where the mapping is to be done , from top-left in clockwise order
    // THESE ARE CONSTANT!
    outputQuad[0] = Point2f(0, 0);
    outputQuad[1] = Point2f(input.cols - 1, 0);
    outputQuad[2] = Point2f(input.cols - 1, input.rows - 1);
    outputQuad[3] = Point2f(0, input.rows - 1);


    // Get the Perspective Transform Matrix i.e. lambda
    lambda = getPerspectiveTransform(inputQuad, outputQuad);
    // Apply the Perspective Transform just found to the src image
    warpPerspective(input, output, lambda, output.size());
}

void JNICALL Java_ch_hepia_iti_opencvnativeandroidstudio_MainActivity_compute_1diff(
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

    // Two tables that I'll later need
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


void JNICALL Java_ch_hepia_iti_opencvnativeandroidstudio_MainActivity_compute_1aruco(
        JNIEnv *env, jobject instance,
        jlong matGrayAddr) {

    /*
    std::vector<int> ids;
    std::vector<std::vector<cv::Point2f> > corners;
    cv::aruco::detectMarkers(image, dictionary, corners, ids);

    // if at least one marker detected
    if (ids.size() > 0)
        cv::aruco::drawDetectedMarkers(imageCopy, corners, ids);
*/

    
    /*
    aruco::MarkerDetector MDetector;
//detect
    std::vector<aruco::Marker> markers = MDetector.detect(image);
//print info to console
    for (size_t i = 0; i < markers.size(); i++)
        std::cout << markers[i] << std::endl;
//draw in the image
    for (size_t i = 0; i < markers.size(); i++)
        markers[i].draw(image);

     */
}
}
