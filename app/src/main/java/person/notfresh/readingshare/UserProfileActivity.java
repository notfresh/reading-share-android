package person.notfresh.readingshare;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.textfield.TextInputEditText;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class UserProfileActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_GALLERY = 2;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private ImageView profileImage;
    private TextInputEditText usernameEdit;
    private TextInputEditText emailEdit;
    private Uri currentPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        // 设置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("个人信息");

        // 初始化视图
        profileImage = findViewById(R.id.profile_image);
        usernameEdit = findViewById(R.id.edit_username);
        emailEdit = findViewById(R.id.edit_email);
        Button saveButton = findViewById(R.id.btn_save);
        ImageView editAvatar = findViewById(R.id.edit_avatar);

        // 加载保存的用户信息
        loadUserProfile();

        // 设置头像编辑点击事件
        editAvatar.setOnClickListener(v -> showImagePickerDialog());

        // 设置保存按钮点击事件
        saveButton.setOnClickListener(v -> saveUserProfile());
    }

    private void showImagePickerDialog() {
        String[] options = {"拍照", "从相册选择"};
        new AlertDialog.Builder(this)
                .setTitle("选择头像")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkCameraPermissionAndLaunch();
                    } else {
                        openGallery();
                    }
                })
                .show();
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE);
        } else {
            launchCamera();
        }
    }

    private void launchCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = createImageFile();
            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".provider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, 
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private File createImageFile() {
        File storageDir = getExternalFilesDir("profile_images");
        File image = new File(storageDir, "profile_image.jpg");
        try {
            if (!image.exists()) {
                image.createNewFile();
            }
            return image;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            try {
                Bitmap bitmap = null;
                if (requestCode == REQUEST_IMAGE_CAPTURE) {
                    if (currentPhotoUri != null) {
                        bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), currentPhotoUri);
                    }
                } else if (requestCode == REQUEST_GALLERY && data != null) {
                    Uri selectedImage = data.getData();
                    bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImage);
                }

                if (bitmap != null) {
                    // 压缩图片
                    bitmap = getResizedBitmap(bitmap, 500); // 限制最大尺寸为 500px
                    
                    // 保存到应用私有目录
                    File filesDir = getFilesDir();
                    File imageFile = new File(filesDir, "profile_" + System.currentTimeMillis() + ".jpg");
                    
                    FileOutputStream fos = new FileOutputStream(imageFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.close();

                    // 更新 UI 和保存 URI
                    profileImage.setImageBitmap(bitmap);
                    saveImageUri(imageFile.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.e("UserProfile", "Error processing image", e);
                Toast.makeText(this, "处理图片时出错", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }

        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    private void loadUserProfile() {
        SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
        usernameEdit.setText(prefs.getString("username", ""));
        emailEdit.setText(prefs.getString("email", ""));
        String imageUri = prefs.getString("profile_image", "");
        if (!imageUri.isEmpty()) {
            profileImage.setImageURI(Uri.parse(imageUri));
        }
    }

    private void saveUserProfile() {
        String username = usernameEdit.getText().toString();
        String email = emailEdit.getText().toString();

        SharedPreferences.Editor editor = getSharedPreferences("UserProfile", MODE_PRIVATE).edit();
        editor.putString("username", username);
        editor.putString("email", email);
        editor.apply();

        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void saveImageUri(String uri) {
        SharedPreferences.Editor editor = getSharedPreferences("UserProfile", MODE_PRIVATE).edit();
        editor.putString("profile_image", uri);
        editor.apply();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 