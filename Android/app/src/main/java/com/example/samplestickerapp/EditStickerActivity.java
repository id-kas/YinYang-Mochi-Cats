
package com.example.samplestickerapp;

import com.example.samplestickerapp.colorswapper.ColorSwapper;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;


public class EditStickerActivity extends AppCompatActivity {
    // why is this necessary? lmao
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private ImageView imageView;
    private FloatingActionButton addImageBtn;

    private Button swapBtn;
    private Button downloadBtn;
    private Button saveBtn;
    private ImageView imageOfEditedSticker;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_sticker);

        imageView = findViewById(R.id.image_of_edited_sticker);
        addImageBtn = findViewById(R.id.add_image_btn);
        swapBtn =  findViewById(R.id.swap_btn);
        downloadBtn = findViewById((R.id.download_btn));
        saveBtn = findViewById((R.id.save_btn));
        imageOfEditedSticker = findViewById(R.id.image_of_edited_sticker);

        addImageBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // check if android version is high enough for permissions to even exist
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) // if permission for external storage hasn't been granted yet, ask for it
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                } else {
                    openImagePicker();
                }
            } else {
                openImagePicker();
            }
        });


        swapBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // get uploaded image from imageview
                Bitmap inputBitmap = null;
                try  {
                    inputBitmap = ((BitmapDrawable) imageOfEditedSticker.getDrawable()).getBitmap();
                }
                catch (NullPointerException e) {
                    Toast.makeText(EditStickerActivity.this, "Please upload an image", Toast.LENGTH_SHORT).show();
                }


                if (inputBitmap != null) {
                    Bitmap swappedBitmap = runColorSwapTool(inputBitmap);
                    // display result
                    imageOfEditedSticker.setImageBitmap(swappedBitmap);
                } else {
                    Toast.makeText(getApplicationContext(), "Please upload an image", Toast.LENGTH_SHORT).show();
                }
            }
        });

        downloadBtn.setOnClickListener(v -> {
            saveImageToGallery(imageView);
        });


        saveBtn.setOnClickListener(v -> {
            Bitmap bitmap = null;
            try  {
                bitmap = ((BitmapDrawable) imageOfEditedSticker.getDrawable()).getBitmap();
            }
            catch (NullPointerException e) {
                Toast.makeText(EditStickerActivity.this, "No image to use", Toast.LENGTH_SHORT).show();
                return;
            }

            addToStickerPack(bitmap);
        });





    }

    private void openImagePicker() {
        /**
         * Opens Image Picker
         */
        // you're telling it that you want to pick something and the 2nd argument indicates the location; photos are in the externals storage
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // further specify type to get only the type of file you want
        intent.setType("image/*");
        // tell it to start an activity to fulfill this intent. PICK_IMAGE_REQUEST is basically just an ID, it can be any number
        // just makes it possible to identify the response that the activity will deliver
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // here you use PICK_IMAGE_REQUEST to identify the result you asked for
        // you also check whether it was successful, whether there's actual data to process
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            // Android doesn't pass the full image, just the URI. You still have to load the image manually.
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                imageView.setImageBitmap(bitmap);

                addImageBtn.setVisibility(View.GONE); // hide the button after successful upload
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private Bitmap runColorSwapTool(Bitmap inputBitmap) {
        // Ensure the input Bitmap is mutable (required for color modification)
        Bitmap mutableBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true);

        return  ColorSwapper.swapColors(mutableBitmap);
    }


    private void saveImageToGallery(ImageView imageView) {
        Bitmap bitmap = null;
        try  {
            bitmap = ((BitmapDrawable) imageOfEditedSticker.getDrawable()).getBitmap();
        }
        catch (NullPointerException e) {
            Toast.makeText(EditStickerActivity.this, "No image to download", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the Bitmap from the ImageView
        saveImageToGalleryAndroidQ(bitmap);
    }

    private void saveImageToGalleryAndroidQ(Bitmap bitmap) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "image_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try (OutputStream outStream = getContentResolver().openOutputStream(imageUri)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);  // Save bitmap to the output stream
            Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
        }
    }

    private File addToStickerPack(Bitmap bitmap) {

        File folder = new File(getFilesDir(), "Android/app/src/main/assets");
        if (!folder.exists()) {
            folder.mkdirs();  // Create the folder if it doesn't exist
        }

        // Create a new file with a unique name
        String fileName = "sticker_" + System.currentTimeMillis() + ".webp";
        File webpFile = new File(folder, fileName);

        try (FileOutputStream out = new FileOutputStream(webpFile)) {
            // Compress the Bitmap to WebP format and save it to the file
            boolean isSaved = bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out);  // 100 is the quality (0-100)
            if (isSaved) {
                return webpFile;  // Return the saved WebP file
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;  // If saving failed, return null
    }

}
