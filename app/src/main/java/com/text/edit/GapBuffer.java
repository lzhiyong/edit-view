package com.text.edit;

import com.text.edit.BufferCache;

/**
 * GapBuffer is a non-threadsafe EditBuffer that is optimized for editing with a 
 * cursor, which tends to make a sequence of inserts and deletes at the same place
 * in the buffer.
 * have all methods work with charOffsets and move all gap handling to getRealIndex() 
 */

public class GapBuffer implements CharSequence {

    private char[] _contents;
    private int _gapStartIndex;
    private int _gapEndIndex;
    private int _lineCount;
    private BufferCache _cache;
    //private UndoStack _undoStack;

    private final int EOF = '\uFFFF';
    private final int NEWLINE = '\n';

    public GapBuffer() {
        _contents = new char[16]; // init size 16
        _lineCount = 1;
        _gapStartIndex = 0;
        _gapEndIndex = _contents.length;
        _cache = new BufferCache();
        //_undoStack = new UndoStack(this);
    }

    public GapBuffer(String buffer) {
        this();
        insert(0, buffer);
    }
    
    public GapBuffer(char[] buffer) {
        _contents = buffer;
        _lineCount = 1;
        _cache = new BufferCache();
        //_undoStack = new UndoStack(this);
        for(char c : _contents) {
            if(c == NEWLINE)
                _lineCount++;
        }
    }
    
    /**
     * Returns a string of text corresponding to the line with index lineNumber.
     *
     * @param lineNumber The index of the line of interest
     * @return The text on lineNumber, or an empty string if the line does not exist
     */
    public synchronized String getLine(int lineNumber) {
        int startIndex = getLineOffset(lineNumber);
        int length = getLineLength(lineNumber);

        return substring(startIndex, startIndex + length);
    }

    /**
     * Get the offset of the first character of the line with index lineNumber.
     * The offset is counted from the beginning of the text.
     *
     * @param lineNumber The index of the line of interest
     * @return The character offset of lineNumber, or -1 if the line does not exist
     */
    public synchronized int getLineOffset(int lineNumber) {
        if (lineNumber <= 0 || lineNumber > getLineCount()) {
            throw new IllegalArgumentException("line index is invalid");
        }

        int lineIndex = --lineNumber;
        // start search from nearest known lineIndex~charOffset pair
        Pair<Integer, Integer> cacheEntry = _cache.getNearestLine(lineIndex);
        int cacheLine = cacheEntry.first;
        int cacheOffset = cacheEntry.second;

        int offset = 0;
        if (lineIndex > cacheLine) {
            offset = findCharOffset(lineIndex, cacheLine, cacheOffset);
        } else if (lineIndex < cacheLine) {
            offset = findCharOffsetBackward(lineIndex, cacheLine, cacheOffset);
        } else {
            offset = cacheOffset;
        }

        if (offset >= 0) {
            // seek successful
            _cache.updateEntry(lineIndex, offset);
        }
        return offset;
    }

    /*
     * Precondition: startOffset is the offset of startLine
     */
    private int findCharOffset(int targetLine, int startLine, int startOffset) {
        assert isValid(startOffset);
        
        int currLine = startLine;
        int offset = getRealIndex(startOffset);

        while ((currLine < targetLine) && (offset < _contents.length)) {
            if (_contents[offset] == NEWLINE) {
                ++currLine;
            }
            ++offset;

            // skip the gap
            if (offset == _gapStartIndex) {
                offset = _gapEndIndex;
            }
        }

        if (currLine != targetLine) {
            return -1;
        }
        return getLogicalIndex(offset);
    }

    /*
     * Precondition: startOffset is the offset of startLine
     */
    private int findCharOffsetBackward(int targetLine, int startLine, int startOffset) {
        assert isValid(startOffset);

        if (targetLine == 0) {
            return 0; // line index 0 always has 0 offset
        }

        int currLine = startLine;
        int offset = getRealIndex(startOffset);
        while (currLine > (targetLine - 1) && offset >= 0) {
            // skip behind the gap
            if (offset == _gapEndIndex) {
                offset = _gapStartIndex;
            }
            --offset;

            if (_contents[offset] == NEWLINE) {
                --currLine;
            }
        }

        int charOffset;
        if (offset >= 0) {
            // now at the '\n' of the line before targetLine
            charOffset = getLogicalIndex(offset);
            ++charOffset;
        } else {
            //assert isValid(offset);
            charOffset = -1;
        }
        return charOffset;
    }

    /**
     * Get the line number that charOffset is on
     *
     * @return The line number that charOffset is on, or -1 if charOffset is invalid
     */
    public synchronized int findLineNumber(int charOffset) {
        assert isValid(charOffset);
        
        Pair<Integer, Integer> cachedEntry = _cache.getNearestCharOffset(charOffset);
        int line = cachedEntry.first;
        int offset = getRealIndex(cachedEntry.second);
        int targetOffset = getRealIndex(charOffset);
        int lastKnownLine = -1;
        int lastKnownCharOffset = -1;

        if (targetOffset > offset) {
            // search forward
            while ((offset < targetOffset) && (offset < _contents.length)) {
                if (_contents[offset] == NEWLINE) {
                    ++line;
                    lastKnownLine = line;
                    lastKnownCharOffset = getLogicalIndex(offset) + 1;
                }

                ++offset;
                // skip the gap
                if (offset == _gapStartIndex) {
                    offset = _gapEndIndex;
                }
            }
        } else if (targetOffset < offset) {
            // search backward
            while ((offset > targetOffset) && (offset > 0)) {
                // skip behind the gap
                if (offset == _gapEndIndex) {
                    offset = _gapStartIndex;
                }
                --offset;

                if (_contents[offset] == NEWLINE) {
                    lastKnownLine = line;
                    lastKnownCharOffset = getLogicalIndex(offset) + 1;
                    --line;
                }
            }
        }

        if (offset == targetOffset) {
            if (lastKnownLine != -1) {
                // cache the lookup entry
                _cache.updateEntry(lastKnownLine, lastKnownCharOffset);
            }
            return line + 1;
        } else {
            return 0;
        }
    }


    /**
     * Finds the number of char on the specified line.
     * All valid lines contain at least one char, which may be a non-printable
     * one like \n, \t or EOF.
     *
     * @return The number of chars in lineNumber, or 0 if the line does not exist.
     */
    public synchronized int getLineLength(int lineNumber) {
        int lineLength = 0;
        int pos = getLineOffset(lineNumber);
        pos = getRealIndex(pos);
        
        //TODO consider adding check for (pos < _contents.length) in case EOF is not properly set
        while (pos < _contents.length &&
               _contents[pos] != NEWLINE &&
               _contents[pos] != EOF) {
            ++lineLength;
            ++pos;
            // skip the gap
            if (pos == _gapStartIndex) {
                pos = _gapEndIndex;
            }
        }
        return lineLength;
    }

    /**
     * Gets the char at charOffset
     * Does not do bounds-checking.
     *
     * @return The char at charOffset. If charOffset is invalid, the result
     * 		is undefined.
     */
    public synchronized char charAt(int charOffset) {
        return _contents[getRealIndex(charOffset)];
    }

    /**
     * Gets up to maxChars number of chars starting at charOffset
     *
     * @return The chars starting from charOffset, up to a maximum of maxChars.
     * 		An empty array is returned if charOffset is invalid or maxChars is
     *		non-positive.
     */
    public synchronized CharSequence subSequence(int start, int end) {
        assert isValid(start) && isValid(end);
        int count = end - start;
        if (end > length()) {
            count = length() - start;
        }
        int realIndex = getRealIndex(start);
        char[] chars = new char[count];

        for (int i = 0; i < count; ++i) {
            chars[i] = _contents[realIndex];
            // skip the gap
            if (++realIndex == _gapStartIndex) {
                realIndex = _gapEndIndex;
            }
        }
        return new String(chars);
    }

    public synchronized String substring(int start, int end) {
        return subSequence(start, end).toString();
    }
    
    /**
     * Insert all characters in c into position charOffset.
     *
     * No error checking is done
     */
    public synchronized GapBuffer insert(int offset, String str/*, long timestamp,
                                     boolean undoable*/) {
//		if (undoable) {
//			_undoStack.captureInsert(charOffset, c.length, timestamp);
//		}

        int length = str.length();
        int insertIndex = getRealIndex(offset);

        // shift gap to insertion point
        if (insertIndex != _gapEndIndex) {
            if (isBeforeGap(insertIndex)) {
                shiftGapLeft(insertIndex);
            } else {
                shiftGapRight(insertIndex);
            }
        }

        if (length >= gapSize()) {
            expandBuffer(length - gapSize());
        }

        for (int i = 0; i < length; ++i) {
            char c = str.charAt(i);
            if (c == NEWLINE) {
                ++_lineCount;
            }
            _contents[_gapStartIndex] = c;
            ++_gapStartIndex;
        }

        _cache.invalidateCache(offset);
        return GapBuffer.this;
    }

    public synchronized GapBuffer append(String str) {
        insert(length(), str);
        return GapBuffer.this;
    }
    
    /**
     * Deletes up to totalChars number of char starting from position
     * charOffset, inclusive.
     *
     * No error checking is done
     */
    public synchronized GapBuffer delete(int start, int end/*, long timestamp,
                                     boolean undoable*/) {
//		if (undoable) {
//			_undoStack.captureDelete(charOffset, totalChars, timestamp);
//		}

        int newGapStart = end;

        // shift gap to deletion point
        if (newGapStart != _gapStartIndex) {
            if (isBeforeGap(newGapStart)) {
                shiftGapLeft(newGapStart);
            } else {
                shiftGapRight(newGapStart + gapSize());
            }
        }

        // increase gap size
        int len = end - start;
        for (int i = 0; i < len; ++i) {
            --_gapStartIndex;
            if (_contents[_gapStartIndex] == NEWLINE) {
                --_lineCount;
            }
        }

        _cache.invalidateCache(start);
        return GapBuffer.this;
    }

    public synchronized GapBuffer replace(int start, int end, String str) {
        delete(start, end);
        insert(start, str);
        return GapBuffer.this;
    }
    
    /**
     * Gets charCount number of consecutive characters starting from _gapStartIndex.
     *
     * Only UndoStack should use this method. No error checking is done.
     */
    private char[] gapSubSequence(int charCount) {
        char[] chars = new char[charCount];

        for (int i = 0; i < charCount; ++i) {
            chars[i] = _contents[_gapStartIndex + i];
        }
        return chars;
    }
    
    /**
     * Moves _gapStartIndex by displacement units. Note that displacement can be
     * negative and will move _gapStartIndex to the left.
     *
     * Only UndoStack should use this method to carry out a simple undo/redo
     * of insertions/deletions. No error checking is done.
     */
    private synchronized void shiftGapStart(int displacement) {
        if (displacement >= 0)
            _lineCount += countNewlines(_gapStartIndex, displacement);
        else
            _lineCount -= countNewlines(_gapStartIndex + displacement, -displacement);

        _gapStartIndex += displacement;
        _cache.invalidateCache(getLogicalIndex(_gapStartIndex - 1) + 1);
    }

    //does NOT skip the gap when examining consecutive positions
    private int countNewlines(int start, int totalChars) {
        int newlines = 0;
        for (int i = start; i < (start + totalChars); ++i) {
            if (_contents[i] == NEWLINE) {
                ++newlines;
            }
        }

        return newlines;
    }

    /**
     * Adjusts gap so that _gapStartIndex is at newGapStart
     */
    private void shiftGapLeft(int newGapStart) {
        while (_gapStartIndex > newGapStart) {
            _gapEndIndex--;
            _gapStartIndex--;
            _contents[_gapEndIndex] = _contents[_gapStartIndex];
        }
    }

    /**
     * Adjusts gap so that _gapEndIndex is at newGapEnd
     */
    private void shiftGapRight(int newGapEnd) {
        while (_gapEndIndex < newGapEnd) {
            _contents[_gapStartIndex] = _contents[_gapEndIndex];
            _gapStartIndex++;
            _gapEndIndex++;
        }
    }

    /**
     * Copies _contents into a buffer that is larger by
     * 		minIncrement + INITIAL_GAP_SIZE * _allocCount bytes.
     *
     * _allocMultiplier doubles on every call to this method, to avoid the
     * overhead of repeated allocations.
     */
    private void expandBuffer(int minIncrement) {
        //TODO handle new size > MAX_INT or allocation failure
        int increasedSize = Math.max(minIncrement, _contents.length * 2 + 2);
        char[] temp = new char[_contents.length + increasedSize];
        // check the maxiunm size
        assert temp.length <= Integer.MAX_VALUE;
        
        int i = 0;
        while (i < _gapStartIndex) {
            temp[i] = _contents[i];
            ++i;
        }

        i = _gapEndIndex;
        while (i < _contents.length) {
            temp[i + increasedSize] = _contents[i];
            ++i;
        }

        _gapEndIndex += increasedSize;
        _contents = temp;
    }

    private boolean isValid(int charOffset) {
        return (charOffset >= 0 && charOffset <= this.length());
    }

    private int gapSize() {
        return _gapEndIndex - _gapStartIndex;
    }

    private int getRealIndex(int index) {
        if (isBeforeGap(index)) 
            return index;
        else 
            return index + gapSize();
    }

    private int getLogicalIndex(int index) {
        if (isBeforeGap(index)) 
            return index;
        else 
            return index - gapSize();
    }

    private boolean isBeforeGap(int index) {
        return index < _gapStartIndex;
    }

    public synchronized int getLineCount() {
        return _lineCount;
    }
    
    @Override
    public synchronized int length() {
        // TODO: Implement this method
        return _contents.length - gapSize();
    }

    @Override
    public synchronized String toString() {
        // TODO: Implement this method
        StringBuffer buf = new StringBuffer();
        int len = this.length();
        for (int i=0; i < len; i++) {
            buf.append(charAt(i));
        }
        return new String(buf);
    }
}
