package com.example.facerecognitionimages;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;


import com.example.facerecognitionimages.face_recognition.FaceClassifier;
import com.example.facerecognitionimages.face_recognition.TFLiteFaceRecognition;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;


public class RecognitionActivity extends AppCompatActivity {
    CardView galleryCard,cameraCard;
    ImageView imageView;
    Uri image_uri;
    public static final int PERMISSION_CODE = 100;


    //TODO declare face detector
    // High-accuracy landmark detection and face classification
    FaceDetectorOptions highAccuracyOpts =
            new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .build();
    FaceDetector detector;
    //TODO declare face recognizer
    FaceClassifier faceClassifier;

    //TODO get the image from gallery and display it
    ActivityResultLauncher<Intent> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        image_uri = result.getData().getData();
                        Bitmap inputImage = uriToBitmap(image_uri);
                        Bitmap rotated = rotateBitmap(inputImage);
                        imageView.setImageBitmap(rotated);
                        performFaceDetection(rotated);
                    }
                }
            });

    //TODO capture the image using camera and display it
    ActivityResultLauncher<Intent> cameraActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Bitmap inputImage = uriToBitmap(image_uri);
                        Bitmap rotated = rotateBitmap(inputImage);
                        imageView.setImageBitmap(rotated);
                        performFaceDetection(rotated);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);

        //TODO handling permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED){
                String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                requestPermissions(permission, PERMISSION_CODE);
            }
        }

        //TODO initialize views
        galleryCard = findViewById(R.id.gallerycard);
        cameraCard = findViewById(R.id.cameracard);
        imageView = findViewById(R.id.imageView2);

        //TODO code for choosing images from gallery
        galleryCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                galleryActivityResultLauncher.launch(galleryIntent);
            }
        });

        //TODO code for capturing images using camera
        cameraCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera();
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
//                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                            == PackageManager.PERMISSION_DENIED){
//                        String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
//                        requestPermissions(permission, PERMISSION_CODE);
//                    }
//                    else {
//                        openCamera();
//                    }
//                }
//
//                else {
//                    openCamera();
//                }
            }
        });

        //TODO initialize face detector
        detector = FaceDetection.getClient(highAccuracyOpts);

        //TODO initialize face recognition model
        try {
            faceClassifier =  TFLiteFaceRecognition.create(getAssets(),"facenet.tflite",160,false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //TODO opens camera so that user can capture image
    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera");
        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
        cameraActivityResultLauncher.launch(cameraIntent);
    }

    //TODO takes URI of the image and returns bitmap
    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);

            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return  null;
    }

    //TODO rotate image if image captured on samsung devices
    //TODO Most phone cameras are landscape, meaning if you take the photo in portrait, the resulting photos will be rotated 90 degrees.
    @SuppressLint("Range")
    public Bitmap rotateBitmap(Bitmap input){
        String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
        Cursor cur = getContentResolver().query(image_uri, orientationColumn, null, null, null);
        int orientation = -1;
        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
        }
        Log.d("tryOrientation",orientation+"");
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(orientation);
        Bitmap cropped = Bitmap.createBitmap(input,0,0, input.getWidth(), input.getHeight(), rotationMatrix, true);
        return cropped;
    }

    //TODO perform face detection
    Canvas canvas;
    public void performFaceDetection(Bitmap bitmap) {
        Bitmap mutatedBmp = bitmap.copy(Bitmap.Config.ARGB_8888,true);
        canvas = new Canvas(mutatedBmp);
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        Task<List<Face>> result =
                detector.process(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<Face> faces) {
                                        // Task completed successfully
                                        // ...
                                        Log.d("Try Face ","Face Len ="+faces.size());
                                        for (Face face : faces) {
                                            Rect bounds = face.getBoundingBox();

                                            Paint paint = new Paint();
                                            paint.setColor(Color.RED);
                                            paint.setStyle(Paint.Style.STROKE);
                                            paint.setStrokeWidth(4.0f);
                                            performFaceRecognition(bounds,bitmap);
                                            canvas.drawRect(bounds,paint);
                                        }

                                        imageView.setImageBitmap(mutatedBmp);
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        // Task failed with an exception
                                        // ...
                                    }
                                });

    }

    //TODO perform face recognition
    public void performFaceRecognition(Rect bound, Bitmap bitmap) {
        if(bound == null){
            Log.d("No Face","No Face Found");
        }
        assert bound != null;
        if(bound.top<0){
            bound.top = 0;
        }
        if(bound.left<0){
            bound.left = 0;
        }
        if(bound.right > bitmap.getWidth()){
            bound.right = bitmap.getWidth()-1;
        }
        if(bound.bottom > bitmap.getHeight()){
            bound.bottom = bitmap.getHeight()-1;
        }

        Bitmap croppedFace =  Bitmap.createBitmap(bitmap, bound.left, bound.top, bound.width(), bound.height());
        imageView.setImageBitmap(croppedFace);
        croppedFace = Bitmap.createScaledBitmap(croppedFace,160,160,false);
        FaceClassifier.Recognition recognition = faceClassifier.recognizeImage(croppedFace,false);
        if(recognition != null){
            Log.d("Recognition Face",recognition.getTitle() +" | "+recognition.getDistance());
            if(recognition.getDistance() < 1){
                Paint paint = new Paint();
                paint.setColor(Color.WHITE);
//                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4.0f);
                paint.setTextSize(7);
                canvas.drawText(recognition.getTitle(),bound.left,bound.top,paint);
            }
        }

//        showRegisterDialog(croppedFace,recognition);
//        recognition.getTitle();

//        recognition.getEmbeeding()
    }
    public void showRegisterDialog(Bitmap face,FaceClassifier.Recognition recognition){
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.register_face_dialogue);
        ImageView imageView1 = dialog.findViewById(R.id.dlg_image);
        EditText editText = dialog.findViewById(R.id.dlg_input);
        EditText editId = dialog.findViewById(R.id.dlg_input3);
        Button register = dialog.findViewById(R.id.button2);
        imageView1.setImageBitmap(face);
        register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(editText.getText().toString().equals("") || editId.getText().toString().equals("")){
                    editText.setError("Entry Name/ID Required");
                }else {
                    faceClassifier.register(editText.getText().toString(),recognition);
                    Toast.makeText(RecognitionActivity.this, "Face Registered", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();

                }
            }
        });

        dialog.show();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

    }
    }