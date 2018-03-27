// IntegriScreenWin.cpp : Defines the entry point for the console application.

#include "stdafx.h"
#include <opencv2/opencv.hpp>
#include <iostream>

using namespace std;
using namespace cv;

Mat onCameraFrame(Mat inputFrame);

// This allows us to simulate that the video is actually camera input.
void process_video(string filename)
{
	VideoCapture capture(filename);
	printf("%s\n", filename.c_str());

	Mat frame;
	
	if (!capture.isOpened())
		throw "Error when reading steam_avi";

	namedWindow("w", 1);
	for (; ; )
	{
		capture >> frame;
		if (frame.empty())
			break;

		Mat frameToShow = onCameraFrame(frame);
		imshow("w", frameToShow);

		waitKey(20); // waits to display frame
	}
	waitKey(0); // key press to close window
}

Mat convertToGrayscale(Mat inputFrame)
{
	cvtColor(inputFrame, inputFrame, COLOR_RGB2GRAY);
	return inputFrame;
}



Mat detect_green(Mat inputMat)
{
	// inputMat = imread("IMG_20180314_141502.jpg");
	
	// imshow("win1", inputMat);

	Mat helperMat(1, 1, CV_8UC1);
	cvtColor(inputMat, helperMat, COLOR_RGB2HSV);
	// imshow("win2", helperMat);

	// 65 to 75
	// The next line thresholds only a specific hue
	inRange(helperMat, Scalar(65, 0, 0), Scalar(75, 255, 255), inputMat);
	helperMat.copyTo(inputMat);

	return inputMat;
}


Mat onCameraFrame(Mat newFrame)
{
	// return convertToGrayscale(newFrame);
	return detect_green(newFrame);
}

void detect_and_draw_circles(Mat &src_gray, Mat &colorMat)
{
	/// Reduce the noise so we avoid false circle detection
	imshow("before_blur", src_gray); 
	GaussianBlur( src_gray, src_gray, Size(9, 9), 2, 2 );
	imshow("after_blur", src_gray);

	vector<Vec3f> circles;

	/// Apply the Hough Transform to find the circles
	HoughCircles(src_gray, circles, CV_HOUGH_GRADIENT, 1, src_gray.rows / 8, 70, 50, 0, 0);

	printf("%d\n", (int)circles.size());

	/// Draw the circles detected
	for (size_t i = 0; i < circles.size(); i++)
	{
		Point center(cvRound(circles[i][0]), cvRound(circles[i][1]));
		int radius = cvRound(circles[i][2]);
		// circle center
		circle(colorMat, center, 3, Scalar(0, 255, 0), -1, 8, 0);
		// circle outline
		circle(colorMat, center, radius, Scalar(0, 0, 255), 3, 8, 0);
	}
	imshow("final", colorMat);
}

void circles_detect(Mat colorMat, int hueCenter)
{		
	Mat helperMat(1, 1, CV_8UC1);
	Mat inputMat(1, 1, CV_8UC1);

	//imshow("a", colorMat);

	// hue values need to be between 0 and 179
	int lower_hue = ((int)hueCenter - 20 + 180) % 180;
	int upper_hue = ((int)hueCenter + 20) % 180;

	cvtColor(colorMat, helperMat, COLOR_RGB2HSV);

	// This is detecting blue color at the moment
	inRange(helperMat, Scalar(lower_hue, 0, 0), Scalar(upper_hue, 255, 255), inputMat);

	char buff[10];
	sprintf_s(buff, sizeof(buff), "_%d", hueCenter);

	imshow("before_thresholded" + string(buff), inputMat);
	threshold(inputMat, inputMat, 230, 255, CV_THRESH_BINARY_INV);

	imshow("thresholded" + string(buff), inputMat);


	detect_and_draw_circles(inputMat, colorMat);
}


int main()
{
	// detect_green(imread("./data/Test_img.png"));	
	circles_detect(imread("./data/test_form.PNG"), 120);	

	// process_video("./data/VID_20180312_171608.3gp");	
	waitKey(0);
    return 0;
}

