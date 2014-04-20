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
import android.view.Menu;
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
		
		slideShow = (SlideShowSwipe)findViewById(R.id.slide_show);
		slideShow.setBitmapContainer(this);
		
		viewControl = (ImageView)findViewById(R.id.view_control);
		slideShow.setOnStateChangeListener(this);
		
		container = new ArrayList<Bitmap>();
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img01));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img02));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img03));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img04));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img05));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img06));
		container.add(BitmapFactory.decodeResource(this.getResources(), R.drawable.img07));
        
		
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
		
		/*
		posCurrent ++;
		if (posCurrent >= container.size()){
			posCurrent = 0;
		}*/
		
		while (posCurrent == posPrec)  // random to simulate mutation
			posCurrent = rnd.nextInt(container.size());
		
		return container.get(posCurrent);
	}

	@Override
	public Bitmap getBitmapPrevious() {
		posPrec = posCurrent;
	
		/*
		posCurrent --;
		if (posCurrent < 0){
			posCurrent = container.size() - 1;
		}*/
		
		while (posCurrent == posPrec)   // random to simulate mutation
			posCurrent = rnd.nextInt(container.size());  
		
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

	
	//---------------------------------------
	
	
	@Override
	public void onStateChange(State state) {
		//viewControl.setImageBitmap(this.getBitmapCurrent());
	}

	@Override
	public void onCurrentBitmapChange() {
		viewControl.setImageBitmap(this.getBitmapCurrent());
	}

}
