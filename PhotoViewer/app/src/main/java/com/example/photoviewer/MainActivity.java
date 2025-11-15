package com.example.photoviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "PhotoViewer";

    // 서버 설정
    private static final String SERVER_URL = "http://10.0.2.2:8000/api_root/Post/";
    private static final String AUTH_TOKEN = "bf46b8f9337d1d27b4ef2511514c798be1a954b8"; // Django REST Framework 인증 토큰
    private static final int PICK_IMAGE_REQUEST = 1;

    private TextView statusTextView;
    private RecyclerView recyclerView;
    private EditText editTextTitle;
    private EditText editTextContent;
    private ImageView imagePreview;

    private Uri selectedImageUri = null;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        recyclerView = findViewById(R.id.recyclerView);
        editTextTitle = findViewById(R.id.editTextTitle);
        editTextContent = findViewById(R.id.editTextContent);
        imagePreview = findViewById(R.id.imagePreview);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 앱 시작 시 게시물 목록을 자동으로 불러옵니다.
        //fetchPosts(SERVER_URL);
    }

    /**
     * 'Select & Upload Image' 버튼 클릭 이벤트 핸들러.
     */
    public void onClickUpload(View view) {
        if (selectedImageUri == null) {
            // 이미지가 선택되지 않았다면 갤러리/파일 탐색기를 열어 이미지를 선택합니다.
            openImageChooser();
        } else {
            // 이미지가 선택되었다면 업로드를 시도합니다.
            uploadImage(selectedImageUri);
        }
    }

    /**
     * 'Refresh List' 버튼 클릭 이벤트 핸들러.
     */
    public void onClickDownload(View view) {
        fetchPosts(SERVER_URL);
    }

    /**
     * 갤러리/파일 탐색기를 열어 이미지 선택을 요청합니다.
     */
    private void openImageChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            // 미리보기 이미지 뷰에 선택된 이미지를 설정합니다.
            imagePreview.setImageURI(selectedImageUri);
            // 업로드 버튼 텍스트를 변경하여 사용자에게 다음 동작을 안내합니다.
            findViewById(R.id.btn_upload).setEnabled(true);
            ((android.widget.Button) findViewById(R.id.btn_upload)).setText("Upload Selected Image");
            Toast.makeText(this, "Image selected. Click again to upload.", Toast.LENGTH_SHORT).show();
        } else {
            selectedImageUri = null;
            imagePreview.setImageResource(android.R.drawable.ic_menu_gallery); // 기본 이미지로 되돌립니다.
            ((android.widget.Button) findViewById(R.id.btn_upload)).setText("Select & Upload Image");
        }
    }

    /**
     * 서버에서 게시물 목록을 가져옵니다.
     */
    private void fetchPosts(final String urlString) {
        statusTextView.setText("Loading posts...");
        executor.execute(() -> {
            String status = "Load failed.";
            List<Post> posts = null;
            HttpURLConnection conn = null;

            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Token " + AUTH_TOKEN);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    status = "Posts loaded successfully!";
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    posts = parsePosts(response.toString());

                } else {
                    status = "Load failed. Response Code: " + responseCode;
                }

            } catch (Exception e) {
                Log.e(TAG, "Fetch Error", e);
                status = "Load failed: " + e.getMessage();
            } finally {
                if (conn != null) conn.disconnect();
            }

            final List<Post> finalPosts = posts;
            final String finalStatus = status;

            handler.post(() -> updateUI(finalPosts, finalStatus));
        });
    }

    /**
     * JSON 응답을 Post 객체 리스트로 파싱합니다.
     */
    private List<Post> parsePosts(String jsonResponse) {
        List<Post> posts = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(jsonResponse);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String title = jsonObject.getString("title");
                String text = jsonObject.getString("text");
                String imageUrl = jsonObject.getString("image"); // URL 문자열입니다.

                posts.add(new Post(title, text, imageUrl));
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON Parsing Error", e);
        }
        return posts;
    }

    /**
     * 이미지 업로드를 서버에 요청합니다. (멀티파트/폼 데이터 방식)
     */
    private void uploadImage(final Uri imageUri) {
        // EditText에서 제목과 내용을 가져옵니다.
        String title = editTextTitle.getText().toString().trim();
        String text = editTextContent.getText().toString().trim();

        if (title.isEmpty() || text.isEmpty()) {
            Toast.makeText(this, "Please enter both title and content.", Toast.LENGTH_SHORT).show();
            return;
        }

        statusTextView.setText("Uploading image...");

        executor.execute(() -> {
            String status = "Upload failed.";
            HttpURLConnection conn = null;
            DataOutputStream dos = null;
            InputStream inputStream = null;
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****" + System.currentTimeMillis() + "*****";

            try {
                URL url = new URL(SERVER_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true); // 입력 허용
                conn.setDoOutput(true); // 출력 허용
                conn.setUseCaches(false); // 캐시 사용 안 함
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Authorization", "Token " + AUTH_TOKEN);
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                // 이미지 데이터 준비
                inputStream = getContentResolver().openInputStream(imageUri);
                ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                int bufferSize = 1024;
                byte[] buffer = new byte[bufferSize];
                int len = 0;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }
                byte[] imageBytes = byteBuffer.toByteArray();

                // 서버에 데이터 전송 시작
                dos = new DataOutputStream(conn.getOutputStream());

                // 1. Title 필드 추가 (텍스트 데이터)
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"title\"" + lineEnd);
                dos.writeBytes(lineEnd);
                dos.writeBytes(title);
                dos.writeBytes(lineEnd);

                // 2. Text 필드 추가 (텍스트 데이터)
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"text\"" + lineEnd);
                dos.writeBytes(lineEnd);
                dos.writeBytes(text);
                dos.writeBytes(lineEnd);

                // 3. Author 필드 추가 (고정된 텍스트 데이터) - 서버 요구 사항 충족
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"author\"" + lineEnd);
                dos.writeBytes(lineEnd);
                dos.writeBytes("1"); // 고정된 작성자 ID 사용
                dos.writeBytes(lineEnd);

                // 4. Image 필드 추가 (바이너리 파일)
                String fileName = "upload_" + System.currentTimeMillis() + ".jpg";
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"image\";filename=\"" + fileName + "\"" + lineEnd);
                dos.writeBytes("Content-Type: image/jpeg" + lineEnd);
                dos.writeBytes(lineEnd);

                dos.write(imageBytes);
                dos.writeBytes(lineEnd);

                // 멀티파트 끝
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                dos.flush();
                dos.close();

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    status = "Upload successful! Response Code: " + responseCode;
                } else {
                    // 서버 오류 응답 처리 로직 추가
                    BufferedReader reader;
                    if (conn.getErrorStream() != null) {
                        reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    } else {
                        reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    }
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    reader.close();
                    Log.e(TAG, "Server Error Response: " + errorResponse.toString());

                    status = "Upload failed. Response Code: " + responseCode;
                }

            } catch (Exception e) {
                Log.e(TAG, "Upload Error", e);
                status = "Upload failed: " + e.getMessage();
            } finally {
                // finally 블록에서 스트림을 안전하게 닫습니다.
                try {
                    if (dos != null) dos.close();
                } catch (IOException e) {
                    Log.e(TAG, "DataOutputStream close error", e);
                }
                try {
                    if (inputStream != null) inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "InputStream close error", e);
                }
                if (conn != null) conn.disconnect();
            }

            final String finalStatus = status;

            // UI 스레드에서 결과 표시 및 목록 새로 고침
            handler.post(() -> {
                Toast.makeText(MainActivity.this, finalStatus, Toast.LENGTH_LONG).show();
                statusTextView.setText(finalStatus);
                // 업로드 성공 시 목록 새로 고침
                if (finalStatus.contains("successful")) {
                    // 업로드 성공 후 입력 필드 및 미리보기 초기화
                    editTextTitle.setText("");
                    editTextContent.setText("");
                    imagePreview.setImageResource(android.R.drawable.ic_menu_gallery);
                    selectedImageUri = null;
                    ((android.widget.Button) findViewById(R.id.btn_upload)).setText("Select & Upload Image");
                    fetchPosts(SERVER_URL);
                }
            });
        });
    }

    /**
     * UI를 업데이트합니다 (게시물 목록 및 상태 텍스트).
     */
    private void updateUI(List<Post> posts, String status) {
        statusTextView.setText(status);

        if (posts != null && !posts.isEmpty()) {
            ImageAdapter adapter = new ImageAdapter(posts);
            recyclerView.setAdapter(adapter);
            recyclerView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.GONE);
            if (posts != null) {
                statusTextView.setText("No images to load. " + status);
            }
        }
    }
}
