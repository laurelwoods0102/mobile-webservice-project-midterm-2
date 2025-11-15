package com.example.photoviewer;

public class Post {
    private final String title;
    private final String text;
    private final String imageUrl;

    public Post(String title, String text, String imageUrl) {
        this.title = title;
        this.text = text;
        if (imageUrl != null && imageUrl.startsWith("http://127.0.0.1")) {
            this.imageUrl = imageUrl.replace("http://127.0.0.1", "http://10.0.2.2");
        } else {
            this.imageUrl = imageUrl;
        }
    }

    public String getTitle() { return title; }
    public String getText() { return text; }
    public String getImageUrl() { return imageUrl; }
}