import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.FlavorListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;


public class ColorSwapTool {
    public static final Color white = new Color(255, 253, 255); 
    public static final Color gray = new Color(203, 190, 184);
    public static final Color white_cheek = new Color(251, 228, 231);
    public static final Color gray_cheek = new Color(255, 159, 140);
    public static final Color white_shadow = new Color(251, 225, 227);
    public static final Color gray_shadow = new Color(185, 164, 159);
    public static final Color border = new Color(60, 10, 0);
    public static final Color green = new Color(0, 255, 0);


    public static void main(String[] args) {
        // TODO leaving webp processing for later

        

        // load image into BufferedImage data structure
        // File inputFile = new File("C:\\Users\\idaka\\Desktop\\YingYang Mochi Cats\\stickers\\mouth_test.jpg"); 
        File inputFile = new File("C:\\Users\\idaka\\Desktop\\YingYang Mochi Cats\\stickers\\piggy.jpg"); 
        // File inputFile = new File("C:\\Users\\idaka\\Desktop\\YingYang Mochi Cats\\stickers\\lounging.jpg"); 
        try {
            BufferedImage image = ImageIO.read(inputFile);

            if (image == null) {
                System.err.println("Could not read image. Please ensure the image format is supported.");
                return;
            }

            // Perform the color swap
            BufferedImage swappedImage = swapColors(image);

            // Save the new image with swapped colors
            File outputFile = new File("C:\\Users\\idaka\\Desktop\\YingYang Mochi Cats\\stickers\\out_piggy.jpg"); // Output file path
            ImageIO.write(swappedImage, "JPG", outputFile); 
            System.out.println("Image saved with swapped colors!");

        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public static boolean is_encircled(int x, int y, BufferedImage img, Color currentColor, Color encircling, double tolerance) {
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
        while (y_copy > 0 && is_within_tolerance(img.getRGB(x_copy, y_copy), currentColor, tolerance)) {
            y_copy--;
        }
        if (y_copy - for_good_measure > 0) {
            y_copy -= for_good_measure;
        }
        
        if (is_within_tolerance(img.getRGB(x_copy, y_copy), encircling, tolerance)) {
            borders++;
        }

        y_copy = y;
        // down
        while (y_copy < height - 1 && is_within_tolerance(img.getRGB(x_copy, y_copy), currentColor, tolerance)) {
            y_copy++;
        }
        if (y_copy + for_good_measure < height -1) {
            y_copy += for_good_measure;
        }
        if (is_within_tolerance(img.getRGB(x_copy, y_copy), encircling, tolerance)) {
            borders++;
        }

        // left
        while (x_copy > 0 && is_within_tolerance(img.getRGB(x_copy, y_copy), currentColor, tolerance)) {
            x_copy--;
        }
        if (x_copy - for_good_measure > 0) {
            x_copy -= for_good_measure;
        }
        if (is_within_tolerance(img.getRGB(x_copy, y_copy), encircling, tolerance)) {
            borders++;
        }

        x_copy = x;
        // right
        while (x_copy < width - 1 && is_within_tolerance(img.getRGB(x_copy, y_copy), currentColor, tolerance)) {
            x_copy++;
        }
        if (x_copy + for_good_measure < width - 1) {
            x_copy += for_good_measure;
        }
        if (is_within_tolerance(img.getRGB(x_copy, y_copy), encircling, tolerance)) {
            borders++;
        }

        // if (borders == 4) {
        //     System.out.println("I am encircled!");
        // }

        return borders == 4;
    }

    public static boolean is_within_tolerance(int actual, Color target, double tolerance) {

        if (tolerance > 1 || tolerance < 0) {
            System.out.println("Tolerance value invalid.");
        }

        int[] actual_components = int_to_Color(actual);

        double max_red_deviation = target.getRed() * tolerance;
        double max_green_deviation = target.getGreen() * tolerance;
        double max_blue_deviation = target.getBlue() * tolerance;

        if (Math.abs(actual_components[0] - target.getRed()) > max_red_deviation) {
            return false;
        }

        if (Math.abs(actual_components[1] - target.getGreen()) > max_green_deviation) {
            return false;
        }

        if (Math.abs(actual_components[2] - target.getBlue()) > max_blue_deviation) {
            return false;
        }

        return true;
    }

    // Method to swap two colors in the BufferedImage
    public static BufferedImage swapColors(BufferedImage originalImage) {
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // Create a new BufferedImage to store the result
        BufferedImage swappedImage = new BufferedImage(width, height, originalImage.getType());

        // Iterate through each pixel
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixelColor = originalImage.getRGB(x, y);

                // Compare the pixel color to color1 or color2 and swap accordingly
                if (is_within_tolerance(pixelColor, white, 0.07)) {
                    swappedImage.setRGB(x, y, gray.getRGB());
                } else if (is_within_tolerance(pixelColor, gray, 0.1)) {
                    swappedImage.setRGB(x, y, white.getRGB());
                } else if (is_within_tolerance(pixelColor, white_shadow, 0.05)) {
                    if (is_encircled(x, y, originalImage, white_shadow, white, 0.1)) { // in this case, it's the cheek, not a shadow (they're roughly the same color)
                        swappedImage.setRGB(x, y, gray_cheek.getRGB());
                        continue;
                    }
                    swappedImage.setRGB(x, y, gray_shadow.getRGB());
                } else if (is_within_tolerance(pixelColor, gray_shadow, 0.1)) {
                    swappedImage.setRGB(x, y, white_shadow.getRGB());
                } else if (is_within_tolerance(pixelColor, white_cheek, 0.04)) {
                    swappedImage.setRGB(x, y, gray_cheek.getRGB());
                } else if (is_within_tolerance(pixelColor, gray_cheek, 0.15)) {
                    if (is_encircled(x, y, originalImage, gray_cheek, border, 0.5)) { // in this case, it's the mouth, not the cheeks (they're the same color)
                        swappedImage.setRGB(x, y, pixelColor);
                        System.out.println("BORDERS");
                        continue;
                    }
                    else {
                        swappedImage.setRGB(x, y, white_cheek.getRGB());
                    }

                    
                } else {
                    swappedImage.setRGB(x, y, pixelColor);
                }
            }
        }
        return swappedImage;
    }
}
