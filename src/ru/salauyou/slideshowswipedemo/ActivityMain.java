package ru.salauyou.slideshowswipedemo;

import java.util.ArrayList;
import java.util.List;

import ru.salauyou.slideshowswipe.SlideShowSwipe;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;

public class ActivityMain extends Activity implements SlideShowSwipe.BitmapContainer {

	SlideShowSwipe slideShow;
	List<Bitmap> container;
	
	int posCurrent = 0;
	int posPrec = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		slideShow = (SlideShowSwipe)findViewById(R.id.slide_show);
		slideShow.setBitmapContainer(this);
		
		container = new ArrayList<Bitmap>();
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img01));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img02));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img03));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img04));
		
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
		Log.d("debug", "Got next: " + posCurrent);
		return container.get(posCurrent);
	}

	@Override
	public Bitmap getBitmapPrevious() {
		posPrec = posCurrent;
		posCurrent --;
		if (posCurrent < 0){
			posCurrent = container.size() - 1;
		}
		Log.d("debug", "Got previous: " + posCurrent);
		return container.get(posCurrent);
	}

	@Override
	public Bitmap getBitmapCurrent() {
		Log.d("debug", "Got current: " + posCurrent);
		return container.get(posCurrent);
	}

	@Override
	public void undoGetBitmap() {
		posCurrent = posPrec;
		Log.d("debug", "Undid: " + posCurrent);
	}

}
