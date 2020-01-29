package com.mojang.paintscan;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.ar.core.ArCoreApk;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.os.Environment;
import android.os.FileUriExposedException;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.provider.MediaStore;
import android.content.Intent;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.google.helpers.CameraPermissionHelper;

public class MainActivity extends AppCompatActivity {

    static final String LOG_TAG = "MainActivity";
    static final int REQUEST_IMAGE_CAPTURE = 1;

    static File sTakePictureFile = null;

    private void dispatchTakePictureIntent() {
        sTakePictureFile = generateSaveFile();

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, getFileUri(this, sTakePictureFile));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        }
    }

    protected Task<List<FirebaseVisionBarcode>> detectInImage(Bitmap bitmap) {
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
        FirebaseVisionBarcodeDetector detector = FirebaseVision.getInstance().getVisionBarcodeDetector();
        return detector.detectInImage(image);
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
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                ImageView imgView = findViewById(R.id.imageView);

                if (sTakePictureFile == null) {
                    Toast.makeText(this, "Problem while reading intent URI", Toast.LENGTH_LONG);
                    return;
                }

                File photoFile = sTakePictureFile;
                if (!photoFile.exists()) {
                    Toast.makeText(MainActivity.this, "Problem while trying to find photo: " + photoFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    return;
                }

                Bitmap imageBitmap = decodeSampledBitmapFromResource(photoFile, 1024, 1024);
                imgView.setImageBitmap(imageBitmap);

                detectInImage(imageBitmap).addOnSuccessListener(barcodes -> {
                    Point center1 = null;
                    Point center2 = null;
                    Point center3 = null;
                    Point center4 = null;
                    String targetPath = null;

                    for (FirebaseVisionBarcode barcode: barcodes) {
                        if (barcode == null) {
                            continue;
                        }
                        String value = barcode.getDisplayValue().toLowerCase();
                        if (value.equals("corner1\r\n") || value.equals("corner1")) {
                            center1 = getCentroid(barcode.getCornerPoints());
                        } else if (value.equals("corner2\r\n") || value.equals("corner2")) {
                            center2 = getCentroid(barcode.getCornerPoints());
                        } else if (value.equals("corner3\r\n") || value.equals("corner3")) {
                            center3 = getCentroid(barcode.getCornerPoints());
                        } else if (value.equals("corner4\r\n") || value.equals("corner4")) {
                            center4 = getCentroid(barcode.getCornerPoints());
                        } else {
                            targetPath = barcode.getDisplayValue();
                        }
                    }
                    if (targetPath == null) {
                        Toast.makeText(MainActivity.this, "You need to scan the name marker", Toast.LENGTH_LONG).show();
                        // HACK
                        targetPath = "textures/entity/pig/pig.png";
                        // return;
                    }
                    if (center1 == null || center2 == null || center3 == null || center4 == null) {
                        Toast.makeText(MainActivity.this, "You need to scan all 5 markers", Toast.LENGTH_LONG).show();
                        // return;
                    }
                    Log.v(LOG_TAG, "calling saveTarget with " + targetPath);

                    saveTarget(imageBitmap, targetPath);
                });
            }
        }
    }

    private Point getCentroid(Point[] cornerPoints) {
        if (cornerPoints == null) {
            return null;
        }
        if (cornerPoints.length < 4) {
            return null;
        }
        return new Point(
                ((cornerPoints[0].x + cornerPoints[1].x + cornerPoints[2].x + cornerPoints[3].x) / 4),
                ((cornerPoints[0].y + cornerPoints[1].y + cornerPoints[2].y + cornerPoints[3].y) / 4));
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

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
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
