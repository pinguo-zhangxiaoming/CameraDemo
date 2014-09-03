package com.zxm.cameraperview;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;

public class PreViewProvider {

	private static PreViewProvider mPreViewProvider;
	private Activity mAct;
	private Camera mCamera;
	private List<Camera.Size> mPreviewSizeList;
	private List<Camera.Size> mPictureSizeList;
	private LayoutMode mLayoutMode;
	
	public static enum LayoutMode {
        FitToParent, // Scale to the size that no side is larger than the parent
        NoBlank // Scale to the size that no side is smaller than the parent
    };
	
	private PreViewProvider() {
		super();
	}

	public static PreViewProvider getInstance(){
		if(mPreViewProvider == null){
			mPreViewProvider = new PreViewProvider();
		}
		return mPreViewProvider;
	}
	
	/**
     * 摄像头的编号，通常0是后置摄像头, 1是前置摄像头.
     */
	@SuppressLint("NewApi")
	public int getCameraId(int cameraId){
		int camId = 0;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
        	//2.3以后支持检测摄像头的数量
            if (Camera.getNumberOfCameras() > cameraId) {
            	camId = cameraId;
            }
        } 
		return camId;
	}

	public void setContext(Activity activity) {
		this.mAct = activity;
	}
	
	/**
	 * 打开摄像头
	 * @param cameraId
	 * @return
	 */
	@SuppressLint("NewApi")
	public Camera openCamera(int cameraId){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
        	mCamera = Camera.open(getCameraId(cameraId));
        } else {
            mCamera = Camera.open();
        }
		if(mCamera != null){
			getCameraSupportSize();
		}
		return mCamera;
	}
	
	private void getCameraSupportSize(){
		Camera.Parameters cameraParams = mCamera.getParameters();
        mPreviewSizeList = cameraParams.getSupportedPreviewSizes();
        mPictureSizeList = cameraParams.getSupportedPictureSizes();
	}
	
	public Camera.Size determinePreviewSize(boolean portrait, int reqWidth, int reqHeight) {
        // Meaning of width and height is switched for preview when portrait,
        // while it is the same as user's view for surface and metrics.
        // That is, width must always be larger than height for setPreviewSize.
        int reqPreviewWidth; // requested width in terms of camera hardware
        int reqPreviewHeight; // requested height in terms of camera hardware
        if (portrait) {
            reqPreviewWidth = reqHeight;
            reqPreviewHeight = reqWidth;
        } else {
            reqPreviewWidth = reqWidth;
            reqPreviewHeight = reqHeight;
        }
        // Adjust surface size with the closest aspect-ratio
        float reqRatio = ((float) reqPreviewWidth) / reqPreviewHeight;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        Camera.Size retSize = null;
        for (Camera.Size size : mPreviewSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }
        return retSize;
    }

    public Camera.Size determinePictureSize(Camera.Size previewSize) {
        Camera.Size retSize = null;
        for (Camera.Size size : mPictureSizeList) {
            if (size.equals(previewSize)) {
                return size;
            }
        }
        // if the preview size is not supported as a picture size
        float reqRatio = ((float) previewSize.width) / previewSize.height;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        for (Camera.Size size : mPictureSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }
        
        return retSize;
    }

	public int[] getLayoutSpec(Size previewSize, boolean portrait,int availableWidth, int availableHeight) {
		float tmpLayoutHeight, tmpLayoutWidth;
		int spec[] = new int[2];
        if (portrait) {
            tmpLayoutHeight = previewSize.width;
            tmpLayoutWidth = previewSize.height;
        } else {
            tmpLayoutHeight = previewSize.height;
            tmpLayoutWidth = previewSize.width;
        }

        float factH, factW, fact;
        factH = availableHeight / tmpLayoutHeight;
        factW = availableWidth / tmpLayoutWidth;
        if (mLayoutMode == LayoutMode.FitToParent) {
            // Select smaller factor, because the surface cannot be set to the size larger than display metrics.
        	fact = factH < factW?factH:factW;
        } else {
        	fact = factH < factW?factW:factH;
        }
        spec[0] = (int) (tmpLayoutWidth * fact);
        spec[1] = (int) (tmpLayoutHeight * fact);
		return spec;
	}

	public boolean removePreViewSize(Size previewSize) {
		// Remove failed size
        mPreviewSizeList.remove(previewSize);
        // Reconfigure
        return mPreviewSizeList.size() > 0;
	}
	
	/**
     * 将touch的x,y坐标点转换成Camera的坐标点。
     * 
     */
    public Rect calculateTapArea(SurfaceView view,float x, float y, float coefficient) {
//        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();
        int areaSize = Float.valueOf(50 * coefficient).intValue();

        int left = clamp((int) x - areaSize / 2, 0, view.getWidth() - areaSize);
        int top = clamp((int) y - areaSize / 2, 0, view.getHeight() - areaSize);

        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }
    /**
     * 点击拍照
     */
    public void takePicture() {
    	if(mCamera != null){
    		mCamera.takePicture(null, null, jpegCallback);
    	}
	}
	
	PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.d("pic", "onPictureTaken - jpeg");
			new SaveImageTask().execute(data);
			if(mCamera != null){
				mCamera.startPreview();
//			mPreview.setCamera(camera);
			}
		}
	};
	
	private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

		@Override
		protected Void doInBackground(byte[]... data) {
			FileOutputStream outStream = null;

			// Write to SD Card
			try {
	            File sdCard = Environment.getExternalStorageDirectory();
	            File dir = new File (sdCard.getAbsolutePath() + "/camtest");
	            dir.mkdirs();				
				
				String fileName = String.format("%d.jpg", System.currentTimeMillis());
				File outFile = new File(dir, fileName);
				
				outStream = new FileOutputStream(outFile);
				outStream.write(data[0]);
				outStream.flush();
				outStream.close();
				
				Log.d("pic", "onPictureTaken - wrote bytes: " + data.length + " to " + outFile.getAbsolutePath());
				
				refreshGallery(outFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
			}
			return null;
		}
	}
	
	private void refreshGallery(File file) {
		Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
	    mediaScanIntent.setData(Uri.fromFile(file));
	    mAct.sendBroadcast(mediaScanIntent);
	}
	/**
	 * 释放摄像头
	 */
	public void stopCamera() {
        if (null == mCamera) {
            return;
        }
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

	public void setLayoutMode(LayoutMode mode) {
		mLayoutMode = mode;
	}

	public int getOrientationAngle() {
		int angle;
        Display display = mAct.getWindowManager().getDefaultDisplay();
        switch (display.getRotation()) {
            case Surface.ROTATION_0: // This is display orientation
                angle = 90; // This is camera orientation
                break;
            case Surface.ROTATION_90:
                angle = 0;
                break;
            case Surface.ROTATION_180:
                angle = 270;
                break;
            case Surface.ROTATION_270:
                angle = 180;
                break;
            default:
                angle = 90;
                break;
        }
		return angle;
	}
}
