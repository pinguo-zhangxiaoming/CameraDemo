package com.zxm.cameraperview;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

public class MainActivity extends ActionBarActivity{

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
        mPreview = new CameraPreview(this, 0, CameraPreview.LayoutMode.FitToParent);
        LayoutParams previewLayoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        mParentView.addView(mPreview, 0, previewLayoutParams);
        Button takePicBtn = new Button(this);
        takePicBtn.setText("click");
        takePicBtn.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				Log.i("lll", "onClick");
				mPreview.takePicture();
			}
        	
        });
        
        LayoutParams btnParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        btnParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        btnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        mParentView.addView(takePicBtn, 1, btnParams);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
        mParentView.removeView(mPreview); // This is necessary.
        mPreview = null;
    }
}
