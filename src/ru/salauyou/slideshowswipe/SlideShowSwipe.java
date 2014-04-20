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

	/**
	 * Interface to control bitmap container
	 */
	public interface BitmapContainer {
		
		/**
		 * Request of next bitmap. Container should shift its cursor to next bitmap and return it 
		 */
		public Bitmap getBitmapNext();
		
		/**
		 * Request of previous bitmap. Container should shift its cursor to previous bitmap and return it
		 */
		public Bitmap getBitmapPrevious();
		
		/**
		 * Request of current bitmap. Container shoud return bitmap at its current cursor position.
		 * 
		 * Container must return the same (or similar) bitmap as returned by preceding {@code getBitmapCurrent()},
		 * {@code getBitmapNext()} or {@code getBitmapPrevious()} invocation.
		 */
		public Bitmap getBitmapCurrent();
		
		/**
		 * Request to restore cursor position to the position before the latest request. 
		 * 
		 * Container should restore its cursor position to the position right before the latest request.
		 * For example, if previously {@code getBitmapCurrent()} returned bitmap A, then {@code getBitmapNext()} 
		 * and {@code undoGetBitmap()} were called, invocation of {@code getBitmapCurrent()} must return 
		 * the same (or similar) bitmap A, independently of any changes that could happen to bitmap container 
		 * between such calls. It is guaranteed that {@code undoGetBitmap()} will be invoked at most once after 
		 * each {@code getBitmapNext} or {@code getBitmapPrevious()} request. 
		 */
		public void undoGetBitmap();
		
	}
	
	/**
	 * Interface to send callbacks when state is changed
	 */
	public interface OnStateChangeListener {
		
		/**
		 * Invoked when the view changed its status
		 */
		public void onStateChange(SlideShowSwipe.State state);
		
		/**
		 * Invoked when current bitmap was changed and now is demonstrated during slide show 
		 * or as the result of user interaction. After this callback, current displayed bitmap
		 * can be obtained by {@code BitmapContainer.getBitmapCurrent()} method
		 */
		public void onCurrentBitmapChange();
	}
	
	/**
	 * Possible states of the view
	 */
	public enum State{
		STOPPED, TOUCHED, RELEASED, NEXT_SLIDE; 
	}
	
	final private SlideShowSwipe self = this;
	
	private BitmapContainer container;
	private Bitmap bitmapFront, bitmapBack;
	
	private State stateCurrent = State.STOPPED;
	private OnStateChangeListener stateChangeListener;
	
	private Rect rectDstBOrig, rectDstFOrig;
	private Rect rectDstF = new Rect();
	private Rect rectDstB = new Rect();
	private Rect rectDimensions = new Rect();
	private Paint paintAlphaF = new Paint();
	private Paint paintAlphaB = new Paint();
	
	private boolean started = false;
	private boolean startedMove = false;
	private float deltaX, deltaXPrec, xStart, xStartRaw;
	private long timeStart;
	private float v0, vC; // start and calculated velocity
	private float xC, xCPrec; // start x, calculated x and preceeding calculated x
	
	private float kVScreen = 4f;	// deceleration coefficient relative to view width
	private float kV; // deceleration coefficient in px/s^2
	
	private boolean undoAtZero = false;
	
	

	/* ===================== Publc methods ====================== */
	
	/**
	 * Sets bitmap container to be controlled by this view
	 * 
	 * @throws NullPointerException	
	 */
	public SlideShowSwipe setBitmapContainer(BitmapContainer container) throws NullPointerException{
		if (container == null)
			throw new NullPointerException("Container is null");
		this.container = container;
		return this;
	}
	
	/**
	 * Sets listener of state changes
	 * 
	 * @throws NullPointerException
	 */
	public SlideShowSwipe setOnStateChangeListener(OnStateChangeListener listener) throws NullPointerException{
		if (listener == null)
			throw new NullPointerException("Listener is null");
		this.stateChangeListener = listener;
		return this;
	}
	
	/**
	 * Gets bitmap container controlled by this view
	 * 
	 * @return
	 */
	public SlideShowSwipe.BitmapContainer getBitmapContainer(){
		return this.container;
	}
	
	/**
	 * Returns the current state of this view
	 */
	public State getState(){
		return this.stateCurrent;
	}
	
	
	
	/* ============== Constructors inherited from View ================ */
	
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
	
	
	/* ================ Protected and private methods ================== */
	
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
					
					stateChanged(State.TOUCHED);
					
				} else if (e.getAction() == MotionEvent.ACTION_MOVE) {
					
					deltaX = e.getRawX() - xStart;
					float dt = (float)(System.currentTimeMillis() - timeStart) / 1000f;
					
					// threshold to avoid very small dt 
					if (dt > 0.02){
						v0 = v0 * 0.25f + 0.75f * (e.getRawX() - xStartRaw) / dt;
						xStartRaw = e.getRawX();
						timeStart = System.currentTimeMillis();
						Log.d("debug", "v0: " + v0);
					}
					
				    self.invalidate();
				    
				} else if (e.getAction() == MotionEvent.ACTION_UP){
					
					timeStart = System.currentTimeMillis();
					
					// correct deceleration coefficient sign
					kV = v0 > 0 ? +Math.abs(kV) : -Math.abs(kV);
					
					// correct start velocity
					// calculate the x point where self motion will stop
					double xEnd = v0 * v0 / 2f / kV;
					
					// swipe not strong enough to launch motion
					if (Math.abs(xEnd) < rectDimensions.width() / 2){ 
						
						// but strong enough to switch photo?
						boolean strong = Math.abs(xEnd) >= rectDimensions.width() / 4; 
						float w = rectDimensions.width();
						
						if (deltaX >= 0 && deltaX < w / 2 ){
							if (strong){
								xEnd = w - deltaX;
								v0 = +1;
							} else {
								xEnd = -deltaX;
								v0 = -1;	
							}
						} else if (deltaX < 0 && deltaX > -w / 2){
							if (strong){
								xEnd = w + deltaX;
								v0 = -1;
							} else {
								xEnd = -deltaX;
								v0 = +1;
							}
						} else if (deltaX >= 0 && deltaX >= w / 2){
							xEnd = w - deltaX;
							v0 = +1;		
						} else if (deltaX < 0 && deltaX < -w / 2){						
							xEnd = -w - deltaX;
							v0 = -1;			
						} 
						
					} else {
						// correct ending point such that motion will stop when full image is displayed
						xEnd = Math.round((xEnd + deltaX)/rectDimensions.width()) * rectDimensions.width() - deltaX;
					}
					
					// correct deceleration coefficient sign
					kV = v0 > 0 ? +Math.abs(kV) : -Math.abs(kV);
					
					// calculate corrected velocity 
					v0 = Math.signum(v0) * (float) Math.sqrt(Math.abs(2.0 * kV * xEnd));
					vC = v0;
					xCPrec = 0;
					
					stateChanged(State.RELEASED);
					
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
		if (!started){  
			bitmapFront = container.getBitmapCurrent();
			bitmapBack = bitmapFront;
			makeCalculations(c);
		    if (bitmapFront != null)
		    	c.drawBitmap(bitmapFront, null, rectDstF, null);	
		} else {
			makeCalculations(c);
			if (bitmapFront != null)
				c.drawBitmap(bitmapFront, null, rectDstF, paintAlphaF);
			if (bitmapBack != null)
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
		
		// first call
		if (!started){
			if (bitmapFront != null)
				rectDstFOrig = calculateRectDst(bitmapFront, rectDimensions);
			rectDstBOrig = rectDstFOrig;
			started = true;
			bitmapChanged();
		}
		
		// first call from stable state
		if (!startedMove){
			if (deltaX > 0){
				bitmapBack = container.getBitmapPrevious();
				if (bitmapBack != null)
					rectDstBOrig = calculateRectDst(bitmapBack, rectDimensions);
				startedMove = true;
			} else if (deltaX < 0){
				bitmapBack = container.getBitmapNext();
				if (bitmapBack != null)
					rectDstBOrig = calculateRectDst(bitmapBack, rectDimensions);
				startedMove = true;
			}
		}
		

		// perform self motion
		if (v0 != 0 && vC != 0)
		{
			float t = (System.currentTimeMillis() - timeStart) / 1000f;
			vC = v0 - kV * t;
			xC = v0 * t - kV * t * t / 2f;
			deltaX += xC - xCPrec;
			xCPrec = xC;
			
			// velocity changed sign: stop self motion
			if ((vC < 0 && v0 > 0) || (vC > 0 && v0 < 0)){ 
	
				// if image didn't reach opposite edge of view 
				if (v0 < 0 && deltaX < -c.getWidth() / 2 && deltaX > -c.getWidth()){
					deltaX = -c.getWidth();
				} else if (v0 > 0 && deltaX > c.getWidth() / 2 && deltaX < c.getWidth()){
					deltaX = c.getWidth();
				} else {
					deltaX = 0;
					deltaXPrec = 0;
				}
				vC = 0;
				v0 = 0;
				stateChanged(State.STOPPED);
			}
		}
		
		// normalize deltas if image crossed opposite canvas border
		while (deltaX >= c.getWidth()){
			xStart += c.getWidth();
			deltaXPrec -= c.getWidth();
			deltaX -= c.getWidth();
			bitmapFront = bitmapBack;
			rectDstFOrig = rectDstBOrig; 
			undoAtZero = false;
			bitmapChanged();
		} 
		while (deltaX <= -c.getWidth()){
			xStart -= c.getWidth();
			deltaXPrec += c.getWidth();
			deltaX += c.getWidth();
			bitmapFront = bitmapBack;
			rectDstFOrig = rectDstBOrig;
			undoAtZero = false;
			bitmapChanged();
		}
		
		// called when movement stops to cancel previous bitmap request 
		if (startedMove && deltaX == 0 && deltaXPrec == 0 && undoAtZero){
			container.undoGetBitmap();
			bitmapBack = bitmapFront;
			rectDstBOrig = rectDstFOrig;
			bitmapChanged();
		}
		

		// left side of back image crossed left border of view
		if (deltaX > 0 && deltaXPrec <= 0){ 
        	if (bitmapFront != bitmapBack)
        		container.undoGetBitmap();
			bitmapBack = container.getBitmapPrevious();
			if (bitmapBack != null)
				rectDstBOrig = calculateRectDst(bitmapBack, rectDimensions);
			undoAtZero = true;
			
		// right side of back image crossed right border of view	
		} else if (deltaX < 0 && deltaXPrec >= 0){ 
        	if (bitmapFront != bitmapBack)
        		container.undoGetBitmap();
			bitmapBack = container.getBitmapNext();
			if (bitmapBack != null)
				rectDstBOrig = calculateRectDst(bitmapBack, rectDimensions);
			undoAtZero = true;
		}

		deltaXPrec = deltaX;
		
		if (rectDstFOrig != null){
			rectDstF.left = rectDstFOrig.left + (int)deltaX;
			rectDstF.top = rectDstFOrig.top;
			rectDstF.right = rectDstFOrig.right + (int)deltaX;
			rectDstF.bottom = rectDstFOrig.bottom; 
		}
		
		if (rectDstBOrig != null){
			rectDstB.left = (int)deltaX - rectDstBOrig.right;
			rectDstB.right = (int)deltaX - rectDstBOrig.left;
			rectDstB.top = rectDstBOrig.top;
			rectDstB.bottom = rectDstBOrig.bottom;
			if (deltaX < 0){
				rectDstB.left += 2 * c.getWidth();
				rectDstB.right += 2 * c.getWidth();
			} 
		}
			
		
		
		paintAlphaB.setAlpha((int) (255f * Math.abs(deltaX / c.getWidth()) ));
		paintAlphaF.setAlpha((int) (255f * (1f - Math.abs(deltaX / c.getWidth()))));
	}
	
	
	/**
	 *	Set current state to changed and perform listener callback 
	 */
	private void stateChanged(State s){
		if (s != stateCurrent){
			stateCurrent = s;
			if (stateChangeListener != null)
				stateChangeListener.onStateChange(s);
		}
	}
	
	/**
	 * Notify that current displaying bitmap was changed
	 */
	private void bitmapChanged(){
		if (stateChangeListener != null)
			stateChangeListener.onCurrentBitmapChange();
	}
	
	/**
	 * Returns rectangle that contains position of a given bitmap in coordinates of destination 
	 * rectangle, such that the bitmap fits it aligned to center and scaled proportionally
	 * 
	 * @param b		source bitmap
	 * @param d		destination rectangle
	 * @return		position in coordinates of destination rectangle
	 * @throws	NullPointerException
	 */
	static private Rect calculateRectDst(Bitmap b, Rect d) throws NullPointerException {
		if (b == null) 
			throw new NullPointerException("Bitmap is null");
		if (d == null)
			throw new NullPointerException("Destination Rect object is null");
		
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
