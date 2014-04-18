package ru.salauyou.slideshowswipe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;


public class SlideShowSwipe extends View {

	public interface BitmapContainer {
		
		public Bitmap getBitmapNext();
		
		public Bitmap getBitmapPrevious();
		
		public Bitmap getBitmapCurrent();
		
		public void undoGetBitmap();
		
	}
	
	final private SlideShowSwipe self = this;
	
	private BitmapContainer container;
	private Bitmap bitmapFront, bitmapBack;
	
	private Rect rectDstBOrig, rectDstFOrig;
	private Rect rectDstF = new Rect();
	private Rect rectDstB = new Rect();
	private Rect rectDimensions = new Rect();
	private Paint paintAlphaF = new Paint();
	private Paint paintAlphaB = new Paint();
	
	private float deltaX, deltaXPrec, xStart, xStartRaw;
	private long timeStart;
	private float v0, vC; // start and calculated velocity
	private float xC, xCPrec; // start x, calculated x and preceeding calculated x
	
	private float kVScreen = 4f;	// deceleration coefficient relative to view width
	private float kV; // deceleration coefficient in px/s^2
	
	
	
	public SlideShowSwipe(Context context) {
		super(context);
		setGestureListener();
	}

	public SlideShowSwipe(Context context, AttributeSet attrs) {
		super(context, attrs);
		setGestureListener();
	}

	public SlideShowSwipe(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		setGestureListener();
	}

	
	/**
	 * Sets bitmap container to be controlled by this view
	 * 
	 * @param 	container	bitmap contaner, must implement {@code BitmapContainer}
	 * @return	
	 */
	public SlideShowSwipe setBitmapContainer(BitmapContainer container){
		if (container != null)
			this.container = container;
		return this;
	}
	
	
	/**
	 * Set gesture listener
	 */
	private void setGestureListener(){
		this.setOnTouchListener(new OnTouchListener(){
			
			@Override
			public boolean onTouch(View v, MotionEvent e) {
				if (e.getAction() == MotionEvent.ACTION_DOWN){
				
					v0 = 0;
					vC = 0;
					
					xStartRaw = e.getRawX();
					xStart = e.getRawX() - deltaX;
					timeStart = System.currentTimeMillis();
					//Log.d("debug", "Touch down: " + startX);
					
				} else if (e.getAction() == MotionEvent.ACTION_MOVE) {
					
					deltaXPrec = deltaX;
					deltaX = e.getRawX() - xStart;
					float dt = (float)(System.currentTimeMillis() - timeStart) / 1000f;
					if (dt > 0.02){
						v0 = v0 * 0.25f + 0.75f * (e.getRawX() - xStartRaw) / dt;
						xStartRaw = e.getRawX();
						timeStart = System.currentTimeMillis();
					}

				    self.invalidate();
				    
				} else if (e.getAction() == MotionEvent.ACTION_UP){
					
					timeStart = System.currentTimeMillis();
					vC = v0;
					xCPrec = 0;
					kV = vC > 0 ? Math.abs(kV) : -Math.abs(kV);
					Log.d("debug", "Start self motion: " + v0);
					self.invalidate();
				}
				return true;
			}

		});
	}
	
	
	/**
	 * {@code onSizeChange} override
	 */
	@Override
	protected void onSizeChanged (int w, int h, int oldw, int oldh){
		super.onSizeChanged(w, h, oldw, oldh);
		// update dimensions
		rectDimensions.left = 0;
		rectDimensions.right = w;
		rectDimensions.top = 0;
		rectDimensions.bottom = h;
		// update decceleration coefficient
		kV = kVScreen * w;
	}
	
	
	/**
	 * {@code onDraw} override
	 */
	@Override
	protected void onDraw(Canvas c){
		super.onDraw(c);

		// if nothing is drawn yet
		if (bitmapFront == null){
			bitmapFront = container.getBitmapCurrent();
			bitmapBack = container.getBitmapCurrent();
			if (bitmapFront != null){
				 makeCalculations(c);
			     c.drawBitmap(bitmapFront, null, rectDstF, null);	
			}
		} else {
			makeCalculations(c);
			c.drawBitmap(bitmapFront, null, rectDstF, paintAlphaF);
			c.drawBitmap(bitmapBack, null, rectDstB, paintAlphaB);
			if (v0 != 0 && vC != 0)
				self.invalidate();
		}
	}
	
	
	/**
	 * Process calculations of positions at which bitmaps should be drawn, 
	 * depending on swipe and slideshow status
	 * 
	 * @param c		Canvas 
	 */
	private void makeCalculations(Canvas c){
		
		// perform self motion
		if (v0 != 0 && vC != 0)
		{
			float t = (System.currentTimeMillis() - timeStart) / 1000f;
			vC = v0 - kV * t;
			xC = v0 * t - kV * t * t / 2f;
			deltaX += xC - xCPrec;
			xCPrec = xC;
			if ((vC < 0 && v0 > 0) || (vC > 0 && v0 < 0)){ // velocity changed sign: stop self motion
				vC = 0;
				v0 = 0;
			}
		}
		
		// normalize deltas if image crossed opposite canvas border
		while (deltaX > c.getWidth()){
			xStart += c.getWidth();
			deltaXPrec -= c.getWidth();
			deltaX -= c.getWidth();
			bitmapFront = bitmapBack;
			rectDstFOrig = rectDstBOrig; 
		}
		while (deltaX < - c.getWidth()){
			xStart -= c.getWidth();
			deltaXPrec += c.getWidth();
			deltaX += c.getWidth();
			bitmapFront = bitmapBack;
			rectDstFOrig = rectDstBOrig;
		}
		
		// proceed various movement situations
		if (deltaXPrec == 0){
			if (deltaX == 0){  
				rectDstFOrig = calculateRectDst(bitmapFront, rectDimensions);
			} else if (deltaX > 0){ // started moving
				bitmapBack = container.getBitmapPrevious();
				rectDstBOrig = calculateRectDst(bitmapBack, rectDimensions);
			} else if (deltaX < 0) {
				bitmapBack = container.getBitmapNext();
				rectDstBOrig = calculateRectDst(bitmapBack, rectDimensions);
			}
			rectDstBOrig = calculateRectDst(bitmapBack, rectDimensions);
			
		} else if (deltaX > 0 && deltaXPrec < 0){ // left side of back image crossed left border of view
        	if (bitmapFront != bitmapBack)
        		container.undoGetBitmap();
			bitmapBack = container.getBitmapPrevious();
			rectDstBOrig = calculateRectDst(bitmapBack, rectDimensions);
			
		} else if (deltaX < 0 && deltaXPrec > 0){ // right side of back image crossed right border of view
        	if (bitmapFront != bitmapBack)
        		container.undoGetBitmap();
			bitmapBack = container.getBitmapNext();
			rectDstBOrig = calculateRectDst(bitmapBack, rectDimensions);
		}
		
		deltaXPrec = deltaX;
		
		rectDstF.left = rectDstFOrig.left + (int)deltaX;
		rectDstF.top = rectDstFOrig.top;
		rectDstF.right = rectDstFOrig.right + (int)deltaX;
		rectDstF.bottom = rectDstFOrig.bottom; 
		
		rectDstB.left = (int)deltaX - rectDstBOrig.right;
		rectDstB.right = (int)deltaX - rectDstBOrig.left;
		rectDstB.top = rectDstBOrig.top;
		rectDstB.bottom = rectDstBOrig.bottom;
		if (deltaX < 0){
			rectDstB.left += 2 * c.getWidth();
			rectDstB.right += 2 * c.getWidth();
		} 
		
		paintAlphaB.setAlpha((int) (255f * Math.abs(deltaX / c.getWidth()) ));
		paintAlphaF.setAlpha((int) (255f * (1f - Math.abs(deltaX / c.getWidth()))));
	}
	
	
	/**
	 * Returns rectangle that contains position of a given bitmap in coordinates of destination 
	 * rectangle, such that the bitmap fits it aligned to center and scaled proportionally
	 * 
	 * @param b		source bitmap
	 * @param d		destination rectangle
	 * @return		position in coordinates of destination rectangle
	 */
	static private Rect calculateRectDst(Bitmap b, Rect d){
		Rect dst = new Rect();
		if ((float)b.getWidth()/(float)b.getHeight() < (float)d.width()/(float)d.height()){
			dst.left = (int)((float)d.width() / 2f - (float)b.getWidth() * (float)d.height() / (float)b.getHeight() / 2f);
			dst.right = (int)((float)d.width() / 2f + (float)b.getWidth() * (float)d.height() / (float)b.getHeight() / 2f);
			dst.top = 0;
			dst.bottom = d.height();
		} else {
			dst.left = 0;
			dst.right = d.width();
			dst.top = (int)((float)d.height() / 2f - (float)b.getHeight() * (float)d.width() / (float)b.getWidth() / 2f);
			dst.bottom = (int)((float)d.height() / 2f + (float)b.getHeight() * (float)d.width() / (float)b.getWidth() / 2f);
		}
		return dst;
	}
	
}
