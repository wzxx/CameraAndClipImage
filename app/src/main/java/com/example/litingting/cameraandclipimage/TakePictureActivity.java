package com.example.litingting.cameraandclipimage;

import android.app.Activity;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.litingting.cameraandclipimage.helper.CameraOperationHelper;
import com.example.litingting.cameraandclipimage.helper.PhotoActionHelper;
import com.example.litingting.cameraandclipimage.interfacePack.Const;
import com.example.litingting.cameraandclipimage.utils.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * 拍摄的界面
 *
 * Created by wzxx on 16/8/15.
 *
 * @author wzxx
 * @version 1.0
 * @since 2016-08-15
 */
public class TakePictureActivity extends Activity implements SurfaceHolder.Callback,View.OnClickListener {

    private final String TAG=".TakePictureActivity";

    private SurfaceView mSurfaceView;
    private View mViewfinder;
    private View mCancel;
    private View mTakePhoto;
    private View mCameraRotate;

    private boolean mWaitForTakePhoto;
    private boolean mIsSurfaceReady;

    private Camera.Size mBestPictureSize;
    private Camera.Size mBestPreviewSize;

    @Nullable
    private Camera mCamera;

    private String mOutput;

    private int mCameraDirection=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_take_photo);
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mViewfinder = findViewById(R.id.viewfinder);
        mCancel = findViewById(R.id.cancel);
        mTakePhoto = findViewById(R.id.take_photo);
        mCameraRotate=findViewById(R.id.camera_rotate);

        mViewfinder.setOnClickListener(this);
//        mSurfaceView.setOnClickListener(this);
        mCancel.setOnClickListener(this);
        mTakePhoto.setOnClickListener(this);
        mCameraRotate.setOnClickListener(this);

        SurfaceHolder holder = mSurfaceView.getHolder();//SurfaceHolder用于控制surface，监视其变化
        holder.addCallback(this);

        mOutput = PhotoActionHelper.getOutputPath(getIntent());
    }

    /**
     * 打开摄像头
     */
    private void openCamera() {
        if (mCamera == null) {//如果刚打开此应用程序，camera还未打开
            try {
                mCamera = Camera.open();//打开摄像头
            } catch (RuntimeException e) {
                if ("Fail to connect to camera service".equals(e.getMessage())) {
                    Toast.makeText(this, R.string.msg_camera_invalid_permission_denied, Toast.LENGTH_SHORT).show();
                } else if ("Camera initialization failed".equals(e.getMessage())) {
                    Toast.makeText(this, R.string.msg_camera_invalid_initial_failed, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.msg_camera_invalid_unknown_error, Toast.LENGTH_SHORT).show();
                }
                /**
                 * finish ()：
                 * 当你调用此方法的时候，系统只是将最上面的Activity移出了栈，并没有及时的调用onDestory（）方法，
                 * 其占用的资源也没有被及时释放。因为移出了栈，所以当你点击手机上面的“back”按键的时候，也不会找到这个Activity。
                 */
                finish();
                return;
            }
        }

        final Camera.Parameters cameraParams = mCamera.getParameters();//获取摄像头的参数
        cameraParams.setPictureFormat(ImageFormat.JPEG);//摄像头获取的图片是JPEG形式的
        cameraParams.setRotation(90);//设置摄像头的屏幕方向：竖直
        cameraParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);//摄像头自动对焦
        mCamera.setParameters(cameraParams);//将改变的都设置好

        /**
         * 图片显示变形是因为比例错误。所以surfaceview和cameraSize的比例应该要一致
         */
        // 短边比长边
        final float ratio = (float) mSurfaceView.getWidth() / mSurfaceView.getHeight();

        // 设置pictureSize，因为camera还有一个参数叫做pictureSize
        List<Camera.Size> pictureSizes = cameraParams.getSupportedPictureSizes();//camera支持的相片大小，包含所有支持的尺寸
        /*看下有什么支持的尺寸*/
        for (int i=0;i<pictureSizes.size();i++){
            Log.i(TAG,"PictureSize Supported Size:"+pictureSizes.get(i).width+"height:"+pictureSizes.get(i).width);
        }
        if (mBestPictureSize == null) {
            mBestPictureSize =findBestPictureSize(pictureSizes, cameraParams.getPictureSize(), ratio);//找到最佳的相片大小
        }
        final Camera.Size pictureSize = mBestPictureSize;
        cameraParams.setPictureSize(pictureSize.width, pictureSize.height);

        // 设置previewSize，因为camera还有一个参数叫做previewSize
        List<Camera.Size> previewSizes = cameraParams.getSupportedPreviewSizes();
        if (mBestPreviewSize == null) {
            mBestPreviewSize = findBestPreviewSize(previewSizes, cameraParams.getPreviewSize(),
                    pictureSize, ratio);
        }
        final Camera.Size previewSize = mBestPreviewSize;
        cameraParams.setPreviewSize(previewSize.width, previewSize.height);

        setSurfaceViewSize(previewSize);

        try {
            mCamera.setParameters(cameraParams);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        if (mIsSurfaceReady) {
            startPreview();//开始预览
        }
    }

    /**
     * 设置SurfaceView尺寸，这里着重在高度
     *
     * @param size
     */
    private void setSurfaceViewSize(Camera.Size size) {
        ViewGroup.LayoutParams params = mSurfaceView.getLayoutParams();
        params.height = mSurfaceView.getWidth() * size.width / size.height;
        mSurfaceView.setLayoutParams(params);
    }

    /**
     * 找到短边比长边大于于所接受的最小比例的最大尺寸
     *
     * @param sizes       支持的尺寸列表
     * @param defaultSize 默认大小
     * @param minRatio    相机图片短边比长边所接受的最小比例
     * @return 返回计算之后的尺寸
     */
    private Camera.Size findBestPictureSize(List<Camera.Size> sizes, Camera.Size defaultSize, float minRatio) {
        final int MIN_PIXELS = 320 * 480;

        sortSizes(sizes);//对camera所支持的相片尺寸进行排序

        Iterator<Camera.Size> it = sizes.iterator();//将list中的尺寸都保存在迭代器中
        while (it.hasNext()) {//遍历序列中的元素
            Camera.Size size = it.next();//获得序列中元素
            //移除不满足比例的尺寸
            if ((float) size.height / size.width <= minRatio) {
                it.remove();
                continue;
            }
            /**
             * 终于发现为什么上面的才可以，而下面的不可以移除掉不满足比例的了。
             *
             * 无论是PreviewSize还是PictureSize，前者是相机预览图像，后者是相机拍摄图像，都是直接从相机传感器接受数据。
             * 而相机传感器方向和屏幕方向相差刚好90度，所以要size.height / size.width和surfaceview的width/height比较
             */
//            /*修改. 移除不满足比例的尺寸*/
//            if ((float) size.width / size.height <= minRatio) {//当短边比长边小于最小的比例
//                it.remove();
//                continue;
//            }
            //移除太小的尺寸
            if (size.width * size.height < MIN_PIXELS) {
                it.remove();
            }
        }

        // 返回符合条件中最大尺寸的一个
        if (!sizes.isEmpty()) {
            return sizes.get(0);
        }
        // 没得选，默认吧
        return defaultSize;
    }

    /**
     * @param sizes
     * @param defaultSize
     * @param pictureSize 图片的大小
     * @param minRatio preview短边比长边所接受的最小比例
     * @return
     */
    private Camera.Size findBestPreviewSize(List<Camera.Size> sizes, Camera.Size defaultSize,
                                            Camera.Size pictureSize, float minRatio) {
        final int pictureWidth = pictureSize.width;
        final int pictureHeight = pictureSize.height;
        boolean isBestSize = (pictureHeight / (float)pictureWidth) > minRatio;
        sortSizes(sizes);

        Iterator<Camera.Size> it = sizes.iterator();
        while (it.hasNext()) {
            Camera.Size size = it.next();
            if ((float) size.height / size.width <= minRatio) {
                it.remove();
                continue;
            }

            // 找到同样的比例，直接返回
            if (isBestSize && size.width * pictureHeight == size.height * pictureWidth) {
                return size;
            }
        }

        // 未找到同样的比例的，返回尺寸最大的
        if (!sizes.isEmpty()) {
            return sizes.get(0);
        }

        // 没得选，默认吧
        return defaultSize;
    }

    /**
     * 对camera支持的所有相片尺寸进行比较，并排序
     *
     * @param sizes
     */
    private static void sortSizes(List<Camera.Size> sizes) {
        Collections.sort(sizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                return b.height * b.width - a.height * a.width;
            }
        });
    }

    private void startPreview() {
        if (mCamera == null) {
            return;
        }
        try {
            mCamera.setPreviewDisplay(mSurfaceView.getHolder());
            mCamera.setDisplayOrientation(90);//主要是为了得到正确的预览画面，保持屏幕方向和相机传感器方向一致
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    private void closeCamera() {
        if (mCamera == null) {
            return;
        }
        mCamera.cancelAutoFocus();
        stopPreview();
        mCamera.release();
        mCamera = null;
    }

    /**
     * 请求自动对焦
     */
    private void requestFocus() {
        if (mCamera == null || mWaitForTakePhoto) {
            return;
        }
        mCamera.autoFocus(null);
    }

    /**
     * 拍照
     */
    private void takePhoto() {
        if (mCamera == null || mWaitForTakePhoto) {
            return;
        }
        mWaitForTakePhoto = true;
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                onTakePhoto(data);
                mWaitForTakePhoto = false;
            }
        });
    }

    private void onTakePhoto(byte[] data) {
        final String tempPath = mOutput + "_";
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tempPath);
            fos.write(data);
            fos.flush();
            PhotoActionHelper.clipImage(this)
                    .extra(getIntent())
                    .output(mOutput).input(tempPath)
                    .requestCode(Const.REQUEST_CLIP_IMAGE).start();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(fos);

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSurfaceView.post(new Runnable() {
            @Override
            public void run() {//新开一个线程用于打开摄像头
                openCamera();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mIsSurfaceReady = true;
        startPreview();

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mIsSurfaceReady = false;
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        switch (id) {
//            case R.id.viewfinder:
//                requestFocus();
//                break;
            case R.id.surface_view:
                requestFocus();
                break;
            case R.id.cancel:
                cancelAndExit();
                break;
            case R.id.take_photo:
                takePhoto();
                break;
            case R.id.camera_rotate://镜头转换
                switchCamera();
                break;
            default:// do nothing
        }
    }

    /**
     * 按back键时的反应
     */
    @Override
    public void onBackPressed() {
        cancelAndExit();
    }

    /**
     * android就会自动调用Activity的finish()方法，设置resultCode为RESULT_CANCELED，也就不会返回任何数据了 .
     */
    private void cancelAndExit() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    /**
     * 上一级activity返回时传递数据
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Const.REQUEST_CLIP_IMAGE) {
            String tempPath = PhotoActionHelper.getInputPath(data);
            if (tempPath != null) {
                new File(tempPath).delete();
            }
            if (resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK, data);
                finish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void switchCamera(){

    }
}
