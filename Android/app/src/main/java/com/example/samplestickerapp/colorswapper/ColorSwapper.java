package com.example.samplestickerapp.colorswapper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.graphics.Color;



public class ColorSwapper {
    public static final int  white = Color.rgb(255, 253, 255);
    public static final int  gray = Color.rgb(203, 190, 184);
    public static final int  white_cheek = Color.rgb(251, 228, 231);
    public static final int  gray_cheek = Color.rgb(255, 159, 140);
    public static final int white_shadow = Color.rgb(251, 225, 227);
    public static final int gray_shadow = Color.rgb(185, 164, 159);
    public static final int border = Color.rgb(60, 10, 0);
    public static final int green = Color.rgb(0, 255, 0);



    public static void main(String[] args) {
    }

    public static int[] int_to_Color(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        int[] ret = new int[] { red, green, blue };
        return ret;
    }

    public static void print(int x) {
        System.out.println(x);
    }

    public static boolean is_encircled(int x, int y, Bitmap img, int currentColor, int encircling, double tolerance) {
        /**
         checks whether first color encountered in all directions is border color
         currentColor is the color of the area that the current pixel is in
         */

        int borders = 0; // if there's 4, the pixel is (likely) surrounded

        int width = img.getWidth();
        int height = img.getHeight();
        // the borders may not be completely sharp; this is meant to jump inside them to get higher color accuracy
        // this calculation assumes that the sticker is roughly square
        // print(width);
        int for_good_measure = (int)((width * 0.0138)/2.0);
        // print(for_good_measure);

        // checks in 4 directions
        int x_copy = x;
        int y_copy = y;
        // up
        while (y_copy > 0 && is_within_tolerance(img.getPixel(x_copy, y_copy), currentColor, tolerance)) {
            y_copy--;
        }
        if (y_copy - for_good_measure > 0) {
            y_copy -= for_good_measure;
        }

        if (is_within_tolerance(img.getPixel(x_copy, y_copy), encircling, tolerance)) {
            borders++;
        }

        y_copy = y;
        // down
        while (y_copy < height - 1 && is_within_tolerance(img.getPixel(x_copy, y_copy), currentColor, tolerance)) {
            y_copy++;
        }
        if (y_copy + for_good_measure < height -1) {
            y_copy += for_good_measure;
        }
        if (is_within_tolerance(img.getPixel(x_copy, y_copy), encircling, tolerance)) {
            borders++;
        }

        // left
        while (x_copy > 0 && is_within_tolerance(img.getPixel(x_copy, y_copy), currentColor, tolerance)) {
            x_copy--;
        }
        if (x_copy - for_good_measure > 0) {
            x_copy -= for_good_measure;
        }
        if (is_within_tolerance(img.getPixel(x_copy, y_copy), encircling, tolerance)) {
            borders++;
        }

        x_copy = x;
        // right
        while (x_copy < width - 1 && is_within_tolerance(img.getPixel(x_copy, y_copy), currentColor, tolerance)) {
            x_copy++;
        }
        if (x_copy + for_good_measure < width - 1) {
            x_copy += for_good_measure;
        }
        if (is_within_tolerance(img.getPixel(x_copy, y_copy), encircling, tolerance)) {
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


    // Method to swap two colors in the BufferedImage
    public static Bitmap swapColors(Bitmap originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // Create a new Bitmap to store the result
        Bitmap swappedImage = Bitmap.createBitmap(width, height, originalImage.getConfig());


        // Iterate through each pixel
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixelColor = originalImage.getPixel(x, y);

                // Compare the pixel color to color1 or color2 and swap accordingly
                if (is_within_tolerance(pixelColor, white_shadow, 0.05)) { // how likely it is to find a shadow
                    if (is_encircled(x, y, originalImage, pixelColor, white, 0.04)) { // in this case, it's the cheek, not a shadow (they're roughly the same color) // how likely it is to think the shadow is a cheek
                        swappedImage.setPixel(x, y, gray_cheek);
                        continue;
                    }
                    swappedImage.setPixel(x, y, gray_shadow);
                }
                else if (is_within_tolerance(pixelColor, white, 0.15)) {
                    swappedImage.setPixel(x, y, gray);
                } else if (is_within_tolerance(pixelColor, gray, 0.1)) {
                    swappedImage.setPixel(x, y, white);
                }
                else if (is_within_tolerance(pixelColor, gray_shadow, 0.1)) {
                    swappedImage.setPixel(x, y, white_shadow);
                }
                // else if (is_within_tolerance(pixelColor, white_cheek, 0.1)) { // the vast majority of this already gets caught by white_shadow
                //     swappedImage.setRGB(x, y, green);}
                else if (is_within_tolerance(pixelColor, gray_cheek, 0.08)) {
                    if (is_encircled(x, y, originalImage, pixelColor, border, 0.05)) { // in this case, it's the mouth, not the cheeks (they're the same color)
                        swappedImage.setPixel(x, y, pixelColor);
                        System.out.println("BORDERS");
                        continue;
                    }
                    else {
                        swappedImage.setPixel(x, y, white_cheek); //TODO it tends to think the ears are cheek too. But the encircle check doesn't work there bc they*re surrounded by white just like the cheeks
                    }


                } else {
                    swappedImage.setPixel(x, y, pixelColor);
                }
            }
        }
        return swappedImage;
    }
}


