package com.text.edit;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.OverScroller;
import android.widget.Scroller;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.view.animation.AnimationUtils;


public class HighlightTextView extends View {

    private static Paint mPaint;
    private static TextPaint mTextPaint;

    // cursor and select handle drawable resources
    private Drawable mDrawableCursorRes;
    private Drawable mTextSelectHandleLeftRes;
    private Drawable mTextSelectHandleRightRes;
    private Drawable mTextSelectHandleMiddleRes;

    private int mCursorPosX, mCursorPosY;
    private int mCursorLine, mCursorIndex;
    private int mCursorWidth, mCursorHeight;

    private int screenWidth, screenHeight;
    private int lineHeight, spaceWidth;
    private int handleMiddleWidth, handleMiddleHeight;
    private int selectionStart, selectionEnd;

    private int selectHandleWidth, selectHandleHeight;
    private int selectHandleLeftX, selectHandleLeftY;
    private int selectHandleRightX, selectHandleRightY;

    private TextBuffer mTextBuffer;
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
        screenWidth = ScreenUtils.getScreenWidth(context);
        screenHeight = ScreenUtils.getScreenHeight(context);

        mDrawableCursorRes = context.getDrawable(R.drawable.abc_text_cursor_material);
        mDrawableCursorRes.setTint(Color.MAGENTA);

        mCursorWidth = mDrawableCursorRes.getIntrinsicWidth();
        mCursorHeight = mDrawableCursorRes.getIntrinsicHeight();

        // set cursor width
        if(mCursorWidth > 5) mCursorWidth = 5;

        // left water
        mTextSelectHandleLeftRes = context.getDrawable(R.drawable.abc_text_select_handle_left_mtrl_dark);
        mTextSelectHandleLeftRes.setTint(Color.MAGENTA);
        mTextSelectHandleLeftRes.setColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN);

        selectHandleWidth = mTextSelectHandleLeftRes.getIntrinsicWidth();
        selectHandleHeight = mTextSelectHandleLeftRes.getIntrinsicHeight();

        // right water
        mTextSelectHandleRightRes = context.getDrawable(R.drawable.abc_text_select_handle_right_mtrl_dark);
        mTextSelectHandleRightRes.setTint(Color.MAGENTA);

        // middle water
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
        spaceWidth = (int) mTextPaint.measureText(String.valueOf(' '));

        setDefaultCursorPosition();

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

    public void setTextBuffer(TextBuffer textBuffer) {
        mTextBuffer = textBuffer;
        setDefaultCursorPosition();
    }

    public void setText(CharSequence c) {
        mTextBuffer = new TextBuffer(c, this);
        setDefaultCursorPosition();
    }

    public TextBuffer getTextBuffer() {
        return mTextBuffer;
    }

    public void setDefaultCursorPosition() {
        int lineNumberWidth = 0;
        if(mTextBuffer != null)
            lineNumberWidth = getLineNumberWidth();
        else
            lineNumberWidth = getCharWidth('0');
        mCursorPosX = getPaddingLeft() + lineNumberWidth + SPACEING;
        mCursorPosY = 0;
        mCursorIndex = 0;
        mCursorLine = mCursorPosY / getLineHeight() + 1;
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

        TextPaint.FontMetricsInt metrics = mTextPaint.getFontMetricsInt();
        lineHeight = metrics.bottom - metrics.top;

        if(mTextBuffer != null) {
            // max width line index
            //int line = mTextBuffer.getWidthList().indexOf(getTextWidth());
            //mTextBuffer.getWidthList().set(line, getLineWidth(line + 1));
            adjustCursorPosition(mCursorIndex, mCursorLine);
            if(isSelectMode)
                adjustSelectHandle(selectionStart, selectionEnd);
            postInvalidate();
        }
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

    public int getVisableLine() {
        return getHeight() / getLineHeight() + 1;
    }

    public static int getTextMeasureWidth(String text) {
        return (int) mTextPaint.measureText(text);
    }

    private int getLineHeight() {
        return lineHeight;
    }

    private int getLineCount() {
        return mTextBuffer.getLineCount();
    }

    private int getCharWidth(char c) {
        return getTextMeasureWidth(String.valueOf(c));
    }

    private int getCharWidth(int index) {
        return getCharWidth(mTextBuffer.getCharAt(index));
    }

    private int getLineNumberWidth() {
        return String.valueOf(getLineCount()).length() * getCharWidth('0');
    }

    private int getLineStart(int line) {
        return mTextBuffer.getLineStart(line);
    }

    private int getLineWidth(int line) {
        return getTextMeasureWidth(mTextBuffer.getLine(line));
    }

    // get the max width of text
    private int getTextWidth() {
        return mTextBuffer.getMaxWidth();
    }

    // get the max height of text
    private int getTextHeight() {
        return getLineCount() * getLineHeight();
    }

    // Get the maximum scrollable width
    public int getMaxScrollX() {
        return Math.max(0, getLeftSpace() + getTextWidth() + spaceWidth * 4 - getWidth());
    }

    // Get the maximum scrollable height
    public int getMaxScrollY() {
        return Math.max(0, getTextHeight() + getLineHeight() * 2 - getHeight());
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

                int line = mTextBuffer.getOffsetLine(start);
                int lineStart = getLineStart(line);

                canvas.drawRect(left + getTextMeasureWidth(mTextBuffer.getText(lineStart, start)),
                                (line - 1) * getLineHeight(),
                                left + getTextMeasureWidth(mTextBuffer.getText(lineStart, end)),
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
        int lineNumWidth = getLineNumberWidth();

        // draw text line[start..end]
        for(int i=startLine; i <= endLine; ++i) {

            int textX = getPaddingLeft();
            // baseline
            int textY =  i * getLineHeight() - (int)mTextPaint.descent();

            // draw line number
            mTextPaint.setColor(Color.GRAY);
            canvas.drawText(String.valueOf(i), textX, textY, mTextPaint);

            // draw vertical line
            canvas.drawLine(lineNumWidth + SPACEING / 2,  (i - 1) * getLineHeight(), lineNumWidth + SPACEING / 2, i * getLineHeight(), mPaint);

            // draw content text
            textX += (lineNumWidth + SPACEING);
            mTextPaint.setColor(Color.BLACK);

            canvas.drawText(mTextBuffer.getLine(i), textX, textY, mTextPaint);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO: Implement this method
        super.onDraw(canvas);
        if(mTextBuffer == null || mTextBuffer.getLength() == 0) {
            canvas.drawText(mDefaultText, 
                            getWidth() / 2 - getTextMeasureWidth(mDefaultText) / 2, 
                            getHeight() / 2 - getLineHeight(), 
                            mTextPaint
                            );
            return; // no text content, return directly 
        } 

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
        if(mTextBuffer == null || mTextBuffer.getLength() == 0) {
            // no text content, return false directly 
            return false;
        }

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
        if(event.getPointerCount() == 2)
            mScaleGestureDetector.onTouchEvent(event);
        return true;
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO: Implement this method
        if(event.getAction() == KeyEvent.ACTION_DOWN) {
            switch(keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                insert("\n", new UndoStack.Action());
                break;
            case KeyEvent.KEYCODE_DEL:
                // delete char at cursor index
                delete(mCursorIndex - 1, mCursorIndex, new UndoStack.Action());
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
    private void insert(CharSequence c, UndoStack.Action action) {
        // nothing to do
        if(!isEditedMode) return;

        removeCallbacks(blinkAction);
        mCursorVisiable = true;
        mHandleMiddleVisable = false;

        int length = c.length();
        String insertText = c.toString();
        String deleteText = null;

        if(isSelectMode) {
            // no need to add action
            deleteText = mTextBuffer.getText(selectionStart, selectionEnd);
            if(action != null) {
                action.deleteStart = selectionStart;
                action.deleteEnd = selectionEnd;
                action.deleteText = deleteText;
            }
        }

        // real insert
        mTextBuffer.insert(mCursorIndex, c, mCursorLine, this);

        // add undo stack action
        if(action != null) {
            action.insertStart = mCursorIndex;
            action.insertEnd = mCursorIndex + length;
            action.insertText = insertText;
            mUndoStack.add(action);
        }

        // recalculate cursor index and line
        mCursorIndex += length;
        mCursorLine = mTextBuffer.getOffsetLine(mCursorIndex);
        adjustCursorPosition(mCursorIndex, mCursorLine);
        mTextListener.onTextChanged();

        scrollToVisable();
        postInvalidate();
        postDelayed(blinkAction, BLINK_TIMEOUT);
    }

    // Delete text
    private void delete(int start, int end, UndoStack.Action action) {
        // nothing to do
        if(!isEditedMode) return;

        removeCallbacks(blinkAction);
        mCursorVisiable = true;
        mHandleMiddleVisable = false;

        if(isSelectMode) {
            start = selectionStart;
            end = selectionEnd;
            isSelectMode = false;
        }

        // cursor at index 0
        if(start < 0) {
            mCursorIndex = 0;
            postDelayed(blinkAction, BLINK_TIMEOUT);
            return; // nothing to do
        } else if(start == end && end > 0) {
            start = end - 1;
        }

        String deleteText = mTextBuffer.getText(start, end);
        // real delete
        mTextBuffer.delete(start, end, mCursorLine, this);

        // add undo stack action
        if(action != null && deleteText != null) {
            action.deleteStart = start;
            action.deleteEnd = end;
            action.deleteText = deleteText;
            mUndoStack.add(action);
        }

        // calculate cursor index and line
        mCursorIndex -= (end - start);
        mCursorLine = mTextBuffer.getOffsetLine(mCursorIndex);
        adjustCursorPosition(mCursorIndex, mCursorLine);
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
        delete(selectionStart, selectionEnd, new UndoStack.Action());
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

                insert(text, new UndoStack.Action());
            }
        }
    }

    private void scrollToFindPos(int curr) {
        int first = (Integer)mReplaceList.get(curr).first;
        int second = (Integer)mReplaceList.get(curr).second; 

        setCursorPosition(second);
        adjustSelectHandle(first, second);

        //mHorizontalScrollView.smoothScrollTo(selectHandleLeftX, mScrollY);
        //mScrollView.smoothScrollTo(mScrollX, selectHandleLeftY);
    }

    // find the current item
    private int current() {
        for(int i=0; i < mReplaceList.size(); ++i) {
            int first = (Integer)mReplaceList.get(i).first;
            int second = (Integer)mReplaceList.get(i).second;
            if(first == selectionStart && second == selectionEnd)
                return i;
        }
        // default return the first item
        return 0;
    }

    // find the previous item
    public void prev() {
        int curr = current();
        if(curr == 0) {
            curr = mReplaceList.size() - 1;
        } else {
            --curr;
        }

        scrollToFindPos(curr);
        postInvalidate();
    }

    // find the next item
    public void next() {
        int curr = current();
        int size = mReplaceList.size();
        if(curr == size - 1) {
            curr = 0;
        } else {
            ++curr;
        }

        scrollToFindPos(curr);  
        postInvalidate();
    }

    // find text
    public void find(String regex) {
        if(!mReplaceList.isEmpty())
            mReplaceList.clear();

        Matcher matcher = Pattern.compile(regex).matcher(mTextBuffer.getBuffer());

        while(matcher.find()) {
            mReplaceList.add(new Pair<Integer, Integer>(matcher.start(), matcher.end()));
        }
    }

    // replace first 
    public void replaceFirst(String replacement) {
        if(!mReplaceList.isEmpty() && isEditedMode) {
            int start = (Integer) mReplaceList.get(0).first;
            int end = (Integer) mReplaceList.get(0).second;

            int length = replacement.length();
            setCursorPosition(start + length);
            adjustSelectHandle(start + length, start + length);

            int delta = start + length - end;
            mTextBuffer.replace(start, end, replacement, mCursorLine, delta, this);

            // remove the first item
            mReplaceList.remove(0);

            // do not use the find(regex) method to re-find
            // recalculate replace list by index
            for(int i=0;i < mReplaceList.size();++i) {
                int first = (Integer)mReplaceList.get(i).first + delta;
                int second = (Integer)mReplaceList.get(i).second + delta;
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
        selectionEnd = mTextBuffer.getLength() - 1;

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
            return mTextBuffer.getText(selectionStart, selectionEnd);
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
        UndoStack.Action action = mUndoStack.undo();
        if(action != null) {
            // delete the inserted text
            if(action.insertText != null)
                delete(action.insertStart, action.insertEnd, null);

            // insert the deleted text
            if(action.deleteText != null)
                insert(action.deleteText, null);
        }
    }

    public void redo() {
        UndoStack.Action action = mUndoStack.redo();
        if(action != null) {
            // delete the deleted text
            if(action.deleteText != null)
                delete(action.deleteStart, action.deleteEnd, null);

            // insert the inserted text
            if(action.insertText != null)
                insert(action.insertText, null);
        }
    }

    // for find match text
    // set the select handle left and right
    public void adjustSelectHandle(int start, int end) {
        int left = getLeftSpace();

        // select handle left
        int startLine = mTextBuffer.getOffsetLine(start);
        int lineStart = getLineStart(startLine);
        String text = mTextBuffer.getText(lineStart, start);

        selectHandleLeftX = left + getTextMeasureWidth(text);
        selectHandleLeftY = startLine * getLineHeight();

        // select handle right
        int endLine = mTextBuffer.getOffsetLine(end);
        lineStart = getLineStart(endLine);
        text = mTextBuffer.getText(lineStart, end);

        selectHandleRightX = left + getTextMeasureWidth(text);
        selectHandleRightY = endLine * getLineHeight();

        // set selection
        selectionStart = start;
        selectionEnd = end;
    }

    // adjust the cursor coordinate for insert and delete text
    private void adjustCursorPosition(int index, int line) {
        mCursorIndex = index;
        mCursorLine = line;

        // cursor x coordinate
        int start = getLineStart(line);

        String text = mTextBuffer.getText(start, index);
        mCursorPosX = getLeftSpace() + getTextMeasureWidth(text);

        // cursor y coordinate
        mCursorPosY = (line - 1) * getLineHeight();

        if(mCursorPosY < getPaddingTop())
            mCursorPosY = getPaddingTop();

        int bottom = (getLineCount() - 1) * getLineHeight() - getPaddingBottom();
        if(mCursorPosY > bottom)
            mCursorPosY = bottom;
    }

    // set the cursor position by index
    private void setCursorPosition(int index) {
        // calculate the cursor index and position
        mCursorIndex = index;
        mCursorLine = mTextBuffer.getOffsetLine(index);

        String text = mTextBuffer.getText(getLineStart(mCursorLine), index);
        int width = getTextMeasureWidth(text);
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

        String text = mTextBuffer.getLine(mCursorLine);
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
            dx = -getCharWidth(mCursorIndex);
        } else if(mCursorPosX - getScrollX() >= screenWidth - slopX) {
            // right scroll
            dx = getCharWidth(mCursorIndex + 1);
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
            int length = mTextBuffer.getLength();

            // select start index
            for(selectionStart = mCursorIndex; selectionStart >= 0; --selectionStart) {
                char c = mTextBuffer.getCharAt(selectionStart);
                if(!Character.isJavaIdentifierPart(c))
                    break;
            }

            // select end index
            for(selectionEnd = mCursorIndex; selectionEnd < length; ++selectionEnd) {
                char c = mTextBuffer.getCharAt(selectionEnd);
                if(!Character.isJavaIdentifierPart(c))
                    break;
            }

            // select start index needs to be incremented by 1
            ++selectionStart;
            if(selectionStart < selectionEnd) 
                return mTextBuffer.getText(selectionStart, selectionEnd);
            return null;
        }


        // swap text select handle left and right
        private void swapSelection() {

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
        private boolean checkSelectRegion(float x, float y) {

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
            // touch middle water drop
            if(mHandleMiddleVisable && x >= mCursorPosX - handleMiddleWidth / 2 && x <= mCursorPosX + handleMiddleWidth / 2
               && y >= mCursorPosY + getLineHeight() && y <= mCursorPosY + getLineHeight() + handleMiddleHeight) {

                touchOnSelectHandleMiddle = true;
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = true;
            }

            // touch left water drop
            if(isSelectMode && x >= selectHandleLeftX - selectHandleWidth + selectHandleWidth / 4 
               && x <= selectHandleLeftX + selectHandleWidth / 4 
               && y >= selectHandleLeftY && y <= selectHandleLeftY + selectHandleHeight) {

                touchOnSelectHandleLeft = true;
                removeCallbacks(blinkAction);
                mCursorVisiable = mHandleMiddleVisable = false;
            }

            // touch right water drop
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

            if(!isSelectMode || !checkSelectRegion(x, y)) {
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
                swapSelection();
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
            if(!touchOnSelectHandleMiddle) {
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
                    selectHandleLeftX = left + getTextMeasureWidth(mTextBuffer.getText(lineStart, selectionStart));
                    selectHandleRightX = left + getTextMeasureWidth(mTextBuffer.getText(lineStart, selectionEnd));
                    selectHandleLeftY = selectHandleRightY = mCursorPosY + getLineHeight();

                    // set cursor index and position
                    setCursorPosition(selectionEnd);

                    find(selectWord);
                }
            }
            postInvalidate();
        }

        // 
        public void onUp(MotionEvent e) {
            if(touchOnSelectHandleMiddle 
               || touchOnSelectHandleLeft 
               || touchOnSelectHandleRight) {

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

            //scrollToVisable((int)(detector.getFocusX() * factor), (int)(detector.getFocusY() * factor), mRect);
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
            insert(text, new UndoStack.Action());
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
