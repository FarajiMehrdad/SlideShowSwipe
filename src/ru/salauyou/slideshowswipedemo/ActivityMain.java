package ru.salauyou.slideshowswipedemo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ru.salauyou.slideshowswipe.SlideShowSwipe;
import ru.salauyou.slideshowswipe.SlideShowSwipe.State;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

public class ActivityMain extends Activity implements SlideShowSwipe.BitmapContainer, SlideShowSwipe.OnStateChangeListener {

	SlideShowSwipe slideShow;
	ImageView viewControl;
	List<Bitmap> container;
	Random rnd = new Random();
	
	int posCurrent = 0;
	int posPrec = 0;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		
		container = new ArrayList<Bitmap>();
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img01));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img02));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img03));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img04));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img05));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img06));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img07));
		
		
		((SlideShowSwipe)findViewById(R.id.slide_show))
			.setOnStateChangeListener(this)
			.setBitmapContainer(this)
			.setSlideShowPeriod(1000)
			.setSlideShowTransition(300)
			.startSlideShow();

		
		viewControl = (ImageView)findViewById(R.id.view_control);
		viewControl.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				
			}
		});
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	/**
	 * SlideShowSwipe.BitmapContainer implementation
	 */
	
	@Override
	public Bitmap getBitmapNext() {
		posPrec = posCurrent;
		posCurrent ++;
		if (posCurrent >= container.size()){
			posCurrent = 0;
		}
		return container.get(posCurrent);
	}

	@Override
	public Bitmap getBitmapPrevious() {
		posPrec = posCurrent;
		posCurrent --;
		if (posCurrent < 0){
			posCurrent = container.size() - 1;
		}
		return container.get(posCurrent);
	}

	@Override
	public Bitmap getBitmapCurrent() {
		return container.get(posCurrent);
	}

	@Override
	public void undoGetBitmap() {
		posCurrent = posPrec;
	}

	
	/**
	 * SlideShowSwipe.OnStateChangeListener implementation
	 */
	
	
	@Override
	public void onStateChange(State s) {
		switch (s){
		case SLIDESHOW_PAUSED :
		case SLIDESHOW_STARTED :
		case NEXT_SLIDE :
			Log.d("debug", s.toString());
			break;
		}
	}

	@Override
	public void onCurrentBitmapChange() {
		viewControl.setImageBitmap(this.getBitmapCurrent());
	}

}
