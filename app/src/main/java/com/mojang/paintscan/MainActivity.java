package com.mojang.paintscan;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.ArCoreApk;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.provider.MediaStore;
import android.content.Intent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.helpers.CameraPermissionHelper;
import com.yalantis.ucrop.UCrop;

public class MainActivity extends AppCompatActivity {

    static final String LOG_TAG = "MainActivity";
    static final int REQUEST_IMAGE_CAPTURE = 1;

    File mTakePictureFile = null;
    File mCroppedFile = null;

    String[] targetArray = {
            "textures/entity/pig/pig.png",
            "textures/entity/cow/cow.png",
            "textures/entity/chicken.png"
    };
    String mTargetPath;

    private void dispatchTakePictureIntent() {
        mTakePictureFile = generateSaveFile();

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, getFileUri(this, mTakePictureFile));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void saveTarget(Bitmap imageBitmap, String targetPath) {
        File targetFile = generateTargetFile(targetPath);
        File parentFolder = targetFile.getParentFile();
        if (!parentFolder.exists()) {
            if (!parentFolder.mkdirs()) {
                Log.e(LOG_TAG, "Problem while creating parent directories for " + targetFile.getAbsolutePath());
            }
        }

        if (targetFile.exists()) {
            targetFile.delete();
        }

        Log.v(LOG_TAG, "File to be saved is: " + targetFile.getAbsolutePath());

        try {
            FileOutputStream out = new FileOutputStream(targetFile);
            imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == RESULT_OK) {
                handleUCropOK(data);
            } else if (resultCode == UCrop.RESULT_ERROR) {
                final Throwable cropError = UCrop.getError(data);
                cropError.printStackTrace();
                Toast.makeText(this, "Crop request failed.", Toast.LENGTH_LONG);
            }
        } else if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                handlePhotoCaptureOK(data);
            } else {
                Toast.makeText(this, "Photo capture failed.", Toast.LENGTH_LONG);
            }
        }
    }

    private void handlePhotoCaptureOK(Intent data) {
        if (mTakePictureFile == null) {
            Toast.makeText(this, "Problem while reading intent URI", Toast.LENGTH_LONG);
            return;
        }

        File photoFile = mTakePictureFile;
        if (!photoFile.exists()) {
            Toast.makeText(MainActivity.this, "Problem while trying to find photo: " + photoFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            return;
        }

        Uri sourceUri = getFileUri(this, mTakePictureFile);
        try {
            Bitmap sourceBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), sourceUri);
            // setImageView(sourceBitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mCroppedFile = new File(this.getCacheDir(), "IMG_" + System.currentTimeMillis());
        Uri targetUri = Uri.fromFile(mCroppedFile);
        if (mCroppedFile.exists()) {
            mCroppedFile.delete();
        }
        UCrop.of(sourceUri, targetUri)
                .withAspectRatio(2, 1)
                .withMaxResultSize(1024, 512)
                .start(this);
    }

    private void handleUCropOK(Intent data) {
        try {
            final Uri resultUri = UCrop.getOutput(data);
            Bitmap croppedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
            setImageView(croppedBitmap);
            saveTarget(croppedBitmap, mTargetPath);

            Toast.makeText(this, "SUCCESS!", Toast.LENGTH_LONG);
        }
        catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Save failedl!", Toast.LENGTH_LONG);
        }
    }

    private void setImageView(Bitmap bitmap) {
        ImageView imgView = findViewById(R.id.imageView);
        imgView.setImageBitmap(bitmap);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ARCore requires camera permission to operate.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        checkARSupport();

        createPhotoOutputFolder();

        ArrayAdapter adapter = new ArrayAdapter<String>(this, R.layout.activity_listview, targetArray);
        ListView listView = findViewById(R.id.listView);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView parent, View view, int position, long id) {
                mTargetPath = (String) parent.getItemAtPosition(position);
                dispatchTakePictureIntent();
            }
        });

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });
        fab.hide();

        Button mcButton = findViewById(R.id.buttonMC);
        mcButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = MainActivity.this.getPackageManager().getLaunchIntentForPackage("com.mojang.minecraftpe");
                    MainActivity.this.startActivity(i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void createPhotoOutputFolder() {
        try {
            File file = generateSaveFile();
            File folder = file.getParentFile();
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    throw new IOException();
                }
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to create saved_images directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void checkARSupport() {

        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Re-query at 5Hz while compatibility is checked in the background.
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkARSupport();
                }
            }, 200);
        }

        if (availability.isSupported()) {
            // Setup AR buttons
            // ARCore requires camera permission to operate.
            if (!CameraPermissionHelper.hasCameraPermission(this)) {
                CameraPermissionHelper.requestCameraPermission(this);
                return;
            }

        } else { // Unsupported or unknown.
            Toast.makeText(this, "AR support missing for this device.", Toast.LENGTH_LONG).show();
        }
    }

    private File generateTargetFile(String imageName) {
        String fileName = "/games/com.mojang/resource_packs/CameraThing/" + imageName;
        return new File(Environment.getExternalStorageDirectory(), fileName);
    }

    private File generateSaveFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "/saved_images/photo_" + timeStamp + ".png";
        return new File(Environment.getExternalStorageDirectory(), fileName);
    }

    private File generateSaveFileTest() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "/saved_images/test_photo_" + timeStamp + ".png";
        return new File(Environment.getExternalStorageDirectory(), fileName);
    }

    private Uri getFileUri(Context context, File file){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", file);
        }
        else{
            return Uri.fromFile(file);
        }
    }

    private Bitmap decodeSampledBitmapFromResource(File file, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
