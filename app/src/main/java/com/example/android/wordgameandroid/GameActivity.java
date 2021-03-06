package com.example.android.wordgameandroid;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class GameActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    // FireBase

    private FirebaseDatabase mFireDatabase;
    private DatabaseReference mDatabaseReference;
    private FirebaseAuth mFireBaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private String mUserName;
    private int mHighScore;

    // SoundPool

    private SoundPool soundPool;
    private int soundID;
    private int failureID;
    boolean loaded = false;

    // Copy button background object

    Drawable buttonBackGround;

    // Necessary textviews

    TextView translatableWord;
    TextView pointsTextView;
    TextView gameOverText;
    TextView mNotEnoughWords;

    // Clickable option buttons

    Button firstOption;
    Button secondOption;
    Button thirdOption;
    Button fourthOption;
    Button moveToAddWords;
    Button loadNewGame;

    // DbHandler

    DbHandler dbHandler = new DbHandler(this);

    // Data structure for already played words and other helping variables

    HashMap<String, String> playedWordsHashMap = new HashMap<String, String>();
    int correctAnswer = 0;
    String roundCorrectWord = "";

    // ProgressBar

    ProgressBar mProgressBar;
    CountDownTimer mCountDownTimer;
    int pbTimer = 0;

    // Spinner

    private Spinner sItems;
    ArrayList<String> spinnerArray =  new ArrayList<String>();

    // Points, level & game status

    int gameLevel = 0;
    int gamePoints = 0;
    boolean gameStopped = true;
    String currentWordList = "";

    // Sound boolean

    boolean playSounds = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

            setContentView(R.layout.activity_game);
            initFireBaseAuthentication();
            loadContentObjects();
            loadSoundPoolManager();
            sharedPreferences();
            navigateBackArrow();

            if(getIntent().getExtras()!=null){

                gamePoints = getIntent().getExtras().getInt("points");
                pointsTextView.setText("Points: "+ String.valueOf(gamePoints));
                gameStopped = getIntent().getExtras().getBoolean("game_stopped");
                playedWordsHashMap = (HashMap<String, String>) getIntent().getExtras().getSerializable("played_words_map");
                String getUpperText = getIntent().getExtras().getString("upper_text");
                gameOverText.setText(getUpperText);
                currentWordList = getIntent().getExtras().getString("current_list");
                addSpinnerValues();

            }
            if(gameStopped && playedWordsHashMap.size() >= 1){
                wrongAnswer();

            }
            if(gameStopped){
                startGame();
            }
            if(!gameStopped) {
                newQuestion();
                sItems.setVisibility(View.INVISIBLE);
            }
    }

    // Create options menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu){

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;

    }

    // Create items selectable on items menu

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem){

        int id = menuItem.getItemId();
        if(id == R.id.settings_menu){
            if(!gameStopped) {
                mCountDownTimer.cancel();
            }
            Intent startIntentActivity = new Intent(this, SettingsActivity.class);
            startIntentActivity.putExtra("CLASS_INFORMATION", GameActivity.class);
            startIntentActivity.putExtra("points", gamePoints);
            startIntentActivity.putExtra("played_words_map", playedWordsHashMap);
            startIntentActivity.putExtra("game_stopped", gameStopped);
            startIntentActivity.putExtra("upper_text", gameOverText.getText().toString());
            startIntentActivity.putExtra("current_list", currentWordList);
            startActivity(startIntentActivity);
            return true;
        }

        else if(id == R.id.sign_out_menu){
            AuthUI.getInstance().signOut(this);
            Intent startIntentActivity = new Intent(this, MainActivity.class);
            startActivity(startIntentActivity);
            return true;
        }

        else {
            Intent goBackToMainActivity = new Intent(this, MainActivity.class);
            startActivity(goBackToMainActivity);
            if(!gameStopped) {
                mCountDownTimer.cancel();
            }
            return true;
        }
    }

    // Destroy shared preferences listener

    @Override
    protected void onDestroy(){

        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause(){
        super.onPause();
        mFireBaseAuth.removeAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onResume(){
        super.onResume();
        mFireBaseAuth.addAuthStateListener(mAuthStateListener);
    }

    // Add values to spinner, which contains all saved lists

    public void addSpinnerValues() {

        if(dbHandler.getWordCount() < 1){
            spinnerArray.add("No lists created");
        }
        else {

            spinnerArray = dbHandler.getLists();
            spinnerArray.add("Choose a list, which you wanna play");

        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, spinnerArray);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sItems = (Spinner) findViewById(R.id.game_mode_spinner);
        sItems.setAdapter(adapter);
        sItems.setSelection(spinnerArray.size()-1);
    }

    // Initialize FireBase authentication

    public void initFireBaseAuthentication() {

        mFireBaseAuth = FirebaseAuth.getInstance();
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    mUserName = user.getDisplayName();
                    mDatabaseReference = mFireDatabase.getReference().child(mUserName);
                    getHighScore();

                } else {
                    Intent startIntentActivity = new Intent(GameActivity.this, MainActivity.class);
                    startActivity(startIntentActivity);
                }
            }
        };
    }

    // Get user highscore

    public void getHighScore() {

        mDatabaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                    if(dataSnapshot.exists()) {
                        mHighScore = Integer.parseInt(dataSnapshot.getValue().toString());
                    }
                    else {
                        mHighScore = 0;
                    }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }



    // Navigation back arrow

    public void navigateBackArrow() {

        ActionBar actionBar = this.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    // SoundPool manager loader

    public void loadSoundPoolManager() {

        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId,
                                       int status) {
                loaded = true;
            }
        });
        soundID = soundPool.load(this, R.raw.correct_answer, 1);
        failureID = soundPool.load(this, R.raw.fail_answer, 1);

    }

    // Create shared preferences

    public void sharedPreferences(){

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        playSounds(sharedPreferences.getBoolean(getString(R.string.play_sounds), getResources().getBoolean(R.bool.pref_sounds)));
        loadGameLevel(sharedPreferences);
    }

    public void loadGameLevel(SharedPreferences sharedPreferences){

        gameLevel = Integer.valueOf(sharedPreferences.getString(getString(R.string.game_level_key),
                getString(R.string.game_level_easy_value)));

    }

    // Load buttons and textviews

    public void loadContentObjects(){

        gameOverText = (TextView) findViewById(R.id.PickText);
        translatableWord = (TextView) findViewById(R.id.roundWord);
        pointsTextView = (TextView) findViewById(R.id.points);
        pointsTextView.setText("Points: " + String.valueOf(gamePoints));

        firstOption = (Button) findViewById(R.id.firstBtn);
        secondOption = (Button) findViewById(R.id.secondBtn);
        thirdOption = (Button) findViewById(R.id.thirdBtn);
        fourthOption = (Button) findViewById(R.id.fourthBtn);
        loadNewGame = (Button) findViewById(R.id.restartGame);
        loadNewGame.setVisibility(View.INVISIBLE);
        buttonBackGround = firstOption.getBackground();

        // ProgressBar

        mProgressBar=(ProgressBar)findViewById(R.id.progressBar);
        sItems = (Spinner) findViewById(R.id.game_mode_spinner);

        // FireBase object

        mFireDatabase = FirebaseDatabase.getInstance();
    }


    // Countdown timer for the progressBar

    public void progressBarCountDown(){

        pbTimer = 0;

        mProgressBar.setProgress(pbTimer);
        mCountDownTimer=new CountDownTimer(100000,50) {

            @Override
            public void onTick(long millisUntilFinished) {
                pbTimer = pbTimer+gameLevel;

                if(pbTimer >= mProgressBar.getMax()){
                    wrongAnswer();
                    gameOverText.setText("Time ran out :(");

                }

                mProgressBar.setProgress((int)pbTimer);

            }

            @Override
            public void onFinish() {
            }
        };
        mCountDownTimer.start();
    }

    // Give new question

    public void newQuestion() {
        gameStopped = false;

        if (gameEnd()) {

            // Compare current gamepoints to user's highscore

            if(gamePoints > mHighScore) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder.setTitle("New Highscore!");
                builder.setMessage("Do you wanna save new highscore?" + " Your old highscore is " + mHighScore + " points" +
                        ", but now you you got " + gamePoints + " points!");

                builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                        mDatabaseReference.setValue(gamePoints);
                        mHighScore = gamePoints;

                        AlertDialog alertDialog = new AlertDialog.Builder(GameActivity.this).create();
                        alertDialog.setTitle("New Highscore!");
                        alertDialog.setMessage("New highscore saved!");
                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        alertDialog.show();
                    }
                });

                builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();
                    }
                });

                AlertDialog alert = builder.create();
                alert.show();

                translatableWord.setTextSize(16);
                translatableWord.setText("All words played! Congratulations! You made new highscore! You got " + gamePoints + " points" +
                        " and your old highscore is " + mHighScore + " points.");
                sItems.setVisibility(View.VISIBLE);
                startGame();
            }
            else {
                translatableWord.setTextSize(16);
                translatableWord.setText("All words played! You got " + gamePoints + " points. Unfortunately" +
                        " this time you did not make a new record. Your current highscore is " + mHighScore + " points.");
                sItems.setVisibility(View.VISIBLE);
                startGame();
            }
        }

        else {

            // Random select correct answer to one button, then call function to fill other buttons

            int randInt = randomNumber();

            switch (randInt) {
                case 1:
                    correctAnswer = 1;
                    setCorrectWord();
                    fillOtherButtons();

                    progressBarCountDown();
                    break;
                case 2:
                    correctAnswer = 2;
                    setCorrectWord();
                    fillOtherButtons();
                    progressBarCountDown();
                    break;
                case 3:
                    correctAnswer = 3;
                    setCorrectWord();
                    fillOtherButtons();
                    progressBarCountDown();
                    break;
                case 4:
                    correctAnswer = 4;
                    setCorrectWord();
                    fillOtherButtons();
                    progressBarCountDown();
                    break;
                default:
                    translatableWord.setText("Should not happen");
                    break;
            }

        }

    }

    // Fill other options with random words from database besides correct answer

    public void fillOtherButtons() {

        HashMap<String, String> usedWords = new HashMap<String, String>();
        int i = 0;

        while (i < 4) {

            String getWord = getRandomWord();

            if (i == 0 && !usedWords.containsKey(getWord)) {

                if (correctAnswer == 1) {
                    i = 1;
                }

                else if (!getWord.equals(roundCorrectWord)) {
                    firstOption.setText(getWord);
                    usedWords.put(getWord, getWord);
                    i = 1;
                }

                else {
                    usedWords.put(getWord, getWord);
                }
            }

            else if (i == 1 && !usedWords.containsKey(getWord)) {

                if (correctAnswer == 2) {
                    i = 2;
                }

                else if (!getWord.equals(roundCorrectWord)) {
                    secondOption.setText(getWord);
                    usedWords.put(getWord, getWord);
                    i = 2;
                }

                else {
                    usedWords.put(getWord, getWord);
                }
            }

            else if (i == 2 && !usedWords.containsKey(getWord)) {

                if (correctAnswer == 3) {
                    i = 3;
                }

                else if (!getWord.equals(roundCorrectWord)) {
                    thirdOption.setText(getWord);
                    usedWords.put(getWord, getWord);
                    i = 3;
                }

                else {
                    usedWords.put(getWord, getWord);
                }
            }

            else if (i == 3 && !usedWords.containsKey(getWord)) {

                if (correctAnswer == 4) {
                    i = 4;
                }

                else if (!getWord.equals(roundCorrectWord)) {
                    fourthOption.setText(getWord);
                    usedWords.put(getWord, getWord);
                    i = 4;
                }
            }
        }
    }

    // Get random word from database

    public String getRandomWord() {
        Word randWord = dbHandler.getRandomWord(currentWordList);
        return randWord.getFirstWord();
    }

    // Random number generator

    public int randomNumber() {
        Random rand = new Random();
        int randomReturn = rand.nextInt(4) + 1;

        return randomReturn;
    }

    // If chosen option is correct, play this sound

    public void playCorrectAnswerSound(){
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        float actualVolume = (float) audioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = (float) audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float volume = actualVolume / maxVolume;

        if (loaded && playSounds) {
            soundPool.play(soundID, volume, volume, 1, 0, 1f);
        }
    }

    // If chosen option is wrong, play this sound

    public void playWrongAnswerSound(){

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        float actualVolume = (float) audioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = (float) audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float volume = actualVolume / maxVolume;

        if (loaded && playSounds) {
            soundPool.play(failureID, volume, volume, 1, 0, 1f);
        }
    }

    // Set correct word using random number generator to one of options

    public void setCorrectWord() {

        boolean check = false;
        while (!check) {
            Word randWord = dbHandler.getRandomWord(currentWordList);

            if (playedWordsHashMap.containsKey(randWord.getSecondWord())) {

            }

            else {
                playedWordsHashMap.put(randWord.getSecondWord(), randWord.getSecondWord());
                translatableWord.setText(randWord.getSecondWord());
                check = true;

                roundCorrectWord = randWord.getFirstWord();

                if (correctAnswer == 1) {
                    firstOption.setText(randWord.getFirstWord());
                } else if (correctAnswer == 2) {
                    secondOption.setText(randWord.getFirstWord());
                } else if (correctAnswer == 3) {
                    thirdOption.setText(randWord.getFirstWord());
                } else {
                    fourthOption.setText(randWord.getFirstWord());
                }
            }

        }
    }

    // If whole database is played, open new activity

    public boolean gameEnd() {

        if (playedWordsHashMap.size() == dbHandler.getWordListCount(currentWordList)) {
            return true;
        }
        return false;
    }

    // Timer, which is used when option is chosen to mask the option button

    public void waitTwoSecTimer(){

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                setAllBackToDefault();
                newQuestion();
            }
        }, 2000);
    }

    // Set all buttons back to default color

    public void setAllBackToDefault(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            firstOption.setBackground(buttonBackGround);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            secondOption.setBackground(buttonBackGround);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            thirdOption.setBackground(buttonBackGround);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            fourthOption.setBackground(buttonBackGround);
        }
    }

    // Updates points, if answer was correct

    public void updateGamePoints(){

        gamePoints = gamePoints+(1500/pbTimer)*gameLevel;
        pointsTextView.setText("Points: "+ String.valueOf(gamePoints));

    }

    // First option click

    public void firstBtnClick(View view) {

        if (correctAnswer == 1) {
            playCorrectAnswerSound();
            firstOption.setBackgroundColor(Color.GREEN);
            updateGamePoints();
            mCountDownTimer.cancel();
            waitTwoSecTimer();
        } else {
            firstOption.setBackgroundColor(Color.RED);
            wrongAnswer();
        }
    }

    // Second option click

    public void secondBtnClick(View view) {

        if (correctAnswer == 2) {

            playCorrectAnswerSound();
            secondOption.setBackgroundColor(Color.GREEN);
            updateGamePoints();
            mCountDownTimer.cancel();
            waitTwoSecTimer();
        } else {
            secondOption.setBackgroundColor(Color.RED);
            wrongAnswer();
        }
    }

    // Third option click

    public void thirdBtnClick(View view) {

        if (correctAnswer == 3) {
            playCorrectAnswerSound();
            thirdOption.setBackgroundColor(Color.GREEN);
            updateGamePoints();
            mCountDownTimer.cancel();
            waitTwoSecTimer();
        } else {
            thirdOption.setBackgroundColor(Color.RED);
            wrongAnswer();
        }
    }

    // Fourth option click

    public void fourthBtnClick(View view) {

        if (correctAnswer == 4) {
            playCorrectAnswerSound();
            fourthOption.setBackgroundColor(Color.GREEN);
            updateGamePoints();
            mCountDownTimer.cancel();
            waitTwoSecTimer();

        } else {
            fourthOption.setBackgroundColor(Color.RED);
            wrongAnswer();
        }
    }

    // Function for wrong answer

    public void wrongAnswer(){

        if(!gameStopped) {
            playWrongAnswerSound();
            mCountDownTimer.cancel();

            if (pbTimer < mProgressBar.getMax()) {
                gameOverText.setText("Wrong answer :(");
            }

        }

        gameStopped = true;

        // Check if new highscore has been created

        if(gamePoints > mHighScore){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setTitle("New Highscore!");
            builder.setMessage("Do you wanna save new highscore?" + " Your old highscore was " + mHighScore + " points" +
                    ", but now you you got "  + gamePoints + " points!");

            builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int which) {

                    dialog.dismiss();
                    mDatabaseReference.setValue(gamePoints);
                    mHighScore = gamePoints;

                    AlertDialog alertDialog = new AlertDialog.Builder(GameActivity.this).create();
                    alertDialog.setTitle("New Highscore!");
                    alertDialog.setMessage("Highscore saved!");
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            });

            builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {

                    dialog.dismiss();
                }
            });

            AlertDialog alert = builder.create();
            alert.show();

            translatableWord.setTextSize(16);
            translatableWord.setText("Congratulations! You made new highscore! You got " + gamePoints + " points " +
                    "and your old highscore is " + mHighScore + " points.");
        }
        else {
            translatableWord.setTextSize(16);
            translatableWord.setText("You got " + gamePoints + " points! This time you did not break your " +
                    "highscore, which is " + mHighScore + " points!");
        }
        hideAllButtons();
        sItems.setVisibility(View.VISIBLE);

    }

    // Hide all buttons

    public void hideAllButtons(){

        firstOption.setVisibility(View.INVISIBLE);
        secondOption.setVisibility(View.INVISIBLE);
        thirdOption.setVisibility(View.INVISIBLE);
        fourthOption.setVisibility(View.INVISIBLE);
        loadNewGame.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.INVISIBLE);

    }

    // Call two functions to set up "game options"

    public void startGame(){

        hideAllButtons();
        addSpinnerValues();

    }

    // Reload whole game

    public void reLoadGame(){

        currentWordList = sItems.getSelectedItem().toString();
        int wordListCount = dbHandler.getWordListCount(currentWordList);

        // Check if wordlist lenght is enough big

        if(wordListCount < 5){
            setContentView(R.layout.not_enough_words_in_db);

            moveToAddWords = (Button) findViewById(R.id.saveWordsBtn);
            mNotEnoughWords = (TextView) findViewById(R.id.notEnoughWords);
            mNotEnoughWords.setText("For starting the game, you need to have at least 5 saved word pairs. Currently you have " + String.valueOf(wordListCount) + " saved word pairs in this " + currentWordList + " word list");
        }

        else {

            translatableWord.setTextSize(20);
            firstOption.setVisibility(View.VISIBLE);
            secondOption.setVisibility(View.VISIBLE);
            thirdOption.setVisibility(View.VISIBLE);
            fourthOption.setVisibility(View.VISIBLE);
            loadNewGame.setVisibility(View.INVISIBLE);
            setAllBackToDefault();

            gamePoints = 0;
            playedWordsHashMap.clear();

            mProgressBar.setVisibility(View.VISIBLE);
            gameOverText.setText("Pick correct translation for word ");
            pointsTextView.setText("Points: " + String.valueOf(gamePoints));
            sItems.setVisibility(View.INVISIBLE);

            newQuestion();
        }
    }

    // Reload game button onClick function

    public void restartGameBtn(View view){
        reLoadGame();
    }

    // Directs user to Addwords activity, if not enough words in a List

    public void saveWordsBtn(View view){

        Intent i = new Intent(this, AddWord.class);
        startActivity(i);
    }

    // Check sharedpreferences for sounds

    public void playSounds(boolean soundOrNot){
    playSounds = soundOrNot;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(getString(R.string.play_sounds))){
            playSounds(sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.pref_sounds)));
        }
        else if(key.equals(getString(R.string.game_level_key))){
            loadGameLevel(sharedPreferences);
        }
    }

}
