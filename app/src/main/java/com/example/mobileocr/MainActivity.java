package com.example.mobileocr;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.leptonica.android.Pix;
import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfRect;
import org.opencv.core.MatOfRotatedRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;

import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class MainActivity extends AppCompatActivity {
    private String currentPhotoPath;
    private final int CAMERA_REQUEST_CODE = 999;
    private final int GALLERY_REQUEST_CODE = 112;
    private Mat image;
    private List<String> strings = new ArrayList<>();

    static {
        System.loadLibrary("opencv_java4"); //the name of the .so file, without the 'lib' prefix
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
        }
        if(!OpenCVLoader.initDebug()){
            System.out.println(" -------------------------------- Not initialized ----------------------------");
        }
        else{
            System.out.println(" -------------------------------- Yes initialized ----------------------------");
        }
        this.copyTessFiles();
        this.copyEastFiles();
    }

    public void chooseFile(View view){
        Intent chooseIntent = new Intent(Intent.ACTION_PICK);
        chooseIntent.setType("image/*");
        String[] mimeTypes = {"image/jpeg", "image/png"};
        chooseIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(chooseIntent, GALLERY_REQUEST_CODE);
    }

    public void startCamera(View view) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (photoFile != null) {
            Uri photoUri = FileProvider.getUriForFile(this, "com.example.mobileocr.fileprovider", photoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            startActivityForResult(intent, CAMERA_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data){
        super.onActivityResult(reqCode, resCode, data);
        if(reqCode == CAMERA_REQUEST_CODE && resCode == RESULT_OK){
            try {
                this.openImage();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if(reqCode == GALLERY_REQUEST_CODE && resCode == RESULT_OK){
            try {
                Uri selectedImage = data.getData();
                currentPhotoPath = getRealFromUri(selectedImage);
                this.openImage();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    private String getRealFromUri(Uri uri){
        String result;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if(cursor == null){
            result = uri.getPath();
        }
        else{
            cursor.moveToFirst();
            int ndx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(ndx);
            cursor.close();
        }
        return result;
    }

    private File createImageFile() throws IOException{
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }


    private void copyTessFiles(){
        try{
            File dir = getExternalFilesDir("/tessdata");
            if(!dir.exists()){
                if(!dir.mkdir()){
                    Toast.makeText(getApplicationContext(), "The folder " + dir.getPath() + "was not created", Toast.LENGTH_SHORT).show();
                }
            }
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            }
            String fileList[] = getAssets().list("tessdata");
            System.out.println(fileList.length);
            for(String filename: fileList){
                System.out.println(filename);
                String pathToDataFile = dir + "/" + filename;
                if(!(new File(pathToDataFile)).exists()){
                    InputStream in = getAssets().open("tessdata/" + filename);
                    OutputStream out = new FileOutputStream(pathToDataFile);
                    byte[] buff = new byte[1024];
                    int len;
                    while((len = in.read(buff)) > 0){
                        out.write(buff, 0, len);
                    }
                    in.close();
                    out.close();
                }
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private void copyEastFiles(){
        try{
            File dir = getExternalFilesDir("/east");
            if(!dir.exists()){
                if(!dir.mkdir()){
                    Toast.makeText(getApplicationContext(), "The folder " + dir.getPath() + "was not created", Toast.LENGTH_SHORT).show();
                }
            }
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
            }
            String fileList[] = getAssets().list("east");
            for(String filename: fileList){
                String pathToDataFile = dir + "/" + filename;
                if(!(new File(pathToDataFile)).exists()){
                    InputStream in = getAssets().open("east/" + filename);
                    OutputStream out = new FileOutputStream(pathToDataFile);
                    byte[] buff = new byte[1024];
                    int len;
                    while((len = in.read(buff)) > 0){
                        out.write(buff, 0, len);
                    }
                    in.close();
                    out.close();
                }
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private void startOCR(Mat mat){
        try{
            Bitmap bm;
            bm = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bm);
            String result = this.runTess(bm);
            strings.add(result);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private String runTess(Bitmap bitmap){
        TessBaseAPI tess = new TessBaseAPI();
        String dataPath = getExternalFilesDir("/").getPath() + "/";
        tess.init(dataPath, "eng");
        tess.setImage(bitmap);
        String ret = "";
        try{
            ret = tess.getUTF8Text();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        tess.end();
        return ret;
    }

    @TargetApi(26)
    private void displayText(){
        String finalString = String.join(" ", strings);
        TextView tv = findViewById(R.id.textView);
        tv.setText(finalString);
    }

    private void openImage(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
        ImageView imageView = findViewById(R.id.imageView);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = false;
        options.inSampleSize = 6;
        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, options);
//        Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        imageView.setImageBitmap(bitmap);
//        return bmp32;
    }

    //////////////// EAST / OPENCV IMPLEMENTATION //////////////////////////////////

    private void openCVImage(){
        Imgcodecs codec = new Imgcodecs();
        image = codec.imread(currentPhotoPath, Imgcodecs.IMREAD_COLOR);
    }

    public List<Mat> runEast(){
//        this.copyEastFiles();
        List<Mat> outs = new ArrayList<>(2);
        List<String> outNames = new ArrayList<String>();
        outNames.add("feature_fusion/Conv_7/Sigmoid");
        outNames.add("feature_fusion/concat_3");

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }

        this.openCVImage();
        Imgproc.cvtColor(image, image, Imgproc.COLOR_RGBA2RGB);
        String dataPath = getExternalFilesDir("/").getPath() + "/east/frozen_east_text_detection.pb";

        try {
            Imgproc.resize(image, image, new Size(320, 320));
            Net net = Dnn.readNetFromTensorflow(dataPath);
            Mat blob = Dnn.blobFromImage(image, 1.0, new Size(320, 320),
                    new Scalar(123.68, 116.78, 103.94), true, false);
            net.setInput(blob);
            Mat scores = net.forward("feature_fusion/Conv_7/Sigmoid");
            Mat geo = net.forward("feature_fusion/concat_3");
            outs.add(scores);
            outs.add(geo);
        }catch(Exception e){
            System.out.println("Error!!!!!!!!!!!!");
            e.printStackTrace();
        }

        return outs;
    }

    private List<Rect> decode(Mat scores, Mat geometry, List<Float> confidences, float scoreThresh) {
        // size of 1 geometry plane
        int W = geometry.cols();
        int H = geometry.rows() / 5;
        //System.out.println(geometry);
        //System.out.println(scores);

        List<Rect> detections = new ArrayList<>();
        for (int y = 0; y < H; ++y) {
            Mat scoresData = scores.row(y);
            Mat x0Data = geometry.submat(0, H, 0, W).row(y);
            Mat x1Data = geometry.submat(H, 2 * H, 0, W).row(y);
            Mat x2Data = geometry.submat(2 * H, 3 * H, 0, W).row(y);
            Mat x3Data = geometry.submat(3 * H, 4 * H, 0, W).row(y);
            Mat anglesData = geometry.submat(4 * H, 5 * H, 0, W).row(y);

            for (int x = 0; x < W; ++x) {
                double score = scoresData.get(0, x)[0];
                if (score >= scoreThresh) {
                    double offsetX = x * 4.0;
                    double offsetY = y * 4.0;
                    double angle = anglesData.get(0, x)[0];
                    double cosA = Math.cos(angle);
                    double sinA = Math.sin(angle);
                    double x0 = x0Data.get(0, x)[0];
                    double x1 = x1Data.get(0, x)[0];
                    double x2 = x2Data.get(0, x)[0];
                    double x3 = x3Data.get(0, x)[0];
                    double h = x0 + x2;
                    double w = x1 + x3;
                    double endX = offsetX + (cosA*x1) + (sinA*x2);
                    double endY = offsetY - (sinA*x1) + (cosA*x2);
                    Point p1 = new Point(endX-w, endY-h);
                    Point p3 = new Point(offsetX + (cosA*x1) + (sinA*x2), offsetY - (sinA*x1) + (cosA*x2));
                    Rect r = new Rect(p1, p3);
                    detections.add(r);
                    confidences.add((float) score);
                }
            }
        }
        return detections;
    }

    public void detectText(View view){
        TextView tv = findViewById(R.id.textView);
        tv.setText(null);
        List<Mat> outs = this.runEast();

        Size size = new Size(320, 320);
        int W = (int)(size.width/4);
        int H = (int)(size.height/4);

        Mat scores = outs.get(0).reshape(1, H);
        Mat geo = outs.get(1).reshape(1, 5*H);

        List<Rect> boxesList;
        List<Float> confidencesList = new ArrayList<>();
        boxesList = decode(scores, geo, confidencesList, 0.5f);

        MatOfFloat confidences = new MatOfFloat(Converters.vector_float_to_Mat(confidencesList));
        Rect[] boxesArray = boxesList.toArray(new Rect[0]);
        MatOfRect boxes = new MatOfRect(boxesArray);
        MatOfInt indices = new MatOfInt();
        Dnn.NMSBoxes(boxes, confidences, 0.5f, 0.4f, indices);

        Point ratio = new Point((float)image.cols()/size.width, (float)image.rows()/size.height);
        int[] indexes = indices.toArray();

        // Custom function calls
        ArrayList<ArrayList<Double>> stripped = strip(boxesArray, indexes);
        System.out.println("stripped is: " + stripped.size());
        ArrayList<ArrayList<ArrayList<Double>>> separate = separate_y(stripped);
        System.out.println("By y is: " + separate.size());
        separate = separate_x(separate);
        System.out.println("By x is: " + separate.size());
        ArrayList<ArrayList<Double>> final_rois = final_meld(separate);
        System.out.println("final_rois is: " + final_rois.size());

        for(int i = 0; i<final_rois.size();i++) {
            double startX = final_rois.get(i).get(0);
            double startY = final_rois.get(i).get(1);
            double endX = final_rois.get(i).get(2);
            double endY = final_rois.get(i).get(3);

            startX *= ratio.x;
            startY *= ratio.y;
            endX *= ratio.x;
            endY *= ratio.y;

            double paddingX = (endX - startX) * 0.05;
            double paddingY = (endY - startY) * 0.05;

            startX = max(0, startX - paddingX);
            startY = max(0, startY - paddingY);
            endX = min(image.cols(), endX + (paddingX));
            endY = min(image.rows(), endY + (paddingY));

            Rect crop = new Rect((int)startX, (int)startY, (int)(endX-startX), (int)(endY-startY));
            System.out.println(crop);
            System.out.println(image);
            System.out.println("Image channel: " + image.channels());

            Mat roi = image.submat(crop);
            System.out.println("roi channels: " + roi.channels());
            System.out.println("roi size: " + roi.size());
            this.startOCR(roi);

            Imgproc.rectangle(image, new Point(startX, startY), new Point(endX, endY), new Scalar(0, 0, 255), 1);
        }
        this.displayImage();
        this.displayText();
    }

    private void displayImage(){
        Bitmap bm = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, bm);
        ImageView iv = findViewById(R.id.imageView);
        iv.setImageBitmap(bm);
    }

    private ArrayList<ArrayList<Double>> strip(Rect[] boxes, int[] indexes) {
        ArrayList<ArrayList<Double>> points = new ArrayList<ArrayList<Double>>();
        for(int i = 0; i < indexes.length; i++) {
            ArrayList<Double> temp = new ArrayList<Double>();
            temp.add(boxes[indexes[i]].tl().x);
            temp.add(boxes[indexes[i]].tl().y);
            temp.add(boxes[indexes[i]].br().x);
            temp.add(boxes[indexes[i]].br().y);
            points.add(temp);
        }
        sortArr(points, 0, false);

        return points;
    }

    private void sortArr(ArrayList<ArrayList<Double>> arr, final int index, boolean reverse) {
        if(!reverse) {
            Collections.sort(arr, new Comparator<ArrayList<Double>>() {
                @Override
                public int compare(final ArrayList<Double> arg0, final ArrayList<Double> arg1) {
                    if(arg0.get(index) > arg1.get(index))
                        return 1;
                    else
                        return -1;
                }
            });
        }
        else {
            Collections.sort(arr, new Comparator<ArrayList<Double>>() {
                @Override
                public int compare(final ArrayList<Double> arg0, final ArrayList<Double> arg1) {
                    if(arg0.get(index) < arg1.get(index))
                        return 1;
                    else
                        return -1;
                }
            });
        }
    }

    private ArrayList<ArrayList<ArrayList<Double>>> separate_y(ArrayList<ArrayList<Double>> box) {
        ArrayList<ArrayList<ArrayList<Double>>> separated = new ArrayList<ArrayList<ArrayList<Double>>>();
        ArrayList<Integer> stop = new ArrayList<>();
        sortArr(box, 1, false);
        for(int i = 0; i < box.size(); i++) {
            double cutOffY = box.get(i).get(3) - box.get(i).get(1);
            try {
                if(Math.abs(box.get(i).get(1) - box.get(i+1).get(1)) < cutOffY)
                    continue;
                else
                    stop.add(i + 1);
            }catch(Exception e) {
                break;
            }
        }
        int beg = 0;
        for(int i = 0; i < stop.size(); i++) {
            int end = stop.get(i);
            ArrayList<ArrayList<Double>> temp = new ArrayList<ArrayList<Double>>(box.subList(beg,  end));
            separated.add(temp);
            beg = end;

        }

        return separated;
    }

    private ArrayList<ArrayList<ArrayList<Double>>> separate_x(ArrayList<ArrayList<ArrayList<Double>>> box) {
        int cutOffX = 40;
        ArrayList<ArrayList<ArrayList<Double>>> stop = new ArrayList<ArrayList<ArrayList<Double>>>();
        for(ArrayList<ArrayList<Double>> arr: box) {
            int start = 0;
            sortArr(arr, 0, false);
            for(int i = 0; i < arr.size(); i++) {
                try {
                    if(Math.abs(arr.get(i).get(2) - arr.get(i+1).get(0)) < cutOffX || arr.get(i).get(2) - arr.get(i+1).get(0) > 0) {
                        continue;
                    }
                    else {
                        ArrayList<ArrayList<Double>> temp = new ArrayList<ArrayList<Double>>(arr.subList(start, i+1));
                        stop.add(temp);
                        start = i + 1;
                    }
                }catch(Exception e) {
                    ArrayList<ArrayList<Double>> temp = new ArrayList<ArrayList<Double>>(arr.subList(start, arr.size()));
                    stop.add(temp);
                    break;
                }
            }
        }

        return stop;
    }

    private ArrayList<ArrayList<Double>> final_meld(ArrayList<ArrayList<ArrayList<Double>>> box) {
        ArrayList<ArrayList<Double>> final_roi = new ArrayList<ArrayList<Double>>();
        for(ArrayList<ArrayList<Double>> line: box) {
            ArrayList<Double> roi_line = new ArrayList<Double>();
            int index = 0;
            while(index < 4) {
                if(line.size() > 0) {
                    if(index < 2) {
                        sortArr(line, index, false);
                        roi_line.add(line.get(0).get(index));
                        index += 1;
                    }
                    else {
                        sortArr(line, index, true);
                        roi_line.add(line.get(0).get(index));
                        index += 1;
                    }
                }else if(line.size() == 1) {
                    roi_line = line.get(0);
                }

            }

            final_roi.add(roi_line);
        }

        return final_roi;
    }

}
