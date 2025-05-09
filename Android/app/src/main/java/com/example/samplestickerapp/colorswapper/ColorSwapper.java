package com.example.samplestickerapp.colorswapper;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import android.graphics.Bitmap;
import android.graphics.Color;

// ok so this handles the computation and splitting up as far as I understand it. It's pretty abstract.

public class ColorSwapper extends RecursiveAction {

    public static final int  white = Color.rgb(255, 253, 255);
    public static final int  gray = Color.rgb(203, 190, 184);
    public static final int  white_cheek = Color.rgb(251, 228, 231);
    public static final int  gray_cheek = Color.rgb(255, 159, 140);
    public static final int white_shadow = Color.rgb(251, 225, 227);
    public static final int gray_shadow = Color.rgb(185, 164, 159);
    public static final int border = Color.rgb(60, 10, 0);
    public static final int green = Color.rgb(0, 255, 0);



    // processing an array is  quicker than a bitmap
    private int[] src;
    private int[] dst;

    private Bitmap srcBitmap = null;

    private int start;
    private int length; // length in PIXELS
    private int rowLength;
    private int columnLength;

    // ok so a ~500 x 500 img has around 300'000 pixels
    // let's say the CPU has 8 cores
    // once a core is done it takes a new task, so we want multiple per core available to enable balancing
    // we want the work slices to not be too coarse but also not too small to be meaningful
    // let's go with 20.000 pixels per slice for now, that would make a normal img about 15 slices
    protected static int sThreshold = 10000;

    public ColorSwapper(Bitmap original) {
        this.srcBitmap = original;
        this.rowLength = original.getWidth();
        this.columnLength = original.getHeight();
        this.length = rowLength*columnLength;
        this.src = bitmapToArray(original);
        this.dst = new int[length];
        this.start = 0;
    }

    private ColorSwapper(int[] src, int[] dst, int start, int length, int rowLength, int columnLength) {
        this.src = src;
        this.dst = dst;
        this.start = start;
        this.length = length;
        this.rowLength = rowLength;
        this.columnLength = columnLength;
    }

    public Bitmap swapColors() {
        if (srcBitmap == null) {
            throw new IllegalStateException("This function should only be used if the ColorSwapper " +
                    "object was initialized using a Bitmap (public constructor)");
        }

        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(this);
        Bitmap swappedImageBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig());
        writeArrayToBitmap(dst, swappedImageBitmap);

        return swappedImageBitmap;
    }

    private static int[] bitmapToArray(Bitmap src) {
        int[] srcArray = new int[src.getWidth() * src.getHeight()];
        src.getPixels(srcArray, 0, src.getWidth(), 0, 0, src.getWidth(), src.getHeight());
        return srcArray;
    }

    private static void writeArrayToBitmap(int[] dst, Bitmap src) {
        src.setPixels(dst, 0, src.getWidth(), 0, 0, src.getWidth(), src.getHeight());
    }

    protected void computeDirectly() {
//        System.out.println("I'm doing a task tralala (" + start + ", " + (start+length) + ")");
        for (int i = start; i < start+length; i++) {

            int pixelColor = src[i];

            if (is_within_tolerance(pixelColor, white_shadow, 0.05)) { // how likely it is to find a shadow
                if (is_encircled(i, pixelColor, white, 0.04)) { // in this case, it's the cheek, not a shadow (they're roughly the same color) // how likely it is to think the shadow is a cheek
                    dst[i] = gray_cheek;
                    continue;
                }
                dst[i] = gray_shadow;
            } else if (is_within_tolerance(pixelColor, white, 0.15)) {
                dst[i] = gray;
            } else if (is_within_tolerance(pixelColor, gray, 0.05)) {
                dst[i] = white;
            } else if (is_within_tolerance(pixelColor, gray_shadow, 0.1)) {
                dst[i] = white_shadow;
            }
            else {
                dst[i] = pixelColor;
            }
        }
    }


    // This creates the tasks for parallel computation
    protected void compute() {
        // if too small do it serially (exit condition of the recursion)
        if (length < sThreshold) {
            computeDirectly();
            return;
        }

        // otherwise split up the work
        int split = length / 2;

        invokeAll(new ColorSwapper(src, dst, start, split, rowLength, columnLength),
                new ColorSwapper(src, dst, start + split, length - split,
                        rowLength, columnLength));
    }

    public static int[] int_to_Color(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        int[] ret = new int[] { red, green, blue };
        return ret;
    }

    private boolean is_in_bounds(int i) {
        return i >= 0 && i < length;
    }

    private boolean is_encircled(int i, int currentColor, int borderColor, double tolerance) {
        /**
         checks whether first color encountered in cardinal directions (up, down, left, right) is borderColor
         currentColor is the color of the area that the current pixel is in
         */

        int borders = 0; // if there's 4, the pixel is (likely) surrounded
        int max_search_distance = rowLength/10;

        int i_copy = i;
        int distance = 0;
        // go UP til you find a new color
        while (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], currentColor, tolerance)) {
            i_copy -= rowLength;
            distance++;
            // rowLength ISNT DEFINED YET LMAO
            if (distance >= max_search_distance) break;
        }
        if (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], borderColor, tolerance)) {
            borders++;
        }

        i_copy = i;
        distance = 0;
        // go DOWN til you find a new color
        while (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], currentColor, tolerance)) {
            i_copy += rowLength;
            distance++;
            if (distance >= max_search_distance) break;
        }
        if (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], borderColor, tolerance)) {
            borders++;
        }

        i_copy = i;
        // go LEFT til you find a new color
        while (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], currentColor, tolerance)) {
            i_copy--;
            distance++;
            if (distance >= max_search_distance) break;
        }
        if (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], borderColor, tolerance)) {
            borders++;
        }

        i_copy = i;
        // go RIGHT til you find a new color
        while (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], currentColor, tolerance)) {
            i_copy++;
            distance++;
            if (distance >= max_search_distance) break;
        }
        if (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], borderColor, tolerance)) {
            borders++;
        }

        return borders == 4;
    }

    public static boolean euclidian_is_within_tolerance(int actual, int target_color, double tolerance) {

        if (tolerance > 1 || tolerance < 0) {
            System.out.println("Tolerance value invalid.");
        }

        int[] actual_components = int_to_Color(actual);

        double red_term = Math.pow((actual_components[0] - Color.red(target_color)), 2);
        double green_term = Math.pow((actual_components[1] - Color.green(target_color)), 2);
        double blue_term =Math.pow((actual_components[2] - Color.blue(target_color)), 2);
        double difference = Math.sqrt(red_term + green_term + blue_term);

        // double square_255 = 255*255;
        // double percentage_difference = distance/Math.sqrt(255*255*3);
        double percentage_difference = difference / 441;

        return percentage_difference <= tolerance;
    }


    public static boolean is_within_tolerance(int actual, int target_color, double tolerance) {

        // return naive_is_within_tolerance(actual, target_color, tolerance);
        return euclidian_is_within_tolerance(actual, target_color, tolerance);

    }

}
