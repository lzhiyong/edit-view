package com.text.edit;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.OverScroller;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;


public class HighlightTextView extends View {

    private Paint mPaint;
    private TextPaint mTextPaint;

    // cursor and select handle drawable resources
    private Drawable mDrawableCursorRes;
    private Drawable mTextSelectHandleLeftRes;
    private Drawable mTextSelectHandleRightRes;
    private Drawable mTextSelectHandleMiddleRes;

    private int mCursorPosX, mCursorPosY;
    private int mCursorLine, mCursorIndex;
    private int mCursorWidth, mCursorHeight;

    private int screenWidth, screenHeight;
    private int lineWidth, spaceWidth;
    private int handleMiddleWidth, handleMiddleHeight;
    private int selectionStart, selectionEnd;

    private int selectHandleWidth, selectHandleHeight;
    private int selectHandleLeftX, selectHandleLeftY;
    private int selectHandleRightX, selectHandleRightY;

    private GapBuffer mGapBuffer;
    private UndoStack mUndoStack;

    private OnTextChangedListener mTextListener;
    private OverScroller mScroller;
    private GestureDetector mGestureDetector;
    private GestureListener mGestureListener;
    private ScaleGestureDetector mScaleGestureDetector;
    private ClipboardManager mClipboard;
    private ArrayList<Pair> mReplaceList;

    private boolean mCursorVisiable = true;
    private boolean mHandleMiddleVisable = false;
    private boolean isEditedMode = true;
    private boolean isSelectMode = false;

    private String mDefaultText;
    private long mLastScroll;
    // record last single tap time
    private long mLastTapTime;
    // left margin for draw text
    private final int SPACEING = 100;
    // animation duration 250ms
    private final int DEFAULT_DURATION = 250;
    // cursor blink BLINK_TIMEOUT 500ms
    private final int BLINK_TIMEOUT = 500;

    private final String TAG = this.getClass().getSimpleName();

    public HighlightTextView(Context context) {
        super(context);
        initView(context);
    }

    public HighlightTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public HighlightTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }

    private void initView(Context context) {
        mGapBuffer = new GapBuffer();
        mCursorLine = getLineCount();
        
        screenWidth = ScreenUtils.getScreenWidth(context);
        screenHeight = ScreenUtils.getScreenHeight(context);

        mDrawableCursorRes = context.getDrawable(R.drawable.abc_text_cursor_material);
        mDrawableCursorRes.setTint(Color.MAGENTA);

        mCursorWidth = mDrawableCursorRes.getIntrinsicWidth();
        mCursorHeight = mDrawableCursorRes.getIntrinsicHeight();

        // set cursor width
        if(mCursorWidth > 5) mCursorWidth = 5;

        // handle left
        mTextSelectHandleLeftRes = context.getDrawable(R.drawable.abc_text_select_handle_left_mtrl_dark);
        mTextSelectHandleLeftRes.setTint(Color.MAGENTA);
        mTextSelectHandleLeftRes.setColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN);

        selectHandleWidth = mTextSelectHandleLeftRes.getIntrinsicWidth();
        selectHandleHeight = mTextSelectHandleLeftRes.getIntrinsicHeight();

        // handle right
        mTextSelectHandleRightRes = context.getDrawable(R.drawable.abc_text_select_handle_right_mtrl_dark);
        mTextSelectHandleRightRes.setTint(Color.MAGENTA);

        // handle middle
        mTextSelectHandleMiddleRes = context.getDrawable(R.drawable.abc_text_select_handle_middle_mtrl_dark);
        mTextSelectHandleMiddleRes.setTint(Color.MAGENTA);
        handleMiddleWidth = mTextSelectHandleMiddleRes.getIntrinsicWidth();
        handleMiddleHeight = mTextSelectHandleMiddleRes.getIntrinsicHeight();

        mGestureListener = new GestureListener();
        mGestureDetector = new GestureDetector(context, mGestureListener);
        //mGestureDetector.setIsLongpressEnabled(false);
        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureListener());

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        setTextSize(ScreenUtils.dip2px(context, 18));
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.GREEN);
        mPaint.setStrokeWidth(5);

        mScroller = new OverScroller(context);
        mClipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        mUndoStack = new UndoStack();
        mReplaceList = new ArrayList<>();

        mDefaultText = getResources().getString(R.string.default_text);
        spaceWidth = (int) mTextPaint.measureText(" ");

        requestFocus();
        setFocusable(true);
        postDelayed(blinkAction, BLINK_TIMEOUT);
    }

    // cursor blink
    private Runnable blinkAction = new Runnable() {
        @Override
        public void run() {
            // TODO: Implement this method
            mCursorVisiable = !mCursorVisiable;
            postDelayed(blinkAction, BLINK_TIMEOUT);

            if(System.currentTimeMillis() - mLastTapTime >= 5 * BLINK_TIMEOUT) {
                mHandleMiddleVisable = false;
            }
            postInvalidate();
        }
    };

    public void setBuffer(GapBuffer buffer) {
        mGapBuffer = buffer;
        invalidate();
    }
    
    public GapBuffer getBuffer() {
        return this.mGapBuffer;
    }

    public void setText(String text) {
        mGapBuffer = new GapBuffer(text);
        invalidate();
    }

    // the text size unit is px
    public void setTextSize(float px) {
        // min text size 10dp
        float min = ScreenUtils.dip2px(getContext(), 10);
        // max text size 30dp
        float max = ScreenUtils.dip2px(getContext(), 30);

        if(px < min) px = min;
        if(px > max) px = max;

        mTextPaint.setTextSize(px);
        
        adjustCursorPosition();
        if(isSelectMode)
            adjustSelectRange(selectionStart, selectionEnd);
            
        postInvalidate();
    }

    public void setEditedMode(boolean editMode) {
        isEditedMode = editMode;
    }

    public boolean getEditedMode() {
        return isEditedMode;
    }

    public void setTypeface(Typeface typeface) {
        mTextPaint.setTypeface(typeface);
    }

    public void setOnTextChangedListener(OnTextChangedListener listener) {
        mTextListener = listener;
    }

    public TextPaint getPaint() {
        return mTextPaint;
    }

    public UndoStack getUndoStack() {
        return mUndoStack;
    }

    private int getLeftSpace() {
        return getPaddingLeft() + getLineNumberWidth() + SPACEING;
    }

    public int measureText(String text) {
        return (int) Math.ceil(mTextPaint.measureText(text));
    }
    
    private int getLineHeight() {
        TextPaint.FontMetricsInt metrics = mTextPaint.getFontMetricsInt();
        return metrics.bottom - metrics.top;
    }

    private int getLineStart(int lineNumber) {
        return mGapBuffer.getLineOffset(lineNumber);
    }
    
    private int getOffsetLine(int offset) {
        return mGapBuffer.findLineNumber(offset);
    }
    
    public int getLineCount() {
        return mGapBuffer.getLineCount();
    }

    private int getLineNumberWidth() {
        return measureText(Integer.toString(getLineCount()));
    }

    public String getLine(int lineNumber) {
        return mGapBuffer.getLine(lineNumber);
    }
    
    private int getLineWidth(int lineNumber) {
        return measureText(getLine(lineNumber));
    }

    // Get the maximum scrollable width
    public int getMaxScrollX() {
        return Math.max(0, getLeftSpace() + lineWidth + spaceWidth * 4 - getWidth());
    }

    // Get the maximum scrollable height
    public int getMaxScrollY() {
        return Math.max(0, (getLineCount() + 2) * getLineHeight() - getHeight());
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param dx the number of pixels to scroll by on the X axis
     * @param dy the number of pixels to scroll by on the Y axis
     */
    public final void smoothScrollBy(int dx, int dy) {
        if(getHeight() == 0) {
            // Nothing to do.
            return;
        }
        long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
        if(duration > DEFAULT_DURATION) {
            mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
            postInvalidateOnAnimation();
        } else {
            if(!mScroller.isFinished()) {
                mScroller.abortAnimation();
            }
            scrollBy(dx, dy);
        }
        mLastScroll = AnimationUtils.currentAnimationTimeMillis();
    }


    /**
     * Like {@link #scrollTo}, but scroll smoothly instead of immediately.
     *
     * @param x the position where to scroll on the X axis
     * @param y the position where to scroll on the Y axis
     */
    public final void smoothScrollTo(int x, int y) {
        smoothScrollBy(x - getScrollX(), y - getScrollY());
    }


    @Override
    public void computeScroll() {
        // TODO: Implement this method
        super.computeScroll();
        if(mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 
                             MeasureSpec.getSize(heightMeasureSpec));
    }

    // draw line background
    public void drawLineBackground(Canvas canvas) {

        if(!isSelectMode) {
            // draw current line background
            int left = getLeftSpace();
            canvas.drawRect(left,
                            getPaddingTop() + mCursorPosY,
                            getScrollX() + screenWidth,
                            mCursorPosY + getLineHeight(),
                            mPaint
                            );
        } else {
            // draw select text background
            //Path path = new Path();
            //path.moveTo(selectHandleLeftX, selectHandleLeftY - getLineHeight());
            mPaint.setColor(Color.YELLOW);

            int left = getLeftSpace();
            int lineHeight = getLineHeight();

            int start = selectHandleLeftY / getLineHeight();
            int end = selectHandleRightY / getLineHeight();

            // start line < end line
            if(start != end) {
                for(int i=start; i <= end; ++i) {
                    int lineWidth = getLineWidth(i) + spaceWidth;
                    if(i == start) {
                        canvas.drawRect(selectHandleLeftX, selectHandleLeftY - lineHeight,
                                        left + lineWidth, selectHandleLeftY, mPaint);
                    } else if(i == end) {
                        canvas.drawRect(left, selectHandleRightY - lineHeight,
                                        selectHandleRightX, selectHandleRightY, mPaint);
                    } else {
                        canvas.drawRect(left, (i - 1) * lineHeight,
                                        left + lineWidth, i * lineHeight, mPaint);
                    }
                }
            } else {
                // start line = end line
                canvas.drawRect(selectHandleLeftX, selectHandleLeftY - getLineHeight(),
                                selectHandleRightX, selectHandleRightY, mPaint);
            }

            mPaint.setColor(Color.GREEN);
        }
    }

    // draw text select handle
    public void drawSelectHandle(Canvas canvas) {
        if(isSelectMode) {
            // select handle left
            mTextSelectHandleLeftRes.setBounds(selectHandleLeftX - selectHandleWidth + selectHandleWidth / 4,
                                               selectHandleLeftY,
                                               selectHandleLeftX + selectHandleWidth / 4,
                                               selectHandleLeftY + selectHandleHeight
                                               );
            mTextSelectHandleLeftRes.draw(canvas);

            // select handle right
            mTextSelectHandleRightRes.setBounds(selectHandleRightX - selectHandleWidth / 4,
                                                selectHandleRightY,
                                                selectHandleRightX + selectHandleWidth - selectHandleWidth / 4,
                                                selectHandleRightY + selectHandleHeight
                                                );
            mTextSelectHandleRightRes.draw(canvas);
        }
    }

    // draw match text background
    public void drawMatchText(Canvas canvas) {
        if(isSelectMode) {
            int size = mReplaceList.size();
            int left = getLeftSpace();

            for(int i=0; i < size; ++i) {
                int start = (Integer) mReplaceList.get(i).first;
                int end = (Integer) mReplaceList.get(i).second;

                if(start == selectionStart && end == selectionEnd)
                    mPaint.setColor(Color.LTGRAY);
                else
                    mPaint.setColor(Color.CYAN);

                int line = mGapBuffer.findLineNumber(start);
                int lineStart = getLineStart(line);

                canvas.drawRect(left + measureText(mGapBuffer.substring(lineStart, start)),
                                (line - 1) * getLineHeight(),
                                left + measureText(mGapBuffer.substring(lineStart, end)),
                                line * getLineHeight(),
                                mPaint
                                );
            }
        }
    }


    // draw cursor
    public void drawCursor(Canvas canvas) {
        if(mCursorVisiable) {
            int left = getLeftSpace();
            int half = 0;
            if(mCursorPosX >= left) {
                half = mCursorWidth / 2;
            } else {
                mCursorPosX = left;
            }

            // draw text cursor 
            mDrawableCursorRes.setBounds(mCursorPosX - half,
                                         getPaddingTop() + mCursorPosY,
                                         mCursorPosX - half + mCursorWidth,
                                         mCursorPosY + getLineHeight()
                                         );
            mDrawableCursorRes.draw(canvas);
        }

        if(mHandleMiddleVisable) {
            // draw text select handle middle 
            mTextSelectHandleMiddleRes.setBounds(mCursorPosX - handleMiddleWidth / 2,
                                                 mCursorPosY + getLineHeight(),
                                                 mCursorPosX + handleMiddleWidth / 2,
                                                 mCursorPosY + getLineHeight() + handleMiddleHeight
                                                 );
            mTextSelectHandleMiddleRes.draw(canvas);
        }
    }

    // draw content text
    public void drawEditableText(Canvas canvas) {
        int startLine = Math.max(canvas.getClipBounds().top / getLineHeight(), 1);
        int endLine = Math.min(canvas.getClipBounds().bottom / getLineHeight() + 1, getLineCount());

        // the text line width
        int lineNumberWidth = getLineNumberWidth();
        lineWidth = getWidth() - lineNumberWidth;
        
        // draw text line[start..end]
        for(int i=startLine; i <= endLine; ++i) {

            int paintX = getPaddingLeft();
            // baseline
            int paintY =  i * getLineHeight() - (int)mTextPaint.descent();

            // draw line number
            mTextPaint.setColor(Color.GRAY);
            canvas.drawText(String.valueOf(i), paintX, paintY, mTextPaint);

            // draw vertical line
            canvas.drawLine(lineNumberWidth + SPACEING / 2,  (i - 1) * getLineHeight(), lineNumberWidth + SPACEING / 2, i * getLineHeight(), mPaint);

            // draw content text
            paintX += (lineNumberWidth + SPACEING);
            mTextPaint.setColor(Color.BLACK);

            String text = getLine(i);
            lineWidth = Math.max(measureText(text), lineWidth);
            
            canvas.drawText(text, paintX, paintY, mTextPaint);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO: Implement this method
        super.onDraw(canvas);
        canvas.save();

        // translate clipping region to create padding around edges
        canvas.clipRect(getScrollX() + getPaddingLeft(),
                        getScrollY() + getPaddingTop(),
                        getScrollX() + getWidth() - getPaddingRight(),
                        getScrollY() + getHeight() - getPaddingBottom());
        canvas.translate(getPaddingLeft(), getPaddingTop());

        // draw background
        Drawable background = getBackground();
        if(background != null) {
            background.draw(canvas);
        }

        drawMatchText(canvas);

        drawLineBackground(canvas);

        // draw content text
        drawEditableText(canvas);

        drawSelectHandle(canvas);

        drawCursor(canvas);

        canvas.restore();
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // TODO: Implement this method
        switch(event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mScroller.abortAnimation();
            break;
        case MotionEvent.ACTION_UP:
            mGestureListener.onUp(event);
            break;
        }

        // gesture detector
        mGestureDetector.onTouchEvent(event);
        // scale gesture detector
        mScaleGestureDetector.onTouchEvent(event);
        return true;
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO: Implement this method
        if(event.getAction() == KeyEvent.ACTION_DOWN) {
            switch(keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                insert("\n");
                break;
            case KeyEvent.KEYCODE_DEL:
                // delete char at cursor index
                delete();
                break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // TODO: Implement this method
        super.onLayout(changed, left, top, right, bottom);
        scrollToVisable();
    }

    // when insert or delete text scroll to visable
    private void scrollToVisable() {
        // horizontal direction
        int dx = 0;
        if(mCursorPosX - getScrollX() <= spaceWidth * 3) 
            dx = mCursorPosX - getScrollX() - spaceWidth * 3;  
        else if(mCursorPosX - getScrollX() >= screenWidth - spaceWidth * 2) 
            dx = mCursorPosX - getScrollX() - screenWidth + spaceWidth * 2;

        // vertical direction
        int dy = 0;
        if(mCursorPosY - getScrollY() <= 0)
            dy = mCursorPosY - getScrollY();
        else if(mCursorPosY - getScrollY() >= getHeight() - getLineHeight())
            dy = mCursorPosY - getScrollY() - getHeight() + getLineHeight();

        smoothScrollBy(dx, dy);
    }


    // Insert text
    private void insert(String text) {
        if(!isEditedMode) return; // nothing to do
        if(isSelectMode) delete();
        
        removeCallbacks(blinkAction);
        mCursorVisiable = true;
        mHandleMiddleVisable = false;
        
        mGapBuffer.insert(mCursorIndex, text);

        // calculate the cursor index and line
        int length = text.length();
        mCursorIndex += length;
        mCursorLine = getOffsetLine(mCursorIndex);
        adjustCursorPosition();
        mTextListener.onTextChanged();

        scrollToVisable();
        postInvalidate();
        postDelayed(blinkAction, BLINK_TIMEOUT);
    }

    // Delete text
    private void delete() {
        if(!isEditedMode) return; // nothing to do
        if(mCursorIndex <= 0) return;
        
        removeCallbacks(blinkAction);
        mCursorVisiable = true;
        mHandleMiddleVisable = false;
        
        if(isSelectMode) {
            isSelectMode = false;
            mGapBuffer.delete(selectionStart, selectionEnd);
            mCursorIndex -= selectionEnd - selectionStart;
        } else {
            mGapBuffer.delete(mCursorIndex - 1, mCursorIndex);
            mCursorIndex--;
        }
        
        // calculate cursor index and line
        mCursorLine = getOffsetLine(mCursorIndex);
        adjustCursorPosition();
        
        mTextListener.onTextChanged();

        scrollToVisable();
        postInvalidate();
        postDelayed(blinkAction, BLINK_TIMEOUT);
    }

    // copy text
    public void copy() {
        String text = getSelectText();
        if(text != null && !text.equals("")) {
            ClipData data = ClipData.newPlainText("content", text);
            mClipboard.setPrimaryClip(data);
        }
    }

    // cut text
    public void cut() {
        copy();
        delete();
        isSelectMode = false;
    }

    // paste text
    public void paste() {
        if(mClipboard.hasPrimaryClip()) {
            ClipDescription description = mClipboard.getPrimaryClipDescription();
            if(description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                ClipData data = mClipboard.getPrimaryClip();
                ClipData.Item item = data.getItemAt(0);
                String text = item.getText().toString();
                insert(text);
            }
        }
    }

    private void scrollToFindPosition(int curr) {
        int first = (Integer)mReplaceList.get(curr).first;
        int second = (Integer)mReplaceList.get(curr).second; 

        setCursorPosition(second);
        adjustSelectRange(first, second);

        smoothScrollTo(Math.max(0, selectHandleLeftX - getWidth() / 2),
                       Math.max(0, selectHandleLeftY - getHeight() / 2));
        postInvalidate();
    }

    // find the current item
    private int current() {
        // default return the first item
        return Collections.binarySearch(mReplaceList, 
            new Pair<Integer, Integer>(selectionStart, selectionEnd), 
            (a, b) -> {
            int result = (int)a.first - (int)b.first;
            return result == 0 ? (int)a.second - (int)b.second : result;
        });
    }

    public void prev() {
        int currIndex = current();
        int prev = --currIndex;
        if(prev < 0) {
            prev = mReplaceList.size() - 1;
        }
        scrollToFindPosition(prev);
    }

    public void next() {
        int currIndex = current();
        int next = ++currIndex;
        if(next >= mReplaceList.size()) {
            next = 0;
        }
        scrollToFindPosition(next);
    }

    // find text
    public void find(String regex) {
        if(!mReplaceList.isEmpty())
            mReplaceList.clear();

        Matcher matcher = Pattern.compile(regex).matcher(mGapBuffer.toString());

        while(matcher.find()) {
            mReplaceList.add(new Pair<Integer, Integer>(matcher.start(), matcher.end()));
        }
    }

    // replace first 
    public void replaceFirst(String replacement) {
        if(!mReplaceList.isEmpty() && isEditedMode) {
            int start = (int)mReplaceList.get(0).first;
            int end = (int)mReplaceList.get(0).second;
            
            mGapBuffer.replace(start, end, replacement);
            
            int length = replacement.length();
            setCursorPosition(start + length);
            adjustSelectRange(start + length, start + length);

            // remove the first item
            mReplaceList.remove(0);
            
            int delta = start + length - end;
            // do not use the find(regex) method to re-find
            // recalculate replace list by index
            for(int i=0;i < mReplaceList.size();++i) {
                int first = (int) mReplaceList.get(i).first + delta;
                int second = (int) mReplaceList.get(i).second + delta;
                mReplaceList.set(i, new Pair<Integer, Integer>(first, second));
            }
        } else {
            // if the replace Lists is empty
            // set the select mode false
            isSelectMode = false;
        }
        postInvalidate();
    }

    // replace all
    public void replaceAll(String replacement) {
        while(!mReplaceList.isEmpty() && isEditedMode) {
            replaceFirst(replacement);
        }
    }

    // select all text
    public void selectAll() {
        removeCallbacks(blinkAction);
        mCursorVisiable = mHandleMiddleVisable = false;
        isSelectMode = true;

        // at first index
        selectionStart = 0;
        // at last index
        selectionEnd = mGapBuffer.length();

        // set handle left at first position
        selectHandleLeftX = getLeftSpace();
        selectHandleLeftY = getLineHeight();

        // set handle right at last position
        selectHandleRightX = getLeftSpace() + getLineWidth(getLineCount());
        selectHandleRightY = getLineCount() * getLineHeight();

        // set cursor index and position
        setCursorPosition(selectionEnd);

        if(!mReplaceList.isEmpty())
            mReplaceList.clear();

        postInvalidate();
    }

    public String getSelectText() {
        if(isSelectMode)
            return mGapBuffer.substring(selectionStart, selectionEnd);
        return null;
    }

    // goto line
    public void gotoLine(int line) {
        if(line < 1) line = 1;

        if(line > getLineCount()) 
            line = getLineCount();
            
        mCursorIndex = getLineStart(line);
        mCursorLine = line;
        mCursorPosX = getLeftSpace();
        mCursorPosY = (line - 1) * getLineHeight();

        smoothScrollTo(0, Math.max(line * getLineHeight() - getHeight() + getLineHeight() * 2, 0));
    }

    public void undo() {
        
    }

    public void redo() {
        
    }

    // for find match text
    // set the select handle left and right
    public void adjustSelectRange(int start, int end) {
        int left = getLeftSpace();

        // select handle left
        int startLine = mGapBuffer.findLineNumber(start);
        int lineStart = getLineStart(startLine);
        String text = mGapBuffer.substring(lineStart, start);

        selectHandleLeftX = left + measureText(text);
        selectHandleLeftY = startLine * getLineHeight();

        // select handle right
        int endLine = getOffsetLine(end);
        lineStart = getLineStart(endLine);
        text = mGapBuffer.substring(lineStart, end);

        selectHandleRightX = left + measureText(text);
        selectHandleRightY = endLine * getLineHeight();

        // set selection
        selectionStart = start;
        selectionEnd = end;
    }

    // adjust the cursor coordinate for insert and delete text
    private void adjustCursorPosition() {
        // cursor x coordinate
        int start = getLineStart(mCursorLine);

        String text = mGapBuffer.substring(start, mCursorIndex);
        mCursorPosX = getLeftSpace() + measureText(text);

        // cursor y coordinate
        mCursorPosY = (mCursorLine - 1) * getLineHeight();
    }

    // set the cursor position by index
    private void setCursorPosition(int index) {
        // calculate the cursor index and position
        mCursorIndex = index;
        mCursorLine = getOffsetLine(index);

        String text = mGapBuffer.substring(getLineStart(mCursorLine), index);
        int width = measureText(text);
        mCursorPosX = getLeftSpace() + width;
        mCursorPosY = (mCursorLine - 1) * getLineHeight();
    }

    // set cursor position by coordinate
    public void setCursorPosition(float x, float y) {
        // calculation the cursor y coordinate
        mCursorPosY = (int)y / getLineHeight() * getLineHeight();
        int bottom = getLineCount() * getLineHeight();

        if(mCursorPosY < getPaddingTop())
            mCursorPosY = getPaddingTop();

        if(mCursorPosY > bottom - getLineHeight())
            mCursorPosY = bottom - getLineHeight();

        // estimate the cursor x position
        int left = getLeftSpace();

        int prev = left;
        int next = left;

        mCursorLine = mCursorPosY / getLineHeight() + 1;
        mCursorIndex = getLineStart(mCursorLine);

        String text = getLine(mCursorLine);
        int length = text.length();

        float[] widths = new float[length];
        mTextPaint.getTextWidths(text, widths);

        for(int i=0; next < x && i < length; ++i) {
            if(i > 0) {
                prev += widths[i - 1];
            }
            next += widths[i];
        }

        // calculation the cursor x coordinate
        if(Math.abs(x - prev) <= Math.abs(next - x)) {
            mCursorPosX = prev;
        } else {
            mCursorPosX = next;
        }

        // calculation the cursor index
        if(mCursorPosX > left) {
            for(int j=0; left < mCursorPosX && j < length; ++j) {
                left += widths[j];
                ++mCursorIndex;
            }
        }
    }

    // toogle soft keyboard
    public void showSoftInput(boolean show) {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if(show)
            imm.showSoftInput(this, 0);
        else
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // TODO: Implement this method
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
            | EditorInfo.IME_ACTION_DONE
            | EditorInfo.IME_FLAG_NO_EXTRACT_UI;

        return new TextInputConnection(this, true);
    }

    // auto scroll select handle and cursor
    private void onMove(int slopX, int slopY) {
        int dx = 0;
        if(mCursorPosX - getScrollX() <= slopX) {
            // left scroll
            dx = -measureText(String.valueOf(mGapBuffer.charAt(mCursorIndex)));
        } else if(mCursorPosX - getScrollX() >= screenWidth - slopX) {
            // right scroll
            dx = measureText(String.valueOf(mGapBuffer.charAt(mCursorIndex + 1)));
        }   

        // when hide soft keyboard
        if(getHeight() > screenHeight / 2)
            slopY = slopY * 3;

        int dy = 0;
        if(mCursorPosY - getScrollY() <= 0) {
            // up scroll
            dy = -getLineHeight();
        } else if(mCursorPosY - getScrollY() >= getHeight() - slopY) {
            // down scroll
            dy = getLineHeight();
        }

        if(mCursorPosY + dy < 0)
            scrollTo(getScrollX(), 0);
        else if(mCursorPosX + dx < 0)
            scrollTo(0, getScrollY());
        else
            scrollBy(dx, dy);
    }  


    class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private boolean touchOnSelectHandleMiddle = false;
        private boolean touchOnSelectHandleLeft = false;
        private boolean touchOnSelectHandleRight = false;

        // for auto scroll select handle
        private Runnable moveAction = new Runnable() {
            @Override
            public void run() {
                // TODO: Implement this method
                onMove(spaceWidth * 4, getLineHeight());
                postDelayed(moveAction, DEFAULT_DURATION);
            }
        };

        // when on long press to select a word
        private String findNearestWord() {
            int length = mGapBuffer.length();

            // select start index
            for(selectionStart = mCursorIndex; selectionStart >= 0; --selectionStart) {
                char c = mGapBuffer.charAt(selectionStart);
                if(!Character.isJavaIdentifierPart(c))
                    break;
            }

            // select end index
            for(selectionEnd = mCursorIndex; selectionEnd < length; ++selectionEnd) {
                char c = mGapBuffer.charAt(selectionEnd);
                if(!Character.isJavaIdentifierPart(c))
                    break;
            }

            // select start index needs to be incremented by 1
            ++selectionStart;
            if(selectionStart < selectionEnd) 
                return mGapBuffer.substring(selectionStart, selectionEnd);
            return null;
        }


        // swap text select handle left and right
        private void reverse() {
            selectHandleLeftX = selectHandleLeftX ^ selectHandleRightX;
            selectHandleRightX = selectHandleLeftX ^ selectHandleRightX;
            selectHandleLeftX = selectHandleLeftX ^ selectHandleRightX;

            selectHandleLeftY = selectHandleLeftY ^ selectHandleRightY;
            selectHandleRightY = selectHandleLeftY ^ selectHandleRightY;
            selectHandleLeftY = selectHandleLeftY ^ selectHandleRightY;

            selectionStart = selectionStart ^ selectionEnd;
            selectionEnd = selectionStart ^ selectionEnd;
            selectionStart = selectionStart ^ selectionEnd;

            touchOnSelectHandleLeft = !touchOnSelectHandleLeft;
            touchOnSelectHandleRight = !touchOnSelectHandleRight;
        }

        // when single tap to check the select region
        private boolean checkSelectRange(float x, float y) {

            if(y < selectHandleLeftY - getLineHeight() || y > selectHandleRightY)
                return false;

            // on the same line
            if(selectHandleLeftY == selectHandleRightY) {
                if(x < selectHandleLeftX || x > selectHandleRightX)
                    return false;
            } else {
                // not on the same line
                int left = getLeftSpace();
                int line = (int)y / getLineHeight() + 1;
                int width = getLineWidth(line) + spaceWidth;
                // select start line
                if(line == selectHandleLeftY / getLineHeight()) {
                    if(x < selectHandleLeftX || x > left + width)
                        return false;
                } else if(line == selectHandleRightY / getLineHeight()) {
                    // select end line
                    if(x < left || x > selectHandleRightX)
                        return false;
                } else {
                    if(x < left || x > left + width) 
                        return false;
                }
            }
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            // TODO: Implement this method
            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();
            // touch handle middle drop
            if(mHandleMiddleVisable && x >= mCursorPosX - handleMiddleWidth / 2 && x <= mCursorPosX + handleMiddleWidth / 2
               && y >= mCursorPosY + getLineHeight() && y <= mCursorPosY + getLineHeight() + handleMiddleHeight) {

                touchOnSelectHandleMiddle = true;
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = true;
            }

            // touch handle left drop
            if(isSelectMode && x >= selectHandleLeftX - selectHandleWidth + selectHandleWidth / 4 
               && x <= selectHandleLeftX + selectHandleWidth / 4 
               && y >= selectHandleLeftY && y <= selectHandleLeftY + selectHandleHeight) {

                touchOnSelectHandleLeft = true;
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = false;
            }

            // touch handle right drop
            if(isSelectMode && x >= selectHandleRightX - selectHandleWidth / 4 
               && x <= selectHandleRightX + selectHandleWidth - selectHandleWidth / 4 
               && y >= selectHandleRightY && y <= selectHandleRightY + selectHandleHeight) {

                touchOnSelectHandleRight = true;
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = false;
            }

            return super.onDown(e);
        }


        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // TODO: Implement this method
            float x = e.getX() + getScrollX();
            float y = e.getY() + getScrollY();

            showSoftInput(true);

            if(!isSelectMode || !checkSelectRange(x, y)) {
                // stop cursor blink
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = true;
                isSelectMode = false;

                if(!mReplaceList.isEmpty()) 
                    mReplaceList.clear();

                setCursorPosition(x, y);
                //Log.i(TAG, "mCursorIndex: " + mCursorIndex);
                postInvalidate();
                mLastTapTime = System.currentTimeMillis();
                // cursor start blink
                postDelayed(blinkAction, BLINK_TIMEOUT);
            } 

            return super.onSingleTapUp(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

            if(touchOnSelectHandleMiddle) {
                // calculation select handle middle coordinate and index
                removeCallbacks(moveAction);
                post(moveAction);
                setCursorPosition(e2.getX() + getScrollX(), 
                                  e2.getY() + getScrollY() - getLineHeight() - Math.min(getLineHeight(), selectHandleHeight) / 2);

            } else if(touchOnSelectHandleLeft) {
                removeCallbacks(moveAction);
                post(moveAction);
                // calculation select handle left coordinate and index
                setCursorPosition(e2.getX() + getScrollX(), 
                                  e2.getY() + getScrollY() - getLineHeight() - Math.min(getLineHeight(), selectHandleHeight) / 2);
                selectHandleLeftX = mCursorPosX;
                selectHandleLeftY = mCursorPosY + getLineHeight();
                selectionStart = mCursorIndex;

            } else if(touchOnSelectHandleRight) {
                removeCallbacks(moveAction);
                post(moveAction);
                // calculation select handle right coordinate and index
                setCursorPosition(e2.getX() + getScrollX(),
                                  e2.getY() + getScrollY() - getLineHeight() - Math.min(getLineHeight(), selectHandleHeight) / 2);
                selectHandleRightX = mCursorPosX;
                selectHandleRightY = mCursorPosY + getLineHeight();
                selectionEnd = mCursorIndex;

            } else {              
                if(Math.abs(distanceY) > Math.abs(distanceX))
                    distanceX = 0;
                else
                    distanceY = 0;

                int newX = (int) distanceX + getScrollX();
                if(newX < 0) {
                    newX = 0;
                } else if(newX > getMaxScrollX()) {
                    newX = getMaxScrollX();
                }

                int newY = (int) distanceY + getScrollY();
                if(newY < 0) {
                    newY = 0;
                } else if(newY > getMaxScrollY()) {
                    newY = getMaxScrollY();
                }
                smoothScrollTo(newX, newY);
            }

            if(isSelectMode && ((selectHandleLeftY > selectHandleRightY) 
               || (selectHandleLeftY == selectHandleRightY 
               && selectHandleLeftX > selectHandleRightX))) {
                // swap selection handle
                reverse();
            }

            postInvalidate();
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // TODO: Implement this method
            if(Math.abs(velocityY) > Math.abs(velocityX))
                velocityX = 0;
            else
                velocityY = 0;

            mScroller.fling(getScrollX(), getScrollY(), (int)-velocityX, (int)-velocityY,
                            0, getMaxScrollX(), 0, getMaxScrollY());

            postInvalidate();
            return super.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            // TODO: Implement this method
            super.onLongPress(e);
            removeCallbacks(blinkAction);
            mCursorVisiable = mHandleMiddleVisable = true;
            if(!touchOnSelectHandleMiddle && mGapBuffer.length() > 0) {
                float x = e.getX() + getScrollX();
                float y = e.getY() + getScrollY();
                setCursorPosition(x, y);

                String selectWord = findNearestWord();
                if(selectWord != null) {
                    removeCallbacks(blinkAction);
                    mCursorVisiable = mHandleMiddleVisable = false;
                    isSelectMode = true;

                    int left = getLeftSpace();
                    int lineStart = getLineStart(mCursorLine);
                    // select handle left (x y)
                    selectHandleLeftX = left + measureText(mGapBuffer.substring(lineStart, selectionStart));
                    selectHandleRightX = left + measureText(mGapBuffer.substring(lineStart, selectionEnd));
                    selectHandleLeftY = selectHandleRightY = mCursorPosY + getLineHeight();

                    // set cursor index and position
                    setCursorPosition(selectionEnd);

                    find(selectWord);
                }
            }
            postInvalidate();
        }
 
        public void onUp(MotionEvent e) {
            if(touchOnSelectHandleLeft ||
               touchOnSelectHandleRight ||
               touchOnSelectHandleMiddle) {
                // remove auto scroll action
                removeCallbacks(moveAction);

                touchOnSelectHandleMiddle = false;
                touchOnSelectHandleLeft = false;
                touchOnSelectHandleRight = false;

                if(isSelectMode) {
                // set cursor index and position at select mode
                setCursorPosition(selectionEnd);
                } else {
                    mLastTapTime = System.currentTimeMillis();
                    postDelayed(blinkAction, BLINK_TIMEOUT);
                }
            }
        }
    }


    class ScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // TODO: Implement this method
            float factor = detector.getScaleFactor();
            setTextSize(mTextPaint.getTextSize() * factor);
            return true;
        }
    }

    class TextInputConnection extends BaseInputConnection {

        public TextInputConnection(View view, boolean fullEditor) {
            super(view, fullEditor);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            // TODO: Implement this method
            insert(text.toString());
            return super.commitText(text, newCursorPosition);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            // TODO: Implement this method
            return super.deleteSurroundingText(beforeLength, afterLength);
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            // TODO: Implement this method
            return onKeyDown(event.getKeyCode(), event);
        }

        @Override
        public boolean finishComposingText() {
            // TODO: Implement this method
            return true;
        }
    }
}
