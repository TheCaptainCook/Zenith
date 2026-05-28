package com.thecaptaincook.zenith;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

public class ThemePrompt {

    /**
     * Shows a beautiful Glassmorphic themed Snackbar prompt.
     * Use this for all foreground fragments and activities.
     */
    public static void show(View view, String message) {
        if (view == null) return;
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        View sbView = snackbar.getView();
        
        Context context = view.getContext();
        TypedValue typedValue = new TypedValue();
        
        // Extract theme colors
        int bgColor = Color.parseColor("#801A1A3E"); // Default Glass
        if (context.getTheme().resolveAttribute(R.attr.glassCardColor, typedValue, true)) {
            bgColor = typedValue.data;
        }
        // Force higher opacity (94%) for readability over other content
        bgColor = Color.argb(240, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor));
        
        int strokeColor = Color.parseColor("#407C4DFF");
        if (context.getTheme().resolveAttribute(R.attr.glassStrokeColor, typedValue, true)) {
            strokeColor = typedValue.data;
        }

        int textColor = Color.WHITE;
        if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
            textColor = typedValue.data;
        }

        // Create Glassmorphic background
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(24f);
        shape.setColor(bgColor);
        shape.setStroke(3, strokeColor);
        
        sbView.setBackgroundTintList(null);
        sbView.setBackground(shape);
        sbView.setElevation(8f);
        
        // Style the text
        TextView textView = sbView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (textView != null) {
            textView.setTextColor(textColor);
            textView.setTextSize(16f);
            textView.setTypeface(null, android.graphics.Typeface.BOLD);
            textView.setMaxLines(4);
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            
            // Add internal padding to make the pill slightly bigger and more breathable
            int paddingH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics());
            int paddingV = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
            textView.setPadding(paddingH, paddingV, paddingH, paddingV);
        }
        
        // Add margins and center it on screen
        if (sbView.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
            android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) sbView.getLayoutParams();
            params.gravity = android.view.Gravity.CENTER;
            params.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(64, 0, 64, 0);
            sbView.setLayoutParams(params);
        } else if (sbView.getLayoutParams() instanceof androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params = (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) sbView.getLayoutParams();
            params.gravity = android.view.Gravity.CENTER;
            params.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(64, 0, 64, 0);
            sbView.setLayoutParams(params);
        }
        
        snackbar.show();
    }

    /**
     * Fallback for background services where View is not available.
     */
    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
