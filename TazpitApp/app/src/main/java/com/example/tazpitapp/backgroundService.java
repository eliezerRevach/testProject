package com.example.tazpitapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.gson.Gson;
import com.google.type.DateTime;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class backgroundService extends Service {
    final int sec=1000;
    boolean StopCity=false;

    private void AlertIfInRange() {
        if(!checkTimeAndDateIfOn()){return;}
        System.out.println("Check if in range ");
        FirebaseFirestore.getInstance() .collection("Scenarios").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {

                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {

                        System.out.println(document.getId());
                        DocumentReference mDocRef= FirebaseFirestore.getInstance().document("Scenarios/"+document.getId());
                        mDocRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                            @Override
                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                if(!getStateOfGps()){
//                                    Object town=documentSnapshot.getData().get("עיר");
//                                    if(!inTown(town)){Notification(document.getId(), town);}
                                }
                                else{
                                    double Range = 0;
                                    try {
                                        if(checkImporent(documentSnapshot)&&isItAfterTime(toTimestamp(documentSnapshot.getData().get("timeCreated")),getLastTime())) {
                                            System.out.println("calulation range");
                                            Range = Double.parseDouble(Range((GeoPoint) documentSnapshot.getData().get("מיקום")));
                                            if (Range < getUserRangeChoice()) {
                                                setLastTime(toTimestamp(documentSnapshot.getData().get("timeCreated")));
                                                System.out.println(document.getId() + " IS in range of " + Range);
                                                Notification(document.getId(), Range);

                                            }
                                        }
                                    } catch (IOException e) {
                                    }
                                }


                            }
                        });
                    }

                }
            }
        });

    }
    private LocationCallback locationcallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull @NotNull LocationResult locationResult) {
            super.onLocationResult(locationResult);
            if (locationResult != null && locationResult.getLastLocation() != null&&FirebaseAuth.getInstance().getCurrentUser()!=null) {
                double latitude = locationResult.getLastLocation().getLatitude();
                double longitude = locationResult.getLastLocation().getLongitude();
                Log.d("Location_Update", latitude + "," + longitude);
                //call funcation here to compare with new scenerios if the distance is right and call the user if  true
                SharedPreferences sharedPreferences = getSharedPreferences(constants.SHARED_PREFS, MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(constants.latOfGps, String.valueOf(latitude));
                editor.putString(constants.longOfGps, String.valueOf(longitude));
                editor.apply();
               // AlertIfInRange();

            }
        }
    };

//    private boolean inTown(Object town) { //town from scenerio.. will return if the user town is the same as the scenerio
//        FirebaseAuth userIdentifier=FirebaseAuth.getInstance();
//        String UID = userIdentifier.getCurrentUser().getUid();
//        FirebaseDatabase database = FirebaseDatabase.getInstance();
//        DatabaseReference ref=database.getReference("Users/"+UID+"/"+"City");
//       // Tasks.await();
//       // Object userTown=
//          Task<FirebaseDatabase>  task=    ref.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
//              private String TAG;
//
//              @Override
//                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
//                        if (task.isSuccessful()) {
//                            DocumentSnapshot document = task.getResult();
//                            if (document.exists()) {
//
//                            } else {
//                                Log.d("aadd", "No such document");
//                            }
//                        }else {
//                            Log.d(TAG, "get failed with ", task.getException());
//                        }
//                    }
//                });
//        Object userTown=null;
//        try {
//            Tasks.await(task);
//            userTown =task.getResult();
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        if(userTown.equals(town)){
//            System.out.println(town);System.out.println(userTown);
//            return true;}System.out.println(town);System.out.println(userTown);return false;
//
//    }

    private static final String CHANNEL_ID = "12341234" ;

    private void Notification(String id, Object range) {
        createNotificationChannel();
        sendNotfication(id,range);

    }

    private void sendNotfication(String id, Object location) {//id = scenerio name , location =range from u (only in gps,in city put 0)
        //after that it will create a notification and show to the user for each case, city or gps
        String context="";
        String title="";
        if(getStateOfGps()){title=" is close";
            context="Is Range is :"+(double)((double)location*1000)+" meters from u\nclick to open list";
        }
        else{
            title=" is in your town";
        }
// Create an explicit intent for an Activity in your app
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.tps_logo_background)
                .setContentTitle(id+title)
                .setContentText(context)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        Intent resultIntent = new Intent(this, SceneriosDetailActivity.class);
        resultIntent.putExtra(SceneriosDetailFragment.ARG_ITEM_CONTENT,id);
// Create the TaskStackBuilder and add the intent, which inflates the back stack
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
// Get the PendingIntent containing the entire back stack
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(resultPendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

// notificationId is a unique int for each notification that you must define
        notificationManager.notify(132214142, builder.build());


    }
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "getString(R.string.channel_name)";
            String description = "getString(R.string.channel_description";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public  boolean getStateOfGps(){//return true or false if the gps is working
        SharedPreferences sharedPreferences = getSharedPreferences(constants.SHARED_PREFS, MODE_PRIVATE);
        return sharedPreferences.getBoolean(constants.gpsState,false);
    }
    public  String getlatOfGps(){//get latitude of curret location
        SharedPreferences sharedPreferences = getSharedPreferences(constants.SHARED_PREFS, MODE_PRIVATE);
        return sharedPreferences.getString(constants.latOfGps,"");
    }
    public  String getlongOfGps(){//get longtitude of curret location
        SharedPreferences sharedPreferences = getSharedPreferences(constants.SHARED_PREFS, MODE_PRIVATE);
        return sharedPreferences.getString(constants.longOfGps,"");
    }
    //       try {//must be try and catch,  lat and long for the array in size of 1 , then get into tv_address the address
    //                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
    //                tv_addressInString=addresses.get(0).getAddressLine(0);
    //                tv_address.setText(tv_addressInString);
    //            } catch (Exception e) {
    //                tv_address.setText("Unale to get address");
    //            }
    public  String Range(GeoPoint gpsLocation) throws IOException {
        String re="";
            if(gpsLocation==null){throw new IOException("");}
            double latCurrent=0;
            double lonCurrent=0;
            if(getStateOfGps()){
                latCurrent=Double.parseDouble(getlatOfGps());
                lonCurrent=Double.parseDouble(getlongOfGps());

            }
            else{
            return "";


            }
            double latScenerio=gpsLocation.getLatitude();
            double lonScenerio=gpsLocation.getLongitude();
            double result=Math.pow(Math.pow((111*(latCurrent-latScenerio)),2.0)+Math.pow((111*(lonCurrent-lonScenerio)),2.0),0.5);
            return String.valueOf(result);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("not yet implemted");

    }

    private void startLocationService() {
        String channlId = "location_notification_channel";
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent resultIntent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                getApplicationContext(),
                channlId
        );
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("Location From City");
        builder.setDefaults(NotificationCompat.DEFAULT_ALL);
        builder.setContentText("Running");
        builder.setContentIntent(pendingIntent);
        builder.setAutoCancel(false);
        builder.setPriority(NotificationCompat.PRIORITY_MAX);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager != null
                    && notificationManager.getNotificationChannel(channlId) == null) {
                NotificationChannel notificationChannel = new NotificationChannel(
                        channlId,
                        "Location Service",
                        NotificationManager.IMPORTANCE_HIGH

                );
                notificationChannel.setDescription("This channel is used by location service");
                notificationManager.createNotificationChannel(notificationChannel);


            }
        }
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(locationRequest, locationcallback, Looper.getMainLooper());
        startForeground(constants.LOCATION_SERVICE_ID,builder.build());


    }
    private  void stopLocationService(){

        LocationServices.getFusedLocationProviderClient(this)
                .removeLocationUpdates(locationcallback);
        stopForeground(true);
        //stopSelf();
}
    public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!= null){
            String action =intent.getAction();
            if(action!=null){
                if(action.equals(constants.ACTION_START_LOCATION_SERVICE)){//gps on
                    StopCity=true;
                    startLocationService();
                }
                else if(action.equals(constants.ACTION_STOP_LOCATION_SERVICE)){//gps off
                    StopCity=false;
                    stopLocationService();
                    //AlertifInCity();
                }

            }
        }
        return super.onStartCommand(intent,flags,startId);
    }

    private void setLastTime(Timestamp time){
        SharedPreferences sharedPreferences = getSharedPreferences(constants.SHARED_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(constants.LastTimeAndDate,new Gson().toJson(time));
        editor.apply();

    }//set last time from timestamp in the data
    private Timestamp getLastTime(){
        SharedPreferences sharedPreferences = getSharedPreferences(constants.SHARED_PREFS, MODE_PRIVATE);
        String inJson = sharedPreferences.getString(constants.LastTimeAndDate,"default");
        if(inJson.equals("default")){return new Timestamp(new Date(0,0,0));}
        Gson gson = new Gson();
        Timestamp today = (Timestamp) (gson.fromJson(inJson,Timestamp.class));
        return today;
    }//get last event time he saw
    private double getUserRangeChoice(){
        SharedPreferences sharedPreferences = getSharedPreferences(constants.SHARED_PREFS, MODE_PRIVATE);
        float inJson = sharedPreferences.getFloat(constants.rangeChoice,-1);
        if(inJson==-1){return 10.00;}
        double range =(double)inJson;
        return range;
    }//get range that the user put in settings
    private boolean isItAfterTime(Timestamp isIt,Timestamp AfterTHisOne){
        if(isIt.toDate().after(AfterTHisOne.toDate())){return true;}return false;

    }//check if isIt More late in date and time then Atter THisOne
    private Timestamp toTimestamp(Object time){
            try{return (Timestamp)time;}
            catch (Exception e){
                e.printStackTrace();
                throw new RuntimeException("error in toTimeStamp,not an Timestanp type");
            }
    }//cast to Timestamp
        private boolean checkTimeAndDateIfOn(){
            Date date = new Date();
            int currentHours=date.getHours()+3;
            int currentMinutes=date.getMinutes();
            Calendar calendar = Calendar.getInstance();
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            if(currentHours>23){currentHours=currentHours-24;
                dayOfWeek=dayOfWeek+1;
                if(dayOfWeek>7){dayOfWeek=dayOfWeek-7;}
            }
            dayTime daytime = null;

            for(int i=1;i<=7;i++){
                if(dayOfWeek==i) {
                    daytime = getDayTime(constants.daysNames[i-1]);

                }
                Log.d("time in settings",((String)(i+" "))+getDayTime(constants.daysNames[i-1]).getHourStart()+"->"+getDayTime(constants.daysNames[i-1]).getHourEnd()+",Current time is:"+currentHours+":"+currentMinutes+",day of week is: "+dayOfWeek);
            }

            if(daytime.getHourStart()<=currentHours&&currentHours<=daytime.getHourEnd()){
                if(daytime.getHourStart()==currentHours&&daytime.getMinuteStart()>currentHours){return false;}
                if(daytime.getHourEnd()==currentHours&&daytime.getMinuteEnd()<currentHours){return false;}
                return true;
            }
            return false;

    }//check if its in the time and day that the user placed in settings

    private dayTime getDayTime(String daysName) {
        SharedPreferences sharedPreferences = getSharedPreferences(constants.SHARED_PREFS, MODE_PRIVATE);
        String inJson = sharedPreferences.getString(daysName,"default");
        return ((dayTime) new Gson().fromJson(inJson,dayTime.class));

    }//get dayTime Object from string name of shared--only used in checktimeanddateifon,irrelevant in other places

    private boolean checkImporent(DocumentSnapshot documentSnapshot){//will check if דחוף Is true
        return documentSnapshot.getBoolean("דחיפות");
    }//get the document(that was alridy given from firebase) and check if inside the importent is on

    //inTown(Object town)// will return if the user town is the same as the scenerio


    public Context context = this;
    public Handler handler = null;
    public static Runnable runnable = null;
    public void onCreate() {
        Toast.makeText(this, "Service created!", Toast.LENGTH_LONG).show();
        if(!StopCity)
            System.out.print("Stopsity is not \n");
        if(StopCity==true)
            System.out.print("Stopsity is yes \n");

        handler = new Handler();
        runnable = new Runnable() {
            public void run() {
                if(!StopCity) {
                    AlertifInCity();
                    Toast.makeText(context, "Service is still running", Toast.LENGTH_LONG).show();
                    handler.postDelayed(runnable, 10000);
                }
            }
        };

        handler.postDelayed(runnable, 15000);
    }

    private void AlertifInCity() {//Check if its in city, if yes ,do a notification
        if(!checkTimeAndDateIfOn()){return;}
        System.out.println("Check if in city ");
        FirebaseFirestore.getInstance() .collection("Scenarios").get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {

                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {

                        System.out.println(document.getId());
                        DocumentReference mDocRef= FirebaseFirestore.getInstance().document("Scenarios/"+document.getId());
                        mDocRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                            @Override
                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                if(!getStateOfGps()){
                                  // Object town=documentSnapshot.getData().get("עיר");
                                    String townScenario=documentSnapshot.getData().get("עיר").toString();
                                       FirebaseAuth userIdentifier=FirebaseAuth.getInstance();
                                 String UID = userIdentifier.getCurrentUser().getUid();
//                         FirebaseDatabase database = FirebaseDatabase.getInstance();
                      //DatabaseReference ref=FirebaseDatabase.getInstance().getReference("Users").child(UID);
                                //======================
                                    DatabaseReference mref = FirebaseDatabase.getInstance().getReference("Users").child(UID).child("City");
                                  System.out.println(mref+"\n");
                                    mref.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(DataSnapshot dataSnapshot) {
                                            String userCity = dataSnapshot.getValue(String.class);
                                           System.out.println("userCity is : "+userCity +"\n");
                                           if(userCity.equals(townScenario)) {
                                               if (checkImporent(documentSnapshot) && isItAfterTime(toTimestamp(documentSnapshot.getData().get("timeCreated")), getLastTime())) {
                                                   setLastTime(documentSnapshot.getData().get("timeCreated"));
                                                   System.out.println("in city mode check2");
                                                   sendNotfication(documentSnapshot.getId(), 0);

                                               }

                                           }
                                        }

                                        @Override
                                        public void onCancelled(DatabaseError databaseError) {

                                        }
                                    });


                                }
                                else{
                                    System.out.println("AlertIfInCity gps is on"); //should not happen

                                }


                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e("error33", "Error requesting connection", e);
                            }
                        });;
                    }

                }
            }
        });


    }
//
//        AlertIfInRange();
//        String channlId = "location_notification_channel";
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(
//                getApplicationContext(),
//                channlId
//        );
//        builder.setSmallIcon(R.mipmap.ic_launcher);
//        builder.setContentTitle("Location From City");
//        builder.setDefaults(NotificationCompat.DEFAULT_ALL);
//        builder.setContentText("Running");
//        builder.setContentIntent(pendingIntent);
//        builder.setAutoCancel(false);
//        builder.setPriority(NotificationCompat.PRIORITY_MAX);
    }




//    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//    final FirebaseDatabase database = FirebaseDatabase.getInstance();
//    DatabaseReference ref = database.getReference("Users/"+ user.getUid()+"/City");
//
//    Geocoder gc=new Geocoder(this);
//    List<Address> ads = gc.getFromLocationName((String) ref.get().getResult().getValue(),1);
//
//                    latCurrent=ads.get(0).getLatitude();
//                            lonCurrent=ads.get(0).getLongitude();