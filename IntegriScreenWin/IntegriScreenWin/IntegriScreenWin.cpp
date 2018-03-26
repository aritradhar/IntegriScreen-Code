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

int main()
{
	// detect_green(imread("./data/Test_img.png"));
	process_video("./data/VID_20180312_171608.3gp");	
	waitKey(0);
    return 0;
}

