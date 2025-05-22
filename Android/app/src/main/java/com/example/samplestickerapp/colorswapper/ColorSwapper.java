package com.example.samplestickerapp.colorswapper;
import java.time.chrono.HijrahChronology;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.HashMap;
import java.util.ArrayList;

// ok so this handles the computation and splitting up as far as I understand it. It's pretty abstract.

public class ColorSwapper extends RecursiveAction {

    public static final int white = Color.rgb(255, 253, 255);
    public static final int gray = Color.rgb(203, 190, 184);
    public static final int white_cheek = Color.rgb(251, 228, 231);
    //    public static final int  gray_cheek = Color.rgb(255, 159, 140);
    public static final int gray_cheek = Color.rgb(255, 0, 0);
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
        this.srcBitmap = original;
        this.rowLength = original.getWidth();
        this.columnLength = original.getHeight();
        this.length = rowLength * columnLength;
        this.src = bitmapToArray(original);
        this.dst = new int[length];
        this.start = 0;
        ArrayList<Integer> whiteCheeks = new ArrayList<Integer>();
        ArrayList<Integer> grayCheeks = new ArrayList<Integer>();
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

        Bitmap swappedImageBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig());

        swapCheeks(swappedImageBitmap);

        swapRest(swappedImageBitmap);

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

    private boolean isInHitbox(int i, HashMap<String, Integer> hitbox) {
        return (getX(i) > hitbox.get("leftmost") && getX(i) < hitbox.get("rightmost") &&
                getY(i) > hitbox.get("bottommost") && getY(i) < hitbox.get("topmost"));
    }

//
//    private HashMap<String, Integer> findHitbox() {
//
//        for (int i = start; i < start+length; i += step) {
//
//            int pixelColor = src[i];
//
//            if (is_within_tolerance(pixelColor, white_cheek, 0.2)) {
//                // check if it's part of already found cheek
//                if (white_cheeks == 0 || (isInHitbox(i, white_hitbox))) continue;
//
//                if (!is_encircled(i, white_cheek, white, 0.2)) {
//                    continue;
//                }
//                else {
//                    white_cheeks++;
//                    // since we're going top to bottom the first pixel in the cheek will always be topmost
//                    white_hitbox.put("topmost", i);
//                }
//
//                //TODO add handling for when the directions return -1
//                // TODO remember to actually check if it's encircled first, I aint doing that yet
//                // TODO finish the algorithm
//                int i_copy = i;
//                // find leftmost
//                while (down(i) != -1){
//                    pixelColor = src[down(i)];
//                    if (is_within_tolerance(pixelColor, white_cheek, 0.2)) {
//                        while (left(i) != -1 && is_within_tolerance(pixelColor, white_cheek, 0.2)){
//                            // we're trying to move along the border on the left
//                            // I'm about to have an aneurysm I swear
//                            pixelColor = src[left(i)];
//                        }
//                    }
//                    else {
//                        white_hitbox.put("leftmost", i);
//                        white_cheeks++;
//                        break;
//                    }
//                }
//                // find rightmost
//                i = i_copy;
//                while (down(i) != -1){
//                    pixelColor = src[down(i)];
//                    if (is_within_tolerance(pixelColor, white_cheek, 0.2)) {
//                        while (right(i) != -1 && is_within_tolerance(pixelColor, white_cheek, 0.2)){
//                            // we're trying to move along the border on the right
//                            pixelColor = src[right(i)];
//                        }
//                    }
//                    else {
//                        white_hitbox.put("rightmost", i);
//                        white_cheeks++;
//                        break;
//                    }
//                }
//
//                // by looking at leftmost and rightmost we can figure out how the cheek is tilting
//                // which is relevant for finding bottommost
//                String tilt;
//                if (getY(white_hitbox.get("leftmost")) > getY(white_hitbox.get("rightmost"))) {
//                    tilt = "right";
//                }
//                else if (getY(white_hitbox.get("leftmost")) < getY(white_hitbox.get("rightmost"))) {
//                    tilt = "left";
//                }
//                else {
//                    tilt = "none";
//                }
//
//                //find bottommost
//                i = i_copy;
//                while (right(i) != -1){
//                    pixelColor = src[right(i)];
//                    if (is_within_tolerance(pixelColor, white_cheek, 0.2)) {
//                        while (down(i) != -1 && is_within_tolerance(pixelColor, white_cheek, 0.2)){
//                            // we're trying to move along the border on the right
//                            pixelColor = src[down(i)];
//                        }
//                    }
//                    else {
//                        white_hitbox.put("bottommost", getY(i));
//                        white_cheeks++;
//                        break;
//                    }
//                }
//
//            }
// }
//
//    }

    private int identifyCheekArea(ArrayList<Integer> cheekPixels, int insideColor, int borderColor, int i) {
        // the return value is the number of border pixels that aren't the expected color
        // used to determine if the area is actually encircled by that color (with margin of error)

        // prevents going backwards
        if (cheekPixels.contains(i)) return 0;

        int left = left(i);
        int right = right(i);
        int up = up(i);
        int down = down(i);

        double tolerance = 0.2;

        int leftBorderErrors = 0;
        int rightBorderErrors = 0;
        int upBorderErrors = 0;
        int downBorderErrors = 0;

        if (left != -1 ) {
            if (is_within_tolerance(src[left], insideColor, tolerance)) {
                cheekPixels.add(left);
                identifyCheekArea(cheekPixels, insideColor, borderColor, left);
            }
            else if (is_within_tolerance(src[left], borderColor, tolerance)) {
                ;
            }
            else {
                leftBorderErrors = 1;
            }
        }
        if (right != -1) {
            if (is_within_tolerance(src[right], insideColor, tolerance)) {
                cheekPixels.add(right);
                identifyCheekArea(cheekPixels, insideColor, borderColor, right);
            }
            else if (is_within_tolerance(src[right], borderColor, tolerance)) {
                ;
            }
            else {
                rightBorderErrors = 1;
            }
        }
        if (up != -1) {
            if (is_within_tolerance(src[up], insideColor, tolerance)) {
                cheekPixels.add(up);
                identifyCheekArea(cheekPixels, insideColor, borderColor, up);
            }
            else if (is_within_tolerance(src[up], borderColor, tolerance)) {
                ;
            }
            else {
                upBorderErrors = 1;
            }
        }
        if (down != -1) {
            if (is_within_tolerance(src[down], insideColor, tolerance)) {
                cheekPixels.add(down);
                identifyCheekArea(cheekPixels, insideColor, borderColor, down);
            }
            else if (is_within_tolerance(src[down], borderColor, tolerance)) {
                ;
            }
            else {
                downBorderErrors = 1;
            }
        }

        return leftBorderErrors + rightBorderErrors + upBorderErrors + downBorderErrors;
    }

    private void swapCheeks(Bitmap swappedImageBitmap) {
        // this needs to be done separately bc it would be a mess to run it in parallel
        int step = 2;
        // idea:
        // 1) check if it's a cheek
        // 2) if it is, recursively figure out which pixels belong to it and store their indices

        //


        for (int i = start; i < start+length; i += step) {

            int pixelColor = src[i];

            if (is_within_tolerance(pixelColor, white_cheek, 0.2)) {
                if (!is_encircled(i, white_cheek, white, 0.2)) {
                    continue;
                }
                else {
                    identifyCheekArea(whiteCheeks, white_cheek, white, i);
                }
            }

            if (is_within_tolerance(pixelColor, gray_cheek, 0.2)) {
                if (!is_encircled(i, gray_cheek, gray, 0.2)) {
                    continue;
                }
                else {
                    identifyCheekArea(grayCheeks, gray_cheek, gray, i);
                }
            }


            // TODO ok so the issue is, I want to retain these arraylists to replace the colors later
            // so bc of that rn theyre class attributes
            // BUT if it turns out at this stage that the cheek isnt encircled I have to throw those pixels out
            // which I can't do if they all go in the same arraylist if you feel me
            // so ill probably need 4 arrayslists aaaa
//            int errors = identifyCheekArea(whiteCheeks, white_cheek, white, i);
//            int errors2 = identifyCheekArea(grayCheeks, gray_cheek, gray, i);
        }
    }

    private void swapRest(Bitmap swappedImageBitmap) {
        // the colors of everything but the cheeks are swapped in parallel using multi threading
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(this);
        writeArrayToBitmap(dst, swappedImageBitmap);
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

            if (is_within_tolerance(pixelColor, white_shadow, 0.2)) { // how likely it is to find a shadow
                if (is_encircled(i, pixelColor, white, 0.2)) { // in this case, it's the cheek, not a shadow (they're roughly the same color) // how likely it is to think the shadow is a cheek
                    dst[i] = gray_cheek;
                    continue;
                }
                dst[i] = gray_shadow;
            }
            else if (is_within_tolerance(pixelColor, gray_cheek, 0.2) && is_encircled(i, pixelColor, gray, 0.2)) {
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

        double leavingCurrentTolerance = 0.5;
        double isItBorderTolerance = 0.1;
        int step = 2;

        int borders = 0; // if there's 4, the pixel is (likely) surrounded
        int max_search_distance = (int)(rowLength*0.1);
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
            System.out.println("UP");
            borders++;
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
            System.out.println("going down");
            if (distance >= max_search_distance) break;
        }
        i_copy += forGoodMeasure*rowLength;
        if (is_in_bounds(i_copy) && is_within_tolerance(src[i_copy], borderColor, isItBorderTolerance)) {
            borders++;
            System.out.println("DOWN");
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
            borders++;
            System.out.println("LEFT");
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
            borders++;
            System.out.println("RIGHT");
        }
        else {
            return false;
        }
        if (borders == 4) System.out.println("I'm encircled");
//        System.out.println(borders);
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

        // 441 is the max difference between two colors, this just normalizes it so it can be
        // compared to the tolerance
        double percentage_difference = difference / 441;

        return percentage_difference <= tolerance;
    }


    public static boolean is_within_tolerance(int actual, int target_color, double tolerance) {
        return euclidian_is_within_tolerance(actual, target_color, tolerance);
    }

}
