package com.zxm.cameraperview;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;

import com.zxm.cameraperview.PreViewProvider.LayoutMode;

public class MainActivity extends Activity{

	private static final String TAG = "MainActivity";
	private RelativeLayout mParentView;
	private CameraPreview mPreview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mParentView = new RelativeLayout(this);
        setContentView(mParentView);
	}

	@Override
    protected void onResume() {
        super.onResume();
        if(!checkCameraHardware(this)){
        	Toast.makeText(this, "Your device isn't support camera.", Toast.LENGTH_LONG).show();
        }else{
        	mPreview = new CameraPreview(this, 0, LayoutMode.FitToParent);
        	LayoutParams previewLayoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        	mParentView.addView(mPreview, 0, previewLayoutParams);
        	Button takePicBtn = new Button(this);
        	takePicBtn.setText("click");
        	takePicBtn.setOnClickListener(new OnClickListener(){
        		
        		@Override
        		public void onClick(View v) {
        			Log.i(TAG, "onClick take photo");
        			mPreview.takePicture();
        		}
        		
        	});
        	
        	LayoutParams btnParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        	btnParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        	btnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        	mParentView.addView(takePicBtn, 1, btnParams);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mPreview != null){
        	mPreview.stopCamera();
        	mParentView.removeView(mPreview); // This is necessary.
        	mPreview = null;
        }
    }
    
    /**
     * ºÏ≤‚ «∑Ò”–…„œÒÕ∑
     * @param context
     * @return
     */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }
}
