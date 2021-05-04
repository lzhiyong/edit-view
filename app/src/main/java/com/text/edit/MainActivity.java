package com.text.edit;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import org.mozilla.universalchardet.UniversalDetector;

public class MainActivity extends AppCompatActivity {

    private TextBuffer mTextBuffer;
    private HighlightTextView mTextView;

    private ProgressBar mIndeterminateBar;

    private SharedPreferences mSharedPreference;
    private Charset mDefaultCharset = StandardCharsets.UTF_8;
    private String externalPath = File.separator;

    private final int REFRESH_OPTION_MENU = 1;
    private final int IS_READ_FILE = 2;
    private final int IS_WRITE_FILE = 3;

    private final String TAG = this.getClass().getSimpleName();

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // TODO: Implement this method
            super.handleMessage(msg);
            switch(msg.what) {
            case REFRESH_OPTION_MENU:
                invalidateOptionsMenu();
                break;
            }
        }
    };

    private OnTextChangedListener textListener = new OnTextChangedListener() {
        @Override
        public void onTextChanged() {
            // TODO: Implement this method
            mHandler.sendEmptyMessage(REFRESH_OPTION_MENU);
            mTextView.postInvalidate();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //getSupportActionBar().hide();

        mIndeterminateBar = findViewById(R.id.indeterminateBar);
        mIndeterminateBar.setBackground(null);

        mTextView = findViewById(R.id.mTextView);
        mTextView.setTypeface(Typeface.MONOSPACE);
        mTextView.setOnTextChangedListener(textListener);

        mSharedPreference = PreferenceManager.getDefaultSharedPreferences(this);
        //mTextView.setText("Hello");

        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;

        if(!hasPermission(permission)) {
            applyPermission(permission);
        }

        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            externalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        mTextBuffer = new TextBuffer();
        mTextView.setTextBuffer(mTextBuffer);

        ActivityManager mActivityManager =  (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        int maxHeapSize = mActivityManager.getLargeMemoryClass();
        int norHeapSize = mActivityManager.getMemoryClass();
        Log.i(TAG, "norHeapSize: " + norHeapSize);
        Log.i(TAG, "maxHeapSize: " + maxHeapSize);
    }


    public boolean hasPermission(String permission) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        else
            return true;
    }

    public void applyPermission(String permission) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(shouldShowRequestPermissionRationale(permission)) {
                Toast.makeText(this, "request read sdcard permmission", Toast.LENGTH_SHORT).show();
            }
            requestPermissions(new String[] {permission}, 0);
        }
    }

    private void toggleEditMode() {
        if(!mTextBuffer.onReadFinish) return;
        mTextView.setEditedMode(!mTextView.getEditedMode());
        mHandler.sendEmptyMessage(REFRESH_OPTION_MENU);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO: Implement this method
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // TODO: Implement this method
        MenuItem itemUndo = menu.findItem(R.id.menu_undo);
        itemUndo.setIcon(R.drawable.ic_undo_white_24dp);
        if(mTextView.getUndoStack().canUndo())
            itemUndo.setEnabled(true);
        else
            itemUndo.setEnabled(false);

        MenuItem itemRedo = menu.findItem(R.id.menu_redo);
        itemRedo.setIcon(R.drawable.ic_redo_white_24dp);
        if(mTextView.getUndoStack().canRedo())
            itemRedo.setEnabled(true);
        else
            itemRedo.setEnabled(false);

        MenuItem itemEdit = menu.findItem(R.id.menu_edit);

        if(mTextView.getEditedMode())
            itemEdit.setIcon(R.drawable.ic_edit_white_24dp);     
        else
            itemEdit.setIcon(R.drawable.ic_look_white_24dp);    
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.menu_undo:
            mTextView.undo();
            break;
        case R.id.menu_redo:
            mTextView.redo();
            break;
        case R.id.menu_edit:
            toggleEditMode();
            break;
        case R.id.menu_open:
            showOperateFileDialog("open file", IS_READ_FILE);
            break;
        case R.id.menu_gotoline:
            showGotoLineDialog();
            break;
        case R.id.menu_settings:
            break;
        case R.id.menu_save:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showGotoLineDialog() {
        final View v = getLayoutInflater().inflate(R.layout.dialog_gotoline, null);
        final EditText lineEdit = v.findViewById(R.id.lineEdit);
        final TextBuffer buffer = mTextView.getTextBuffer();
        if(buffer != null)
            lineEdit.setHint("1.." + buffer.getLineCount());
        else
            lineEdit.setHint("0");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        builder.setTitle("goto line");

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(buffer == null) return;
                    String line = lineEdit.getText().toString();
                    if(line != null && !line.isEmpty()) {
                        mTextView.gotoLine(Integer.parseInt(line));
                    }
                }
            });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        builder.setCancelable(true).show();
    }

    // open and save the file
    private void showOperateFileDialog(String title, final int option) {
        View v = getLayoutInflater().inflate(R.layout.dialog_openfile, null);
        final EditText pathEdit = v.findViewById(R.id.pathEdit);

        final String path = mSharedPreference.getString("filepath", null);
        if(option == IS_READ_FILE) {
            if(path != null && !path.isEmpty()) pathEdit.setText(path);
        }
        pathEdit.setHint("please enter the file path");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        builder.setTitle(title);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String pathname = pathEdit.getText().toString();
                    if(pathname != null && !pathname.isEmpty()) {                
                        if(option == IS_READ_FILE) {
                            mSharedPreference.edit().putString("filepath", pathname).commit();
                            if(FileUtils.checkOpenFileState(Paths.get(pathname)) 
                               && !FileUtils.checkSameFile(Paths.get(pathname))) {
                                FileUtils.removeOpenedFile(path);
                                // add an opened file
                                FileUtils.addOpenedFile(pathname);
                                new ReadFileThread().execute(pathname);
                            }
                        } else if(option == IS_WRITE_FILE) {
                            new WriteFileThread().execute(pathname);
                        }
                    }
                }
            });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener(){
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        builder.setCancelable(true).show();
    }


    // read file
    class ReadFileThread extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected void onPreExecute() {
            // TODO: Implement this method
            super.onPreExecute();
            mTextView.setEditedMode(false);
            mHandler.sendEmptyMessage(REFRESH_OPTION_MENU);
            mIndeterminateBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(String...params) {
            // TODO: Implement this method
            Path path = Paths.get(params[0]);
            
            StringBuilder strBuilder = mTextBuffer.getBuffer();
            ArrayList<Integer> indexList = mTextBuffer.getIndexList();
            ArrayList<Integer> widthList = mTextBuffer.getWidthList();

            try {
                // detect the file charset
                String charset = UniversalDetector.detectCharset(path.toFile());
                if(charset != null) mDefaultCharset = Charset.forName(charset);

                // create buffered reader
                BufferedReader br = Files.newBufferedReader(path, mDefaultCharset);

                String text = null;
                // read file
                while((text = br.readLine()) != null) {
                    strBuilder.append(text).append(System.lineSeparator());

                    if(indexList.size() == 0) {
                        // add first index 0
                        indexList.add(0);
                    }
                    indexList.add(strBuilder.length());

                    // text line width
                    int width = mTextView.getTextMeasureWidth(text);
                    if(width > mTextBuffer.lineWidth) {
                        mTextBuffer.lineWidth = width;
                        mTextBuffer.lineIndex = mTextBuffer.lineCount;
                    }
                    widthList.add(width);
                    // line count
                    ++mTextBuffer.lineCount;
                }

                if(indexList.size() > 0) {
                    // remove the last index of '\n'
                    indexList.remove(indexList.size() - 1);
                } else {
                    // the file is empty
                    // set a default empty string
                    mTextBuffer.setBuffer("", mTextView);
                }
                // close the stream
                br.close();
            } catch(Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            }

            return mTextBuffer.onReadFinish = true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // TODO: Implement this method
            super.onPostExecute(result);
            mTextView.setEditedMode(true);
            mHandler.sendEmptyMessage(REFRESH_OPTION_MENU);
            mIndeterminateBar.setVisibility(View.GONE);
        }
    }

    // write file
    class WriteFileThread extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(String...params) {
            // TODO: Implement this method
            Path path = Paths.get(params[0]);
            if(!FileUtils.checkSaveFileState(path)) 
                return false;

            try {
                BufferedWriter bufferWrite = null;
                bufferWrite = Files.newBufferedWriter(path, mDefaultCharset, 
                                                      StandardOpenOption.WRITE);
                bufferWrite.write(mTextView.getTextBuffer().getBuffer().toString());     
                bufferWrite.flush();
                bufferWrite.close();
            } catch(Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // TODO: Implement this method
            super.onPostExecute(result);
            mTextView.getTextBuffer().onWriteFinish = true;
            Toast.makeText(getApplicationContext(), "saved success!", Toast.LENGTH_SHORT).show();
        }
    }
}
