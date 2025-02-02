package com.mad.madproject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

public class SpaceFreeRidersView extends SurfaceView implements Runnable,SensorEventListener {

    final private float volume = (float) 0.2;

    Context context;

    // This is our thread
    private Thread gameThread = null;

    // Our SurfaceHolder to lock the surface before we draw our graphics
    private SurfaceHolder ourHolder;

    // A boolean which we will set and unset
    // when the game is running- or not.
    private volatile boolean playing;

    // Game is paused at the start
    private boolean paused = true;

    // A Canvas and a Paint object
    private Canvas canvas;
    private Paint paint;

    // This variable tracks the game frame rate
    private long fps;

    // This is used to help calculate the fps
    private long timeThisFrame;

    // The size of the screen in pixels
    private int screenX;
    private int screenY;

    // The player
    private Player player;

    // The player's bullets
    private Bullet[] playerBullets = new Bullet[200];
    private int nextPlayerBullet;
    private int maxplayerBullets = 2;

    // The freeriders bullets
    private Bullet[] freeriderBullets = new Bullet[200];
    private int nextBullet;
    private int maxfreeriderBullets = 10;


    // Up to 60 freeriders
    FreeRider[] freeRiders = new FreeRider[60];
    int numfreeriders = 0;

    // For sound FX
    private SoundPool soundPool;
    private int freeriderExplodeID = -1;
    private int shootID = -1;
    private int ohID = -1;


    // The score
    int score = 0;

    // Lives
    private int lives = 4;

    // How menacing should the sound be?
    private long menaceInterval = 1000;

    SensorManager aSensorM; // a sensor manager
    Sensor aAccSensor; // the accelerometer sensor object


    // When the we initialize (call new()) on gameView
    // This special constructor method runs
    public SpaceFreeRidersView(Context context, int x, int y) {
        // The next line of code asks the
        // SurfaceView class to set up our object.
        super(context);

        // Make a globally available copy of the context so we can use it in another method
        this.context = context;
        aSensorM = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        aAccSensor = aSensorM.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // Initialize ourHolder and paint objects
        ourHolder = getHolder();
        paint = new Paint();

        screenX = x;
        screenY = y;

        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC,0);

        try{
            // Create objects of the 2 required classes
            AssetManager assetManager = context.getAssets();
            AssetFileDescriptor descriptor;

            // Load our fx in memory ready for use
            descriptor = assetManager.openFd("shoot.ogg");
            shootID = soundPool.load(descriptor, 0);

            descriptor = assetManager.openFd("freeridersexplode.ogg");
            freeriderExplodeID = soundPool.load(descriptor, 0);

            descriptor = assetManager.openFd("oh.ogg");
            ohID = soundPool.load(descriptor, 0);

        }catch(IOException e){
            // Print an error message to the console
            Log.e("error", "failed to load sound files");

        }

        prepareLevel();
    }

    private void prepareLevel() {

        // Here we will initialize all the game objects
        score = 0 ;
        // Make a new player
        player = new Player(context, screenX, screenY);
        // Prepare the players bullet
        for(int i = 0; i < playerBullets.length; i++) {
            playerBullets[i] = new Bullet(screenY);
        }
        // Initialize the Bullets array
        for(int i = 0; i < freeriderBullets.length; i++){
            freeriderBullets[i] = new Bullet(screenY);
        }

        // Build an army of freeriders
        numfreeriders = 0;
        for(int column = 0; column < 7; column ++ ){
            for(int row = 0; row < 6; row ++ ){
                freeRiders[numfreeriders] = new FreeRider(context, row, column, screenX, screenY);
                numfreeriders++;
            }
        }
    }

    @Override
    public void run() {
        while (playing) {

            // Capture the current time in milliseconds in startFrameTime
            long startFrameTime = System.currentTimeMillis();

            // Update the frame
            if (!paused) {
                update();
            }

            // Draw the frame
            draw();

            // Calculate the fps this frame
            // We can then use the result to
            // time animations and more.
            timeThisFrame = System.currentTimeMillis() - startFrameTime;
            if (timeThisFrame >= 1) {
                fps = 1000 / timeThisFrame;
            }

        }
    }

    private void update(){

        // Did an emery bump into the side of the screen
        boolean bumped = false;

        // Has the player lost
        boolean lost = false;

        // Move the player's ship
        player.update(fps);
        // Update the emery if visible
        for(int i = 0; i < numfreeriders; i++){

            if(freeRiders[i].getVisibility()) {
                // Move the next invader
                freeRiders[i].update(fps);

                // Does he want to take a shot?
                if(freeRiders[i].takeAim(player.getX(),
                        player.getLength())){

                    // If so try and spawn a bullet
                    if(freeriderBullets[nextBullet].shoot(freeRiders[i].getX()
                                    + freeRiders[i].getLength() / 2,
                            freeRiders[i].getY(), 1)) {

                        // Shot fired
                        // Prepare for the next shot
                        nextBullet++;

                        // Loop back to the first one if we have reached the last
                        if (nextBullet == maxfreeriderBullets) {
                            // This stops the firing of another bullet until one completes its journey
                            // Because if bullet 0 is still active shoot returns false.
                            nextBullet = 0;
                        }
                    }
                }

                // If that move caused them to bump the screen change bumped to true
                if (freeRiders[i].getX() > screenX - freeRiders[i].getLength()
                        || freeRiders[i].getX() < 0){

                    bumped = true;

                }
            }

        }
        // Update all the emery bullets if active
        for(int i = 0; i < freeriderBullets.length; i++){
            if(freeriderBullets[i].getStatus()) {
                freeriderBullets[i].update(fps);
            }
        }
        // Did an emery bump into the edge of the screen
        if(bumped){

            // Move all the invaders down and change direction
            for(int i = 0; i < numfreeriders; i++){
                freeRiders[i].dropDownAndReverse();
                // Have the invaders landed
                if(freeRiders[i].getY() > screenY - screenY / 100){
                    lost = true;
                }
            }

            // Increase the menace level
            // By making the sounds more frequent
            menaceInterval = menaceInterval - 80;
        }
        if(lost){
            Intent intent = new Intent(context, GameOverActivity.class);
            intent.putExtra("score", score);
            context.startActivity(intent);
            finishFunction();
        }

        // Update the players bullet
        for(int i = 0; i < playerBullets.length; i++){
            if(playerBullets[i].getStatus()){
                playerBullets[i].update(fps);
            }
        }

        // Has the player's bullet hit the top of the screen
        for(int i = 0; i < playerBullets.length; i++){
            if(playerBullets[i].getImpactPointY() < 0){
                playerBullets[i].setInactive();
            }
        }

        // Has an emery bullet hit the bottom of the screen
        for(int i = 0; i < freeriderBullets.length; i++){
            if(freeriderBullets[i].getImpactPointY() > screenY){
                freeriderBullets[i].setInactive();
            }
        }
        // Has the player's bullet hit an emery
        for(int j = 0; j < playerBullets.length; j++) {
            if (playerBullets[j].getStatus()) {
                for (int i = 0; i < numfreeriders; i++) {
                    if (freeRiders[i].getVisibility()) {
                        if (RectF.intersects(playerBullets[j].getRect(), freeRiders[i].getRect())) {
                            freeRiders[i].setInvisible();
                            soundPool.play(freeriderExplodeID, volume, volume, 0, 0, 1);
                            playerBullets[j].setInactive();
                            score = score + 10;

                            // Has the player won
                            if (score == numfreeriders * 10) {
                                paused = true;
                                lives = 3;
                                Intent intent = new Intent(context, GameWinActivity.class);
                                intent.putExtra("score", score);
                                context.startActivity(intent);
                                finishFunction();
                            }
                        }
                    }
                }
            }
        }

        // Has an emery bullet hit the player
        for(int i = 0; i < freeriderBullets.length; i++){
            if(freeriderBullets[i].getStatus()){
                if(RectF.intersects(player.getRect(), freeriderBullets[i].getRect())){
                    freeriderBullets[i].setInactive();
                    lives --;
                    soundPool.play(ohID, 1, 1, 0, 0, 1);
                    // Is it game over?
                    if(lives == 0){
                        paused = true;
                        lives = 3;
                        Intent intent = new Intent(context, GameOverActivity.class);
                        intent.putExtra("score", score);
                        context.startActivity(intent);
                        finishFunction();
                    }
                }
            }
        }

    }

    private void draw(){
        // Make sure our drawing surface is valid or we crash
        if (ourHolder.getSurface().isValid()) {
            // Lock the canvas ready to draw
            canvas = ourHolder.lockCanvas();

            // Draw the background color
            Bitmap bg = BitmapFactory.decodeResource(getResources(),R.drawable.classrm);
            canvas.drawBitmap(bg,new Rect(0,0,bg.getWidth(), bg.getHeight()), new Rect(0,0,canvas.getWidth(), canvas.getHeight()),paint);

            // Choose the brush color for drawing
            paint.setColor(Color.argb(255,  255, 255, 255));

            // Draw the player
            canvas.drawBitmap(player.getBitmap(), player.getX(), screenY - 50, paint);
            // Draw the emery
            for(int i = 0; i < numfreeriders; i++){
                if(freeRiders[i].isVisible)
                canvas.drawBitmap(freeRiders[i].getBitmap(), freeRiders[i].getX(), freeRiders[i].getY(), paint);
                }

            // Draw the players bullet if active
            for(int i = 0; i < freeriderBullets.length; i++) {
                if (playerBullets[i].getStatus()) {
                    canvas.drawRect(playerBullets[i].getRect(), paint);
                }
            }
            // Draw the emery bullets if active
            for(int i = 0; i < freeriderBullets.length; i++){
                if(freeriderBullets[i].getStatus()) {
                    canvas.drawRect(freeriderBullets[i].getRect(), paint);
                }
            }
            // Draw the score and remaining lives
            // Change the brush color
            paint.setColor(Color.argb(255,  255, 255, 255));
            paint.setTextSize(80);
            canvas.drawText("Score: " + score + "   Lives: " + lives, 10,80, paint);

            // Draw everything to the screen
            ourHolder.unlockCanvasAndPost(canvas);
        }
    }

    // If SpaceInvadersActivity is paused/stopped
    // shutdown our thread.
    public void pause() {
        aSensorM.unregisterListener(this);
        playing = false;
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            Log.e("Error:", "joining thread");
        }

    }

    // If SpaceInvadersActivity is started then
    // start our thread.
    public void resume() {
        aSensorM.registerListener(this, aAccSensor, SensorManager.SENSOR_DELAY_NORMAL);
        playing = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        paused = false;
        float x = event.values[0];
        float y = event.values[1];
            if ((x < -1) && (player.getX()<screenX-screenX/10)) {
                player.setMovementState(player.RIGHT);
            }else if ((x > 1) &&(player.getX()>(screenX/20) ) ){
                player.setMovementState(player.LEFT);
            }else {
                player.setMovementState(player.STOPPED);
            }

        // spawn a bullet
        if(playerBullets[nextPlayerBullet].shoot(player.getX() +
                player.getLength() / 2, screenY, playerBullets[nextPlayerBullet].UP)) {

            // Shot fired
            soundPool.play(shootID, volume, volume, 0, 0, 1);
            // Prepare for the next shot
            nextPlayerBullet++;

            // Loop back to the first one if we have reached the last
            if (nextPlayerBullet == maxplayerBullets) {
                // This stops the firing of another bullet until one completes its journey
                // Because if bullet 0 is still active shoot returns false.
                nextPlayerBullet = 0;
            }
        }
    }
    private void finishFunction() {
        Activity activity = (Activity)getContext();
        activity.finish();
    }
}
