
package com.example.samplestickerapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;


public class EditStickerActivity extends AppCompatActivity {
    // why is this necessary? lmao
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private ImageView imageView;
    private FloatingActionButton addImageBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_sticker);

        imageView = findViewById(R.id.image_of_edited_sticker);
        addImageBtn = findViewById(R.id.add_image_btn);

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

}
