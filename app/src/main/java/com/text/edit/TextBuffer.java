package com.text.edit;

import android.util.Log;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

public class TextBuffer implements Serializable {

    // save the text content
    private StringBuilder strBuilder = new StringBuilder();

    // the start index of each line text
    private ArrayList<Integer> indexList = new ArrayList<>();

    // the width of each line text
    private ArrayList<Integer> widthList = new ArrayList<>();

    // load text dynamically if the text content is too large
    // implement a loading progress bar
    public static int lineCount = 0;
    // the text max width
    public static int lineWidth = 0;
    // the text max width index
    public static int lineIndex = 0;

    // the text read finish
    public static boolean onReadFinish = false;
    // the text write finish
    public static boolean onWriteFinish = false;

    private final String TAG = this.getClass().getSimpleName();

    public TextBuffer() {
        // nothing to do
    }

    public TextBuffer(CharSequence c, HighlightTextView tv) {
        setBuffer(c, tv);
    }

    public void setBuffer(CharSequence c, HighlightTextView tv) {
        emptyBuffer();
        // add a dafault new line
        strBuilder.append(c).append(System.lineSeparator());
        // line start index
        int start = 0;
        for(int i=0; i < getLength(); ++i) {           
            if(getCharAt(i) == '\n') {
                // add first index 0
                if(indexList.size() == 0)
                    indexList.add(0);
                indexList.add(i + 1);

                // the text width
                String text = getText(start, i + 1);
                int width = tv.getTextMeasureWidth(text);
                widthList.add(width);
                if(width > lineWidth) {
                    lineWidth = width;
                    lineIndex = lineCount;
                }

                start = i + 1;
                ++lineCount;
            }
        }
        // remove the last index of '\n'
        indexList.remove(indexList.size() - 1);
        onReadFinish = true;
        Log.i(TAG, "size: " + indexList.size());
    }

    public void setBuffer(StringBuilder strBuilder) {
        emptyBuffer();
        this.strBuilder = strBuilder;
    }

    public StringBuilder getBuffer() {
        return strBuilder;
    }

    // empty the string builder
    public void emptyBuffer() {
        if(strBuilder.length() > 0) {
            indexList.clear();
            widthList.clear();
            strBuilder.delete(0, strBuilder.length());
            onReadFinish = onWriteFinish = false;
            lineCount = lineWidth = 0;
        }
    }

    public int getLength() {
        return strBuilder.length();
    }

    // Get the text line count
    public int getLineCount() {
        // don't use the lineCount
        return onReadFinish ? indexList.size(): lineCount;
    }

    // return the text max width
    public int getMaxWidth() {
        return lineWidth;
    }  

    // Set the text line index lists
    public void setIndexList(ArrayList<Integer> list) {
        indexList = list;
    }

    public ArrayList<Integer> getIndexList() {
        return indexList;
    }

    // Set the text line width lists
    public void setWidthList(ArrayList<Integer> list) {
        widthList = list;
    }

    public ArrayList<Integer> getWidthList() {
        return widthList;
    }

    // find the line where the cursor is by binary search
    public int getOffsetLine(int index) {
        int low = 0;
        int line = 0;
        int high = getLineCount() + 1;

        while(high - low > 1) {
            line = (low + high) >> 1; 
            // cursor index at middle line
            if(line == getLineCount() || index >= indexList.get(line - 1) 
               && index < indexList.get(line))
                break;
            if(index < indexList.get(line - 1))
                high = line;
            else
                low = line;
        }

        // find the cursor line
        return line;
    }

    public int getLineWidth(int line) {
        return widthList.get(line - 1);
    }

    // start index of the text line
    public int getLineStart(int line) {
        return indexList.get(line - 1);
    }

    // end index of the text line
    public int getLineEnd(int line) {
        int start = getLineStart(line);
        int length = indexOfLineText(start).length();
        return start + length - 1;
    }

    public char getCharAt(int index) {
        return strBuilder.charAt(index);
    }

    // get line text by index
    public String indexOfLineText(int start) {
        int end = start;
        while(getCharAt(end) != '\n'
              && getCharAt(end) != '\uFFFF') {
            ++end;
        }
        // line text index[start..end]
        return strBuilder.substring(start, end);
    }  

    // Get a line of text
    public String getLine(int line) {

        int lineStart= getLineStart(line);

        return indexOfLineText(lineStart);
    }

    // get text by index[start..end]
    public String getText(int start, int end) {
        return strBuilder.substring(start, end);
    }

    public void calculate(int line, int delta) {
        // calculation the line start index
        for(int i=line; i < getLineCount(); ++i) {
            indexList.set(i, indexList.get(i) + delta);
        }

        // calculation the line max width and index
        if(widthList.get(lineIndex) != lineWidth) {
            lineWidth = Collections.max(widthList);
            lineIndex = widthList.indexOf(lineWidth);
        }
    }

    // insert text
    public synchronized void insert(int index, CharSequence c, 
                                    int line, HighlightTextView tv) {
        // real insert text
        strBuilder.insert(index, c);

        int length = c.length();
        int start = getLineStart(line);

        // calculate the line width
        int width = tv.getTextMeasureWidth(indexOfLineText(start));
        if(width > lineWidth) {
            lineWidth = width;
            lineIndex = line - 1;
        }
        widthList.set(line - 1, width);

        for(int i=index; i < index + length; ++i) {
            if(strBuilder.charAt(i) == '\n') {
                start = i + 1;
                // text line width
                width = tv.getTextMeasureWidth(indexOfLineText(start));
                if(width > lineWidth) {
                    lineWidth = width;
                    lineIndex = line;
                } else {
                    if(line - 1 < lineIndex) {
                        ++lineIndex;
                    }
                }
                indexList.add(line, start);
                widthList.add(line, width);
                ++lineCount;
                ++line;
            }
        }

        // calculate the lists
        calculate(line, length);
    }


    // delete text
    public synchronized void delete(int start, int end, 
                                    int line, HighlightTextView tv) {   
        int length = end - start;
        for(int i=start; i < end; ++i) {
            if(strBuilder.charAt(i) == '\n') {
                if(line - 1 < lineIndex) {
                    --lineIndex;
                }
                indexList.remove(line - 1);
                widthList.remove(line - 1);
                --lineCount;
                --line;
            }
        }

        // real delete text
        strBuilder.delete(start, end);

        // calculate the line width
        String text = getLine(line);
        int width = tv.getTextMeasureWidth(text);
        if(width > lineWidth) {
            lineWidth = width;
            lineIndex = line - 1;
        }
        widthList.set(line - 1, width);

        // calculate the lists
        calculate(line, -length);
    }

    // replace text
    public synchronized void replace(int start, int end, String replacement, 
                                     int line, int delta, HighlightTextView tv) {
        if(replacement.contains("\n")) {
            // the lists needs add new line
            // replace = delete + insert
            strBuilder.delete(start, end);
            insert(start, replacement, line, tv);
        } else {
            // real replace
            strBuilder.replace(start, end, replacement);
            // recalculate the lists
            if(delta != 0) {
                for(int i=line; i < getLineCount(); ++i) {
                    indexList.set(i, indexList.get(i) + delta);
                }
            }
        }
    }
}
