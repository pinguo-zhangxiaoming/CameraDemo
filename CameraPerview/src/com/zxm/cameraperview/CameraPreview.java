package com.zxm.cameraperview;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.zxm.cameraperview.PreViewProvider.LayoutMode;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

/**
 * 照相机预览的界面
 * api：http://developer.android.com/guide/topics/media/camera.html
 */
@SuppressLint("NewApi")
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback ,Camera.AutoFocusCallback{
	
    private static boolean DEBUGGING = true;
    private static final String LOG_TAG = "CameraPreviewSample";
    private static final String CAMERA_PARAM_ORIENTATION = "orientation";
    private static final String CAMERA_PARAM_LANDSCAPE = "landscape";
    private static final String CAMERA_PARAM_PORTRAIT = "portrait";
    protected Activity mActivity;
    private SurfaceHolder mHolder;
    protected Camera mCamera;
    protected Camera.Size mPreviewSize;
    protected Camera.Size mPictureSize;
    private int mSurfaceChangedCallDepth = 0;
    
    private int mCenterPosX = -1;
    private int mCenterPosY;
    boolean meteringAreaSupported;
    
    
    PreviewReadyCallback mPreviewReadyCallback = null;
    
    /**
     * 预览已经就绪的回调接口
     */
    public interface PreviewReadyCallback {
    	/**
    	 * 已经可以预览的回调方法
    	 */
        public void onPreviewReady();
    }
 
    /**
     * State flag: true when surface's layout size is set and surfaceChanged()
     * process has not been completed.
     */
    protected boolean mSurfaceConfiguring = false;

    @SuppressLint("NewApi")
	public CameraPreview(Activity activity, int cameraId, LayoutMode mode) {
        super(activity); // Always necessary
        mActivity = activity;
        
        mHolder = getHolder();
        mHolder.addCallback(this);
        //为了实现照片预览功能，需要将SurfaceHolder的类型设置为PUSH 
        //这样，画图缓存就由Camera类来管理，画图缓存是独立于Surface的 
        //3.0之前需要
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        PreViewProvider.getInstance().setContext(activity);
        PreViewProvider.getInstance().setLayoutMode(mode);
        mCamera = PreViewProvider.getInstance().openCamera(cameraId);
        
        this.setFocusable(true);
        this.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				//触摸对焦
				//touch屏幕时去获取Camera.Area.
				//当前的对焦模式是 FOCUS_MODE_AUTO, FOCUS_MODE_MACRO, FOCUS_MODE_CONTINUOUS_VIDEO,
				//FOCUS_MODE_CONTINUOUS_PICTURE,点击对焦区域才会起作用,通过setFocusAreas 方法触发对焦. 
				//camera的坐标是从左上角(-1000, -1000) 到右下角(1000, 1000)，所以需要做左边的转换.
				try {
					touchToFouce(event);
				} catch (Exception e) {
				}
				return true;
			}
		});
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    	try {
        	//surface被创建，将预览显示到holder上
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            mCamera.release();
            mCamera = null;
        }
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceChangedCallDepth++;
        doSurfaceChanged(width, height);
        mSurfaceChangedCallDepth--;
    }
    
    @SuppressLint("NewApi")
	protected void touchToFouce(MotionEvent event) {
        if (mCamera != null) {

        	mCamera.cancelAutoFocus();
            Rect focusRect = PreViewProvider.getInstance().calculateTapArea(this,event.getX(), event.getY(), 1f);

            Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
            
            ArrayList<Camera.Area> list = new ArrayList<Camera.Area>();
            list.add(new Camera.Area(focusRect, 1000));
            //触发对焦
            parameters.setFocusAreas(list);

            ArrayList<Camera.Area> meteringList = new ArrayList<Camera.Area>();
            list.add(new Camera.Area(focusRect, 1000));
			if (meteringAreaSupported) {
                parameters.setMeteringAreas(meteringList);
            }

            mCamera.setParameters(parameters);
            mCamera.autoFocus(this);
        }
    }
    
	private void doSurfaceChanged(int width, int height) {
        mCamera.stopPreview();
        
        Camera.Parameters cameraParams = mCamera.getParameters();
        boolean portrait = isPortrait();

        //在if语句里，setLayoutParams被调用，当布局size发生变化时，surfaceChanged被回调，防止重复执行if语句里的代码。
        if (!mSurfaceConfiguring) {
            Camera.Size previewSize = PreViewProvider.getInstance().determinePreviewSize(portrait, width, height);
            Camera.Size pictureSize = PreViewProvider.getInstance().determinePictureSize(previewSize);
            if (DEBUGGING) { Log.v(LOG_TAG, "Desired Preview Size - w: " + width + ", h: " + height); }
            mPreviewSize = previewSize;
            mPictureSize = pictureSize;
            mSurfaceConfiguring = adjustSurfaceLayoutSize(previewSize, portrait, width, height);
            //第一次被调用，return掉，只有当layoutChange才去往下执行。
            if (mSurfaceConfiguring && (mSurfaceChangedCallDepth <= 1)) {
            	mCamera.autoFocus(this);
                return;
            }
        }
        if (cameraParams.getMaxNumMeteringAreas() > 0) {
            this.meteringAreaSupported = true;
         }
        configureCameraParameters(cameraParams, portrait);
        mSurfaceConfiguring = false;

        try {
            mCamera.startPreview();
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to start preview: " + e.getMessage());
            if(PreViewProvider.getInstance().removePreViewSize(mPreviewSize)){ 
            	// prevent infinite loop
                surfaceChanged(null, 0, width, height);
            } else {
                Toast.makeText(mActivity, "Can't start preview", Toast.LENGTH_LONG).show();
                Log.w(LOG_TAG, "Gave up starting preview");
            }
            mPreviewSize = null;
        }
        
        if (null != mPreviewReadyCallback) {
            mPreviewReadyCallback.onPreviewReady();
        }
    }
    
    protected boolean adjustSurfaceLayoutSize(Camera.Size previewSize, boolean portrait,
            int availableWidth, int availableHeight) {
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)this.getLayoutParams();

        int spec[] = PreViewProvider.getInstance().getLayoutSpec(previewSize,portrait,availableWidth, availableHeight);
        int layoutWidth = spec[0];
        int layoutHeight = spec[1];
        boolean layoutChanged;
        if ((layoutWidth != this.getWidth()) || (layoutHeight != this.getHeight())) {
            layoutParams.height = layoutHeight;
            layoutParams.width = layoutWidth;
            if (mCenterPosX >= 0) {
                layoutParams.topMargin = mCenterPosY - (layoutHeight / 2);
                layoutParams.leftMargin = mCenterPosX - (layoutWidth / 2);
            }
            //又会调用surfaceChangend
            this.setLayoutParams(layoutParams);
            layoutChanged = true;
        } else {
            layoutChanged = false;
        }

        return layoutChanged;
    }

    /**
     * @param x X coordinate of center position on the screen. Set to negative value to unset.
     * @param y Y coordinate of center position on the screen.
     */
    public void setCenterPosition(int x, int y) {
        mCenterPosX = x;
        mCenterPosY = y;
    }
    
    @SuppressLint("NewApi")
	protected void configureCameraParameters(Camera.Parameters cameraParams, boolean portrait) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) { // for 2.1 and before
            if (portrait) {
                cameraParams.set(CAMERA_PARAM_ORIENTATION, CAMERA_PARAM_PORTRAIT);
            } else {
                cameraParams.set(CAMERA_PARAM_ORIENTATION, CAMERA_PARAM_LANDSCAPE);
            }
        } else {// for 2.2 and later
        	int angle = PreViewProvider.getInstance().getOrientationAngle();
            Log.v(LOG_TAG, "angle: " + angle);
            mCamera.setDisplayOrientation(angle);
        }

        cameraParams.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        cameraParams.setPictureSize(mPictureSize.width, mPictureSize.height);
        if (DEBUGGING) {
            Log.v(LOG_TAG, "Preview Actual Size - w: " + mPreviewSize.width + ", h: " + mPreviewSize.height);
            Log.v(LOG_TAG, "Picture Actual Size - w: " + mPictureSize.width + ", h: " + mPictureSize.height);
        }

        mCamera.setParameters(cameraParams);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        PreViewProvider.getInstance().stopCamera();
    }
    
    public boolean isPortrait() {
        return (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
    }
    
    public void setOneShotPreviewCallback(PreviewCallback callback) {
        if (null == mCamera) {
            return;
        }
        mCamera.setOneShotPreviewCallback(callback);
    }
    
    public void setPreviewCallback(PreviewCallback callback) {
        if (null == mCamera) {
            return;
        }
        mCamera.setPreviewCallback(callback);
    }
    
    public Camera.Size getPreviewSize() {
        return mPreviewSize;
    }
    
    public void setOnPreviewReady(PreviewReadyCallback cb) {
        mPreviewReadyCallback = cb;
    }

	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		Log.i(LOG_TAG, "success = " + success);
	}

	public void takePicture() {
		PreViewProvider.getInstance().takePicture();
	}

	public void stopCamera() {
		PreViewProvider.getInstance().stopCamera();
	}

	
}
