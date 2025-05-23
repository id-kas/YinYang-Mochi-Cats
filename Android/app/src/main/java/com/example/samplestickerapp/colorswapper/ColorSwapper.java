package com.example.samplestickerapp.colorswapper;
import java.time.chrono.HijrahChronology;
import java.util.LinkedList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.photo.Photo;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Queue;

// ok so this handles the computation and splitting up as far as I understand it. It's pretty abstract.

public class ColorSwapper extends RecursiveAction {

    public static final int white = Color.rgb(255, 253, 255);
    public static final int gray = Color.rgb(203, 190, 184);
    public static final int white_cheek = Color.rgb(251, 228, 231);
    //    public static final int  gray_cheek = Color.rgb(255, 159, 140);
    public static final int gray_cheek = Color.rgb(255, 159, 140);
    public static final int white_shadow = Color.rgb(251, 225, 227);
    public static final int gray_shadow = Color.rgb(185, 164, 159);
    public static final int border = Color.rgb(60, 10, 0);
    public static final int green = Color.rgb(0, 255, 0);
    public static final int blue = Color.rgb(0, 0, 255);


    // processing an array is  quicker than a bitmap
    private int[] src;
    private int[] dst;

    private Bitmap srcBitmap = null;

    private int start;
    private int length; // length in PIXELS
    private int rowLength;
    private int columnLength;
    private ArrayList<Integer> whiteCheeks;
    private ArrayList<Integer> grayCheeks;

    // ok so a ~500 x 500 img has around 300'000 pixels
    // let's say the CPU has 8 cores
    // once a core is done it takes a new task, so we want multiple per core available to enable balancing
    // we want the work slices to not be too coarse but also not too small to be meaningful
    // let's go with 20.000 pixels per slice for now, that would make a normal img about 15 slices
    protected static int sThreshold = 10000;

    public ColorSwapper(Bitmap original) {
        this.srcBitmap = denoiseBitmap(original);
        this.rowLength = original.getWidth();
        this.columnLength = original.getHeight();
        this.length = rowLength * columnLength;
        this.src = bitmapToArray(original);
        this.dst = new int[length];
        this.start = 0;
        this.whiteCheeks = new ArrayList<Integer>();
        this.grayCheeks = new ArrayList<Integer>();
    }

    private ColorSwapper(int[] src, int[] dst, int start, int length, int rowLength, int columnLength, ArrayList<Integer> whiteCheeks, ArrayList<Integer> grayCheeks) {
        this.src = src;
        this.dst = dst;
        this.start = start;
        this.length = length;
        this.rowLength = rowLength;
        this.columnLength = columnLength;
        this.whiteCheeks = whiteCheeks;
        this.grayCheeks = grayCheeks;
    }

    private Bitmap denoiseBitmap(Bitmap bitmap) {
        Mat og = new Mat();
        Utils.bitmapToMat(bitmap, og);
        Mat denoised = new Mat();
        Photo.fastNlMeansDenoising(og, denoised, 10, 7, 21);
        return Bitmap.createBitmap(denoised.cols(), denoised.rows(), Bitmap.Config.ARGB_8888);
    }

    public Bitmap swapColors() {
        if (srcBitmap == null) {
            throw new IllegalStateException("This function should only be used if the ColorSwapper " +
                    "object was initialized using a Bitmap (public constructor)");
        }

        Bitmap swappedImageBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig());

        swapCheeks();

        swapRest();
//        swapAreaIter(5, green);
        writeArrayToBitmap(dst, swappedImageBitmap);

        return swappedImageBitmap;
    }

    private int getX(int i) {
        return i % rowLength;
    }

    private int getY(int i) {
        return i / rowLength;
    }

    private int up(int i) {
        if (i - rowLength < 0) {
            return -1;
        }
        return i - rowLength;
    }

    private int down(int i) {
        if (i + rowLength >= length) {
            return -1;
        }
        return i + rowLength;
    }

    private int left(int i) {
        if (i % rowLength == 0) {
            return -1;
        }
        return i - 1;
    }

    private int right(int i) {
        if ((i + 1) % rowLength == 0) {
            return -1;
        }
        return i + 1;
    }

    private HashMap<String, Integer> emptyHitbox() {
        HashMap<String, Integer> hitbox = new HashMap<>();
        hitbox.put("leftmost", 0);
        hitbox.put("rightmost", 0);
        hitbox.put("topmost", 0);
        hitbox.put("bottommost", 0);

        return hitbox;
    }


    private void swapAreaIter(int pixelWithinArea, int newColor) {
        // idea: instead of recursion, process one pixel per loop and add 4 pixels to the queue per loop
        // continue processing queue tasks even after adding new pixels is done
        double tolerance = 0.01;
        int insideColor = src[pixelWithinArea];
        HashSet<Integer> alreadyBeen = new HashSet<Integer>();
        Queue<Integer> toDo = new LinkedList<Integer>();

        toDo.add(pixelWithinArea);
        int i;

        while(!toDo.isEmpty()) {
            i = toDo.poll();

            if (alreadyBeen.contains(i)) continue;
            alreadyBeen.add(i);

            if (is_within_tolerance(src[i], insideColor, tolerance)) {
                src[i] = newColor;
            }
            else {
                continue;
            }

            int left = left(i);
            int right = right(i);
            int up = up(i);
            int down = down(i);


            if (left != -1) {
                toDo.add(left);
            }
            if (right != -1) {
                toDo.add(right);
            }
            if (up != -1) {
                toDo.add(up);
                }
            if (down != -1) {
                toDo.add(down);
            }

        }
    }


    private void swapCheeks() {
        // this needs to be done separately bc it would be a mess to run it in parallel
        int step = 2;


//        int cheek = 72 +  313*rowLength;
//        swapAreaIter(cheek, blue);
//
        for (int i = start; i < start+length; i += step) {

            int pixelColor = src[i];

            if (is_within_tolerance(pixelColor, white_cheek, 0.05)) {
                if (!is_encircled(i, white_cheek, white, 0.2)) {
                    swapAreaIter(i, green);
                }
            }
            else if (is_within_tolerance(pixelColor, gray_cheek, 0.05)) {
                if (!is_encircled(i, gray_cheek, gray, 0.2)) {
                    swapAreaIter(i, blue);
                }
            }

        }

    }

    private void swapRest() {
        // the colors of everything but the cheeks are swapped in parallel using multi threading
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(this);
//        writeArrayToBitmap(dst, swappedImageBitmap);
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

//            if (is_within_tolerance(pixelColor, green, 0.1)) {
//                dst[i] = white_cheek;
//            }
//            else if (is_within_tolerance(pixelColor, blue, 0.1)) {
//                dst[i] = gray_cheek;
//            }
            if (is_within_tolerance(pixelColor, white_shadow, 0.05)) { // how likely it is to find a shadow
                dst[i] = gray_shadow;
            }
            else if (is_within_tolerance(pixelColor, gray_cheek, 0.05)) {
                dst[i] = white_cheek;
            }
            else if (is_within_tolerance(pixelColor, white, 0.15)) {
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

        invokeAll(new ColorSwapper(src, dst, start, split, rowLength, columnLength, whiteCheeks, grayCheeks),
                new ColorSwapper(src, dst, start + split, length - split,
                        rowLength, columnLength, whiteCheeks, grayCheeks));
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

        double leavingCurrentTolerance = tolerance;
        double isItBorderTolerance = tolerance;
        int step = 2;

        int max_search_distance = (int)(rowLength*0.2);
        // in case the jpg noise or whatever else stops us early,
        // jump a few pixels to be properly in the border color
        int forGoodMeasure = 2;

        int i_copy = i;
        int distance = 0;
        // go UP til you find a new color
        while (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], currentColor, leavingCurrentTolerance)) {
            i_copy -= rowLength*step;
            distance += step;
            if (distance >= max_search_distance) break;
        }
        i_copy -= forGoodMeasure*rowLength;
        if (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], borderColor, isItBorderTolerance)) {
//            System.out.println("UP");
            ;
        }
        else {
            return false;
        }

        i_copy = i;
        distance = 0;
        // go DOWN til you find a new color
        while (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], currentColor, leavingCurrentTolerance)) {
            i_copy += rowLength*step;
            distance += step;
//            System.out.println("going down");
            if (distance >= max_search_distance) break;
        }
        i_copy += forGoodMeasure*rowLength;
        if (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], borderColor, isItBorderTolerance)) {
            ;
//            System.out.println("DOWN");
        }
        else {
            return false;
        }

        i_copy = i;
        distance = 0;
        // go LEFT til you find a new color
        while (i_copy % rowLength >= step && is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], currentColor, leavingCurrentTolerance)) {
            i_copy -= step;
            distance += step;
            if (distance >= max_search_distance) break;
        }
        i_copy -= forGoodMeasure;
        if (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], borderColor, isItBorderTolerance)) {
            ;
//            System.out.println("LEFT");
        }
        else {
            return false;
        }

        i_copy = i;
        distance = 0;
        // go RIGHT til you find a new color
        while ((i_copy + 1) % rowLength >= step && is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], currentColor, leavingCurrentTolerance)) {
            i_copy += step;
            distance += step;
            if (distance >= max_search_distance) break;
        }
        i_copy += forGoodMeasure;
        if (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], borderColor, isItBorderTolerance)) {
            ;
//            System.out.println("RIGHT");
        }
        else {
            return false;
        }
//        if (borders == 4) System.out.println("I'm encircled");
//        System.out.println(borders);
        return true;
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

        // 441 is the max difference between two colors, this just normalizes it so it can be
        // compared to the tolerance
        double percentage_difference = difference / 441;

        return percentage_difference <= tolerance;
    }


    public static boolean is_within_tolerance(int actual, int target_color, double tolerance) {
        return euclidian_is_within_tolerance(actual, target_color, tolerance);
    }

}
