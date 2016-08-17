package com.example.litingting.cameraandclipimage.helper;

import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.example.litingting.cameraandclipimage.R;

/**
 * 相机操作功能类。
 *
 * Created by wzxx on 16/8/16.
 *
 * 单例模式统一管理相机资源，封装相机API的直接调用，并提供用于跟自定义相机activity做ui交互的回调接口。
 */
public class CameraOperationHelper {

    private static final String TAG="wzxx.CameraOperationHelper";
    private static CameraOperationHelper myCameraOperation=null;
    private Camera mCamera;

    public CameraOperationHelper(){

    }
    public static synchronized CameraOperationHelper getInstance(){//单例模式
        if (myCameraOperation==null){
            myCameraOperation=new CameraOperationHelper();
            return myCameraOperation;
        }
        else {
            return myCameraOperation;
        }
    }

    public Camera doGetCameraInstance(int mCameraId){
         if (mCamera!=null){
             return mCamera;
         }
        else {
             return null;
         }
    }

    /**
     * 打开摄像头
     *
     * @param callback
     * @param holder
     */
    public void doOpenCamera(CameraOverCallback callback, SurfaceHolder holder){

    }

    public interface CameraOverCallback{

//        void cameraRateChanged(boolean isDefaultRatio);

//        void cameraFlashModeChanged(int flashMode);

//        void cameraFacingChanged(boolean hasFrontCamera,int cameraId);

        void cameraPhotoTaken(String path);

//        void cameraErrorReport(String path);
    }
}


