import os
import cv2
import pathlib
import requests
import matplotlib.pyplot as plt
import numpy as np
from datetime import datetime
from collections import deque 

class ChangeDetection:
    ANOMALY_WINDOW_SIZE = 5  # Number of frames for rolling average/stdev
    Z_SCORE_THRESHOLD = 2.0  # Threshold for Z-score to flag an anomaly
    
    HOST = 'http://127.0.0.1:8000'
    username = 'client'
    password = 'pw12345678'
    token = ''
    title = ''
    text = ''
    
    def __init__(self, names):
        self.result_prev = [0 for i in range(len(names))]
        self.names = names
        
        self.detection_stats = {}  
        self.total_frames = 0
        
        self.rolling_counts = {name: deque(maxlen=self.ANOMALY_WINDOW_SIZE) for name in names}


        res = requests.post(self.HOST + '/api-token-auth/', {
            'username': self.username,'password': self.password,
        })
        res.raise_for_status()
        self.token = res.json()['token']
        print(self.token)


    def add(self, names, detected_current_mask, save_dir, image, current_frame_counts):
        self.title = ''
        self.text = ''
        change_flag = 0 
        i = 0
        while i < len(self.result_prev):
            if self.result_prev[i]==0 and detected_current_mask[i]==1:
                change_flag = 1
                self.title = names[i]
                self.text += names[i] + ", "
            i += 1
        self.result_prev = detected_current_mask[:] 
        
        is_anomaly, anomaly_info = self.is_anomaly(current_frame_counts)

        if change_flag == 1:
            if not is_anomaly:
                self.title = f"Change Detected: {self.title}"
                self.text = f"New objects appeared: {self.text.strip(', ')}"
            self.send(save_dir, image)
            
        elif is_anomaly:
            self.title = f"Current Main Focus: High {anomaly_info['class']} Count"
            self.text = (
                f"Class: {anomaly_info['class']} | Count: {anomaly_info['count']} | "
                f"Avg: {anomaly_info['avg']:.2f} | Stdev: {anomaly_info['stdev']:.2f} | "
                f"Z-score: {anomaly_info['zscore']:.2f}"
            )
            self.send(save_dir, image)

    def is_anomaly(self, current_frame_counts):
        anomaly_flag = False
        anomaly_info = {}
        
        for cls_index, cls_name in enumerate(self.names):
            current_count = current_frame_counts.get(cls_index, 0)
            
            self.rolling_counts[cls_name].append(current_count)

            if len(self.rolling_counts[cls_name]) == self.ANOMALY_WINDOW_SIZE:
                history = np.array(self.rolling_counts[cls_name])
                
                mean = history[:-1].mean()
                stdev = history[:-1].std() 
                
                if stdev > 0.1: 
                    z_score = abs(current_count - mean) / stdev
                    
                    if z_score >= self.Z_SCORE_THRESHOLD:
                        anomaly_flag = True
                        if z_score > anomaly_info.get('zscore', 0):
                             anomaly_info = {
                                'class': cls_name,
                                'count': current_count,
                                'avg': mean,
                                'stdev': stdev,
                                'zscore': z_score
                            }

        return anomaly_flag, anomaly_info

    def send(self, save_dir, image):
        now = datetime.now()
        now_str = now.isoformat()
        today = datetime.now()
        
        save_dir_path = pathlib.Path(save_dir)
        save_path = save_dir_path / 'detected' / str(today.year) / str(today.month) / str(today.day)
        pathlib.Path(save_path).mkdir(parents=True, exist_ok=True)
        full_path = save_path / '{0}-{1}-{2}-{3}.jpg'.format(today.hour,today.minute,today.second,today.microsecond)
        
        dst = cv2.resize(image, dsize=(320, 240), interpolation=cv2.INTER_AREA)
        cv2.imwrite(str(full_path), dst)
        
        headers = {'Authorization' : 'JWT ' + self.token, 'Accept' : 'application/json'}
        
        data = {'author': 1, 'title' : self.title,'text' : self.text,'created_date' : now_str,'published_date' : now_str}
        file = {'image' : open(full_path, 'rb')}
        res = requests.post(self.HOST + '/api_root/Post/', data=data, files=file, headers=headers)
        print(f"Image Post Response: {res}")
        res.raise_for_status()

    def accumulate_stats(self, detection_counts_current_frame):
        self.total_frames += 1
        
        for cls_index, count in detection_counts_current_frame.items():
            cls_name = self.names[cls_index]
            self.detection_stats[cls_name] = self.detection_stats.get(cls_name, 0) + count

    def send_stats_plot(self, save_dir):
        stats = {k: v for k, v in self.detection_stats.items() if v > 0}
        if not stats:
            print("No objects detected for statistics plot.")
            return

        classes = list(stats.keys())
        counts = list(stats.values())
        total_objects = sum(counts)
        
        fig, ax = plt.subplots(figsize=(10, 6))
        
        y_pos = np.arange(len(classes))
        ax.barh(y_pos, counts, align='center')
        ax.set_yticks(y_pos, labels=classes)
        ax.invert_yaxis()
        ax.set_xlabel('Total Count Across All Frames')
        ax.set_title(f'Object Detection Summary (Total Frames: {self.total_frames})')

        for i, v in enumerate(counts):
            ax.text(v + 3, i + 0.25, str(v), color='blue', fontweight='bold')
            
        plot_dir = pathlib.Path(save_dir) / 'stats_plots'
        plot_dir.mkdir(parents=True, exist_ok=True)
        plot_path = plot_dir / f'detection_summary_{datetime.now().strftime("%Y%m%d_%H%M%S")}.png'
        
        plt.tight_layout()
        plt.savefig(plot_path)
        plt.close(fig)
        
        self.send_plot(plot_path, self.total_frames, total_objects)

    def send_plot(self, full_path, total_frames, total_objects):
        now = datetime.now().isoformat()
        
        headers = {'Authorization' : 'JWT ' + self.token, 'Accept' : 'application/json'}
        
        self.title = "Video Detection Statistics"
        self.text = f"Total Frames: {total_frames}, Total Objects Detected: {total_objects}"
        
        data = {
            'author': 1, 
            'title': self.title,
            'text': self.text,
            'created_date': now, 
            'published_date': now,
        }
        
        file = {'image' : open(full_path, 'rb')}
        res = requests.post(self.HOST + '/api_root/Post/', data=data, files=file, headers=headers)
        print(f"Stats Post Response: {res}")
        res.raise_for_status()