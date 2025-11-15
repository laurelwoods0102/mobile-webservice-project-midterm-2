package com.example.photoviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * RecyclerView의 데이터를 관리하고 뷰를 바인딩하는 어댑터입니다.
 * 서버에서 가져온 Post 객체 리스트를 사용합니다.
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.PostViewHolder> {
    private static final String TAG = "ImageAdapter";
    private final List<Post> postList;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public ImageAdapter(List<Post> postList) {
        this.postList = postList;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = postList.get(position);
        holder.titleTextView.setText(post.getTitle());
        holder.textTextView.setText(post.getText());

        String imageUrl = post.getImageUrl();

        if (imageUrl != null && !imageUrl.isEmpty()) {
            holder.imageView.setVisibility(View.VISIBLE);
            // 기본 이미지(ic_launcher_background) 설정 (로딩 중 표시)
            holder.imageView.setImageResource(R.drawable.ic_launcher_background);

            // Picasso 대신 직접 이미지 다운로드 로직 호출
            downloadImage(imageUrl, holder.imageView);

        } else {
            // 이미지 URL이 없을 경우 ImageView 숨기기
            holder.imageView.setVisibility(View.GONE);
        }
    }

    /**
     * 지정된 URL에서 이미지를 다운로드하여 ImageView에 설정합니다.
     * 네트워크 작업은 Executor를 사용하여 백그라운드 스레드에서 수행됩니다.
     */
    private void downloadImage(final String urlString, final ImageView imageView) {
        // 이미지를 다운로드할 백그라운드 스레드에서 실행
        executor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();

                InputStream input = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(input);

            } catch (Exception e) {
                Log.e(TAG, "Error downloading image: " + urlString, e);
                // 오류 발생 시 null로 유지
            } finally {
                if (connection != null) connection.disconnect();
            }

            // 다운로드 완료 후 UI 스레드에서 ImageView 업데이트
            final Bitmap downloadedBitmap = bitmap;
            handler.post(() -> {
                if (downloadedBitmap != null) {
                    imageView.setImageBitmap(downloadedBitmap);
                } else {
                    // 다운로드 실패 시 오류 이미지 설정
                    imageView.setImageResource(R.drawable.ic_launcher_background);
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    /**
     * 리사이클러 뷰 항목의 개별 뷰를 담는 ViewHolder 클래스입니다.
     */
    public static class PostViewHolder extends RecyclerView.ViewHolder {
        public final TextView titleTextView;
        public final TextView textTextView;
        public final ImageView imageView;

        public PostViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.itemTitleTextView);
            textTextView = itemView.findViewById(R.id.itemTextTextView);
            imageView = itemView.findViewById(R.id.itemImageView);
        }
    }
}
