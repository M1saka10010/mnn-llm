package com.mnn.llm;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;


public class MainActivity extends AppCompatActivity {
    private Chat mChat;
    private Intent mIntent;
    private Button mLoadButton;
    private TextView mModelInfo;
    private RelativeLayout mProcessView;
    private Handler mProcessHandler;
    private ProgressBar mProcessBar;
    private TextView mProcessName;
    private TextView mProcessPercent;
    // resource files
    private String mModelName = "qwen-1.8b-int4";
    private String mModelDir = "/data/local/tmp/chat/" + mModelName; // default dir
    private boolean mModelReady = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mIntent = new Intent(this, Conversation.class);
        mModelInfo = (TextView)findViewById(R.id.model_info);
        mLoadButton = (Button)findViewById(R.id.load_button);
        mProcessView = (RelativeLayout)findViewById(R.id.process_view);
        mProcessBar = (ProgressBar)findViewById(R.id.process_bar);
        mProcessName = (TextView)findViewById(R.id.process_name);
        mProcessPercent = (TextView)findViewById(R.id.process_percent);
        // using assert file
        mModelDir = this.getCacheDir() + "/" + mModelName;
        mProcessHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                int progress = msg.arg1;
                mProcessBar.setProgress((int)progress);
                mProcessPercent.setText(" " + progress + "%");
                if (progress >= 100) {
                    mLoadButton.setClickable(true);
                    mLoadButton.setBackgroundColor(Color.parseColor("#3e3ddf"));
                    mLoadButton.setText("加载已完成");
                    mIntent.putExtra("chat", mChat);
                    startActivity(mIntent);
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public void onCheckModels() {
        mModelReady = checkModelsReady();
        // try copy from assert file
        if (!mModelReady) {
            try {
                mModelDir = Common.copyAssetResource2File(this, mModelName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            mModelReady = checkModelsReady();
        }
        // download models
        if (!mModelReady) {
            mModelInfo.setVisibility(View.VISIBLE);
            mModelInfo.setText("请下载模型文件");
            mLoadButton.setText("下载模型");
        } else {
            mModelInfo.setVisibility(View.VISIBLE);
            mModelInfo.setText(mModelName + "模型文件就绪，模型加载中");
            mLoadButton.setText("加载模型");
        }
    }
    public boolean checkModelsReady() {
        System.out.println("### Check Models!");
        File dir = new File(mModelDir);
        if (!dir.exists()) {
            return false;
        }
        String[] modelArray = this.getResources().getStringArray(R.array.model_list);
        for (int i = 0; i < modelArray.length; i++) {
            File model = new File(mModelDir, modelArray[i]);
            if (!model.exists()) {
                return false;
            }
        }
        return true;
    }

    public void loadModel(View view) {
        onCheckModels();
        if (!mModelReady) {
            startActivity(new Intent(this, DownloadModel.class));
            return;
        }
        mLoadButton.setClickable(false);
        mLoadButton.setBackgroundColor(Color.parseColor("#2454e4"));
        mLoadButton.setText("模型加载中 ...");
        mProcessView.setVisibility(View.VISIBLE);
        mChat = new Chat();
        Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mIntent.putExtra("chat", mChat);
                startActivity(mIntent);
            }
        };
        // copy models
        LoadThread loadT = new LoadThread(mChat, handler, mModelDir);
        loadT.start();
        ProgressThread progressT = new ProgressThread(mChat, mProcessHandler);
        progressT.start();
    }
}

class LoadThread extends Thread {
    private Chat mChat;
    private Handler mHandler;
    private String mModelDir;
    LoadThread(Chat chat, Handler handler, String modelDir) {
        mChat = chat;
        mHandler = handler;
        mModelDir = modelDir;
    }
    public void run() {
        super.run();
        mChat.Init(mModelDir);
        mHandler.sendMessage(new Message());
    }
}

class ProgressThread extends Thread {
    private Handler mHandler;
    private Chat mChat;

    ProgressThread(Chat chat, Handler handler) {
        mChat = chat;
        mHandler = handler;
    }

    public void run() {
        super.run();
        float progress = 0;
        while (progress < 100) {
            try {
                Thread.sleep(50);
            } catch (Exception e) {}
            float new_progress = mChat.Progress();
            if (Math.abs(new_progress - progress) < 0.01) {
                continue;
            }
            progress = new_progress;
            Message msg = new Message();
            msg.arg1 = (int)progress;
            mHandler.sendMessage(msg);
        }
    }
}