package com.text.edit;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import androidx.appcompat.app.AppCompatActivity;
import java.lang.reflect.Field;

public final class ScreenUtils {

    public static int getStatusBarHeight(Context context) {
        int statusBarHeight = 0;

        try {
            Class<?> c = Class.forName("com.android.internal.R$dimen");
            Object obj = c.newInstance();
            Field field = c.getField("status_bar_height");
            statusBarHeight = context.getResources().getDimensionPixelSize(Integer.parseInt(field.get(obj).toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return statusBarHeight;
    }


    public static int getActionBarHeight(Context context) {
        TypedValue typedValue = new TypedValue();

        int actionBarHeight = 0;
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(typedValue.data,
                              context.getResources().getDisplayMetrics());
        }

        return actionBarHeight;
    }


    public static int getScreenWidth(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        ((AppCompatActivity)context).getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm.widthPixels;
    }


    public static int getScreenHeight(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        ((AppCompatActivity)context).getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm.heightPixels;
    }
    
    // dip to px
    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    // px to dip
    public static int px2dip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    // px to sp
    public static int px2sp(float pxValue, float fontScale) {
        return (int) (pxValue / fontScale + 0.5f);
    }

    // px to sp
    public static int sp2px(float spValue, float fontScale) {
        return (int) (spValue * fontScale + 0.5f);
    }
 
}
