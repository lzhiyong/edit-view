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
import com.text.edit.R;
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

    private HighlightTextView mTextView;

    private ProgressBar mIndeterminateBar;

    private SharedPreferences mSharedPreference;
    private Charset mDefaultCharset = StandardCharsets.UTF_8;
    private String externalPath = File.separator;

    private final String TAG = this.getClass().getSimpleName();

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // TODO: Implement this method
            super.handleMessage(msg);
            invalidateOptionsMenu();
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
        mTextView.setOnTextChangedListener(() -> {
            mHandler.sendEmptyMessage(0);
            mTextView.postInvalidate();
        });

        mSharedPreference = PreferenceManager.getDefaultSharedPreferences(this);

        String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;

        if(!hasPermission(permission)) {
            applyPermission(permission);
        }

        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            externalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        }

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
        mTextView.setEditedMode(!mTextView.getEditedMode());
        mHandler.sendEmptyMessage(0);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // TODO: Implement this method
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // TODO: Implement this method
        MenuItem undo = menu.findItem(R.id.menu_undo);
        undo.setIcon(R.drawable.ic_undo_white_24dp);
        if(mTextView.canUndo())
            undo.setEnabled(true);
        else
            undo.setEnabled(false);

        MenuItem redo = menu.findItem(R.id.menu_redo);
        redo.setIcon(R.drawable.ic_redo_white_24dp);
        if(mTextView.canRedo())
            redo.setEnabled(true);
        else
            redo.setEnabled(false);

        MenuItem editMode = menu.findItem(R.id.menu_edit);

        if(mTextView.getEditedMode())
            editMode.setIcon(R.drawable.ic_edit_white_24dp);     
        else
            editMode.setIcon(R.drawable.ic_look_white_24dp);    
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
            showOpenFileDialog();
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
        lineEdit.setHint("1.." + mTextView.getLineCount());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        builder.setTitle("goto line");

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String line = lineEdit.getText().toString();
            if(!line.isEmpty()) {
                mTextView.gotoLine(Integer.parseInt(line));
            }
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());
        builder.setCancelable(true).show();
    }

    
    private void showOpenFileDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_openfile, null);
        final EditText pathEdit = v.findViewById(R.id.pathEdit);
        String path = mSharedPreference.getString("path", "");
        if(path.isEmpty())
            pathEdit.setHint("please enter the file path");
        else
            pathEdit.setText(path);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        builder.setTitle("open file");

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String pathname = pathEdit.getText().toString();
            if(!pathname.isEmpty()) {
                mSharedPreference.edit().putString("path", pathname).commit();
                new ReadFileThread().execute(pathname);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());
        builder.setCancelable(true).show();
    }


    // read file
    class ReadFileThread extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected void onPreExecute() {
            // TODO: Implement this method
            super.onPreExecute();
            mTextView.setEditedMode(false);
            mHandler.sendEmptyMessage(0);
            mIndeterminateBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(String...params) {
            // TODO: Implement this method
            Path path = Paths.get(params[0]);
            GapBuffer buffer = mTextView.getBuffer();
            buffer.delete(0, buffer.length(), false);
            try {
                // detect the file charset
                String charset = UniversalDetector.detectCharset(path.toFile());
                if(charset != null) 
                    mDefaultCharset = Charset.forName(charset);
                // create buffered reader
                BufferedReader br = Files.newBufferedReader(path, mDefaultCharset);
                String text = null;
                while((text = br.readLine()) != null) {
                    buffer.append(text).append("\n");
                }
                // close the stream
                br.close();
            } catch(Exception e) {
                e.printStackTrace();
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // TODO: Implement this method
            super.onPostExecute(result);
            mTextView.setEditedMode(true);
            mHandler.sendEmptyMessage(0);
            mIndeterminateBar.setVisibility(View.GONE);
        }
    }

    // write file
    class WriteFileThread extends AsyncTask<String, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(String...params) {
            // TODO: Implement this method
            Path path = Paths.get(params[0]);
            
            try {
                BufferedWriter bufferWrite = null;
                bufferWrite = Files.newBufferedWriter(path, mDefaultCharset, 
                                                      StandardOpenOption.WRITE);
                bufferWrite.write(mTextView.getBuffer().toString());     
                bufferWrite.flush();
                bufferWrite.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // TODO: Implement this method
            super.onPostExecute(result);
            Toast.makeText(getApplicationContext(), "saved success!", Toast.LENGTH_SHORT).show();
        }
    }
}
