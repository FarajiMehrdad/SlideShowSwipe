package ru.salauyou.slideshowswipe;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;


public class SlideShowSwipe extends View {

	
	/* =================== Publc interfaces ==================== */
	
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
	
	
	
	/* ===================== Publc fields ====================== */
	
	/**
	 * Possible states of the slide show
	 */
	public enum State{ 
		RESET, SLIDESHOW_STARTED, NEXT_SLIDE, SLIDESHOW_PAUSED; 
	}
	
	static public long PERIOD_DEFAULT = 1500L;
	static public long TRANSITION_DEFAULT = 500L;
	
	
    
	/**
	 * Sets bitmap container to be controlled by this view
	 * 
	 * @throws NullPointerException	
	 */
	public SlideShowSwipe setBitmapContainer(BitmapContainer container) throws NullPointerException{
		if (container == null)
			throw new NullPointerException("Container is null");
		this.container = container;
		reset();
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
	
	
	
	/**
	 * Sets period of demonstration of every slide in slideshow
	 * 
	 * @param period	period in ms. If {@code period <= 0}, is set to default (1500 ms)
	 * @return
	 */
	public SlideShowSwipe setSlideShowPeriod(long period){
		if (period <= 0)
			this.period = PERIOD_DEFAULT;
		else
			this.period = period;
		transition = Math.min(transition, this.period / 2);
		return this;
	}
	
	
	
	/**
	 * Sets duration of transition between slides
	 * 
	 * @param duration duration in ms. If exceeds one half of slideshow period, replaced 
	 * by one half of slideshow period. If {@code duration < 0}, is set to default (300 ms)
	 * @return
	 */
	public SlideShowSwipe setSlideShowTransition(long transition){
		if (transition < 0)
			this.transition = Math.min(TRANSITION_DEFAULT, this.period / 2);
		else if (transition > this.period / 2)
			this.transition = this.period / 2;
		else
			this.transition = transition;
		
		return this;
	}
	
	
	
	/**
	 * Starts or unpauses slideshow, if it waw paused by {@code pauseSlideShow() or user's gesture.
	 * After creating the view and setting bitmap container, revoke {@code startSlideShow()} 
	 * to launch demonstration
	 */
	public void startSlideShow(){
		if (pausedManually || paused){
			pausedManually = false;
			if (paused && container != null && started){
				unPause();
			} else {
				stateChanged(State.SLIDESHOW_STARTED);
				this.invalidate();
			}
		}
	}
	
	
	
	/**
	 * Forces slideshow to pause. Clicks on the view will not resume slideshow demonstration
	 * until {@code startSlideShow()} is invoked
	 */
	public void pauseSlideShow(){
		if (!pausedManually){
			pausedManually = true;
			pause();
		}
	}
	
	
	/**
	 * Restores slideshow to demonstrate the current bitmap. It must not be null.
	 * To make the view wait for non-null current bitmap, use {@code startSlideShow()} instead
	 * 
	 * @param paused	if slideshow demonstration should be paused
	 * @throws NullPointerException		if bitmap container or current bitmap is null
	 */
	public void restoreCurrent(boolean paused) throws NullPointerException{
		if (container == null)
			throw new NullPointerException("Container is null");
		if (container.getBitmapCurrent() == null)
			throw new NullPointerException("Current bitmap is null");
		
		bitmapPrec = bitmapFront = container.getBitmapCurrent();
		
		started = true;
		pausedManually = false;
		if (paused)
			pause();
		else
			unPause();
		
		this.invalidate();
	}
	
	
	/* ============== Constructors inherited from View ================ */
	
	public SlideShowSwipe(Context context) {
		super(context);
		//setGestureListener();
	}

	public SlideShowSwipe(Context context, AttributeSet attrs) {
		super(context, attrs);
		//setGestureListener();
	}

	public SlideShowSwipe(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		//setGestureListener();
	}
	
	
	
	/* ================ Protected and private fields ================== */
	
	final private SlideShowSwipe self = this;
	
	private BitmapContainer container;
	private Bitmap bitmapFront, bitmapBack, bitmapPrec;
	
	private State stateCurrent = State.RESET;
	private OnStateChangeListener stateChangeListener;

	private Rect rectDimensions = new Rect();
	private Rect rectDstBOrig, rectDstFOrig, rectDstPOrig;
	private Rect rectDstF = new Rect(); // destination rect of 'front' bitmap (i. e. current)
	private Rect rectDstB = new Rect(); // destination rect of 'back' bitmap (i. e. that which is partially viewed on swipe)
	private Rect rectDstP = new Rect(); // destination rect of 'preceding' bitmap (i. e. that which fades out during slideshow transition)

	private Paint paintAlphaF = new Paint(Paint.FILTER_BITMAP_FLAG);
	private Paint paintAlphaB = new Paint(Paint.FILTER_BITMAP_FLAG);
	private Paint paintAlphaP = new Paint(Paint.FILTER_BITMAP_FLAG);
	
	private volatile boolean firstBitmapRequested = false;
	private boolean started = false; // indicates if slide show was started (i. e. bitmaps != null)
	private boolean startedMove = false; // indicates if some touch movement already occured
	private boolean pausedNow = false;
	private boolean paused = true;
	private boolean pausedManually = true;
	private boolean undoAtZero = false;

	private float touchPath;
	private float deltaX, deltaXPrec, xStart, xStartRaw, yStartRaw;
	
	private long timeTouchStart, timeStartRaw;
	private long touchTimeThreshold = 200;
	private long period = PERIOD_DEFAULT;
	private long transition = TRANSITION_DEFAULT;
	private volatile AtomicLong timeTransitionStart = new AtomicLong(-1);
	
	private float v0, vC; // start and calculated velocity
	private float xC, xCPrec; // start x, calculated x and preceeding calculated x
	private float kVScreen = 4f;	// deceleration coefficient relative to view width
	private float kV; // deceleration coefficient in px/s^2
	private float touchMoveThresholdScreen = 0.03f; // size of maximum movement to be threated as click, in screen width 
	private float touchMoveThreshold; // the same in pixels
	
	private ExecutorService scheduler;
	
	
	
	/**
	 * Set gesture listener
	 * @return 
	 */
	@Override
	public boolean onTouchEvent(MotionEvent e){
		super.onTouchEvent(e);
		if (started){
			if (e.getAction() == MotionEvent.ACTION_DOWN){
			
				v0 = 0;
				vC = 0;
			
				xStartRaw = e.getRawX();
				yStartRaw = e.getRawY();
			
				xStart = e.getRawX() - deltaX;
				timeTouchStart = timeStartRaw = System.currentTimeMillis();
			
				touchPath = 0;
			
				// pause slideshow if needed
				if (!paused && !pausedManually){
					pause();
					pausedNow = true;
				}
			
			
			} else if (e.getAction() == MotionEvent.ACTION_MOVE) {
		
				deltaX = e.getRawX() - xStart;
				float dt = (float)(System.currentTimeMillis() - timeTouchStart) / 1000f;
			
				// threshold to avoid very small dt 
				if (dt > 0.02){
					v0 = v0 * 0.25f + 0.75f * (e.getRawX() - xStartRaw) / dt;
					xStartRaw = e.getRawX();
					timeTouchStart = System.currentTimeMillis();
				}
			
				// calculate path of touch--this is needed to recognize clicks
				touchPath += Math.hypot(e.getRawX() - xStartRaw, e.getRawY() - yStartRaw);
			
				self.invalidate();
		    
			} else if (e.getAction() == MotionEvent.ACTION_UP){
			
				timeTouchStart = System.currentTimeMillis();
			
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
					xEnd = Math.round((xEnd + deltaX)/rectDimensions.width()) 
							* rectDimensions.width() - deltaX;
				}
			
				// correct deceleration coefficient sign
				kV = v0 > 0 ? +Math.abs(kV) : -Math.abs(kV);
			
				// calculate corrected velocity 
				v0 = Math.signum(v0) * (float) Math.sqrt(Math.abs(2.0 * kV * xEnd));
				vC = v0;
				xCPrec = 0;
			
				// small movement treated as touch unpauses slideshow
				if (touchPath <= touchMoveThreshold && !pausedManually && paused && !pausedNow
						&& System.currentTimeMillis() - timeStartRaw <= touchTimeThreshold)			
					unPause();
				else 
					pausedNow = false;
			
				self.invalidate();
			}
		}
		return true;
	}
	

	
	/**
	 * {@code onSizeChange()} override
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
		
		// update move/click threshold
		touchMoveThreshold = touchMoveThresholdScreen * Math.min(w, h);
	}
	
	
	
	/**
	 * {@code onDetachedFromWindow()} override
	 */
	@Override
	protected void onDetachedFromWindow(){
		super.onDetachedFromWindow();
		pause();
		vC = 0;
		v0 = 0;
		timeTransitionStart.set(-1);
	}
	
	
	
	/**
	 * {@code onDraw} override
	 */
	@Override
	protected void onDraw(Canvas c){
		super.onDraw(c);
		if (container != null){
			// if view just created or reset
			if (!started){  
				if (bitmapFront == null){
					if (!firstBitmapRequested){
						getFirstBitmap();
						firstBitmapRequested = true;
					}
				} else {
					if (!pausedManually){
						unPause();
					}
				}
			} else {
				makeCalculations();
				if (bitmapBack != null)
					c.drawBitmap(bitmapBack, null, rectDstB, paintAlphaB);
				if (timeTransitionStart.get() > 0 && bitmapPrec != null)
					c.drawBitmap(bitmapPrec, null, rectDstP, paintAlphaP);
				if (bitmapFront != null)
					c.drawBitmap(bitmapFront, null, rectDstF, paintAlphaF);
				if ((v0 != 0 && vC != 0) || timeTransitionStart.get() > 0)
					self.invalidate();
			}
		}
	}
	
	
	
	/**
	 * Runs new thread to get the first non-null bitmap from the container
	 */
	
	private void getFirstBitmap(){

		if (container.getBitmapCurrent() == null){
			
			// launch executor service to perform attempts to get current bitmap
			final ExecutorService e = Executors.newScheduledThreadPool(1);
			
			final Handler h = new Handler(){
				@Override
				public void handleMessage(Message m){
					bitmapFront = container.getBitmapCurrent();
					self.invalidate();
				}
			};
			
			((ScheduledExecutorService) e).scheduleAtFixedRate(new Runnable(){
				@Override
				public void run() {
					if (container.getBitmapCurrent() != null){
						h.sendEmptyMessage(1);
						e.shutdownNow();
					}
				}
			}, 150, 150, TimeUnit.MILLISECONDS);

		} else {
			bitmapFront = container.getBitmapCurrent();
			self.invalidate();
		}
	}
	
	
	
	/**
	 * Pauses slide show
	 */
	private void pause(){
		paused = true;
		if (scheduler != null){
			((ScheduledExecutorService)scheduler).shutdown();
			scheduler = null;
		}
		stateChanged(State.SLIDESHOW_PAUSED);
	}
	
	
	
	/**
	 * Unpauses slide show and launches transition
	 */
	private void unPause(){
		
		if (paused || pausedManually){
			
			final Handler h = new Handler(){
				@Override
				public void handleMessage(Message m){
					bitmapPrec = bitmapFront;
					rectDstPOrig = rectDstFOrig;
					bitmapFront = container.getBitmapNext();
					bitmapBack = bitmapFront;
					bitmapChanged();
					if (bitmapFront != null)
						rectDstBOrig = rectDstFOrig = calculateRectDst(bitmapFront, rectDimensions);
					makeCalculations();
					
					stateChanged(State.NEXT_SLIDE);
					
					self.invalidate();
				}
			};
		
			long p = bitmapPrec == null ? 0 : period;

			// timer that performs slide changes
			scheduler = Executors.newScheduledThreadPool(1);
			((ScheduledExecutorService)scheduler).scheduleAtFixedRate(new Runnable(){
				@Override
				public void run() {
					timeTransitionStart.set(System.currentTimeMillis());
					h.sendEmptyMessage(1);
				}
			}, p, period, TimeUnit.MILLISECONDS);
			
			paused = false;
			stateChanged(State.SLIDESHOW_STARTED);
			
			self.invalidate();
		}
		
	}
	
	
	
	/**
	 * Process calculations of positions at which bitmaps should be drawn, 
	 * depending on swipe and transition between bitmaps
	 */
	private void makeCalculations(){
		
		// special case: calculate destination rect in case of restoring slide show position
		if (bitmapFront != null && rectDstFOrig == null)
			rectDstFOrig = calculateRectDst(bitmapFront, rectDimensions);
	
		
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

		float w = rectDimensions.width();
		
		// perform self motion
		if (v0 != 0 && vC != 0)
		{
			float t = (System.currentTimeMillis() - timeTouchStart) / 1000f;
			vC = v0 - kV * t;
			xC = v0 * t - kV * t * t / 2f;
			deltaX += xC - xCPrec;
			xCPrec = xC;
			
			// velocity changed sign: stop self motion
			if ((vC < 0 && v0 > 0) || (vC > 0 && v0 < 0)){ 
	
				// correct image position on motion stop
				if (v0 < 0 && deltaX < -0.5 * w && deltaX > -1.5 * w){
					deltaX = -w;
					deltaXPrec = -w;
				} else if (v0 > 0 && deltaX > 0.5 * w && deltaX < 1.5 * w){
					deltaX = w;
					deltaXPrec = w;
				} else {
					deltaX = 0;
					deltaXPrec = 0;
				}
				vC = 0;
				v0 = 0;
			}
		}
		
		// normalize deltas if image crossed opposite canvas border
		while (deltaX >= w){
			xStart += w;
			deltaXPrec -= w;
			deltaX -= w;
			bitmapFront = bitmapBack;
			rectDstFOrig = rectDstBOrig; 
			undoAtZero = false;
			bitmapChanged();
		} 
		while (deltaX <= -w){
			xStart -= w;
			deltaXPrec += w;
			deltaX += w;
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
			undoAtZero = false;
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
		
		// calculate destination rectangles
		if (rectDstFOrig != null){
			rectDstF.left = rectDstFOrig.left + (int)deltaX;
			rectDstF.top = rectDstFOrig.top;
			rectDstF.right = rectDstFOrig.right + (int)deltaX;
			rectDstF.bottom = rectDstFOrig.bottom; 
		}
		if (rectDstPOrig != null && timeTransitionStart.get() > 0){
			rectDstP.left = rectDstPOrig.left + (int)deltaX;
			rectDstP.top = rectDstPOrig.top;
			rectDstP.right = rectDstPOrig.right + (int)deltaX;
			rectDstP.bottom = rectDstPOrig.bottom; 
		}
		if (rectDstBOrig != null){
			rectDstB.left = (int)deltaX - rectDstBOrig.right;
			rectDstB.right = (int)deltaX - rectDstBOrig.left;
			rectDstB.top = rectDstBOrig.top;
			rectDstB.bottom = rectDstBOrig.bottom;
			if (deltaX < 0){
				rectDstB.left += 2 * w;
				rectDstB.right += 2 * w;
			} 
		}
			
		// set alpha values to paints
		paintAlphaB.setAlpha((int) (127f + 128f * Math.abs(deltaX / w)));
		paintAlphaF.setAlpha((int) (127f + 128f * (1f - Math.abs(deltaX / w))));
		
		// calculate transparencies on transition
		long tStart = timeTransitionStart.get();
		if (tStart > 0){
			long t = System.currentTimeMillis();
			float a = (float)(t - tStart) / (float)transition;
			if (a > 1f)
				a = 1f;
			paintAlphaP.setAlpha((int) ((float)paintAlphaF.getAlpha() * (1f - a)));
			paintAlphaF.setAlpha((int) ((float)paintAlphaF.getAlpha() * a));
			
			if (t >= tStart + transition)
				timeTransitionStart.set(-1);
		}
		
	}
	

	
	/**
	 *	Set current state to changed and perform listener callback 
	 */
	private void stateChanged(State s){
		if (s != stateCurrent || s == State.NEXT_SLIDE){
			stateCurrent = s;
			if (stateChangeListener != null)
				stateChangeListener.onStateChange(s);
		}
	}
	
	
	
	/**
	 * Notify listener that current displaying bitmap was changed
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
	static public Rect calculateRectDst(Bitmap b, Rect d) throws NullPointerException {
		if (b == null) 
			throw new NullPointerException("Bitmap is null");
		if (d == null)
			throw new NullPointerException("Destination Rect object is null");
		
		Rect dst = new Rect();
		
		if ((float)b.getWidth()/(float)b.getHeight() < (float)d.width()/(float)d.height()){
			dst.left = (int)((float)d.width() / 2f 
					- (float)b.getWidth() * (float)d.height() / (float)b.getHeight() / 2f);
			dst.right = (int)((float)d.width() / 2f 
					+ (float)b.getWidth() * (float)d.height() / (float)b.getHeight() / 2f);
			dst.top = 0;
			dst.bottom = d.height();
			
		} else {
			
			dst.left = 0;
			dst.right = d.width();
			dst.top = (int)((float)d.height() / 2f 
					- (float)b.getHeight() * (float)d.width() / (float)b.getWidth() / 2f);
			dst.bottom = (int)((float)d.height() / 2f 
					+ (float)b.getHeight() * (float)d.width() / (float)b.getWidth() / 2f);
		}
		
		return dst;
	}
	
	
	/**
	 * Resets bitmaps, rects and values
	 */
	private void reset(){
		pause();
		
		started = false;
		startedMove = false;
				
		rectDstBOrig = null;
		rectDstFOrig = null;
		rectDstPOrig = null;
		bitmapFront = null;
		bitmapBack = null;
		bitmapPrec = null;
		
		undoAtZero = false;

		v0 = 0;
		vC = 0;
		deltaX = 0;
		deltaXPrec = 0;
		
		firstBitmapRequested = false;
		pausedManually = true;
		
		timeTransitionStart.set(-1);
		
		stateChanged(State.RESET);
		
		this.invalidate();
	}
}
