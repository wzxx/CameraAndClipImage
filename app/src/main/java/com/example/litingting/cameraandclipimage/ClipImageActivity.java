package com.example.litingting.cameraandclipimage;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.clipimage.ClipImageView;
import com.example.litingting.cameraandclipimage.helper.PhotoActionHelper;
import com.example.litingting.cameraandclipimage.utils.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 裁剪图片
 *
 * Created by wzxx on 16/8/15.
 *
 * @author wzxx
 * @version 1.0
 * @since 2016-08-15
 */
public class ClipImageActivity extends Activity implements View.OnClickListener{

    private ClipImageView mClipImageView;
    private TextView mCancel;
    private TextView mClip;

//    private ViewPager mViewPager;

    private String mOutput;
    private String mInput;
    private int mMaxWidth;

    // 图片被旋转的角度
    private int mDegree;
    // 大图被设置之前的缩放比例
    private int mSampleSize;
    private int mSourceWidth;
    private int mSourceHeight;

    private ProgressDialog mDialog;

    @Override
    protected void onCreate(Bundle savedIndstanceState){
        super.onCreate(savedIndstanceState);
        this.setContentView(R.layout.activity_clip_image);
        mClipImageView=(ClipImageView) findViewById(R.id.clip_image_view);
        mCancel = (TextView) findViewById(R.id.cancel);
        mClip = (TextView) findViewById(R.id.clip);

        mCancel.setOnClickListener(this);
        mClip.setOnClickListener(this);

        final Intent data=getIntent();
        mOutput = PhotoActionHelper.getOutputPath(data);
        mInput = PhotoActionHelper.getInputPath(data);
        mMaxWidth = PhotoActionHelper.getMaxOutputWidth(data);

//        setImageAndClipParams(); //大图裁剪
        mClipImageView.setImageURI(Uri.fromFile(new File(mInput)));
        mDialog = new ProgressDialog(this);
        mDialog.setMessage(getString(R.string.msg_clipping_image));
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        switch (id) {
            case R.id.cancel:
                onBackPressed();
                break;
            case R.id.clip:
//                clipImage();
                mClipImageView.clip();
                break;
            default:// do nothing
        }
    }

    /**
     * 直接返回，不带数据
     */
    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED, getIntent());
        super.onBackPressed();
    }

    /***********************************************内部调用函数**********************************************/


    /**
     * 1.大图的处理，缩放到裁剪框的大小。
     * 2.对有些系统返回旋转过的图片进行处理。
     */
    private void setImageAndClipParams(){
        mClipImageView.post(new Runnable() {//增加新线程
            @Override
            public void run() {
                mClipImageView.setMaxOutputWidth(mMaxWidth);

                mDegree=readPictureDegree(mInput);//读取图片旋转的角度

                final boolean isRotate = (mDegree == 90 || mDegree == 270);//假如是90度或者270度就是旋转了

                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(mInput, options);

                mSourceWidth = options.outWidth;
                mSourceHeight = options.outHeight;

                // 如果图片被旋转，则宽高度置换
                int w = isRotate ? options.outHeight : options.outWidth;

                // 裁剪是宽高比例3:2，只考虑宽度情况，这里按border宽度的两倍来计算缩放。
                mSampleSize = findBestSample(w, mClipImageView.getClipBorder().width());

                options.inJustDecodeBounds = false;
                options.inSampleSize = mSampleSize;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                final Bitmap source = BitmapFactory.decodeFile(mInput, options);

                // 解决图片被旋转的问题
                Bitmap target;
                if (mDegree == 0) {
                    target = source;
                } else {
                    final Matrix matrix = new Matrix();
                    matrix.postRotate(mDegree);
                    target = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, false);
                    if (target != source && !source.isRecycled()) {
                        source.recycle();
                    }
                }
                mClipImageView.setImageBitmap(target);
            }
        });
    }

    /**
     * 读取图片属性：旋转的角度
     *
     * @param path 图片绝对路径
     * @return degree旋转的角度
     */
    public static int readPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    /**
     * 计算最好的采样大小。
     *
     * @param origin 当前宽度
     * @param target 限定宽度
     * @return sampleSize
     */
    private static int findBestSample(int origin, int target) {
        int sample = 1;
        for (int out = origin / 2; out > target; out /= 2) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * 异步消息执行裁剪（开启新线程）。
     */
    private void clipImage() {
        if (mOutput != null) {
            mDialog.show();//把dialog显示出来
            /**
             * 这里我们把AsyncTask的第一个泛型参数指定为Void，表示在执行AsyncTask的时候不需要传入参数给后台任务。
             * 第二个泛型参数指定为Void，表示不使用什么来作为进度显示单位。
             * 第三个泛型参数指定为Void，则表示不使用什么来反馈执行结果。
             */
            AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

                /**
                 * 这个方法中的所有代码都会在子线程中运行，我们应该在这里去处理所有的耗时任务。
                 * 任务一旦完成就可以通过return语句来将任务的执行结果进行返回，
                 * 因为这里AsyncTask的第三个泛型参数指定的是Void，就可以不返回任务执行结果。
                 *
                 * 注意，在这个方法中是不可以进行UI操作的，如果需要更新UI元素，比如说反馈当前任务的执行进度，
                 * 可以调用publishProgress(Progress...)方法来完成。
                 *
                 * @param params
                 * @return
                 */
                @Override
                protected Void doInBackground(Void... params) {
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(mOutput);
                        Bitmap bitmap = createClippedBitmap();//制作裁剪的图片
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);//写到fos中，fos会写到mOutput中
                        if (!bitmap.isRecycled()) {//如果bitmap还未被回收
                            bitmap.recycle();//回收
                        }
                        setResult(Activity.RESULT_OK, getIntent());
                    } catch (Exception e) {
                        Toast.makeText(ClipImageActivity.this, R.string.msg_could_not_save_photo, Toast.LENGTH_SHORT).show();
                    } finally {
                        IOUtils.close(fos);
                    }
                    return null;
                }

                /**
                 * 当后台任务执行完毕并通过return语句进行返回时，这个方法就很快会被调用。
                 * 返回的数据会作为参数传递到此方法中，可以利用返回的数据来进行一些UI操作，
                 * 比如说提醒任务执行的结果，以及关闭掉进度条对话框等。
                 *
                 * @param aVoid
                 */
                @Override
                protected void onPostExecute(Void aVoid) {
                    mDialog.dismiss();//关掉dialog
                    finish();
                }
            };
            AsyncTaskCompat.executeParallel(task);//并行运行这个任务
        } else {
            finish();
        }
    }

    /**
     *
     * @return
     */
    private Bitmap createClippedBitmap() {
        if (mSampleSize <= 1) {
            return mClipImageView.clip();
        }

        // 获取缩放位移后的矩阵值
        final float[] matrixValues = mClipImageView.getClipMatrixValues();
        final float scale = matrixValues[Matrix.MSCALE_X];//x方向缩放比例
        final float transX = matrixValues[Matrix.MTRANS_X];//x方向平移距离
        final float transY = matrixValues[Matrix.MTRANS_Y];//y方向平移距离

        // 获取在显示的图片中裁剪的位置
        final Rect border = mClipImageView.getClipBorder();
        final float cropX = ((-transX + border.left) / scale) * mSampleSize;
        final float cropY = ((-transY + border.top) / scale) * mSampleSize;
        final float cropWidth = (border.width() / scale) * mSampleSize;
        final float cropHeight = (border.height() / scale) * mSampleSize;

        // 获取在旋转之前的裁剪位置
        final RectF srcRect = new RectF(cropX, cropY, cropX + cropWidth, cropY + cropHeight);
        final Rect clipRect = getRealRect(srcRect);

        final BitmapFactory.Options ops = new BitmapFactory.Options();
        final Matrix outputMatrix = new Matrix();

        outputMatrix.setRotate(mDegree);
        // 如果裁剪之后的图片宽高仍然太大,则进行缩小
        if (mMaxWidth > 0 && cropWidth > mMaxWidth) {
            ops.inSampleSize = findBestSample((int) cropWidth, mMaxWidth);

            final float outputScale = mMaxWidth / (cropWidth / ops.inSampleSize);
            outputMatrix.postScale(outputScale, outputScale);
        }

        // 裁剪
        BitmapRegionDecoder decoder = null;
        try {
            decoder = BitmapRegionDecoder.newInstance(mInput, false);
            final Bitmap source = decoder.decodeRegion(clipRect, ops);
            recycleImageViewBitmap();
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), outputMatrix, false);
        } catch (Exception e) {
            return mClipImageView.clip();
        } finally {
            if (decoder != null && !decoder.isRecycled()) {
                decoder.recycle();
            }
        }
    }

    private Rect getRealRect(RectF srcRect) {
        switch (mDegree) {
            case 90:
                return new Rect((int) srcRect.top, (int) (mSourceHeight - srcRect.right),
                        (int) srcRect.bottom, (int) (mSourceHeight - srcRect.left));
            case 180:
                return new Rect((int) (mSourceWidth - srcRect.right), (int) (mSourceHeight - srcRect.bottom),
                        (int) (mSourceWidth - srcRect.left), (int) (mSourceHeight  - srcRect.top));
            case 270:
                return new Rect((int) (mSourceWidth - srcRect.bottom), (int) srcRect.left,
                        (int) (mSourceWidth - srcRect.top), (int) srcRect.right);
            default:
                return new Rect((int) srcRect.left, (int) srcRect.top, (int) srcRect.right, (int) srcRect.bottom);
        }
    }

    private void recycleImageViewBitmap() {
        mClipImageView.post(new Runnable() {
            @Override
            public void run() {
                mClipImageView.setImageBitmap(null);
            }
        });
    }
}
