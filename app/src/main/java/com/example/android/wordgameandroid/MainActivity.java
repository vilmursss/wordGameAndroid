package com.example.android.wordgameandroid;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void addWord(View view){
        Intent i = new Intent(this, AddWord.class);
        startActivity(i);
    }

    public void modifyWord(View view){
        Intent i = new Intent(this, ModifyWords.class);
        startActivity(i);
    }
}