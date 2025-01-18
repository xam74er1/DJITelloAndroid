package fr.xam74er1.trellodrone.services;

import org.opencv.core.Mat;

public interface GenericServiceCallBackListener {
    void onCallBack(Mat frame);
}
