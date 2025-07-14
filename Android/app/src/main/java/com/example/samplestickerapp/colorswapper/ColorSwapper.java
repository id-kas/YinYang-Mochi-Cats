package com.example.samplestickerapp.colorswapper;
import java.time.chrono.HijrahChronology;
import java.util.LinkedList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import java.util.HashSet;
import java.util.Queue;

// RecursiveAction is for parallel computation
// I think it's called recursive bc it splits the data up recursively before handing it off to the threads or sth
public class ColorSwapper extends RecursiveAction {

    public static final int white = Color.rgb(255, 253, 255);
    public static final int gray = Color.rgb(203, 190, 184);
    public static final int white_cheek = Color.rgb(251, 228, 231);
    public static final int gray_cheek = Color.rgb(255, 159, 140);
    public static final int white_shadow = Color.rgb(251, 225, 227);
    public static final int gray_shadow = Color.rgb(185, 164, 159);
    public static final int border = Color.rgb(60, 10, 0);
    public static final int green = Color.rgb(0, 255, 0);
    public static final int blue = Color.rgb(0, 0, 255);

    // bc processing an array is  quicker than a bitmap
    private int[] src;
    private int[] dst;

    private Bitmap srcBitmap;

    private int start;
    private int length; // length in PIXELS
    private int rowLength;
    private int columnLength;


    // ok so a ~500 x 500 img has around 300'000 pixels
    // let's say the CPU has 8 cores
    // once a core is done it takes a new task, so we want multiple tasks per core available to enable balancing
    // we want the work slices to not be too coarse but also not too small to be meaningful
    // let's go with 20.000 pixels per slice for now, that would make a normal img about 15 slices
    protected static int sThreshold = 15000;

    public ColorSwapper(Bitmap original) {
        this.srcBitmap = denoiseBitmap(original);
        this.src = bitmapToArray(srcBitmap);
        this.rowLength = original.getWidth();
        this.columnLength = original.getHeight();
        this.length = rowLength * columnLength;
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

    private Bitmap denoiseBitmap(Bitmap bitmap) {
        Mat og = new Mat();
        Utils.bitmapToMat(bitmap, og);
        Mat denoised = new Mat();
        Photo.fastNlMeansDenoising(og, denoised, 50, 7, 21);

        Bitmap result = Bitmap.createBitmap(denoised.cols(), denoised.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(denoised, result);

        return result;
    }

    private Bitmap smoothEdges(Bitmap bitmap) {
        Mat og = new Mat();
        Utils.bitmapToMat(bitmap, og);
        Imgproc.cvtColor(og, og, Imgproc.COLOR_RGBA2RGB);

        Mat smoothed = new Mat();
        Imgproc.bilateralFilter(og, smoothed, 9, 150, 180);

        Bitmap result = Bitmap.createBitmap(smoothed.cols(), smoothed.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(smoothed, result);
        return result;
    }


    public Bitmap swapColors() {
        if (srcBitmap == null) {
            throw new IllegalStateException("This function should only be used if the ColorSwapper " +
                    "object was initialized using a Bitmap (public constructor)");
        }

        Bitmap swappedImageBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig());

        swapCheeks();
        swapRest();

        writeArrayToBitmap(dst, swappedImageBitmap);
        swappedImageBitmap = smoothEdges(swappedImageBitmap);

        return swappedImageBitmap;
    }

    private int getX(int i) {
        return i % rowLength;
    }

    private int getY(int i) {
        return i / rowLength;
    }

    private int up(int i, int step) {
        int result = i - rowLength*step;
        return (is_in_bounds(result)? result : -1);
    }

    private int down(int i, int step) {
        int result = i + rowLength*step;
        return (is_in_bounds(result)? result : -1);
    }

    private int left(int i, int step) {
        int result = i - step;
        return ((is_in_bounds(result) && getY(result) == getY(i))? result : -1);
    }

    private int right(int i, int step) {
        int result = i + step;
        return ((is_in_bounds(result) && getY(result) == getY(i))? result : -1);
    }


    private HashSet<Integer> floodSelection(int pixelWithinArea, double tolerance) {
        // idea: instead of recursion, process one pixel per loop and add 4 pixels to the queue per loop
        // continue processing queue tasks even after adding new pixels is done
        HashSet<Integer> selection = new HashSet<>();
        int color = src[pixelWithinArea];
        HashSet<Integer> alreadyBeen = new HashSet<Integer>();
        Queue<Integer> toDo = new LinkedList<Integer>();

        toDo.add(pixelWithinArea);
        int i;

        while(!toDo.isEmpty()) {

            i = toDo.poll();

            if (alreadyBeen.contains(i)) continue;
            alreadyBeen.add(i);

            if (is_within_tolerance(src[i], color, tolerance)) {
                selection.add(i);
            }
            else {
                continue;
            }

            int left = left(i,1 );
            int right = right(i, 1);
            int up = up(i, 1);
            int down = down(i, 1);


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

        return selection;
    }



    private void floodSwapArea(int pixelWithinArea, int newColor, double tolerance) {

        HashSet<Integer> area = floodSelection(pixelWithinArea, tolerance);

        if (area.size() < length * 0.1 ) {
            for (int j : area) {
                src[j] = newColor;
            }
        }
    }


    private void swapCheeks() {
        // identifying and swapping the cheeks is run sequentially bc doing it in parallel would be a mess
        // like if a cheek is on a border between two data slices
        int step = 2;

        //TODO remove
//        int cheek = 72 +  313*rowLength;
//        floodSwapArea(cheek, blue);

        HashSet<Integer> no = new HashSet<>();

        for (int i = 0; i < length; i += step) {

            if (no.contains(i)) continue;

            int pixelColor = src[i];

            //TODO sth is fundamentally wrong here. like when you think about it, I'm not checking from the middle
            // but from the top left corner
            if (is_within_tolerance(pixelColor, white_cheek, 0.1)) {
//                System.out.println("checking");
                int adjusted = right(down(i, 10),10);
                if (is_encircled(adjusted, white_cheek, white, 0.1, 0.2)) {
                    floodSwapArea(adjusted, green, 0.1);
                    System.out.println("White cheek");
                }
                else {
                    no.addAll(floodSelection(adjusted, 0.05));
                }
            }
            else if (is_within_tolerance(pixelColor, gray_cheek, 0.1)) {
                if (is_encircled(i, gray_cheek, gray, 0.1, 0.2)) {
                    floodSwapArea(i, blue, 0.05);
                }
                else {
                    no.addAll(floodSelection(i, 0.05));
                }
            }

        }

    }

    private void swapRest() {
        // the colors of everything but the cheeks are swapped in parallel using multi threading
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(this);
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

    private enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    private int goDirection(Direction direction, int startPixel, int step) {
        switch (direction) {
            case UP:
                return up(startPixel, step);
            case DOWN:
                return down(startPixel, step);
            case LEFT:
                return left(startPixel, step);
            case RIGHT:
                return right(startPixel, step);
            default:
                return -1;
        }
    }

    public boolean checkBorderInDirection(int pixel, Direction direction, int currentColor, int borderColor, double insideTolerance, double borderTolerance, int max_search_distance){
        int step = 2;
        // in case the jpg noise or whatever else stops us early,
        // jump a few pixels to be properly in the border color
        int forGoodMeasure = 10;
        int distance = 0;

        int maxBorderErrors = 5; // some error is allowed due to jpg noise
        int errors = 0;
        // go DIRECTION til you find a new color
        while (true) {
            pixel = goDirection(direction, pixel, step);
            distance += step;
            if (!is_in_bounds(pixel) || (distance > max_search_distance) || errors > maxBorderErrors) return false;

            if (is_within_tolerance(src[pixel], currentColor, insideTolerance)) {
                continue;
            }
            else if (!is_within_tolerance(src[pixel], borderColor, borderTolerance)) {

                errors++;
                pixel = goDirection(direction, pixel, forGoodMeasure);
            }
            else {
                return true; // border of correct color has been found
            }
        }
    }

    private boolean is_encircled(int i, int currentColor, int borderColor, double insideTolerance, double borderTolerance) {
        /**
         checks whether first color encountered in cardinal directions (up, down, left, right) is borderColor
         currentColor is the color of the area that the current pixel is in
         insidetolerance should be smaller than bordertolerance
         */

        int max_search_distance = (int)(rowLength*0.2);

        return checkBorderInDirection(i, Direction.UP, currentColor, borderColor, insideTolerance, borderTolerance, max_search_distance) &&
        checkBorderInDirection(i, Direction.DOWN, currentColor, borderColor, insideTolerance, borderTolerance, max_search_distance) &&
        checkBorderInDirection(i, Direction.LEFT, currentColor, borderColor, insideTolerance, borderTolerance, max_search_distance) &&
        checkBorderInDirection(i, Direction.RIGHT, currentColor, borderColor, insideTolerance, borderTolerance, max_search_distance);

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
