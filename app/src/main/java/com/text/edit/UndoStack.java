package com.text.edit;

import java.util.Stack;

public class UndoStack {

    // undo stack
    private Stack<Action> undoStack = new Stack<>();

    // redo stack
    private Stack<Action> redoStack = new Stack<>();
    
    // maximum stack capacity
    private final int MAX_SIZE = 50;

    // add action
    public void add(Action action) {
        // check if the action is already in the stack
        if(action != null && !action.isExist) {
            action.isExist = true;
            undoStack.push(action);
            // auto remove the first item
            removeFirst(undoStack);
        }
    }

    // undo operator
    public Action undo() {
        if(canUndo()) {
            // pop the top action from undo stack
            Action action = undoStack.pop();
            // then add the action to redo stack
            redoStack.push(action);
            // when size > MAX_SIZE to delete the first item
            removeFirst(redoStack);
            return action;
        }
        return null;
    }

    // redo operator
    public Action redo() {
        if(canRedo()) {
            // pop the top action from redo stack
            Action action = redoStack.pop();
            // then add the action to undo stack
            undoStack.push(action);
            // when size > MAX_SIZE to delete the first item
            removeFirst(undoStack);
            return action;
        }
        return null;
    }

    public boolean canUndo() {
        return undoStack.size() > 0;
    }

    public boolean canRedo() {
        return redoStack.size() > 0;
    }

    // when size > MAX_SIZE to delete the first item
    public void removeFirst(Stack<Action> stack) {
        if(undoStack.size() > MAX_SIZE) 
            stack.remove(0);
    }

    // empty the undo and redo stack
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    // total size of stack
    public int size() {
        return undoStack.size() + redoStack.size();
    }

    // record the insert and delete action
    static class Action {

        // prevent adding the same Action
        public boolean isExist = false; 

        // start and end index for insert text
        public int insertStart, insertEnd;
        // start and end index for delete text
        public int deleteStart, deleteEnd;

        // inserted and deleted text
        public String insertText, deleteText;

        // select text
        public int selectionStart, selectionEnd;
    }
}
