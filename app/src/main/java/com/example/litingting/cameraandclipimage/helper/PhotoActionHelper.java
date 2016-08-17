package com.example.litingting.cameraandclipimage.helper;

import android.app.Activity;
import android.content.Intent;

import com.example.litingting.cameraandclipimage.ClipImageActivity;
import com.example.litingting.cameraandclipimage.TakePictureActivity;

/**
 * Created by wzxx on 16/8/15.
 */
public class PhotoActionHelper {

    private static final String EXTRA_OUTPUT = "output";
    private static final String EXTRA_INPUT = "input";
    private static final String EXTRA_OUTPUT_MAX_WIDTH = "output-max-width";

    private final Intent mIntent;
    private final Activity mFrom;
    private int mRequestCode;

    /**
     * 构造函数
     *
     * @param from
     * @param to
     */
    private PhotoActionHelper(Activity from, Class to) {
        mFrom = from;
        mIntent = new Intent(from, to);
    }

    public static PhotoActionHelper takePhoto(Activity from) {
        return new PhotoActionHelper(from, TakePictureActivity.class);
    }

    public static PhotoActionHelper clipImage(Activity from) {
        return new PhotoActionHelper(from, ClipImageActivity.class);
    }

    public PhotoActionHelper output(String path) {
        mIntent.putExtra(EXTRA_OUTPUT, path);
        return this;
    }

    public PhotoActionHelper input(String path) {
        mIntent.putExtra(EXTRA_INPUT, path);
        return this;
    }

    public PhotoActionHelper maxOutputWidth(int width) {
        mIntent.putExtra(EXTRA_OUTPUT_MAX_WIDTH, width);
        return this;
    }

    public PhotoActionHelper extra(Intent intent) {
        mIntent.putExtras(intent);
        return this;
    }

    public PhotoActionHelper requestCode(int code) {
        mRequestCode = code;
        return this;
    }

    public void start() {
        mFrom.startActivityForResult(mIntent, mRequestCode);
    }

    public static String getOutputPath(Intent data) {
        return data == null ? null : data.getStringExtra(EXTRA_OUTPUT);
    }

    public static String getInputPath(Intent data) {
        return data == null ? null : data.getStringExtra(EXTRA_INPUT);
    }

    public static int getMaxOutputWidth(Intent data) {
        return data.getIntExtra(EXTRA_OUTPUT_MAX_WIDTH, 0);
    }
}
