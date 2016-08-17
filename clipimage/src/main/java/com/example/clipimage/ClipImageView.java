package com.example.clipimage;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

/**
 * Created by wzxx on 16/8/12.
 *
 * @author wzxx (wzxxkcer@foxmail.com)
 * @version 2016-08-12 1.0
 * @since 2016-08-12 1.0
 */
public class ClipImageView extends ImageView implements ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {

    /**
     * final要不要加？？？？
     */
    private int mMaskColor;//遮罩层颜色

    private Paint mPaint;//画笔
    private int mWidth;//裁剪框大小（从属性上读到的整型值）
    private int mHeight;//裁剪框宽的大小（从属性上读到的整型值）
    private String mTipText;//提示文字
    private int mClipPadding;//裁剪框相对于控件的内边距

    private float mScaleMax=4.0f;//图片最大缩放大小
    private float mScaleMin=2.0f;//图片最小缩放大小

    /**
     * 初始化时的缩放比例
     */
    private float mInitScale=1.0f;

    /**
     * 用于存放矩阵。Matrix是一个3*3的矩阵，一共九个值，所以数组大小为9.
     */
    private final float[] mMatrixValues = new float[9];

    /**
     * 缩放的手势检查
     */
    private ScaleGestureDetector mScaleGestureDetector=null;
    private final Matrix mScaleMatrix = new Matrix();//对图片进行缩放平移的matrix。初始化。

    /**
     * 用于双击
     */
    private GestureDetector mGestureDetector;
    private boolean isAutoScale;//是否正在自动放大缩小呐

    /**
     * 上一次滑动的x和y的坐标
     */
    private float mLastX;
    private float mLastY;

    private boolean isCanDrag;
    private int lastPointerCount;//上一次触控点的数量

    private Rect mClipBorder = new Rect();
    private int mMaxOutputWidth = 0;

    private boolean mDrawCircleFlag;//裁剪框
    private float mRoundCorner;//裁剪后的图片的最大输出宽度

    /**
     * 构造函数.主要是多了一些自定义属性的读取
     */
    public ClipImageView(Context context){
        this(context,null);
    }
    public ClipImageView(Context context, AttributeSet attrs){
        super(context,attrs);

        setScaleType(ScaleType.MATRIX);//用矩形绘制
        //初始化手势检测器，监听双击事件
        mGestureDetector=new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener(){//SimpleOnGestureListener是个类
                    @Override
                    public boolean onDoubleTap(MotionEvent e){
                        //如果是正在自动缩放，则直接返回，不进行处理
                        if (isAutoScale){
                            return true;
                        }

                        /**
                         * 获得点击的位置的x、y坐标值
                         */
                        float x=e.getX();
                        float y=e.getY();

                        if (getScale()<mScaleMin){//如果当前图片的缩放值小于指定的最小缩放值。mScaleMin为2.0f
                            //本来缩放比例就够小啦，所以就设置多线程消息来控制缩放比例为最小缩放值。这下子就自动放大了
                            ClipImageView.this.postDelayed(new AutoScaleRunnable(mScaleMin,x,y),16);//定时器是16毫秒
                        }else {//当前图片的缩放值大于初试缩放值，则自动缩小
                            ClipImageView.this.postDelayed(new AutoScaleRunnable(mInitScale,x,y),16);
                        }
                        isAutoScale=true;

                        return true;
                    }
                });
        mScaleGestureDetector=new ScaleGestureDetector(context,this);//初始化缩放手势监听器
        this.setOnTouchListener(this);

        mPaint=new Paint(Paint.ANTI_ALIAS_FLAG);//绘制时抗锯齿
        mPaint.setColor(Color.WHITE);//设置画笔颜色白色

        /**
         * 裁剪框相关参数值的设置
         */
        TypedArray ta=context.obtainStyledAttributes(attrs,R.styleable.ClipImageView);
        mWidth=ta.getInteger(R.styleable.ClipImageView_civHeight,1);
        mHeight=ta.getInteger(R.styleable.ClipImageView_civHeight,1);
        mClipPadding=ta.getDimensionPixelSize(R.styleable.ClipImageView_civClipPadding,0);
        mTipText=ta.getString(R.styleable.ClipImageView_civTipText);
        mMaskColor=ta.getColor(R.styleable.ClipImageView_civMaskColor,0xB2000000);
        mDrawCircleFlag=ta.getBoolean(R.styleable.ClipImageView_civClipCircle,false);
        mRoundCorner=ta.getDimension(R.styleable.ClipImageView_civClipRoundCorner,0);
        final int textSize=ta.getDimensionPixelSize(R.styleable.ClipImageView_civTipTextSize,24);
        mPaint.setTextSize(textSize);
        ta.recycle();

        mPaint.setDither(true);//防抖动,让人觉得是平滑的过渡
    }

    /**************************************内部调用函数和类***********************************************/

    /**
     * 获得当前的缩放比例
     *
     * @return
     */
    private final float getScale(){
        //将Matrix的9个值映射到mMatrixValues数组中
        mScaleMatrix.getValues(mMatrixValues);
        //拿到Matrix中的MSCALE_X的值，这个值为图片宽度的缩放比例，因为图片高度
        //的缩放比例和宽度的缩放比例一致，所以我们取一个就可以了
        //还可以 return mMatrixValues[Matrix.MSCALE_Y];
        return mMatrixValues[Matrix.MSCALE_X];
    }

    /**
     * 自动缩放的任务
     */
    private class AutoScaleRunnable implements Runnable{
         static final float BIGGer=1.07f;
        static final float SMALLER=0.93f;
        private float mTargetScale;//目标的缩放比例
        private float tmpScale;//暂时的缩放比例

        /**
         * 缩放的中心的坐标
         */
        private float x;
        private float y;

        public AutoScaleRunnable(float targetScale,float x,float y){
            this.mTargetScale=targetScale;
            this.x=x;
            this.y=y;
            if (getScale()<mTargetScale){
                tmpScale=BIGGer;
            }else {
                tmpScale=SMALLER;
            }
        }

        /**
         * 重写函数run()
         */
        @Override
        public void run(){
            //进行缩放
            mScaleMatrix.postScale(tmpScale,tmpScale,x,y);//矩阵缩放比例作为参数
            checkBorder();//边界检查
            setImageMatrix(mScaleMatrix);

            final float currentScale=getScale();//获得当前的缩放比例
            // 如果值在合法范围内，继续缩放
            if (((tmpScale>1f)&&(currentScale<mTargetScale))||((tmpScale<1f)&&(mTargetScale<currentScale))){
                 ClipImageView.this.postDelayed(this,16);
            }else {
                // 设置为目标的缩放比例
                final float deltaScale=mTargetScale/currentScale;
                mScaleMatrix.postScale(deltaScale,deltaScale,x,y);
                checkBorder();
                setImageMatrix(mScaleMatrix);
                isAutoScale=false;//没有在自动缩放呐
            }
        }
    }

    /**
     * 边界检查
     */
    private void checkBorder(){
        RectF rect=getMatrixRectF();//当前图片的范围
        float deltaX=0;
        float deltaY=0;

        //假如缩放后图片的宽度大于等于裁剪边界的宽度
        if (rect.width()>=mClipBorder.width()){
            if (rect.left>mClipBorder.left){
                deltaX=-rect.left+mClipBorder.left;
            }

            if (rect.right<mClipBorder.right){
                deltaX=mClipBorder.right-rect.right;
            }
        }

        //假如缩放后图片的高度大于等于裁剪边界的高度
        if (rect.height()>=mClipBorder.height()){
            if (rect.top>mClipBorder.top){
                deltaY=-rect.top+mClipBorder.top;
            }

            if (rect.bottom<mClipBorder.bottom){
                deltaY=mClipBorder.bottom-rect.bottom;
            }
        }

        mScaleMatrix.postTranslate(deltaX,deltaY);//由于缩放是以(0,0)为中心的,所以为了把界面的中心与(0,0)对齐，就要postTranslate
    }

    /**
     * 根据当前图片的Matrix获得图片的范围.即，获得缩放后图片的上下左右坐标、宽高。
     *
     * @return
     */
    private RectF getMatrixRectF(){
        Matrix matrix=mScaleMatrix;//获得当前图片的矩阵
        RectF rect=new RectF();//创建一个浮点类型的矩形
        Drawable drawable=getDrawable();//得到当前的图片
        if (null!=drawable){
            rect.set(0,0,drawable.getIntrinsicWidth(),drawable.getMinimumHeight());//使这个矩形的宽和高同当前图片一致
            //将矩阵映射到矩形上面，之后我们可以通过获取到矩阵的上下左右坐标以及宽高来得到缩放后图片的上下左右坐标和宽高
            matrix.mapRect(rect);
        }
        return rect;
    }

    /**
     * 这里没有使用post方式,因为图片会有明显的从初始位置移动到需要缩放的位置
     */
    private void postResetImageMatrix(){
        if (getWidth()!=0){//发现这个图片的宽度是存在的
            resetImageMatrix();//设置图片的初始缩放及平移
        }else {//不存在的话就新开一个线程

            /**
             * 获取控件宽高是要在控件被绘制出来之后才能获取得到的，
             * 所以通过post一个Runnable对象到主线程的Looper中，保证它是在界面绘制完成之后被调用。
             */
            post(new Runnable() {
                @Override
                public void run() {
                    resetImageMatrix();
                }
            });
        }
    }

    /**
     * 垂直方向与View的边矩
     *
     * 设置图片的初始缩放及平移。参考图片大小、控件本身大小以及裁剪框的大小进行计算。
     */
    public void resetImageMatrix(){
        final Drawable d=getDrawable();//imageview获取的Drawable对象
        if(d==null){
            return;
        }

        /**
         * 取得Drawable对象的固有的宽度和高度，而不是bitmap对象。
         *
         * Drawable对象可能由于对Bitmap的放大或缩小显示，导致它的宽或高与Bitmap的宽高不同。
         */
        final int dWidth=d.getIntrinsicWidth();
        final int dHeight=d.getIntrinsicHeight();

        /**
         * 裁剪框的大小
         */
        final int cWidth=mClipBorder.width();
        final int cHeight=mClipBorder.height();

        /**
         * 图片展示出来的的大小
         */
        final int vWidth=getWidth();
        final int vHeight=getHeight();

        final float scale;
        final float dx;
        final float dy;

        if(dWidth*cHeight>cWidth*dHeight){
            scale=cHeight/(float)dHeight;
        }else {
            scale=cWidth/(float)dWidth;
        }

        dx=(vWidth-dWidth*scale)*0.5f;
        dy=(vHeight-dHeight*scale)*0.5f;

        mScaleMatrix.setScale(scale,scale);
        mScaleMatrix.postTranslate((int)(dx+0.5f),(int)(dy+0.5f));

        setImageMatrix(mScaleMatrix);

        mInitScale=scale;
        mScaleMin = mInitScale*2;
        mScaleMax=mInitScale*4;
    }

    /**
     * 是否是拖拽行为
     *
     * @param dx
     * @param dy
     * @return
     */
    private boolean isCanDrag(float dx,float dy){
        return Math.sqrt((dx*dx)+(dy*dy))>=0;
    }

    /**
     * 剪切图片
     *
     * @return 返回剪切后的bitmap对象
     */
    public Bitmap clip(){
        /**
         * 由于我们是对Bitmap进行裁剪，所以首先获取这个Bitmap
         */
        final Drawable drawable=getDrawable();
        final Bitmap originalBitmap=((BitmapDrawable)drawable).getBitmap();

        /**
         * 然后，我们的矩阵值可以通过一个包含9个元素的float数组读出
         *
         * Matrix.MSCALE_X为X上的缩放值
         */
        final float[] matrixValues=new float[9];
        mScaleMatrix.getValues(matrixValues);//获得缩放矩阵的这九个值

        //这里缩放的是Drawable对象，但是我们裁剪时用的Bitmap，如果图片太大的话是可能在Drawable上进行缩放的，所以缩放大小的计算应该为：
        final float scale=matrixValues[Matrix.MSCALE_X]*drawable.getIntrinsicWidth()/originalBitmap.getWidth();

        /**
         * 然后获取图片平移量
         */
        final float transX=matrixValues[Matrix.MSCALE_X];
        final float transY=matrixValues[Matrix.MSCALE_Y];

        /**
         * 计算裁剪框对应在图片上的起点及宽高
         */
        final float cropX=(-transX+mClipBorder.left)/scale;
        final float cropY=(-transY+mClipBorder.top)/scale;
        final float cropWidth=mClipBorder.width()/scale;
        final float cropHeight=mClipBorder.height()/scale;

        /**
         * 当裁剪出来的宽度超出我们最大宽度时，进行缩放。
         */
        Matrix outputMatrix=null;
        if (mMaxOutputWidth>0&&cropWidth>mMaxOutputWidth){
            final float outputScale=mMaxOutputWidth/cropWidth;
            outputMatrix=new Matrix();
            outputMatrix.setScale(outputScale,outputScale);
        }

        /**
         * 最终根据上面计算出来的值，创建裁剪出来的Bitmap
         */
        return Bitmap.createBitmap(originalBitmap,(int)cropX,(int)cropY,(int)cropWidth,(int)cropHeight,outputMatrix,false);

    }

    public Rect getClipBorder(){
        return mClipBorder;
    }

    public void setMaxOutputWidth(int maxOutputWidth){
        mMaxOutputWidth=maxOutputWidth;
    }

    public float[] getClipMatrixValues(){
        final float[] matrixValues=new float[9];
        mScaleMatrix.getValues(matrixValues);
        return matrixValues;
    }

    /*************************************************************************************************/



    /********************************************重写的函数**********************************************/

    /**
     * 因为在构造方法中，由于控件还没有绘制出来，无法获取到控件的宽高，
     * 所以并不能计算裁剪框的大小和位置，所以重写该方法，在这里计算裁剪框的位置
     *
     * @param changed
     * @param left
     * @param top
     * @param right
     * @param bottom
     */
    @Override
    protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);
        final int width=getWidth();
        final int height=getHeight();
        mClipBorder.left=mClipPadding;
        mClipBorder.right=width-mClipPadding;
        final int borderHeight=mClipBorder.width()*mHeight/mWidth;//获得裁剪框的高度
        mClipBorder.top=(height-borderHeight)/2;
        mClipBorder.bottom=mClipBorder.top+borderHeight;
    }

    /**
     * 先画上下两个矩形，再画左右两个矩形，中间所围起来的没有画的部分就是裁剪框。
     *
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        final int width=getWidth();
        final int height=getHeight();

        mPaint.setColor(mMaskColor);
        mPaint.setStyle(Paint.Style.FILL);//设置画笔的风格为实心
        canvas.drawRect(0,0,width,mClipBorder.top,mPaint);
        canvas.drawRect(0,mClipBorder.bottom,width,height,mPaint);
        canvas.drawRect(0,mClipBorder.top,mClipBorder.left,mClipBorder.bottom,mPaint);
        canvas.drawRect(mClipBorder.right,mClipBorder.top,width,mClipBorder.bottom,mPaint);

        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(1);//设置画笔线的宽度
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(mClipBorder.left,mClipBorder.top,mClipBorder.right,mClipBorder.bottom,mPaint);

        if (mTipText!=null){//如果存在提示文字,就要canvas画出来撒
            final float testWidth=mPaint.measureText(mTipText);//粗略计算文字宽度
            final float startX=(width-testWidth)/2;
            final Paint.FontMetrics fm=mPaint.getFontMetrics();//Canvas 绘制文本时，使用FontMetrics对象，计算位置的坐标。
            final float startY=mClipBorder.bottom+mClipBorder.top/2-(fm.descent-fm.ascent)/2;
            mPaint.setStyle(Paint.Style.FILL);
            canvas.drawText(mTipText,startX,startY,mPaint);
        }
    }

    /**
     * 不使用全局布局的监听（通过getViewTreeObserver加入回调），
     * 而是直接重写几个设置图片的方法，在设置图片后进行初始显示的设置。
     *
     * setImageDrawable、setImageResource、setImageURI就都是更新ClipImageView图片，只是后两个其实最后都要调用到setImageDrawable()
     */
    @Override
    public void setImageDrawable(Drawable drawable){
        super.setImageDrawable(drawable);
        postResetImageMatrix();
    }

    @Override
    public void setImageResource(int resId){
        super.setImageResource(resId);
        postResetImageMatrix();
    }

    @Override
    public void setImageURI(Uri uri){
        super.setImageURI(uri);
        postResetImageMatrix();
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector){
        float scale=getScale();//获得当前缩放比例
        float scaleFactor=detector.getScaleFactor();//ScaleGestureDetector会根据手指的分开和合拢计算出一个缩放因子

        if (getDrawable()==null){
            return true;
        }

        /**
         * 缩放的范围控制
         */
        if ((scale<mScaleMax&&scaleFactor>1.0f)||(scale>mInitScale&&scaleFactor<1.0f)){
            /**
             * 缩放阙值最大最小值判断
             */
            if (scaleFactor*scale<mInitScale){
                scaleFactor=mInitScale/scale;
            }
            if (scaleFactor*scale>mScaleMax){
                scaleFactor=mScaleMax/scale;
            }

            /**
             * 设置缩放比例
             */
            mScaleMatrix.postScale(scaleFactor,scaleFactor,detector.getFocusX(),detector.getFocusY());//后两个参数是收汁缩放的中心点坐标
            checkBorder();
            setImageMatrix(mScaleMatrix);//实现
        }
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector){
        // TODO Auto-generated method stub
        //一定要返回true才会进入onScale()这个函数
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector){

    }

    @Override
    public boolean onTouch(View v,MotionEvent event){
        if (mGestureDetector.onTouchEvent(event)){//双击手势已触发就返回true
            return true;
        }
        mScaleGestureDetector.onTouchEvent(event);//触发缩放手势

        float x=0,y=0;
        final int pointerCount=event.getPointerCount();//拿到触摸点的个数

        // 得到多个触摸点的x与y均值
        for (int i=0;i<pointerCount;i++){
            x+=event.getX(i);
            y+=event.getY(i);
        }
        x/=pointerCount;
        y/=pointerCount;

        /**
         * 每当触摸点发生变化时，重置mLasX , mLastY
         */
        if (pointerCount!=lastPointerCount){
            isCanDrag=false;
            mLastX=x;
            mLastY=y;
        }
        lastPointerCount=pointerCount;

        switch (event.getAction()){
            case MotionEvent.ACTION_MOVE:
                float dx=x-mLastX;
                float dy=y-mLastY;

                if (!isCanDrag){//假如isCanDrag为false
                    isCanDrag=isCanDrag(dx,dy);//判断下是不是拖拽行为
                }
                if (isCanDrag){//假如isCanDrag为true
                    if (getDrawable()!=null){
                        RectF rectF=getMatrixRectF();
                        // 如果缩放后图片的宽度小于屏幕宽度，则禁止左右移动
                        if (rectF.width()<=mClipBorder.width()){
                            dx=0;
                        }
                        // 如果缩放后图片高度小于屏幕高度，则禁止上下移动
                        if (rectF.height()<=mClipBorder.height()){
                            dy=0;
                        }

                        mScaleMatrix.postTranslate(dx,dy);
                        checkBorder();
                        setImageMatrix(mScaleMatrix);
                    }
                }
                mLastX=x;
                mLastY=y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastPointerCount=0;
                break;
        }
        return true;
    }

}
