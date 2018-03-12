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

void JNICALL Java_ch_hepia_iti_opencvnativeandroidstudio_MainActivity_apply_1median(JNIEnv *env, jobject instance,
                                                                                   jlong matAddrGray,
                                                                                   jint filterSize) {
    Mat &mGr = *(Mat *) matAddrGray;
    medianBlur(mGr, mGr, filterSize);
}

void JNICALL Java_ch_hepia_iti_opencvnativeandroidstudio_MainActivity_compute_1diff(JNIEnv *env,
                                                                                     jobject instance,
                                                                                     jlong matAddrFirst,
                                                                                     jlong matAddrSecond,
                                                                                     jlong matAddrDiff) {
    Mat &matFirst = *(Mat *) matAddrFirst;
    Mat &matSecond = *(Mat *) matAddrSecond;
    Mat &matDiff = *(Mat *) matAddrDiff;

    Mat blurFirst(matFirst.size(), CV_8UC1);
    Mat blurSecond(matFirst.size(), CV_8UC1);

    // medianBlur(matFirst, blurFirst, 9);
    // medianBlur(matSecond, blurSecond, 9);

    blur( matFirst, blurFirst, Size(3,3) );
    blur( matSecond, blurSecond, Size(3,3) );

    for(int i = 0; i < blurFirst.rows; ++i)
        for(int j = 0; j < blurFirst.cols; ++j) {
//             int int_diff = ((int)blurFirst.at<uchar>(i, j)) - blurSecond.at<uchar>(i, j);
//             if (abs(int_diff) < 50)
//                 int_diff = 0;
//             else
//                 int_diff = 255;   //    ako razlika < 0 stavi 0, inace 255?
//             matDiff.at<uchar>(i, j) = (uchar)int_diff;

            matDiff.at<uchar>(i, j) = blurFirst.at<uchar>(i, j) - blurSecond.at<uchar>(i, j);
        }

/*
    % morfolosko zatvaranje
    se = strel('disk', 7);
    img(:,:,n) = imclose(img(:,:,n), se);

    % morfolosko otvaranje, izbacujem male crne likove
    img(:,:,n) = ~bwareaopen(~img(:,:,n), 500);
*/

    /// Apply the specified morphology operation
    int morph_size = 3;
    Mat element = getStructuringElement( 2, Size( 2*morph_size + 1, 2*morph_size+1 ), Point( morph_size, morph_size ) );

    // Morphological opening
    morphologyEx( matDiff, matDiff, MORPH_OPEN, element );

    // Morphological closing
    morphologyEx( matDiff, matDiff, MORPH_CLOSE, element );
}
}
